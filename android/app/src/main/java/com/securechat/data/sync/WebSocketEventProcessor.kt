package com.securechat.data.sync

import com.securechat.core.common.Result
import com.securechat.core.security.E2eeSessionManager
import com.securechat.data.local.dao.ConversationParticipantDao
import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.entity.MediaEntity
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.MediaDto
import com.securechat.data.remote.websocket.EnvelopePayload
import com.securechat.data.remote.websocket.WsEvent
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.domain.model.MediaResourceType
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.domain.repository.E2eeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consumes WebSocket events and applies them to the local Room cache so the
 * UI (which observes Room flows) stays live: outgoing acks replace optimistic
 * rows, incoming messages are inserted, and receipts/delete marks are applied.
 */
/**
 * An ephemeral typing signal. Deliberately not persisted to Room: typing state is
 * momentary UI, and a stale "typing…" after a crash would be worse than none.
 */
data class TypingEvent(
    val conversationId: Long,
    val userId: Long,
    val isTyping: Boolean
)

@Singleton
class WebSocketEventProcessor @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val messageDao: MessageDao,
    private val mediaDao: MediaDao,
    private val conversationParticipantDao: com.securechat.data.local.dao.ConversationParticipantDao,
    private val e2eeRepository: E2eeRepository,
    private val apiService: ApiService
) {
    private var job: Job? = null

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 64)

    /**
     * Typing signals from other participants. A shared flow rather than Room
     * state on purpose — consumers own the timeout, because a lost TYPING_STOP
     * must not leave "typing…" on screen forever.
     */
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            webSocketManager.events.collect { event -> handle(event) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun handle(event: WsEvent) {
        when (event) {
            is WsEvent.MessageAck -> handleAck(event)
            is WsEvent.MessageReceived -> handleReceived(event)
            is WsEvent.MessageDelivered -> messageDao.markDeliveredByServerId(
                event.payload.message_id,
                event.payload.delivered_at
            )
            is WsEvent.MessageRead -> messageDao.markReadByServerId(
                event.payload.message_id,
                event.payload.read_at
            )
            is WsEvent.MessageDelete -> messageDao.markDeletedByServerId(
                event.payload.message_id,
                System.currentTimeMillis()
            )
            // Presence is a process-wide broadcast carrying only a user id, so it
            // is applied to every conversation that user appears in. Without this
            // the chat header's online state froze at whatever it was when the
            // conversation was last fetched.
            is WsEvent.UserOnline -> applyPresence(event.payload.user_id, true, event.payload.last_seen)
            is WsEvent.UserOffline -> applyPresence(event.payload.user_id, false, event.payload.last_seen)
            // user_id is nullable in the payload type; the server always sets it
            // (it deliberately ignores a client-supplied value), but a frame
            // without an author cannot be attributed, so it is dropped.
            is WsEvent.TypingStart -> event.payload.user_id?.let {
                emitTyping(event.payload.conversation_id, it, true)
            }
            is WsEvent.TypingStop -> event.payload.user_id?.let {
                emitTyping(event.payload.conversation_id, it, false)
            }
            else -> Unit
        }
    }

    private fun emitTyping(conversationId: Long, userId: Long, isTyping: Boolean) {
        // tryEmit with a buffer: a dropped typing frame is harmless, a blocked
        // collector is not.
        _typingEvents.tryEmit(TypingEvent(conversationId, userId, isTyping))
    }

    private suspend fun applyPresence(userId: Long, isOnline: Boolean, lastSeen: Long?) {
        conversationParticipantDao.updatePresence(
            userId = userId,
            isOnline = isOnline,
            lastSeen = lastSeen ?: if (isOnline) System.currentTimeMillis() else 0L
        )
    }

    private suspend fun handleAck(event: WsEvent.MessageAck) {
        val entity = messageDao.getByTempId(event.payload.temp_id) ?: return
        messageDao.update(
            entity.copy(
                serverId = event.payload.message_id,
                status = MessageStatus.SENT.storageValue,
                isOptimistic = false,
                createdAt = event.payload.created_at,
                updatedAt = event.payload.created_at
            )
        )
    }

    private suspend fun handleReceived(event: WsEvent.MessageReceived) {
        val msgObj = event.payload.message
        val serverId = msgObj["id"]?.toString()?.toLongOrNull() ?: return
        val conversationId = msgObj["conversation_id"]?.toString()?.toLongOrNull() ?: return
        val senderId = msgObj["sender_id"]?.toString()?.toLongOrNull() ?: 0L
        val senderName = msgObj["sender_name"]?.toString() ?: senderId.toString()
        val type = msgObj["type"]?.toString() ?: "text"
        val text = msgObj["text"]?.toString() ?: ""
        val status = msgObj["status"]?.toString() ?: "sent"
        val createdAt = msgObj["created_at"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
        val updatedAt = msgObj["updated_at"]?.toString()?.toLongOrNull() ?: createdAt
        val mediaId = msgObj["media_id"]?.toString()?.toLongOrNull()
        val replyToId = msgObj["reply_to_id"]?.toString()?.toLongOrNull()
        val deliveredAt = msgObj["delivered_at"]?.toString()?.toLongOrNull()
        val readAt = msgObj["read_at"]?.toString()?.toLongOrNull()
        val deletedAt = msgObj["deleted_at"]?.toString()?.toLongOrNull()

        // E2EE: decrypt this device's envelope before inserting. Legacy plaintext
        // messages have no envelope and are stored verbatim.
        val finalText = if (event.payload.envelope != null) {
            val env = event.payload.envelope!!
            val ciphertext = Base64.getDecoder().decode(env.ciphertext)
            val decrypted = e2eeRepository.decryptFrom(
                senderDeviceId = senderId,
                senderName = senderName,
                envelopeType = env.envelope_type,
                ciphertext = ciphertext
            )
            decrypted.map { String(it) }.getOrElse { text }
        } else {
            text
        }

        // Dedupe: the same message can arrive via WS after a REST page load.
        val existing = messageDao.getByServerId(serverId).first()
        if (existing != null) {
            // The first copy may have been stored before its media existed (REST
            // page load racing the upload). Backfill the attachment instead of
            // returning, otherwise the bubble stays empty forever.
            val mediaDto = msgObj["media"]?.let { mapMediaDto(it) }
            mediaDto?.let { upsertMedia(it, existing.id) }
            if (mediaDto != null && existing.mediaId != mediaDto.id) {
                messageDao.update(existing.copy(mediaId = mediaDto.id))
            }
            return
        }

        val localId = messageDao.insert(
            MessageEntity(
                serverId = serverId,
                conversationId = conversationId,
                senderId = senderId,
                senderName = senderName,
                type = MessageType.fromStorage(type).storageValue,
                text = finalText,
                // The server media id, mirroring media.serverId so the
                // MessageWithMedia relation resolves.
                mediaId = mediaId,
                replyToId = replyToId,
                // Normalised through fromStorage: the raw server string used to
                // be stored verbatim, so a differently-cased or unknown status
                // produced rows the DAO's lowercase queries never matched.
                status = MessageStatus.fromStorage(status).storageValue,
                createdAt = createdAt,
                updatedAt = updatedAt,
                deliveredAt = deliveredAt,
                readAt = readAt,
                deletedAt = deletedAt,
                isOptimistic = false
            )
        )

        val mediaDto = msgObj["media"]?.let { mapMediaDto(it) }
        mediaDto?.let { upsertMedia(it, localId) }
    }

    /**
     * Parses a media attachment object from a flat WS payload (as sent by the
     * server's MESSAGE_RECEIVED map form) into a MediaDto.
     */
    private fun mapMediaDto(raw: Any?): MediaDto? {
        val obj = raw ?: return null
        return try {
            MediaDto(
                id = (obj as? Number)?.toLong() ?: obj.toString().toLongOrNull() ?: 0,
                message_id = null,
                cloudinary_public_id = "",
                resource_type = "raw",
                secure_url = "",
                original_filename = "",
                mime_type = "",
                file_size = 0,
                width = null,
                height = null,
                duration = null,
                sha256 = "",
                thumbnail_url = null,
                created_at = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stores the attachment row for a message. Deleting first keeps exactly one
     * media row per message: `serverId` has no unique index, so a replace-style
     * upsert would append a second row and the single-value @Relation would then
     * fail to resolve.
     */
    private suspend fun upsertMedia(dto: MediaDto, localMessageId: Long) {
        mediaDao.deleteByMessageId(localMessageId)
        mediaDao.insert(
            MediaEntity(
                serverId = dto.id,
                messageId = localMessageId,
                cloudinaryPublicId = dto.cloudinary_public_id,
                resourceType = MediaResourceType.fromStorage(dto.resource_type).storageValue,
                secureUrl = dto.secure_url,
                originalFilename = dto.original_filename,
                mimeType = dto.mime_type,
                fileSize = dto.file_size,
                width = dto.width,
                height = dto.height,
                duration = dto.duration,
                sha256 = dto.sha256,
                thumbnailUrl = dto.thumbnail_url,
                createdAt = dto.created_at
            )
        )
    }
}
