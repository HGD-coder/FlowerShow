package com.example.flower_show.data.auth

import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: AuthSessionManager,
    private val trustedOrigin: HttpUrl,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().removeHeader("X-User-Id")
        if (request.url.hasSameOrigin(trustedOrigin)) {
            val accessToken = sessionManager.currentAccessToken()
            if (!accessToken.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $accessToken")
            } else {
                // 已登出时也必须移除调用方手动设置的陈旧 Authorization 头，
                // 否则登出后的请求可能仍带着旧 bearer 发出。
                builder.removeHeader("Authorization")
            }
        }
        return chain.proceed(builder.build())
    }
}

internal fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
    scheme.webEquivalentScheme() == other.scheme.webEquivalentScheme() &&
        host == other.host &&
        port == other.port

/**
 * WebSocket URLs (ws/wss) belong to the same origin as their HTTP equivalents.
 * Without this normalization the chat handshake to wss://host/ws/chat never
 * matches the trusted https origin, so it is sent without an Authorization
 * header and its 401s never trigger token refresh.
 */
private fun String.webEquivalentScheme(): String = when (this) {
    "ws" -> "http"
    "wss" -> "https"
    else -> this
}
