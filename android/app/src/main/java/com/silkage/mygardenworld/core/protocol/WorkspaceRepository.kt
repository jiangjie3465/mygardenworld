package com.silkage.mygardenworld.core.protocol

import com.mygardenworld.v1.AccountStatus
import com.mygardenworld.v1.AlipayLoginProgress
import com.mygardenworld.v1.Event
import com.mygardenworld.v1.FeatureCapability
import com.mygardenworld.v1.WorkspaceError
import com.mygardenworld.v1.WorkspaceLogPage
import com.mygardenworld.v1.WorkspaceLogPageKind
import com.mygardenworld.v1.WorkspaceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory log window for one account, newest first. */
data class LogWindow(
    val events: List<Event> = emptyList(),
    val nextBeforeId: Long = 0,
    val hasMoreBefore: Boolean = false,
    val gapDetected: Boolean = false,
    val loadingOlder: Boolean = false,
)

data class WorkspaceUiState(
    val connection: WorkspaceConnectionState = WorkspaceConnectionState.CLOSED,
    val serverVersion: String = "",
    val capabilities: List<FeatureCapability> = emptyList(),
    val statuses: Map<Long, AccountStatus> = emptyMap(),
    val selectedAccountId: Long = 0,
    val state: WorkspaceState? = null,
    val logs: LogWindow = LogWindow(),
    val alipay: AlipayLoginProgress? = null,
    val lastError: WorkspaceError? = null,
    val authExpired: Boolean = false,
) {
    val online: Boolean get() = connection == WorkspaceConnectionState.OPEN
    val selectedStatus: AccountStatus? get() = statuses[selectedAccountId]
}

/**
 * Reduces WorkspaceEvents into WorkspaceUiState. Pure apart from the state
 * flow so it can be driven directly in tests.
 */
class WorkspaceRepository(private val maxLogEvents: Int = 1_000) {
    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state

    fun select(accountId: Long) {
        _state.update { current ->
            if (current.selectedAccountId == accountId) current
            else current.copy(selectedAccountId = accountId, state = null, logs = LogWindow(), lastError = null)
        }
    }

    fun markLoadingOlder() = _state.update { it.copy(logs = it.logs.copy(loadingOlder = true)) }

    fun clearError() = _state.update { it.copy(lastError = null) }

    fun onEvent(event: WorkspaceEvent) {
        when (event) {
            is WorkspaceEvent.Connection -> _state.update { it.copy(connection = event.state) }
            is WorkspaceEvent.Ready -> _state.update {
                it.copy(
                    serverVersion = event.ready.serverVersion,
                    capabilities = event.ready.featureCapabilitiesList,
                    statuses = event.ready.accountsList.associateBy { s -> s.accountId },
                    authExpired = false,
                )
            }
            is WorkspaceEvent.Statuses -> _state.update { current ->
                val merged = current.statuses.toMutableMap()
                event.batch.accountsList.forEach { merged[it.accountId] = it }
                current.copy(statuses = merged)
            }
            is WorkspaceEvent.Snapshot -> _state.update { current ->
                val snapshot = event.snapshot
                val accountId = snapshot.state.accountId
                if (current.selectedAccountId != 0L && accountId != current.selectedAccountId) current
                else current.copy(
                    selectedAccountId = accountId,
                    state = snapshot.state,
                    logs = if (snapshot.hasLogs()) applyLogPage(LogWindow(), snapshot.logs) else LogWindow(),
                    lastError = null,
                )
            }
            is WorkspaceEvent.Patch -> _state.update { current ->
                if (event.patch.accountId != 0L && event.patch.accountId != current.selectedAccountId) current
                else current.copy(state = WorkspaceStateMerger.apply(current.state, event.patch))
            }
            is WorkspaceEvent.Logs -> _state.update { current ->
                if (event.page.accountId != current.selectedAccountId) current
                else current.copy(logs = applyLogPage(current.logs, event.page))
            }
            is WorkspaceEvent.AlipayLogin -> _state.update { it.copy(alipay = event.progress) }
            is WorkspaceEvent.Error -> _state.update { it.copy(lastError = event.error) }
            is WorkspaceEvent.AuthExpired -> _state.update { it.copy(authExpired = true, connection = WorkspaceConnectionState.CLOSED) }
            is WorkspaceEvent.RedeemAttempts -> Unit
        }
    }

    fun clearAlipay() = _state.update { it.copy(alipay = null) }

    private fun applyLogPage(window: LogWindow, page: WorkspaceLogPage): LogWindow {
        val incoming = page.eventsList
        return when (page.kind) {
            WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_RECENT -> LogWindow(
                events = incoming.take(maxLogEvents),
                nextBeforeId = page.nextBeforeId,
                hasMoreBefore = page.hasMoreBefore,
                gapDetected = page.gapDetected,
            )
            WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_BEFORE -> window.copy(
                events = mergeNewestFirst(window.events, incoming, appendOlder = true).take(maxLogEvents),
                nextBeforeId = page.nextBeforeId,
                hasMoreBefore = page.hasMoreBefore,
                loadingOlder = false,
            )
            WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_AFTER,
            WorkspaceLogPageKind.WORKSPACE_LOG_PAGE_KIND_LIVE,
            -> window.copy(
                events = mergeNewestFirst(window.events, incoming, appendOlder = false).take(maxLogEvents),
                gapDetected = window.gapDetected || page.gapDetected,
            )
            else -> window
        }
    }

    /** Both lists are newest-first; ids deduplicate persisted events. */
    private fun mergeNewestFirst(current: List<Event>, incoming: List<Event>, appendOlder: Boolean): List<Event> {
        if (incoming.isEmpty()) return current
        val seen = HashSet<Long>()
        current.forEach { if (it.id > 0) seen += it.id }
        val fresh = incoming.filter { it.id <= 0 || seen.add(it.id) }
        return if (appendOlder) current + fresh else fresh + current
    }
}
