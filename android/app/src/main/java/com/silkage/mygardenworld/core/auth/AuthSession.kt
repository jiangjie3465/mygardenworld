package com.silkage.mygardenworld.core.auth

import android.util.Log
import com.mygardenworld.v1.MobileLoginRequest
import com.mygardenworld.v1.MobileLoginResponse
import com.mygardenworld.v1.MobileLogoutRequest
import com.mygardenworld.v1.MobileLogoutResponse
import com.mygardenworld.v1.MobileRefreshRequest
import com.mygardenworld.v1.MobileRefreshResponse
import com.mygardenworld.v1.User
import com.silkage.mygardenworld.core.network.ConnectClient
import com.silkage.mygardenworld.core.network.TokenAuthority
import com.silkage.mygardenworld.core.network.normalizeApiBaseUrl
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

sealed interface AuthState {
    /** Nothing persisted, or the persisted session was rejected. */
    data class SignedOut(val reason: String? = null) : AuthState

    /** A refresh token exists but no access token has been obtained yet. */
    data object Restoring : AuthState

    data class SignedIn(val user: User) : AuthState
}

/**
 * Owns the access token (memory only), the refresh token (TokenStore), the
 * device identity, and the shared ConnectClient. Refreshes are coalesced so
 * concurrent 401s trigger a single MobileRefresh.
 */
class AuthSession(
    private val tokenStore: TokenStore,
    httpClient: OkHttpClient = ConnectClient.defaultHttpClient(),
    private val deviceName: String = "Android",
) : TokenAuthority {
    val rpc: ConnectClient = ConnectClient(this, httpClient)

    private val refreshMutex = Mutex()
    @Volatile private var accessToken: String? = null
    @Volatile private var accessTokenExpiresAtMillis: Long = 0

    private val _state = MutableStateFlow<AuthState>(
        if (tokenStore.readRefreshToken() != null) AuthState.Restoring else AuthState.SignedOut(),
    )
    val state: StateFlow<AuthState> = _state

    val baseUrl: String? get() = tokenStore.readBaseUrl()

    val deviceId: String
        get() = tokenStore.readDeviceId() ?: UUID.randomUUID().toString().also(tokenStore::writeDeviceId)

    init {
        rpc.baseUrl = tokenStore.readBaseUrl()
    }

    override fun accessToken(): String? = accessToken

    override fun accessTokenExpired(): Boolean =
        accessToken == null || accessTokenExpiresAtMillis <= 0 || System.currentTimeMillis() >= accessTokenExpiresAtMillis - EXPIRY_SLACK_MILLIS

    /** Establishes a session against [rawBaseUrl]. Throws ConnectException or IllegalArgumentException. */
    suspend fun login(rawBaseUrl: String, username: String, password: String): User {
        val base = normalizeApiBaseUrl(rawBaseUrl)
        rpc.baseUrl = base
        val response: MobileLoginResponse = rpc.call(
            "AuthService", "MobileLogin",
            MobileLoginRequest.newBuilder()
                .setUsername(username.trim())
                .setPassword(password)
                .setDeviceId(deviceId)
                .setDeviceName(deviceName)
                .build(),
            MobileLoginResponse.parser(),
            authenticated = false,
        )
        tokenStore.writeBaseUrl(base)
        tokenStore.writeRefreshToken(response.refreshToken)
        accessToken = response.accessToken
        accessTokenExpiresAtMillis = response.accessExpiresAt.seconds * 1000
        _state.value = AuthState.SignedIn(response.user)
        return response.user
    }

    /**
     * Uses the persisted refresh token to obtain an access token, e.g. at app
     * start. Returns false when the user must log in again.
     */
    suspend fun restore(): Boolean {
        if (tokenStore.readRefreshToken() == null) {
            _state.value = AuthState.SignedOut()
            return false
        }
        return refreshAccessToken()
    }

    override suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        val base = tokenStore.readBaseUrl() ?: return@withLock false
        val refresh = tokenStore.readRefreshToken() ?: return@withLock false
        rpc.baseUrl = base
        val response = try {
            rpc.call(
                "AuthService", "MobileRefresh",
                MobileRefreshRequest.newBuilder().setRefreshToken(refresh).build(),
                MobileRefreshResponse.parser(),
                authenticated = false,
            )
        } catch (e: com.silkage.mygardenworld.core.network.ConnectException) {
            // Transport failures keep the stored refresh token; the server
            // rejected it only when it answered with an auth code.
            if (e.code == com.silkage.mygardenworld.core.network.ConnectCode.UNAUTHENTICATED ||
                e.code == com.silkage.mygardenworld.core.network.ConnectCode.PERMISSION_DENIED
            ) {
                Log.w(TAG, "refresh rejected by server: ${e.code} ${e.message}")
                clearLocal(e.userMessage)
            } else {
                Log.w(TAG, "refresh failed (kept session): ${e.code} ${e.message}")
            }
            return@withLock false
        }
        Log.i(TAG, "access token refreshed")
        tokenStore.writeRefreshToken(response.refreshToken)
        accessToken = response.accessToken
        accessTokenExpiresAtMillis = response.accessExpiresAt.seconds * 1000
        _state.value = AuthState.SignedIn(response.user)
        true
    }

    suspend fun logout() {
        val refresh = tokenStore.readRefreshToken()
        if (rpc.baseUrl != null && refresh != null) {
            runCatching {
                rpc.call(
                    "AuthService", "MobileLogout",
                    MobileLogoutRequest.newBuilder().setRefreshToken(refresh).build(),
                    MobileLogoutResponse.parser(),
                    authenticated = false,
                )
            }
        }
        clearLocal(null)
    }

    /** Re-points the client at a new server without logging in (used by connection test). */
    fun previewBaseUrl(rawBaseUrl: String): String = normalizeApiBaseUrl(rawBaseUrl)

    private companion object {
        const val TAG = "MGW.Auth"
        const val EXPIRY_SLACK_MILLIS = 30_000L
    }

    private fun clearLocal(reason: String?) {
        accessToken = null
        accessTokenExpiresAtMillis = 0
        tokenStore.clearRefreshToken()
        _state.value = AuthState.SignedOut(reason)
    }
}
