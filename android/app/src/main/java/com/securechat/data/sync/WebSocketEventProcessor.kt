package com.securechat.data.sync

import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.entity.MediaEntity
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.remote.dto.MediaDto
import com.securechat.data.remote.websocket.WsEvent
import com.securechat.domain.model.MediaResourceType
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.data.remote.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val conversationParticipantDao: com.securechat.data.local.dao.ConversationParticipantDao
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
        val dto = event.payload.message
        // Dedupe: the same message can arrive via WS after a REST page load.
        val existing = messageDao.getByServerId(dto.id).first()
        if (existing != null) {
            // The first copy may have been stored before its media existed (REST
            // page load racing the upload). Backfill the attachment instead of
            // returning, otherwise the bubble stays empty forever.
            dto.media?.let { upsertMedia(it, existing.id) }
            if (dto.media != null && existing.mediaId != dto.media.id) {
                messageDao.update(existing.copy(mediaId = dto.media.id))
            }
            return
        }

        val localId = messageDao.insert(
            MessageEntity(
                serverId = dto.id,
                conversationId = dto.conversation_id,
                senderId = dto.sender_id,
                senderName = dto.sender_name,
                type = MessageType.fromStorage(dto.type).storageValue,
                text = dto.text,
                // The server media id, mirroring media.serverId so the
                // MessageWithMedia relation resolves.
                mediaId = dto.media?.id,
                replyToId = dto.reply_to?.id,
                // Normalised through fromStorage: the raw server string used to
                // be stored verbatim, so a differently-cased or unknown status
                // produced rows the DAO's lowercase queries never matched.
                status = MessageStatus.fromStorage(dto.status).storageValue,
                createdAt = dto.created_at,
                updatedAt = dto.updated_at,
                deliveredAt = dto.delivered_at,
                readAt = dto.read_at,
                deletedAt = dto.deleted_at,
                isOptimistic = false
            )
        )

        dto.media?.let { upsertMedia(it, localId) }
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
