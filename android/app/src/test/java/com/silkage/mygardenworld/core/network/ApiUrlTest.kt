package com.silkage.mygardenworld.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiUrlTest {
    @Test
    fun acceptsHttpsRootsAndStripsTrailingSlash() {
        assertEquals("https://garden.example.com", normalizeApiBaseUrl(" https://garden.example.com/ ", allowInsecure = false))
        assertEquals("https://garden.example.com:50051", normalizeApiBaseUrl("https://garden.example.com:50051", allowInsecure = false))
        assertEquals("https://host/prefix", normalizeApiBaseUrl("https://host/prefix/", allowInsecure = false))
    }

    @Test
    fun rejectsHttpUnlessInsecureAllowed() {
        assertThrows(IllegalArgumentException::class.java) { normalizeApiBaseUrl("http://10.0.2.2:50051", allowInsecure = false) }
        assertEquals("http://10.0.2.2:50051", normalizeApiBaseUrl("http://10.0.2.2:50051", allowInsecure = true))
    }

    @Test
    fun rejectsMalformedAndRpcPaths() {
        for (input in listOf("", "garden.example.com", "https://", "https://host/mygardenworld.v1.AuthService/Login", "https://host/?x=1", "ftp://host")) {
            assertThrows(input, IllegalArgumentException::class.java) { normalizeApiBaseUrl(input, allowInsecure = true) }
        }
    }

    @Test
    fun buildsRpcAndSocketUrls() {
        assertEquals("https://host/mygardenworld.v1.AuthService/MobileLogin", rpcUrl("https://host", "AuthService", "MobileLogin"))
        assertEquals("wss://host:50051/api/workspace", workspaceSocketUrl("https://host:50051"))
        assertEquals("ws://10.0.2.2:50051/api/workspace", workspaceSocketUrl("http://10.0.2.2:50051"))
        assertEquals("wss://host/prefix/api/workspace", workspaceSocketUrl("https://host/prefix"))
    }
}
