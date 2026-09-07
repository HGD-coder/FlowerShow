package com.example.flower_show.data.remote.chat

import com.example.flower_show.model.RemoteChatMessage
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Closeable
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface ChatRealtimeConnectionState {
    data object Disconnected : ChatRealtimeConnectionState
    data object Connecting : ChatRealtimeConnectionState
    data object Connected : ChatRealtimeConnectionState

    data class Reconnecting(
        val attempt: Int,
        val delayMillis: Long,
    ) : ChatRealtimeConnectionState
}

sealed interface ChatRealtimeEvent {
    data object ConnectionReady : ChatRealtimeEvent

    data class MessageCreated(
        val eventId: String,
        val occurredAt: String,
        val message: RemoteChatMessage,
    ) : ChatRealtimeEvent
}

interface ChatRealtimeClient : Closeable {
    val connectionState: StateFlow<ChatRealtimeConnectionState>
    val events: Flow<ChatRealtimeEvent>

    fun connect()

    fun disconnect()
}

object NoOpChatRealtimeClient : ChatRealtimeClient {
    private val disconnected = MutableStateFlow<ChatRealtimeConnectionState>(
        ChatRealtimeConnectionState.Disconnected,
    )

    override val connectionState: StateFlow<ChatRealtimeConnectionState> =
        disconnected.asStateFlow()
    override val events: Flow<ChatRealtimeEvent> = kotlinx.coroutines.flow.emptyFlow()

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun close() = Unit
}

class OkHttpChatRealtimeClient(
    private val client: OkHttpClient,
    private val webSocketUrl: String,
    private val gson: Gson = Gson(),
    private val initialReconnectDelayMillis: Long = 1_000L,
    private val maxReconnectDelayMillis: Long = 30_000L,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatRealtimeClient {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    // 有界通道：服务端广播是 fire-and-forget、无背压、不重投，
    // UNLIMITED 通道会在 UI 未消费时无限堆积内存；队满时丢弃并靠 REST 补齐。
    private val eventChannel = Channel<ChatRealtimeEvent>(Channel.BUFFERED)
    private val _connectionState = MutableStateFlow<ChatRealtimeConnectionState>(
        ChatRealtimeConnectionState.Disconnected,
    )

    override val connectionState: StateFlow<ChatRealtimeConnectionState> =
        _connectionState.asStateFlow()
    override val events: Flow<ChatRealtimeEvent> = eventChannel.receiveAsFlow()

    private var generation = 0L
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var webSocket: WebSocket? = null
    private var connectionRequested = false
    private var closed = false

    init {
        require(initialReconnectDelayMillis > 0L) {
            "initialReconnectDelayMillis must be positive"
        }
        require(maxReconnectDelayMillis >= initialReconnectDelayMillis) {
            "maxReconnectDelayMillis must be at least initialReconnectDelayMillis"
        }
    }

    override fun connect() {
        val connectionGeneration = synchronized(lock) {
            if (closed || connectionRequested) return
            connectionRequested = true
            reconnectAttempt = 0
            generation += 1
            generation
        }
        _connectionState.value = ChatRealtimeConnectionState.Connecting
        openWebSocket(connectionGeneration)
    }

    override fun disconnect() {
        stop(permanently = false)
    }

    override fun close() {
        stop(permanently = true)
        eventChannel.close()
        scope.cancel()
    }

    private fun stop(permanently: Boolean) {
        val socketToClose = synchronized(lock) {
            if (permanently) {
                closed = true
            }
            connectionRequested = false
            generation += 1
            reconnectAttempt = 0
            reconnectJob?.cancel()
            reconnectJob = null
            webSocket.also { webSocket = null }
        }
        _connectionState.value = ChatRealtimeConnectionState.Disconnected
        if (socketToClose?.close(NormalClosureCode, "client disconnect") == false) {
            socketToClose.cancel()
        }
    }

    private fun openWebSocket(connectionGeneration: Long) {
        val request = Request.Builder()
            .url(webSocketUrl)
            .build()
        val listener = Listener(connectionGeneration)
        synchronized(lock) {
            if (
                closed ||
                !connectionRequested ||
                generation != connectionGeneration ||
                webSocket != null
            ) {
                return
            } else {
                // Keep assignment under the same lock used by callbacks so an immediate
                // onFailure cannot be lost before the socket becomes current.
                webSocket = client.newWebSocket(request, listener)
            }
        }
    }

    private fun isCurrent(
        connectionGeneration: Long,
        socket: WebSocket,
    ): Boolean = synchronized(lock) {
        !closed &&
            connectionRequested &&
            generation == connectionGeneration &&
            webSocket === socket
    }

    private fun handleTextMessage(
        connectionGeneration: Long,
        socket: WebSocket,
        text: String,
    ) {
        if (!isCurrent(connectionGeneration, socket)) return
        runCatching {
            val payload = JsonParser.parseString(text).asJsonObject
            when (payload.string("type")) {
                ConnectionReadyType -> handleConnectionReady(connectionGeneration, socket)
                MessageCreatedType ->
                    handleMessageCreated(connectionGeneration, payload, socket)
            }
        }.onFailure { error ->
            logW("Unparseable realtime frame; relying on REST catch-up", error)
        }
    }

    private fun handleConnectionReady(
        connectionGeneration: Long,
        socket: WebSocket,
    ) {
        if (!isCurrent(connectionGeneration, socket)) return
        synchronized(lock) {
            if (
                closed ||
                !connectionRequested ||
                generation != connectionGeneration ||
                webSocket !== socket
            ) {
                return
            }
            reconnectAttempt = 0
        }
        _connectionState.value = ChatRealtimeConnectionState.Connected
        eventChannel.trySend(ChatRealtimeEvent.ConnectionReady)
    }

    private fun handleMessageCreated(
        connectionGeneration: Long,
        payload: JsonObject,
        socket: WebSocket,
    ) {
        // 服务端 ack 语义 = “收到即丢弃”（不追踪、不重投、不流控），
        // 因此解析失败也照常 ack（否则没有任何补偿机制），记日志后靠 REST messages 补齐。
        // 注意：服务端只接受 ack 帧，nack/其他业务帧会被 1008 断连，绝不能发。
        val eventId = payload.string("eventId")
        if (eventId.isNullOrBlank() || eventId.length > 128) {
            logW("Realtime message event without valid eventId; cannot acknowledge")
            return
        }
        val parsed = runCatching {
            val occurredAt = payload.string("occurredAt").required("occurredAt")
            val messagePayload = payload.getAsJsonObject("message")
                ?: error("Missing realtime message payload")
            occurredAt to gson.fromJson(messagePayload, RemoteChatMessageDto::class.java).toDomain()
        }
        parsed.fold(
            onSuccess = { (occurredAt, message) ->
                if (isCurrent(connectionGeneration, socket)) {
                    val accepted = eventChannel.trySend(
                        ChatRealtimeEvent.MessageCreated(
                            eventId = eventId,
                            occurredAt = occurredAt,
                            message = message,
                        ),
                    ).isSuccess
                    if (accepted) {
                        socket.send(gson.toJson(AckFrame(eventId = eventId)))
                    } else {
                        logW("Realtime event buffer full; dropping message $eventId (REST catch-up will cover it)")
                    }
                }
            },
            onFailure = { error ->
                logW("Malformed realtime message $eventId; acknowledging and relying on REST catch-up", error)
                if (isCurrent(connectionGeneration, socket)) {
                    socket.send(gson.toJson(AckFrame(eventId = eventId)))
                }
            },
        )
    }

    private fun handleTermination(
        connectionGeneration: Long,
        socket: WebSocket,
        closeCode: Int?,
        response: Response?,
    ) {
        val retry = synchronized(lock) {
            if (
                closed ||
                !connectionRequested ||
                generation != connectionGeneration ||
                webSocket !== socket
            ) {
                return
            }
            webSocket = null
            // 认证失败（握手 HTTP 401 / 关闭码 4001 token 过期）：停止自动重连，
            // 等 token 刷新或重新登录后再 connect（轮询周期会低频重试）。
            // 网络类错误（1001 读空闲、超时、DNS）保持无限指数退避——服务端对
            // 重连风暴无任何限制与惩罚。
            val authenticationFailure =
                response?.code == 401 || closeCode == AuthExpiredCloseCode
            if (authenticationFailure) {
                connectionRequested = false
                reconnectAttempt = 0
                _connectionState.value = ChatRealtimeConnectionState.Disconnected
                return
            }
            reconnectAttempt += 1
            val attempt = reconnectAttempt
            attempt to reconnectDelayMillis(attempt)
        }
        scheduleReconnect(connectionGeneration, retry.first, retry.second)
    }

    private fun scheduleReconnect(
        connectionGeneration: Long,
        attempt: Int,
        delayMillis: Long,
    ) {
        _connectionState.value = ChatRealtimeConnectionState.Reconnecting(
            attempt = attempt,
            delayMillis = delayMillis,
        )
        val job = scope.launch {
            delay(delayMillis)
            val shouldOpen = synchronized(lock) {
                if (
                    closed ||
                    !connectionRequested ||
                    generation != connectionGeneration
                ) {
                    false
                } else {
                    reconnectJob = null
                    true
                }
            }
            if (shouldOpen) {
                openWebSocket(connectionGeneration)
            }
        }
        synchronized(lock) {
            if (
                closed ||
                !connectionRequested ||
                generation != connectionGeneration
            ) {
                job.cancel()
            } else {
                reconnectJob?.cancel()
                reconnectJob = job
            }
        }
    }

    private fun reconnectDelayMillis(attempt: Int): Long {
        var delayMillis = initialReconnectDelayMillis
        repeat((attempt - 1).coerceAtLeast(0)) {
            if (delayMillis >= maxReconnectDelayMillis) return maxReconnectDelayMillis
            delayMillis = if (delayMillis > maxReconnectDelayMillis / 2) {
                maxReconnectDelayMillis
            } else {
                min(delayMillis * 2, maxReconnectDelayMillis)
            }
        }
        return delayMillis
    }

    private inner class Listener(
        private val connectionGeneration: Long,
    ) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            handleTextMessage(connectionGeneration, webSocket, text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleTermination(connectionGeneration, webSocket, closeCode = code, response = null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleTermination(connectionGeneration, webSocket, closeCode = null, response = response)
        }
    }

    // JVM 单元测试中 android.util.Log 未 mock，直接调用会抛异常；
    // 与项目其他类一致用 runCatching 包裹。
    private fun logW(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                Log.w(TAG, message, error)
            } else {
                Log.w(TAG, message)
            }
        }
    }

    private data class AckFrame(
        val type: String = AckType,
        val eventId: String,
    )

    private companion object {
        const val TAG = "ChatRealtimeClient"
        const val NormalClosureCode = 1_000
        const val AuthExpiredCloseCode = 4_001
        const val ConnectionReadyType = "connection.ready"
        const val MessageCreatedType = "chat.message.created"
        const val AckType = "ack"
    }
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

private fun String?.required(field: String): String =
    takeIf { !it.isNullOrBlank() } ?: error("Missing realtime event $field")
