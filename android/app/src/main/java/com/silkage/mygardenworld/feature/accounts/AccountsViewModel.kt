package com.silkage.mygardenworld.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.Account
import com.mygardenworld.v1.AlipayLoginStatus
import com.mygardenworld.v1.User
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
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

    fun toggleAutomation(account: Account, online: Boolean) = run(account.id) {
        if (online) {
            container.accounts.disableAutomation(account.id)
            container.accounts.disconnect(account.id)
        } else {
            container.accounts.enableAutomation(account.id)
            runCatching { container.accounts.connect(account.id) }
        }
    }

    fun stop(account: Account) = run(account.id) {
        container.accounts.disableAutomation(account.id)
        container.accounts.disconnect(account.id)
    }

    fun createIos(username: String, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = "") }
            try {
                val response = container.accounts.createIos(username, password)
                _state.update { it.copy(creating = false, error = response.loginError, createdAccountId = response.account.id) }
                refresh()
                onDone(true)
            } catch (e: ConnectException) {
                _state.update { it.copy(creating = false, error = e.userMessage) }
                onDone(false)
            }
        }
    }

    fun startAlipay() {
        viewModelScope.launch {
            _state.update { it.copy(creating = true, error = "", qr = null) }
            try {
                val response = container.accounts.startAlipayLogin()
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
