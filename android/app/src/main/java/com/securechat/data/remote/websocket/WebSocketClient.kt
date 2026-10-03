package com.securechat.data.remote.websocket

import com.securechat.core.common.Result
import com.securechat.data.remote.dto.ConversationDto
import com.securechat.data.remote.dto.MessageDto
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
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
@Serializable data class TypingPayload(val conversation_id: Long, val user_id: Long? = null)
@Serializable data class UserPresencePayload(val user_id: Long, val is_online: Boolean? = null, val last_seen: Long? = null)
@Serializable data class ConversationUpdatedPayload(val conversation: ConversationDto)
@Serializable data class ErrorPayload(val code: String, val message: String)

/**
 * Owns the single WebSocket connection lifecycle: a connection loop with
 * exponential backoff, the in-band AUTHENTICATE handshake the server requires,
 * and fan-out of incoming events. Outgoing events are queued in a channel and
 * flushed by the in-session writer, so sends made while offline are delivered
 * on reconnect. Consumers observe [events] and [state].
 */
class WebSocketManager(
    private val client: HttpClient,
    private val wsUrl: String,
    private val tokenProvider: () -> String?
) {
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val outboundQueue = Channel<WsEvent>(Channel.UNLIMITED)
    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 256)
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val started = AtomicBoolean(false)
    private val sessionActive = AtomicBoolean(false)
    private var connectionJob: Job? = null

    val events: SharedFlow<WsEvent> = _events.asSharedFlow()
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    val connected: Boolean
        get() = sessionActive.get()

    /**
     * Starts the connection loop. Idempotent: once started, the manager keeps
     * the connection alive (with backoff) until [disconnect] is called.
     */
    fun start() {
        if (started.getAndSet(true)) return
        connectionJob = scope.launch { connectionLoop() }
    }

    private suspend fun connectionLoop() {
        var backoffMs = 0L
        while (currentCoroutineContext().isActive) {
            if (backoffMs > 0) delay(backoffMs)

            val token = tokenProvider()
            if (token == null) {
                _state.value = ConnectionState.DISCONNECTED
                backoffMs = 5_000L
                continue
            }

            _state.value = if (backoffMs > 0) ConnectionState.RECONNECTING else ConnectionState.CONNECTING
            try {
                runSession(token)
                backoffMs = if (backoffMs == 0L) 1_000L else minOf(backoffMs * 2, 30_000L)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                backoffMs = if (backoffMs == 0L) 1_000L else minOf(backoffMs * 2, 30_000L)
            }
        }
    }

    private suspend fun runSession(token: String) {
        client.webSocket(
            urlString = wsUrl,
            request = {
                headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        ) {
            sessionActive.set(true)
            _state.value = ConnectionState.CONNECTED

            // The server requires an in-band AUTHENTICATE before it registers
            // this connection, even though the bearer header is set.
            send(Frame.Text(json.encodeToString(WsEvent.Authenticate(AuthPayload(access_token = token))) ))

            coroutineScope {
                // Reader: parse incoming frames into the event flow.
                val reader = launch {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            try {
                                val event = json.decodeFromString<WsEvent>(frame.readText())
                                _events.emit(event)
                            } catch (_: Exception) {
                                // Unparseable frame: skip it, keep the session.
                            }
                        }
                    }
                }

                // Writer: the single consumer of the outbound queue. Anything
                // queued while offline is flushed here on reconnect.
                val writer = launch {
                    for (event in outboundQueue) {
                        try {
                            send(Frame.Text(json.encodeToString(event)))
                        } catch (_: Exception) {
                            break // socket dead; the connection loop reconnects
                        }
                    }
                }

                reader.join()
                writer.cancel()
            }
            sessionActive.set(false)
        }
    }

    /** Queue an event for delivery. Frames sent while offline flush on reconnect. */
    suspend fun send(event: WsEvent): Result<Unit> {
        start()
        return if (!outboundQueue.isClosedForSend && outboundQueue.trySend(event).isSuccess) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("WebSocket is stopped"))
        }
    }

    suspend fun sendMessage(
        conversationId: Long,
        type: String,
        text: String?,
        mediaId: Long?,
        replyToId: Long?,
        tempId: String
    ): Result<Unit> {
        val payload = MessageSendPayload(
            conversation_id = conversationId,
            type = type,
            text = text,
            media_id = mediaId,
            reply_to_id = replyToId,
            temp_id = tempId
        )
        return send(WsEvent.MessageSend(id = tempId, payload = payload))
    }

    suspend fun sendTypingStart(conversationId: Long): Result<Unit> =
        send(WsEvent.TypingStart(payload = TypingPayload(conversation_id = conversationId)))

    suspend fun sendTypingStop(conversationId: Long): Result<Unit> =
        send(WsEvent.TypingStop(payload = TypingPayload(conversation_id = conversationId)))

    suspend fun sendReadReceipt(messageId: Long, conversationId: Long): Result<Unit> =
        send(WsEvent.MessageRead(
            payload = MessageReadPayload(
                message_id = messageId,
                conversation_id = conversationId,
                read_by = 0, // server derives the user from the session
                read_at = System.currentTimeMillis() / 1000
            )
        ))

    /** Stops the connection loop and discards queued frames. */
    fun disconnect() {
        started.set(false)
        connectionJob?.cancel()
        connectionJob = null
        // Drain anything queued so a later restart doesn't replay stale sends.
        while (true) {
            val r = outboundQueue.tryReceive()
            if (r.isFailure) break
        }
        sessionActive.set(false)
        _state.value = ConnectionState.DISCONNECTED
    }
}
