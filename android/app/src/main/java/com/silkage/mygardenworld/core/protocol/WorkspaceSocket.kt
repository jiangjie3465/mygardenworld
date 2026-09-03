package com.silkage.mygardenworld.core.protocol

import android.util.Log
import com.mygardenworld.v1.AccountRedeemAttemptFilter
import com.mygardenworld.v1.AccountRedeemAttemptPage
import com.mygardenworld.v1.AccountStatusBatch
import com.mygardenworld.v1.AlipayLoginProgress
import com.mygardenworld.v1.AlipayLoginStatus
import com.mygardenworld.v1.LoadAccountRedeemAttempts
import com.mygardenworld.v1.LoadWorkspaceLogs
import com.mygardenworld.v1.OpenWorkspace
import com.mygardenworld.v1.ResyncWorkspace
import com.mygardenworld.v1.SelectWorkspaceAccount
import com.mygardenworld.v1.WatchAlipayLogin
import com.mygardenworld.v1.WorkspaceClientFrame
import com.mygardenworld.v1.WorkspaceError
import com.mygardenworld.v1.WorkspaceLogPage
import com.mygardenworld.v1.WorkspacePatch
import com.mygardenworld.v1.WorkspaceReady
import com.mygardenworld.v1.WorkspaceServerFrame
import com.mygardenworld.v1.WorkspaceSnapshot
import com.silkage.mygardenworld.core.network.TokenAuthority
import com.silkage.mygardenworld.core.network.workspaceSocketUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

enum class WorkspaceConnectionState { CONNECTING, OPEN, CLOSED }

sealed interface WorkspaceEvent {
    data class Connection(val state: WorkspaceConnectionState) : WorkspaceEvent
    data class Ready(val ready: WorkspaceReady) : WorkspaceEvent
    data class Statuses(val batch: AccountStatusBatch) : WorkspaceEvent
    data class Snapshot(val snapshot: WorkspaceSnapshot) : WorkspaceEvent
    data class Patch(val patch: WorkspacePatch) : WorkspaceEvent
    data class Logs(val page: WorkspaceLogPage) : WorkspaceEvent
    data class RedeemAttempts(val page: AccountRedeemAttemptPage) : WorkspaceEvent
    data class AlipayLogin(val progress: AlipayLoginProgress) : WorkspaceEvent
    data class Error(val error: WorkspaceError) : WorkspaceEvent
    data object AuthExpired : WorkspaceEvent
}

/**
 * Binary Protobuf client for `/api/workspace`. Mirrors the Web client: the
 * first frame is OpenWorkspace carrying the access token, sequences are
 * strictly checked, a gap triggers resync, log cursors drive reconnect
 * catch-up, and close code 4401 refreshes the token before reconnecting.
 */
class WorkspaceSocket(
    private val scope: CoroutineScope,
    private val tokenAuthority: TokenAuthority,
    private val factory: WebSocket.Factory,
    private val baseUrlProvider: () -> String?,
) {
    private val _events = MutableSharedFlow<WorkspaceEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val events: SharedFlow<WorkspaceEvent> = _events

    private val tracker = WorkspaceSequenceTracker()
    private val lock = Any()
    private var socket: WebSocket? = null
    private var stopped = true
    private var reconnectJob: Job? = null
    private var reconnectDelayMs = RECONNECT_INITIAL_MS
    private var selectedAccountId = 0L
    private var alipayLoginId = ""
    private var requestId = 0L

    fun start(selectedAccountId: Long = 0) {
        synchronized(lock) {
            this.selectedAccountId = selectedAccountId
            if (!stopped) return
            stopped = false
        }
        connect()
    }

    fun stop() {
        val current: WebSocket?
        synchronized(lock) {
            stopped = true
            reconnectJob?.cancel()
            reconnectJob = null
            current = socket
            socket = null
        }
        current?.close(1000, "workspace closed")
        emit(WorkspaceEvent.Connection(WorkspaceConnectionState.CLOSED))
    }

    fun selectAccount(accountId: Long) {
        synchronized(lock) { selectedAccountId = accountId }
        if (accountId == 0L) return
        send { setSelectAccount(SelectWorkspaceAccount.newBuilder().setAccountId(accountId).setAfterLogId(tracker.afterLogId(accountId))) }
    }

    fun resync() {
        val accountId = synchronized(lock) { selectedAccountId }
        if (accountId == 0L) return
        send { setResync(ResyncWorkspace.newBuilder().setAfterLogId(tracker.afterLogId(accountId))) }
    }

    fun loadLogs(accountId: Long, beforeId: Long = 0, limit: Int = 200): Boolean {
        if (accountId == 0L) return false
        return send { setLoadLogs(LoadWorkspaceLogs.newBuilder().setAccountId(accountId).setBeforeId(beforeId).setLimit(limit)) }
    }

    fun loadRedeemAttempts(
        accountId: Long,
        beforeId: Long = 0,
        limit: Int = 20,
        filter: AccountRedeemAttemptFilter = AccountRedeemAttemptFilter.ACCOUNT_REDEEM_ATTEMPT_FILTER_ALL,
    ): Boolean {
        if (accountId == 0L) return false
        return send {
            setLoadRedeemAttempts(
                LoadAccountRedeemAttempts.newBuilder().setAccountId(accountId).setBeforeId(beforeId).setLimit(limit).setFilter(filter),
            )
        }
    }

    fun watchAlipayLogin(loginId: String) {
        if (loginId.isBlank()) return
        synchronized(lock) { alipayLoginId = loginId }
        send { setWatchAlipayLogin(WatchAlipayLogin.newBuilder().setLoginId(loginId)) }
    }

    private fun connect() {
        val base = baseUrlProvider()
        val token = tokenAuthority.accessToken()
        synchronized(lock) {
            if (stopped || socket != null) return
        }
        if (base == null || token == null) {
            scheduleReconnect(refreshToken = true)
            return
        }
        emit(WorkspaceEvent.Connection(WorkspaceConnectionState.CONNECTING))
        val request = Request.Builder().url(workspaceSocketUrl(base)).build()
        val listener = Listener(token)
        val created = factory.newWebSocket(request, listener)
        synchronized(lock) {
            if (stopped) {
                created.close(1000, "workspace closed")
                return
            }
            socket = created
        }
    }

    private inner class Listener(private val token: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val accountId: Long
            val alipay: String
            synchronized(lock) {
                if (socket !== webSocket || stopped) return
                accountId = selectedAccountId
                alipay = alipayLoginId
                reconnectDelayMs = RECONNECT_INITIAL_MS
            }
            tracker.resetConnection()
            emit(WorkspaceEvent.Connection(WorkspaceConnectionState.OPEN))
            send(webSocket) {
                setOpen(
                    OpenWorkspace.newBuilder()
                        .setProtocolVersion(PROTOCOL_VERSION)
                        .setAccessToken(token)
                        .setSelectedAccountId(accountId)
                        .setAfterLogId(if (accountId != 0L) tracker.afterLogId(accountId) else 0),
                )
            }
            if (alipay.isNotBlank()) send(webSocket) { setWatchAlipayLogin(WatchAlipayLogin.newBuilder().setLoginId(alipay)) }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            synchronized(lock) { if (socket !== webSocket || stopped) return }
            handleFrame(bytes.toByteArray())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronized(lock) { if (socket !== webSocket || stopped) return }
            reportInvalidFrame("服务端返回了非二进制消息")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "closed code=$code reason=$reason")
            handleClosed(webSocket, code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "failure http=${response?.code} ${t.javaClass.simpleName}: ${t.message}")
            handleClosed(webSocket, response?.code?.let { if (it == 401) CLOSE_AUTH_EXPIRED else 0 } ?: 0)
        }
    }

    private fun handleClosed(webSocket: WebSocket, code: Int) {
        synchronized(lock) {
            if (socket === webSocket) socket = null
            if (stopped) return
        }
        emit(WorkspaceEvent.Connection(WorkspaceConnectionState.CLOSED))
        scheduleReconnect(refreshToken = code == CLOSE_AUTH_EXPIRED)
    }

    private fun handleFrame(bytes: ByteArray) {
        val frame = try {
            WorkspaceServerFrame.parseFrom(bytes)
        } catch (_: Exception) {
            reportInvalidFrame("无法解析服务端状态消息")
            return
        }
        if (!tracker.accept(frame.sequence)) {
            resync()
            return
        }
        when (frame.payloadCase) {
            WorkspaceServerFrame.PayloadCase.READY -> {
                if (frame.ready.protocolVersion != PROTOCOL_VERSION) {
                    emit(WorkspaceEvent.Error(error("protocol_version_mismatch", "前后端工作区协议版本不一致", retryable = false)))
                    stop()
                    return
                }
                emit(WorkspaceEvent.Ready(frame.ready))
            }
            WorkspaceServerFrame.PayloadCase.ACCOUNT_STATUSES -> emit(WorkspaceEvent.Statuses(frame.accountStatuses))
            WorkspaceServerFrame.PayloadCase.SNAPSHOT -> {
                tracker.noteLogs(frame.snapshot.logs.eventsList)
                emit(WorkspaceEvent.Snapshot(frame.snapshot))
            }
            WorkspaceServerFrame.PayloadCase.PATCH -> emit(WorkspaceEvent.Patch(frame.patch))
            WorkspaceServerFrame.PayloadCase.LOGS -> {
                tracker.noteLogs(frame.logs.eventsList)
                emit(WorkspaceEvent.Logs(frame.logs))
            }
            WorkspaceServerFrame.PayloadCase.REDEEM_ATTEMPTS -> emit(WorkspaceEvent.RedeemAttempts(frame.redeemAttempts))
            WorkspaceServerFrame.PayloadCase.ALIPAY_LOGIN -> {
                val status = frame.alipayLogin.status
                if (status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_COMPLETE ||
                    status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_EXPIRED ||
                    status == AlipayLoginStatus.ALIPAY_LOGIN_STATUS_FAILED
                ) {
                    synchronized(lock) { alipayLoginId = "" }
                }
                emit(WorkspaceEvent.AlipayLogin(frame.alipayLogin))
            }
            WorkspaceServerFrame.PayloadCase.ERROR -> emit(WorkspaceEvent.Error(frame.error))
            else -> Unit
        }
    }

    private fun reportInvalidFrame(message: String) {
        emit(WorkspaceEvent.Error(error("invalid_server_frame", message, retryable = true)))
        resync()
    }

    private fun send(build: WorkspaceClientFrame.Builder.() -> Unit): Boolean {
        val current = synchronized(lock) { socket } ?: return false
        return send(current, build)
    }

    private fun send(target: WebSocket, build: WorkspaceClientFrame.Builder.() -> Unit): Boolean {
        val id = synchronized(lock) { ++requestId }
        val frame = WorkspaceClientFrame.newBuilder().setRequestId(id).apply(build).build()
        return target.send(frame.toByteArray().toByteString())
    }

    private fun scheduleReconnect(refreshToken: Boolean) {
        val delayMs: Long
        synchronized(lock) {
            if (stopped || reconnectJob?.isActive == true) return
            delayMs = reconnectDelayMs
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_MS)
            reconnectJob = scope.launch {
                delay(delayMs)
                synchronized(lock) { reconnectJob = null }
                if (refreshToken && !tokenAuthority.refreshAccessToken()) {
                    // AuthSession signs out when the server rejected the
                    // refresh token; the auth state collector then stops this
                    // socket. Otherwise keep retrying with backoff.
                    Log.w(TAG, "token refresh failed before reconnect, retrying later")
                    if (tokenAuthority.accessToken() == null) emit(WorkspaceEvent.AuthExpired)
                    scheduleReconnect(refreshToken = true)
                    return@launch
                }
                connect()
            }
        }
    }

    private fun emit(event: WorkspaceEvent) {
        if (!_events.tryEmit(event)) scope.launch { _events.emit(event) }
    }

    private fun error(code: String, message: String, retryable: Boolean): WorkspaceError =
        WorkspaceError.newBuilder().setCode(code).setMessage(message).setRetryable(retryable).build()

    companion object {
        private const val TAG = "MGW.Socket"
        const val PROTOCOL_VERSION = 1
        const val CLOSE_AUTH_EXPIRED = 4401
        const val RECONNECT_INITIAL_MS = 1_000L
        const val RECONNECT_MAX_MS = 30_000L
    }
}
