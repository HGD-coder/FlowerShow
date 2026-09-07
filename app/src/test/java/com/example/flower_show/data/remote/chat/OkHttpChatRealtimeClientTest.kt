package com.example.flower_show.data.remote.chat

import com.example.flower_show.data.auth.AuthInterceptor
import com.example.flower_show.data.auth.AuthSessionManager
import com.example.flower_show.data.auth.AuthTokenResponse
import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.auth.RefreshTokenStore
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpChatRealtimeClientTest {
    private lateinit var server: MockWebServer
    private var realtimeClient: OkHttpChatRealtimeClient? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        realtimeClient?.close()
        server.shutdown()
    }

    @Test
    fun authenticatedClientAddsBearerToWebSocketHandshake() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"type":"connection.ready"}""")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        val sessionManager = authenticatedSession()
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager, server.url("/api/v1")))
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        val client = newRealtimeClient(authenticatedClient)

        client.connect()

        withTimeout(2_000L) {
            client.connectionState.first {
                it == ChatRealtimeConnectionState.Connected
            }
        }
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/ws/chat", request.path)
        assertEquals("Bearer realtime-access", request.getHeader("Authorization"))
        assertFalse(request.requestUrl!!.queryParameterNames.contains("token"))
    }

    @Test
    fun malformedEventDoesNotStopParsingAndBothFramesAreAcknowledged() = runBlocking {
        val acknowledgements = Collections.synchronizedList(mutableListOf<String>())
        val ackLatch = CountDownLatch(2)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"type":"connection.ready"}""")
                        webSocket.send(
                            """
                            {
                              "type":"chat.message.created",
                              "eventId":"malformed-event",
                              "occurredAt":"2026-07-30T08:00:00Z",
                              "message":{}
                            }
                            """.trimIndent(),
                        )
                        webSocket.send(
                            """
                            {
                              "type":"chat.message.created",
                              "eventId":"event-2",
                              "occurredAt":"2026-07-30T08:00:01Z",
                              "message":{
                                "id":"message-2",
                                "conversationId":"conversation-1",
                                "senderUserId":"friend-1",
                                "senderNickname":"好友",
                                "senderAvatarUrl":null,
                                "type":"text",
                                "text":"新消息",
                                "sharedContentId":null,
                                "videoPreview":null,
                                "clientMessageId":"client-2",
                                "createdAt":"2026-07-30T08:00:01Z"
                              }
                            }
                            """.trimIndent(),
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        acknowledgements += text
                        ackLatch.countDown()
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        val client = newRealtimeClient(OkHttpClient())
        val message = async {
            client.events
                .filterIsInstance<ChatRealtimeEvent.MessageCreated>()
                .first()
        }

        client.connect()

        val event = withTimeout(2_000L) { message.await() }
        assertEquals("event-2", event.eventId)
        assertEquals("message-2", event.message.messageId)
        assertEquals("好友", event.message.senderNickname)
        assertNull(event.message.senderAvatarUrl)
        assertTrue(ackLatch.await(2, TimeUnit.SECONDS))
        // 服务端 fire-and-forget、不重投：解析失败的坏消息也必须 ack
        // （客户端记录日志后靠 REST messages 补齐），否则没有任何补偿机制。
        assertTrue(acknowledgements.any { it.contains("\"eventId\":\"malformed-event\"") })
        assertTrue(acknowledgements.any { it.contains("\"eventId\":\"event-2\"") })
    }

    @Test
    fun http401DuringHandshakeStopsReconnectingInsteadOfRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val client = newRealtimeClient(
            client = OkHttpClient(),
            initialReconnectDelayMillis = 100L,
        )

        client.connect()

        withTimeout(2_000L) {
            client.connectionState.first { it == ChatRealtimeConnectionState.Disconnected }
        }
        // 等待远超重连间隔的时间，确认认证失败不会进入指数退避重试。
        Thread.sleep(400L)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun websocketClose4001StopsReconnectingInsteadOfRetrying() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.close(4_001, "Access token expired.")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        val client = newRealtimeClient(
            client = OkHttpClient(),
            initialReconnectDelayMillis = 100L,
        )

        client.connect()

        withTimeout(2_000L) {
            client.connectionState.first { it == ChatRealtimeConnectionState.Disconnected }
        }
        Thread.sleep(400L)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun disconnectCancelsPendingReconnect() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val client = newRealtimeClient(
            client = OkHttpClient(),
            initialReconnectDelayMillis = 200L,
        )

        client.connect()

        assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)
        withTimeout(2_000L) {
            client.connectionState.first {
                it is ChatRealtimeConnectionState.Reconnecting
            }
        }
        client.disconnect()
        Thread.sleep(350L)

        assertEquals(ChatRealtimeConnectionState.Disconnected, client.connectionState.value)
        assertEquals(1, server.requestCount)
    }

    private fun newRealtimeClient(
        client: OkHttpClient,
        initialReconnectDelayMillis: Long = 1_000L,
    ): OkHttpChatRealtimeClient =
        OkHttpChatRealtimeClient(
            client = client,
            webSocketUrl = server.url("/ws/chat").toString().replaceFirst("http", "ws"),
            initialReconnectDelayMillis = initialReconnectDelayMillis,
            maxReconnectDelayMillis = 1_000L,
        ).also { realtimeClient = it }
}

private fun authenticatedSession(): AuthSessionManager =
    AuthSessionManager(RealtimeRefreshTokenStore()).apply {
        replace(
            AuthTokenResponse(
                tokenType = "Bearer",
                accessToken = "realtime-access",
                accessTokenExpiresInSeconds = 900L,
                refreshToken = "realtime-refresh",
                refreshTokenExpiresInSeconds = 2_592_000L,
                user = AuthUser(
                    userId = "user-1",
                    accountId = "account-1",
                    username = "garden.user",
                    nickname = "花友",
                    role = "USER",
                ),
            ),
        )
    }

private class RealtimeRefreshTokenStore : RefreshTokenStore {
    private var token: String? = null

    override fun read(): String? = token

    override fun write(refreshToken: String) {
        token = refreshToken
    }

    override fun clear() {
        token = null
    }
}
