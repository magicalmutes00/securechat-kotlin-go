package com.securechat.data.remote.websocket

import com.securechat.core.common.Result
import com.securechat.data.remote.dto.*
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
sealed interface WsEvent {
    @Serializable
    @SerialName("AUTHENTICATE")
    data class Authenticate(val payload: AuthPayload) : WsEvent

    @Serializable
    @SerialName("AUTH_ACK")
    data class AuthAck(val payload: AuthAckPayload) : WsEvent

    @Serializable
    @SerialName("MESSAGE_SEND")
    data class MessageSend(val id: String, val payload: MessageSendPayload) : WsEvent

    @Serializable
    @SerialName("MESSAGE_ACK")
    data class MessageAck(val payload: MessageAckPayload) : WsEvent

    @Serializable
    @SerialName("MESSAGE_RECEIVED")
    data class MessageReceived(val payload: MessageReceivedPayload) : WsEvent

    @Serializable
    @SerialName("MESSAGE_DELIVERED")
    data class MessageDelivered(val payload: MessageDeliveredPayload) : WsEvent

    @Serializable
    @SerialName("MESSAGE_READ")
    data class MessageRead(val payload: MessageReadPayload) : WsEvent

    @Serializable
    @SerialName("MESSAGE_DELETE")
    data class MessageDelete(val payload: MessageDeletePayload) : WsEvent

    @Serializable
    @SerialName("TYPING_START")
    data class TypingStart(val payload: TypingPayload) : WsEvent

    @Serializable
    @SerialName("TYPING_STOP")
    data class TypingStop(val payload: TypingPayload) : WsEvent

    @Serializable
    @SerialName("USER_ONLINE")
    data class UserOnline(val payload: UserPresencePayload) : WsEvent

    @Serializable
    @SerialName("USER_OFFLINE")
    data class UserOffline(val payload: UserPresencePayload) : WsEvent

    @Serializable
    @SerialName("CONVERSATION_UPDATED")
    data class ConversationUpdated(val payload: ConversationUpdatedPayload) : WsEvent

    @Serializable
    @SerialName("MEDIA_MESSAGE_CREATED")
    data class MediaMessageCreated(val payload: MessageReceivedPayload) : WsEvent

    @Serializable
    @SerialName("ERROR")
    data class Error(val payload: ErrorPayload) : WsEvent

    @Serializable
    @SerialName("PING")
    data object Ping : WsEvent

    @Serializable
    @SerialName("PONG")
    data object Pong : WsEvent
}

@Serializable data class AuthPayload(val access_token: String)
@Serializable data class AuthAckPayload(val user_id: Long, val device_id: Long)
@Serializable data class MessageSendPayload(
    val conversation_id: Long,
    val type: String,
    val text: String?,
    val media_id: Long?,
    val reply_to_id: Long?,
    val temp_id: String
)
@Serializable data class MessageAckPayload(
    val temp_id: String,
    val message_id: Long,
    val status: String,
    val created_at: Long
)
@Serializable data class MessageReceivedPayload(val message: MessageDto)
@Serializable data class MessageDeliveredPayload(
    val message_id: Long,
    val conversation_id: Long,
    val delivered_at: Long
)
@Serializable data class MessageReadPayload(
    val message_id: Long,
    val conversation_id: Long,
    val read_by: Long,
    val read_at: Long
)
@Serializable data class MessageDeletePayload(
    val message_id: Long,
    val conversation_id: Long,
    val deleted_by: Long
)
@Serializable data class TypingPayload(val conversation_id: Long)
@Serializable data class UserPresencePayload(val user_id: Long, val last_seen: Long?)
@Serializable data class ConversationUpdatedPayload(val conversation: ConversationDto)
@Serializable data class ErrorPayload(val code: String, val message: String)

class WebSocketClient(
    private val client: HttpClient,
    private val wsUrl: String,
    private val tokenProvider: () -> String?
) {
    private var session: DefaultWebSocketSession? = null
    private val isConnected = AtomicBoolean(false)
    private var reconnectJob: Job? = null
    private val eventChannel = Channel<WsEvent>(Channel.UNLIMITED)
    private val json = Json { ignoreUnknownKeys = true }

    val events: ReceiveChannel<WsEvent> = eventChannel

    suspend fun connect(): Result<Unit> {
        return try {
            if (isConnected.get()) {
                Result.success(Unit)
            } else {
                val token = tokenProvider() ?: return Result.failure(IllegalStateException("No auth token"))

                client.webSocket(
                    urlString = wsUrl,
                    request = {
                        headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                ) {
                    session = this
                    isConnected.set(true)
                    handleIncoming(this)
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            isConnected.set(false)
            Result.failure(e)
        }
    }

    private suspend fun handleIncoming(session: DefaultWebSocketSession) {
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val event = json.decodeFromString(WsEvent.serializer(), frame.readText())
                            eventChannel.send(event)
                        } catch (e: Exception) {
                            // Log parse error but continue
                        }
                    }
                    is Frame.Binary -> {
                        // Handle binary if needed
                    }
                    is Frame.Close -> {
                        break
                    }
                    is Frame.Pong -> {
                        // Pong received
                    }
                    is Frame.Ping -> {
                        // Ping received
                    }
                }
            }
        } finally {
            isConnected.set(false)
            session.close(CloseReason(CloseReason.Codes.NORMAL, "Disconnected"))
        }
    }

    suspend fun send(event: WsEvent) {
        session?.send(Frame.Text(json.encodeToString(event)))
    }

    suspend fun sendMessage(
        conversationId: Long,
        type: String,
        text: String?,
        mediaId: Long?,
        replyToId: Long?,
        tempId: String
    ) {
        val payload = MessageSendPayload(
            conversation_id = conversationId,
            type = type,
            text = text,
            media_id = mediaId,
            reply_to_id = replyToId,
            temp_id = tempId
        )
        send(WsEvent.MessageSend(id = tempId, payload = payload))
    }

    suspend fun sendTypingStart(conversationId: Long) {
        send(WsEvent.TypingStart(payload = TypingPayload(conversation_id = conversationId)))
    }

    suspend fun sendTypingStop(conversationId: Long) {
        send(WsEvent.TypingStop(payload = TypingPayload(conversation_id = conversationId)))
    }

    suspend fun sendReadReceipt(messageId: Long, conversationId: Long) {
        // Server derives user from session, we just send message_id
        // This would need a specific event type
    }

    suspend fun disconnect() {
        isConnected.set(false)
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
        session = null
        reconnectJob?.cancel()
    }

    val connected: Boolean
        get() = isConnected.get()
}

class WebSocketManager(
    private val client: HttpClient,
    private val wsUrl: String,
    private val tokenProvider: () -> String?
) {
    private val clientInstance = WebSocketClient(client, wsUrl, tokenProvider)
    private var reconnectJob: Job? = null
    private var connectionState = ConnectionState.DISCONNECTED

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }

    val events: ReceiveChannel<WsEvent> = clientInstance.events
    val state: ConnectionState
        get() = connectionState

    suspend fun connect(): Result<Unit> {
        connectionState = ConnectionState.CONNECTING
        val result = clientInstance.connect()
        if (result.isSuccess) {
            connectionState = ConnectionState.CONNECTED
            startPingPong()
        } else {
            connectionState = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
        return result
    }

    private fun startPingPong() {
        // Ping every 30 seconds
        // In a real implementation, this would be a coroutine
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            connectionState = ConnectionState.RECONNECTING
            var delay = 1000L
            val maxDelay = 30000L

            while (true) {
                delay(delay)
                val result = clientInstance.connect()
                if (result.isSuccess) {
                    connectionState = ConnectionState.CONNECTED
                    startPingPong()
                    break
                }
                delay = minOf(delay * 2, maxDelay)
            }
        }
    }

    suspend fun send(event: WsEvent) {
        clientInstance.send(event)
    }

    suspend fun disconnect() {
        reconnectJob?.cancel()
        clientInstance.disconnect()
        connectionState = ConnectionState.DISCONNECTED
    }
}