package com.silkage.mygardenworld.core.network

import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Connect error codes, see https://connectrpc.com/docs/protocol#error-codes. */
enum class ConnectCode(val wire: String, val httpStatus: Int) {
    CANCELED("canceled", 408),
    UNKNOWN("unknown", 500),
    INVALID_ARGUMENT("invalid_argument", 400),
    DEADLINE_EXCEEDED("deadline_exceeded", 408),
    NOT_FOUND("not_found", 404),
    ALREADY_EXISTS("already_exists", 409),
    PERMISSION_DENIED("permission_denied", 403),
    RESOURCE_EXHAUSTED("resource_exhausted", 429),
    FAILED_PRECONDITION("failed_precondition", 412),
    ABORTED("aborted", 409),
    OUT_OF_RANGE("out_of_range", 400),
    UNIMPLEMENTED("unimplemented", 404),
    INTERNAL("internal", 500),
    UNAVAILABLE("unavailable", 503),
    DATA_LOSS("data_loss", 500),
    UNAUTHENTICATED("unauthenticated", 401);

    companion object {
        fun fromWire(value: String?): ConnectCode? = entries.firstOrNull { it.wire == value }

        fun fromHttpStatus(status: Int): ConnectCode = when (status) {
            400 -> INTERNAL
            401 -> UNAUTHENTICATED
            403 -> PERMISSION_DENIED
            404 -> UNIMPLEMENTED
            408 -> DEADLINE_EXCEEDED
            429 -> UNAVAILABLE
            502, 503, 504 -> UNAVAILABLE
            else -> UNKNOWN
        }
    }
}

/** Error produced by a Connect RPC, including transport failures. */
class ConnectException(
    val code: ConnectCode,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    val isNetworkFailure: Boolean get() = cause != null && (code == ConnectCode.UNAVAILABLE || code == ConnectCode.DEADLINE_EXCEEDED)

    /** Chinese, user-facing description mirroring the Web client. */
    val userMessage: String
        get() = when {
            isNetworkFailure -> "暂时无法访问后端服务，请检查网络后重试"
            message.isNullOrBlank() -> when (code) {
                ConnectCode.UNAUTHENTICATED -> "登录已过期，请重新登录"
                ConnectCode.PERMISSION_DENIED -> "没有权限执行此操作"
                ConnectCode.INVALID_ARGUMENT -> "请求参数不正确"
                ConnectCode.NOT_FOUND -> "请求的资源不存在"
                ConnectCode.ALREADY_EXISTS -> "资源已存在"
                ConnectCode.RESOURCE_EXHAUSTED -> "请求过于频繁，请稍后再试"
                ConnectCode.FAILED_PRECONDITION -> "当前状态不允许执行此操作"
                ConnectCode.UNAVAILABLE -> "后端服务暂时不可用，请稍后再试"
                ConnectCode.DEADLINE_EXCEEDED -> "请求超时，请稍后再试"
                ConnectCode.INTERNAL -> "后端服务内部错误"
                else -> "请求失败"
            }
            else -> message!!
        }
}

/**
 * Minimal Connect unary client using the binary Protobuf codec. Authenticated
 * calls carry a Bearer token, retry exactly once after a coalesced refresh on
 * `unauthenticated`, and never retry anything else.
 */
class ConnectClient(
    private val tokenAuthority: TokenAuthority,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    @Volatile var baseUrl: String? = null

    suspend fun <Resp : MessageLite> call(
        service: String,
        method: String,
        request: MessageLite,
        parser: Parser<Resp>,
        authenticated: Boolean = true,
        readTimeoutSeconds: Long = DEFAULT_READ_TIMEOUT_SECONDS,
    ): Resp {
        val base = baseUrl ?: throw ConnectException(ConnectCode.FAILED_PRECONDITION, "尚未配置服务地址")
        val url = rpcUrl(base, service, method)
        val body = request.toByteArray()
        val client = if (readTimeoutSeconds == DEFAULT_READ_TIMEOUT_SECONDS) http else http.newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build()
        val first = execute(client, url, body, parser, if (authenticated) tokenAuthority.accessToken() else null)
        if (first.isSuccess || !authenticated) return first.getOrThrow()
        val error = first.exceptionOrNull() as? ConnectException ?: return first.getOrThrow()
        if (error.code != ConnectCode.UNAUTHENTICATED) throw error
        Log.i(TAG, "$service/$method unauthenticated, refreshing access token")
        // A failed refresh does not sign the user out here: AuthSession clears
        // local state only when the server rejected the refresh token, and a
        // transport failure must keep the session for the next attempt.
        if (!tokenAuthority.refreshAccessToken()) throw error
        val second = execute(client, url, body, parser, tokenAuthority.accessToken())
        val retryError = second.exceptionOrNull() as? ConnectException
        if (retryError?.code == ConnectCode.UNAUTHENTICATED) {
            Log.w(TAG, "$service/$method still unauthenticated after refresh")
            tokenAuthority.onAuthExpired()
        }
        return second.getOrThrow()
    }

    private suspend fun <Resp : MessageLite> execute(
        client: OkHttpClient,
        url: String,
        body: ByteArray,
        parser: Parser<Resp>,
        token: String?,
    ): Result<Resp> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(PROTO_MEDIA_TYPE))
            .header("Connect-Protocol-Version", "1")
            .header("Accept", "application/proto")
        if (token != null) builder.header("Authorization", "Bearer $token")
        try {
            client.newCall(builder.build()).execute().use { response ->
                val bytes = response.body.bytes()
                if (response.isSuccessful) {
                    Result.success(parser.parseFrom(bytes))
                } else {
                    Result.failure(parseError(response.code, bytes))
                }
            }
        } catch (e: ConnectException) {
            Result.failure(e)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "timeout calling $url", e)
            Result.failure(ConnectException(ConnectCode.DEADLINE_EXCEEDED, "请求超时，请稍后再试", e))
        } catch (e: UnknownHostException) {
            Result.failure(ConnectException(ConnectCode.UNAVAILABLE, "无法解析服务地址", e))
        } catch (e: IOException) {
            Log.w(TAG, "transport failure calling $url", e)
            Result.failure(ConnectException(ConnectCode.UNAVAILABLE, "暂时无法访问后端服务", e))
        }
    }

    companion object {
        private const val TAG = "MGW.Connect"
        private val PROTO_MEDIA_TYPE = "application/proto".toMediaType()
        const val DEFAULT_READ_TIMEOUT_SECONDS = 30L

        /** Game-side logins (account creation, connect, Alipay start) can take minutes. */
        const val LONG_READ_TIMEOUT_SECONDS = 180L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Connect unary errors are always JSON `{ "code": "...", "message": "..." }`. */
        fun parseError(httpStatus: Int, body: ByteArray): ConnectException {
            val text = runCatching { String(body, Charsets.UTF_8) }.getOrDefault("")
            val json = runCatching { JSONObject(text) }.getOrNull()
            val code = ConnectCode.fromWire(json?.optString("code")) ?: ConnectCode.fromHttpStatus(httpStatus)
            val message = json?.optString("message").orEmpty()
            return ConnectException(code, message)
        }
    }
}
