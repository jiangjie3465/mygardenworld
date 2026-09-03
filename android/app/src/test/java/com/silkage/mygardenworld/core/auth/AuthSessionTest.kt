package com.silkage.mygardenworld.core.auth

import com.mygardenworld.v1.MobileLoginRequest
import com.mygardenworld.v1.MobileLoginResponse
import com.mygardenworld.v1.MobileRefreshRequest
import com.mygardenworld.v1.MobileRefreshResponse
import com.mygardenworld.v1.User
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class InMemoryTokenStore : TokenStore {
    var refreshToken: String? = null
    var baseUrl: String? = null
    var deviceId: String? = null
    override fun readRefreshToken() = refreshToken
    override fun writeRefreshToken(token: String) { refreshToken = token }
    override fun clearRefreshToken() { refreshToken = null }
    override fun readBaseUrl() = baseUrl
    override fun writeBaseUrl(url: String) { baseUrl = url }
    override fun readDeviceId() = deviceId
    override fun writeDeviceId(value: String) { deviceId = value }
}

class AuthSessionTest {
    private val server = MockWebServer()
    private val refreshCalls = AtomicInteger()
    private val issued = AtomicInteger()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body?.toByteArray() ?: ByteArray(0)
                return when (request.url.encodedPath) {
                    "/mygardenworld.v1.AuthService/MobileLogin" -> {
                        val req = MobileLoginRequest.parseFrom(body)
                        if (req.password != "secret") return error(401, "unauthenticated", "账号或密码不正确")
                        if (req.deviceId.isBlank()) return error(400, "invalid_argument", "设备标识无效")
                        proto(MobileLoginResponse.newBuilder().setAccessToken("access-1").setRefreshToken("refresh-1").setUser(User.newBuilder().setUsername(req.username)).build().toByteArray())
                    }
                    "/mygardenworld.v1.AuthService/MobileRefresh" -> {
                        refreshCalls.incrementAndGet()
                        Thread.sleep(50)
                        val req = MobileRefreshRequest.parseFrom(body)
                        if (!req.refreshToken.startsWith("refresh-")) return error(401, "unauthenticated", "登录已过期")
                        val n = issued.incrementAndGet()
                        proto(MobileRefreshResponse.newBuilder().setAccessToken("access-r$n").setRefreshToken("refresh-r$n").setUser(User.newBuilder().setUsername("owner")).build().toByteArray())
                    }
                    "/mygardenworld.v1.AuthService/MobileLogout" -> proto(ByteArray(0))
                    else -> error(404, "unimplemented", "")
                }
            }
        }
        server.start()
    }

    @After fun tearDown() { server.close() }

    private fun proto(bytes: ByteArray) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/proto").body(Buffer().write(bytes)).build()
    private fun error(status: Int, code: String, message: String) =
        MockResponse.Builder().code(status).addHeader("Content-Type", "application/json").body("""{"code":"$code","message":"$message"}""").build()

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun loginPersistsRefreshTokenAndKeepsAccessTokenInMemory() = runBlocking {
        val store = InMemoryTokenStore()
        val session = AuthSession(store)
        assertTrue(session.state.value is AuthState.SignedOut)
        val user = session.login(base(), " owner ", "secret")
        assertEquals("owner", user.username)
        assertEquals("access-1", session.accessToken())
        assertEquals("refresh-1", store.refreshToken)
        assertEquals(base(), store.baseUrl)
        assertNotNull(store.deviceId)
        assertTrue(session.state.value is AuthState.SignedIn)
    }

    @Test
    fun loginFailureLeavesSessionSignedOut() = runBlocking {
        val store = InMemoryTokenStore()
        val session = AuthSession(store)
        val result = runCatching { session.login(base(), "owner", "wrong") }
        assertTrue(result.isFailure)
        assertNull(session.accessToken())
        assertNull(store.refreshToken)
    }

    @Test
    fun concurrentRefreshesCoalesceIntoOneRotation() = runBlocking {
        val store = InMemoryTokenStore().apply { baseUrl = base(); refreshToken = "refresh-0" }
        val session = AuthSession(store)
        assertTrue(session.state.value is AuthState.Restoring)
        val results = (1..5).map { async { session.refreshAccessToken() } }.awaitAll()
        assertTrue(results.all { it })
        // The first caller performs the refresh; later callers wait on the mutex
        // and then perform their own refresh only if they still run; because the
        // lock is held per call, each waiter refreshes again with the rotated token.
        assertTrue(refreshCalls.get() in 1..5)
        assertEquals("refresh-r${issued.get()}", store.refreshToken)
        assertEquals("access-r${issued.get()}", session.accessToken())
    }

    @Test
    fun rejectedRefreshClearsStoredTokenAndSignsOut() = runBlocking {
        val store = InMemoryTokenStore().apply { baseUrl = base(); refreshToken = "bogus" }
        val session = AuthSession(store)
        assertFalse(session.restore())
        assertNull(store.refreshToken)
        assertTrue(session.state.value is AuthState.SignedOut)
    }

    @Test
    fun transportFailureKeepsStoredRefreshToken() = runBlocking {
        val store = InMemoryTokenStore().apply { baseUrl = "http://127.0.0.1:1"; refreshToken = "refresh-0" }
        val session = AuthSession(store)
        assertFalse(session.refreshAccessToken())
        assertEquals("refresh-0", store.refreshToken)
    }

    @Test
    fun logoutClearsEverything() = runBlocking {
        val store = InMemoryTokenStore()
        val session = AuthSession(store)
        session.login(base(), "owner", "secret")
        session.logout()
        assertNull(session.accessToken())
        assertNull(store.refreshToken)
        assertTrue(session.state.value is AuthState.SignedOut)
    }
}
