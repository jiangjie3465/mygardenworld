package com.silkage.mygardenworld.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.Account
import com.mygardenworld.v1.AccountRedeemAttemptFilter
import com.mygardenworld.v1.AlipayLoginStatus
import com.mygardenworld.v1.Channel
import com.mygardenworld.v1.FmlRaceTask
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
import com.silkage.mygardenworld.core.protocol.ProtoJson
import com.silkage.mygardenworld.feature.accounts.AlipayQr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceScreenState(
    val account: Account? = null,
    val policy: Policy? = null,
    val policyDirty: Boolean = false,
    val policyLoading: Boolean = false,
    val savingPolicy: Boolean = false,
    val busyAction: String = "",
    val busyRaceTaskId: Long = 0,
    val busyRaceDeleteId: Long = 0,
    val raceMessage: String = "",
    val reauthBusy: Boolean = false,
    val reauthError: String = "",
    val reauthQr: AlipayQr? = null,
    val reauthDone: Boolean = false,
    val message: String = "",
    val error: String = "",
    val deleted: Boolean = false,
)

class WorkspaceViewModel(private val container: AppContainer, val accountId: Long) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceScreenState())
    val state: StateFlow<WorkspaceScreenState> = _state
    val workspace = container.workspace.state
    val catalog get() = container.catalog
    val protoJson: ProtoJson? get() = container.protoJson

    init {
        container.selectAccount(accountId)
        loadAccount()
        loadPolicy()
        viewModelScope.launch {
            container.workspace.state.collect { ws ->
                val progress = ws.alipay ?: return@collect
                val qr = _state.value.reauthQr ?: return@collect
                if (qr.loginId != progress.loginId) return@collect
                _state.update { it.copy(reauthQr = qr.copy(status = progress.status, error = progress.loginError)) }
                if (progress.status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_COMPLETE) {
                    container.workspace.clearAlipay()
                    _state.update { it.copy(reauthQr = null, reauthDone = true, message = "已重新登录，配置、历史与运行／暂停设置保持不变") }
                    loadAccount()
                    container.socket.resync()
                }
            }
        }
    }

    fun loadAccount() {
        viewModelScope.launch {
            runCatching { container.accounts.list().firstOrNull { it.id == accountId } }
                .onSuccess { account -> _state.update { it.copy(account = account) } }
        }
    }

    fun loadPolicy() {
        viewModelScope.launch {
            _state.update { it.copy(policyLoading = true) }
            try {
                val policy = container.policies.get(accountId)
                _state.update { it.copy(policy = policy, policyDirty = false, policyLoading = false) }
            } catch (e: ConnectException) {
                _state.update { it.copy(policyLoading = false, error = e.userMessage) }
            }
        }
    }

    /** Applies a validated whole-policy import to the editor; it takes effect only after saving. */
    fun applyImportedPolicy(policy: Policy) {
        _state.update { it.copy(policy = policy, policyDirty = true, message = "已导入到编辑器，保存后生效") }
    }

    fun editPolicy(transform: (Policy) -> Policy) {
        _state.update { current -> current.policy?.let { current.copy(policy = transform(it), policyDirty = true, message = "") } ?: current }
    }

    fun savePolicy() {
        val policy = _state.value.policy ?: return
        if (!container.workspace.state.value.online) {
            _state.update { it.copy(error = "当前离线，无法保存设置") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(savingPolicy = true, error = "", message = "") }
            try {
                val saved = container.policies.set(accountId, policy)
                _state.update { it.copy(policy = saved, policyDirty = false, savingPolicy = false, message = "设置已保存") }
            } catch (e: ConnectException) {
                _state.update { it.copy(savingPolicy = false, error = e.userMessage) }
            }
        }
    }

    fun setAutomation(enabled: Boolean) = action("automation") {
        if (enabled) container.accounts.enableAutomation(accountId) else container.accounts.disableAutomation(accountId)
        loadPolicy()
    }

    fun connect() = action("login") { container.accounts.connect(accountId); loadAccount() }

    fun disconnect() = action("logout") { container.accounts.disconnect(accountId); loadAccount() }

    /** iOS: verifies the new password and logs in again. */
    fun reauthenticate(password: String) {
        if (_state.value.reauthBusy || password.isBlank()) return
        if (!container.workspace.state.value.online) {
            _state.update { it.copy(reauthError = "当前离线，无法重新登录") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(reauthBusy = true, reauthError = "") }
            try {
                val response = container.accounts.reauthenticate(accountId, password)
                _state.update { it.copy(account = response.account, reauthBusy = false, reauthDone = true, message = "已重新登录，配置、历史与运行／暂停设置保持不变") }
                container.socket.resync()
            } catch (e: ConnectException) {
                _state.update { it.copy(reauthBusy = false, reauthError = e.userMessage) }
            }
        }
    }

    /** Alipay: the QR must be scanned with the account's original Alipay identity. */
    fun startAlipayReauth() {
        if (_state.value.reauthBusy) return
        if (!container.workspace.state.value.online) {
            _state.update { it.copy(reauthError = "当前离线，无法获取二维码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(reauthBusy = true, reauthError = "", reauthQr = null) }
            try {
                val response = container.accounts.startAlipayLogin(accountId = accountId)
                _state.update { it.copy(reauthBusy = false, reauthQr = AlipayQr(response.loginId, response.qrContent, response.status, "")) }
                container.socket.watchAlipayLogin(response.loginId)
            } catch (e: ConnectException) {
                _state.update { it.copy(reauthBusy = false, reauthError = e.userMessage) }
            }
        }
    }

    fun closeReauth() = _state.update { it.copy(reauthBusy = false, reauthError = "", reauthQr = null, reauthDone = false) }

    val isAlipay: Boolean get() = _state.value.account?.channel == Channel.CHANNEL_ALIPAY

    fun delete() = action("delete") {
        container.accounts.delete(accountId)
        _state.update { it.copy(deleted = true) }
    }

    fun loadOlderLogs() {
        val logs = container.workspace.state.value.logs
        if (logs.loadingOlder || !logs.hasMoreBefore) return
        container.workspace.markLoadingOlder()
        container.socket.loadLogs(accountId, logs.nextBeforeId)
    }

    fun resync() = container.socket.resync()

    fun loadRedeemAttempts(filter: AccountRedeemAttemptFilter, more: Boolean = false) {
        val feed = container.workspace.state.value.redeem
        if (feed.loading) return
        if (more && !feed.hasMore) return
        container.workspace.markRedeemLoading(filter)
        container.socket.loadRedeemAttempts(accountId, beforeId = if (more && filter == feed.filter) feed.nextBeforeId else 0, filter = filter)
    }

    fun takeRaceTask(task: FmlRaceTask) {
        if (_state.value.busyRaceTaskId != 0L) return
        if (!container.workspace.state.value.online) {
            _state.update { it.copy(raceMessage = "当前离线，无法接取任务") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyRaceTaskId = task.msId, raceMessage = "") }
            try {
                container.accounts.takeUnionRaceTask(accountId, task.msId)
                _state.update { it.copy(raceMessage = "接取请求已成功，正在等待任务状态同步。") }
            } catch (e: ConnectException) {
                _state.update { it.copy(raceMessage = e.userMessage) }
            } finally {
                _state.update { it.copy(busyRaceTaskId = 0) }
            }
        }
    }

    fun deleteRaceTask(task: FmlRaceTask) {
        if (_state.value.busyRaceDeleteId != 0L || _state.value.busyRaceTaskId != 0L) return
        if (!container.workspace.state.value.online) {
            _state.update { it.copy(raceMessage = "当前离线，无法删除任务") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyRaceDeleteId = task.msId, raceMessage = "") }
            try {
                container.accounts.deleteUnionRaceTask(accountId, task.msId)
                _state.update { it.copy(raceMessage = "删除请求已成功，正在等待任务池刷新。") }
            } catch (e: ConnectException) {
                _state.update { it.copy(raceMessage = e.userMessage) }
            } finally {
                _state.update { it.copy(busyRaceDeleteId = 0) }
            }
        }
    }

    fun dismissMessages() = _state.update { it.copy(error = "", message = "") }

    private fun action(name: String, block: suspend () -> Unit) {
        if (_state.value.busyAction.isNotBlank()) return
        if (!container.workspace.state.value.online) {
            _state.update { it.copy(error = "当前离线，无法执行操作") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busyAction = name, error = "") }
            try {
                block()
            } catch (e: ConnectException) {
                _state.update { it.copy(error = e.userMessage) }
            } finally {
                _state.update { it.copy(busyAction = "") }
            }
        }
    }
}
