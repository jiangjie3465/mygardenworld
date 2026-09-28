package com.silkage.mygardenworld.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.Account
import com.mygardenworld.v1.AlipayLoginStatus
import com.mygardenworld.v1.User
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.ui.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlipayQr(val loginId: String, val content: String, val status: AlipayLoginStatus, val error: String)

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val user: User? = null,
    val loading: Boolean = false,
    val error: String = "",
    val busyAccountId: Long = 0,
    val bulkAction: String = "",
    val creating: Boolean = false,
    val qr: AlipayQr? = null,
    val createdAccountId: Long = 0,
)

class AccountsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state
    val workspace = container.workspace.state

    init {
        refresh()
        viewModelScope.launch {
            // Deletion finishes in the background; the account leaves the
            // status batch when cleanup commits, so re-list once it is gone.
            val relisted = HashSet<Long>()
            container.workspace.state.collect { ws ->
                if (!ws.online || ws.statuses.isEmpty()) return@collect
                val gone = _state.value.accounts.filter { account ->
                    account.deletionPending && ws.statuses[account.id] == null && relisted.add(account.id)
                }
                if (gone.isNotEmpty()) refresh()
            }
        }
        viewModelScope.launch {
            container.workspace.state.collect { ws ->
                val progress = ws.alipay ?: return@collect
                _state.update { current ->
                    val qr = current.qr ?: return@update current
                    if (qr.loginId != progress.loginId) current
                    else current.copy(qr = qr.copy(status = progress.status, error = progress.loginError))
                }
                if (progress.status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_COMPLETE) {
                    container.workspace.clearAlipay()
                    _state.update { it.copy(createdAccountId = progress.account.id) }
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            try {
                val user = runCatching { container.accounts.me() }.getOrNull()
                val accounts = container.accounts.list()
                _state.update { it.copy(accounts = accounts, user = user ?: it.user, loading = false) }
            } catch (e: ConnectException) {
                _state.update { it.copy(loading = false, error = e.userMessage) }
            }
        }
    }

    /** Web parity: start is ConnectAccount and pause/stop is DisconnectAccount; failures are shown, never swallowed. */
    fun toggleAutomation(account: Account, online: Boolean) = run(account.id) {
        if (online) container.accounts.disconnect(account.id) else container.accounts.connect(account.id)
    }

    fun stop(account: Account) = run(account.id) { container.accounts.disconnect(account.id) }

    /** Starts or pauses every eligible account sequentially, collecting per-account failures. */
    /** [only] limits the batch to the selected accounts; null means every account. */
    fun bulk(start: Boolean, only: Set<Long>? = null) {
        if (_state.value.bulkAction.isNotBlank() || _state.value.busyAccountId != 0L) return
        val statuses = container.workspace.state.value.statuses
        val targets = _state.value.accounts.filter { account ->
            (only == null || account.id in only) &&
                !Format.accountDeleting(account, statuses[account.id]) && Format.accountConnected(account, statuses[account.id]) != start
        }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(bulkAction = if (start) "start" else "pause", error = "") }
            val failures = ArrayList<String>()
            for (account in targets) {
                _state.update { it.copy(busyAccountId = account.id) }
                try {
                    if (start) container.accounts.connect(account.id) else container.accounts.disconnect(account.id)
                } catch (e: ConnectException) {
                    failures += "${Format.accountNickname(account)}: ${e.userMessage}"
                }
            }
            container.socket.resync()
            _state.update {
                it.copy(
                    bulkAction = "",
                    busyAccountId = 0,
                    error = when {
                        failures.isEmpty() -> ""
                        failures.size == 1 -> failures.first()
                        else -> "${failures.size} 个账号失败：" + failures.take(3).joinToString("；") + if (failures.size > 3) "…" else ""
                    },
                )
            }
            refresh()
        }
    }

    fun createIos(username: String, password: String, initialPolicyAccountId: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = "") }
            try {
                val response = container.accounts.createIos(username, password, initialPolicyAccountId)
                _state.update { it.copy(creating = false, error = response.loginError, createdAccountId = response.account.id) }
                refresh()
                onDone(true)
            } catch (e: ConnectException) {
                _state.update { it.copy(creating = false, error = e.userMessage) }
                onDone(false)
            }
        }
    }

    fun startAlipay(initialPolicyAccountId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = "", qr = null) }
            try {
                val response = container.accounts.startAlipayLogin(initialPolicyAccountId = initialPolicyAccountId)
                _state.update { it.copy(creating = false, qr = AlipayQr(response.loginId, response.qrContent, response.status, "")) }
                container.socket.watchAlipayLogin(response.loginId)
            } catch (e: ConnectException) {
                _state.update { it.copy(creating = false, error = e.userMessage) }
            }
        }
    }

    fun clearQr() = _state.update { it.copy(qr = null) }

    fun consumeCreated() = _state.update { it.copy(createdAccountId = 0) }

    fun dismissError() = _state.update { it.copy(error = "") }

    private fun run(accountId: Long, block: suspend () -> Unit) {
        if (_state.value.busyAccountId != 0L) return
        viewModelScope.launch {
            _state.update { it.copy(busyAccountId = accountId, error = "") }
            try {
                block()
                refresh()
            } catch (e: ConnectException) {
                _state.update { it.copy(error = e.userMessage) }
            } finally {
                _state.update { it.copy(busyAccountId = 0) }
            }
        }
    }
}
