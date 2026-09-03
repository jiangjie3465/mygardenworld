package com.silkage.mygardenworld.core.network

/**
 * Supplies the in-memory access token and performs a single coalesced refresh
 * when the server reports it as expired. Implemented by AuthSession.
 */
interface TokenAuthority {
    fun accessToken(): String?

    /** Returns true when a new access token is available. */
    suspend fun refreshAccessToken(): Boolean

    /** Called when refresh failed and the user must sign in again. */
    fun onAuthExpired()
}
