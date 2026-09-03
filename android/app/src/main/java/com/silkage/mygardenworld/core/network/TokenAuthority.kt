package com.silkage.mygardenworld.core.network

/**
 * Supplies the in-memory access token and performs a single coalesced refresh
 * when the server reports it as expired. Implemented by AuthSession.
 */
interface TokenAuthority {
    fun accessToken(): String?

    /** True when the in-memory access token has passed (or is about to pass) its expiry. */
    fun accessTokenExpired(): Boolean

    /**
     * Returns true when a new access token is available. The implementation
     * signs the user out itself when the server rejects the refresh token;
     * callers never decide that from an RPC status.
     */
    suspend fun refreshAccessToken(): Boolean
}
