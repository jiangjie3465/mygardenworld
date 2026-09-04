package com.silkage.mygardenworld.core.auth

import com.mygardenworld.v1.ListMobileSessionsRequest
import com.mygardenworld.v1.ListMobileSessionsResponse
import com.mygardenworld.v1.MobileSession
import com.mygardenworld.v1.RevokeMobileSessionRequest
import com.mygardenworld.v1.RevokeMobileSessionResponse
import com.silkage.mygardenworld.core.network.ConnectClient

/** The caller's own mobile device sessions. */
class SessionsRepository(private val rpc: ConnectClient, private val deviceId: () -> String) {
    suspend fun list(): List<MobileSession> = rpc.call(
        "AuthService", "ListMobileSessions",
        ListMobileSessionsRequest.newBuilder().setDeviceId(deviceId()).build(),
        ListMobileSessionsResponse.parser(),
    ).sessionsList

    suspend fun revoke(sessionId: Long) {
        rpc.call("AuthService", "RevokeMobileSession", RevokeMobileSessionRequest.newBuilder().setSessionId(sessionId).build(), RevokeMobileSessionResponse.parser())
    }
}
