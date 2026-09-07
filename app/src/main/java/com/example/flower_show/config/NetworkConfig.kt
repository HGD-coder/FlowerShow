package com.example.flower_show.config

import com.example.flower_show.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

object NetworkConfig {
    private val gatewayBaseUrl: String = BuildConfig.PUBLIC_GATEWAY_BASE_URL.trimEnd('/')

    val apiBaseUrl: String = "$gatewayBaseUrl/api/v1"
    val mediaBaseUrl: String = "$gatewayBaseUrl/media"
    val chatWebSocketUrl: String = deriveChatWebSocketUrl(gatewayBaseUrl)
}

internal fun deriveChatWebSocketUrl(gatewayBaseUrl: String): String {
    val gateway = gatewayBaseUrl.trimEnd('/').toHttpUrl()
    val webSocketScheme = when (gateway.scheme) {
        "http" -> "ws"
        "https" -> "wss"
        else -> error("Unsupported public gateway scheme: ${gateway.scheme}")
    }
    val normalized = gateway.newBuilder()
        .encodedPath("/ws/chat")
        .query(null)
        .fragment(null)
        .build()
        .toString()
    return "$webSocketScheme://${normalized.substringAfter("://")}"
}
