package com.example.flower_show.config

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkConfigTest {
    @Test
    fun derivesSecureChatSocketFromUnifiedHttpsGateway() {
        assertEquals(
            "wss://gateway.example.com/ws/chat",
            deriveChatWebSocketUrl("https://gateway.example.com/"),
        )
    }

    @Test
    fun derivesLocalChatSocketAndReplacesGatewayPathAndQuery() {
        assertEquals(
            "ws://10.0.2.2:8088/ws/chat",
            deriveChatWebSocketUrl("http://10.0.2.2:8088/public/base?ignored=true"),
        )
    }
}
