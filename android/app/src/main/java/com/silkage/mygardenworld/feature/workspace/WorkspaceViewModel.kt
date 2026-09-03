package com.silkage.mygardenworld.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mygardenworld.v1.Account
import com.mygardenworld.v1.Policy
import com.silkage.mygardenworld.AppContainer
import com.silkage.mygardenworld.core.network.ConnectException
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
    val message: String = "",
    val error: String = "",
    val deleted: Boolean = false,
)

class WorkspaceViewModel(private val container: AppContainer, val accountId: Long) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceScreenState())
    val state: StateFlow<WorkspaceScreenState> = _state
    val workspace = container.workspace.state
    val catalog get() = container.catalog

    init {
        container.selectAccount(accountId)
        loadAccount()
        loadPolicy()
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
