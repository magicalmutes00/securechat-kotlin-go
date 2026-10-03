package com.securechat.data.sync

import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.remote.websocket.WsEvent
import com.securechat.domain.model.MessageStatus
import com.securechat.data.remote.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consumes WebSocket events and applies them to the local Room cache so the
 * UI (which observes Room flows) stays live: outgoing acks replace optimistic
 * rows, incoming messages are inserted, and receipts/delete marks are applied.
 */
@Singleton
class WebSocketEventProcessor @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val messageDao: MessageDao
) {
    private var job: Job? = null

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
            else -> Unit
        }
    }

    private suspend fun handleAck(event: WsEvent.MessageAck) {
        val entity = messageDao.getByTempId(event.payload.temp_id) ?: return
        messageDao.update(
            entity.copy(
                serverId = event.payload.message_id,
                status = MessageStatus.SENT.name.lowercase(),
                isOptimistic = false,
                createdAt = event.payload.created_at,
                updatedAt = event.payload.created_at
            )
        )
    }

    private suspend fun handleReceived(event: WsEvent.MessageReceived) {
        val dto = event.payload.message
        // Dedupe: the same message can arrive via WS after a REST page load.
        if (messageDao.getByServerId(dto.id).first() != null) return

        messageDao.insert(
            MessageEntity(
                serverId = dto.id,
                conversationId = dto.conversation_id,
                senderId = dto.sender_id,
                type = dto.type,
                text = dto.text,
                mediaId = dto.media?.id,
                replyToId = dto.reply_to?.id,
                status = dto.status,
                createdAt = dto.created_at,
                updatedAt = dto.updated_at,
                deliveredAt = dto.delivered_at,
                readAt = dto.read_at,
                deletedAt = dto.deleted_at,
                isOptimistic = false
            )
        )
    }
}
