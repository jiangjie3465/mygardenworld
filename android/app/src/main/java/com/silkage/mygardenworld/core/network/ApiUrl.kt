package com.silkage.mygardenworld.core.network

import com.silkage.mygardenworld.BuildConfig
import java.net.URI
import java.net.URISyntaxException

/**
 * Normalizes a user-entered gardend site root such as `https://host` or
 * `https://host:50051`. Release builds only accept HTTPS; debug builds may also
 * use plain HTTP for emulator development.
 */
fun normalizeApiBaseUrl(raw: String, allowInsecure: Boolean = BuildConfig.ALLOW_INSECURE_ENDPOINTS): String {
    val value = raw.trim().trimEnd('/')
    require(value.isNotEmpty()) { "请输入服务地址" }
    val uri = try {
        URI(value)
    } catch (e: URISyntaxException) {
        throw IllegalArgumentException("服务地址无效", e)
    }
    val scheme = uri.scheme?.lowercase()
    require(scheme == "https" || (allowInsecure && scheme == "http")) {
        if (scheme == "http") "生产环境必须使用 HTTPS" else "服务地址必须以 https:// 开头"
    }
    require(!uri.host.isNullOrBlank()) { "服务地址无效" }
    require(uri.rawQuery == null && uri.rawFragment == null) { "服务地址不能包含查询参数" }
    require(!uri.path.orEmpty().contains("/mygardenworld.v1")) { "服务地址应填写站点根地址" }
    return value
}

fun rpcUrl(baseUrl: String, service: String, method: String): String =
    "$baseUrl/mygardenworld.v1.$service/$method"

fun workspaceSocketUrl(baseUrl: String): String {
    val uri = URI(baseUrl)
    val scheme = if (uri.scheme.equals("https", ignoreCase = true)) "wss" else "ws"
    val authority = uri.rawAuthority
    val path = uri.rawPath.orEmpty().trimEnd('/')
    return "$scheme://$authority$path/api/workspace"
}
