package com.silkage.mygardenworld.core.network

import com.mygardenworld.v1.GetMeRequest
import com.mygardenworld.v1.GetMeResponse
import com.mygardenworld.v1.User
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ConnectClientTest {
    private val server = MockWebServer()

    private class FakeAuthority(var token: String? = "t1", private val refreshResult: Boolean = true, var expired: Boolean = true) : TokenAuthority {
        var refreshes = 0
        override fun accessToken(): String? = token
        override fun accessTokenExpired(): Boolean = expired
        override suspend fun refreshAccessToken(): Boolean {
            refreshes++
            if (refreshResult) token = "t2"
            return refreshResult
        }
    }

    @Before fun setUp() { server.start() }
    @After fun tearDown() { server.close() }

    private fun client(authority: TokenAuthority) = ConnectClient(authority).apply { baseUrl = server.url("/").toString().trimEnd('/') }

    private fun protoResponse(user: User) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/proto")
        .body(Buffer().write(GetMeResponse.newBuilder().setUser(user).build().toByteArray()))
        .build()

    private fun errorResponse(status: Int, json: String) = MockResponse.Builder()
        .code(status)
        .addHeader("Content-Type", "application/json")
        .body(json)
        .build()

    @Test
    fun sendsBinaryProtobufWithBearerToken() = runBlocking {
        server.enqueue(protoResponse(User.newBuilder().setUsername("owner").build()))
        val authority = FakeAuthority()
        val response = client(authority).call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
        assertEquals("owner", response.user.username)
        val recorded = server.takeRequest()
        assertEquals("/mygardenworld.v1.AuthService/GetMe", recorded.url.encodedPath)
        assertEquals("Bearer t1", recorded.headers["Authorization"])
        assertEquals("application/proto", recorded.headers["Content-Type"])
        assertEquals("1", recorded.headers["Connect-Protocol-Version"])
    }

    @Test
    fun mapsConnectErrorJson() = runBlocking {
        server.enqueue(errorResponse(412, """{"code":"failed_precondition","message":"当前状态不允许"}"""))
        try {
            client(FakeAuthority()).call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
            fail("expected ConnectException")
        } catch (e: ConnectException) {
            assertEquals(ConnectCode.FAILED_PRECONDITION, e.code)
            assertEquals("当前状态不允许", e.userMessage)
        }
    }

    @Test
    fun refreshesOnceAndReplaysOnUnauthenticated() = runBlocking {
        server.enqueue(errorResponse(401, """{"code":"unauthenticated","message":"登录已过期"}"""))
        server.enqueue(protoResponse(User.newBuilder().setUsername("owner").build()))
        val authority = FakeAuthority()
        val response = client(authority).call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
        assertEquals("owner", response.user.username)
        assertEquals(1, authority.refreshes)
        assertEquals("Bearer t1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer t2", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun businessUnauthenticatedSurfacesMessageWithoutRefreshWhenTokenIsFresh() = runBlocking {
        // CreateAccount answers unauthenticated for wrong game credentials.
        val raw = "login: account/login: response missing signature: map[msg:用户名或密码错误 status:4002 success:false]"
        server.enqueue(errorResponse(401, """{"code":"unauthenticated","message":"$raw"}"""))
        val authority = FakeAuthority(expired = false)
        try {
            client(authority).call("AccountService", "CreateAccount", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
            fail("expected ConnectException")
        } catch (e: ConnectException) {
            assertEquals(ConnectCode.UNAUTHENTICATED, e.code)
            assertEquals("用户名或密码错误", e.userMessage)
        }
        assertEquals("a fresh token is not refreshed for a business 401", 0, authority.refreshes)
        assertEquals(1, server.requestCount)
        assertEquals("t1", authority.token)
    }

    @Test
    fun tokenErrorWithFreshLocalTokenStillRefreshesOnce() = runBlocking {
        server.enqueue(errorResponse(401, """{"code":"unauthenticated","message":"token invalid"}"""))
        server.enqueue(errorResponse(401, """{"code":"unauthenticated","message":"token invalid"}"""))
        val authority = FakeAuthority(expired = false)
        try {
            client(authority).call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
            fail("expected ConnectException")
        } catch (e: ConnectException) {
            assertEquals(ConnectCode.UNAUTHENTICATED, e.code)
        }
        assertEquals(1, authority.refreshes)
        assertEquals("refreshed once, retried once, never a third attempt", 2, server.requestCount)
    }

    @Test
    fun failedRefreshThrowsWithoutRetryAndWithoutSignOut() = runBlocking {
        server.enqueue(errorResponse(401, """{"code":"unauthenticated"}"""))
        val authority = FakeAuthority(refreshResult = false)
        try {
            client(authority).call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
            fail("expected ConnectException")
        } catch (e: ConnectException) {
            assertEquals(ConnectCode.UNAUTHENTICATED, e.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun publicCallsNeverRefresh() = runBlocking {
        server.enqueue(errorResponse(401, """{"code":"unauthenticated","message":"账号或密码不正确"}"""))
        val authority = FakeAuthority(token = null)
        try {
            client(authority).call("AuthService", "MobileLogin", GetMeRequest.getDefaultInstance(), GetMeResponse.parser(), authenticated = false)
            fail("expected ConnectException")
        } catch (e: ConnectException) {
            assertEquals("账号或密码不正确", e.userMessage)
        }
        assertEquals(0, authority.refreshes)
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun transportFailureIsUnavailable() = runBlocking {
        val authority = FakeAuthority()
        val closed = ConnectClient(authority).apply { baseUrl = "http://127.0.0.1:1" }
        try {
            closed.call("AuthService", "GetMe", GetMeRequest.getDefaultInstance(), GetMeResponse.parser())
            fail("expected ConnectException")
        } catch (e: ConnectException) {
            assertEquals(ConnectCode.UNAVAILABLE, e.code)
            assertTrue(e.isNetworkFailure)
        }
        assertFalse(ConnectClient.parseError(500, "not json".toByteArray()).isNetworkFailure)
        assertEquals(ConnectCode.UNKNOWN, ConnectClient.parseError(500, "not json".toByteArray()).code)
    }
}
