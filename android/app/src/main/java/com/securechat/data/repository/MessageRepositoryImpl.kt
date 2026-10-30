package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.core.utils.UserSession
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.entity.MediaEntity
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.local.entity.MessageWithMedia
import com.securechat.data.local.entity.toDomain
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.MessageDto
import com.securechat.data.remote.dto.MediaDto
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.data.sync.PendingOperationQueue
import com.securechat.domain.model.Message
import com.securechat.domain.model.Media
import com.securechat.domain.model.MediaResourceType
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val messageDao: MessageDao,
    private val mediaDao: MediaDao,
    private val webSocketManager: WebSocketManager,
    private val operationQueue: PendingOperationQueue
) : MessageRepository {

    override suspend fun getMessages(
        conversationId: Long,
        limit: Int,
        cursor: String?
    ): Result<MessageRepository.MessagePage> {
        return withContext(Dispatchers.IO) {
            apiService.getMessages(conversationId, cursor, limit)
                .map { response ->
                    val messages = response.messages.map { dto ->
                        val message = mapToMessage(dto)
                        saveMessage(message)
                        message
                    }
                    MessageRepository.MessagePage(
                        messages = messages,
                        nextCursor = response.next_cursor,
                        hasMore = response.has_more
                    )
                }
        }
    }

    override suspend fun sendMessage(message: Message): Result<Message> {
        return withContext(Dispatchers.IO) {
            // Save optimistic message locally first
            val optimisticMessage = message.copy(isOptimistic = true)
            saveMessage(optimisticMessage)

            val messageType = when (message.type) {
                MessageType.TEXT -> "text"
                MessageType.IMAGE -> "image"
                MessageType.VIDEO -> "video"
                MessageType.AUDIO -> "audio"
                MessageType.DOCUMENT -> "document"
            }

            // The server only accepts sends over WebSocket. The manager queues
            // frames made while offline and flushes them on reconnect; the
            // MESSAGE_ACK event flips this row to SENT via the event processor.
            val tempId = message.tempId ?: java.util.UUID.randomUUID().toString()
            val sendResult = webSocketManager.sendMessage(
                conversationId = message.conversationId,
                type = messageType,
                text = message.text,
                mediaId = message.media?.id,
                replyToId = message.replyTo?.id,
                tempId = tempId
            )

            if (sendResult.isFailure) {
                // storageValue (lowercase) is the form the DAO filters on;
                // writing `name` here left the row invisible to getFailedMessages.
                database.messageDao().getByTempId(tempId)?.let { entity ->
                    database.messageDao().update(
                        entity.copy(
                            status = MessageStatus.FAILED.storageValue,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            Result.success(optimisticMessage.copy(tempId = tempId))
        }
    }

    override suspend fun resendMessage(messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val entity = messageDao.getById(messageId).first()
                ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))

            // Compare through fromStorage: the row holds "failed" while
            // MessageStatus.FAILED.name is "FAILED", so the old equality check
            // was never true and retry was permanently dead.
            if (MessageStatus.fromStorage(entity.status) != MessageStatus.FAILED) {
                return@withContext Result.failure(IllegalStateException("Message is not in failed state"))
            }

            val tempId = entity.tempId ?: UUID.randomUUID().toString()

            messageDao.update(
                entity.copy(
                    status = MessageStatus.SENDING.storageValue,
                    tempId = tempId,
                    updatedAt = System.currentTimeMillis()
                )
            )

            // Actually re-send. Previously this only flipped the status, so the
            // row sat in SENDING forever and the user saw a spinner that never
            // resolved.
            val sendResult = webSocketManager.sendMessage(
                conversationId = entity.conversationId,
                type = entity.type,
                text = entity.text,
                mediaId = entity.mediaId,
                replyToId = entity.replyToId,
                tempId = tempId
            )

            if (sendResult.isFailure) {
                messageDao.update(
                    entity.copy(
                        status = MessageStatus.FAILED.storageValue,
                        tempId = tempId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Result.failure(
                    (sendResult as? Result.Failure)?.exception
                        ?: IllegalStateException("Resend failed")
                )
            } else {
                Result.success(Unit)
            }
        }
    }

    override suspend fun deleteMessage(messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val entity = messageDao.getById(messageId).first()

            // A message that never reached the server has nothing to delete there,
            // and nothing to queue either — it is not in any conversation yet.
            if (entity?.serverId == null) {
                messageDao.deleteById(messageId)
                return@withContext Result.success(Unit)
            }

            when (val deleted = apiService.deleteMessage(entity.serverId)) {
                is Result.Success -> {
                    messageDao.deleteById(messageId)
                    Result.success(Unit)
                }
                is Result.Failure -> {
                    // Hide it immediately — the user asked for it gone — but queue
                    // the server-side delete. Returning success here would claim
                    // the delete happened when the message is still in everyone
                    // else's history.
                    operationQueue.enqueueMessageDelete(entity.conversationId, entity.serverId)
                    messageDao.deleteById(messageId)
                    Result.failure(deleted.exception)
                }
            }
        }
    }

    override suspend fun markAsRead(conversationId: Long, messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val sent = webSocketManager.sendReadReceipt(messageId, conversationId)
            if (sent.isFailure) {
                operationQueue.enqueueReadReceipt(conversationId, messageId)
            }
            sent
        }
    }

    override suspend fun markConversationAsRead(conversationId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Read the newest unread id *before* flipping the local rows: the
            // query filters on `status != 'read'`, so running it afterwards always
            // found nothing and the receipt was never sent at all.
            val latestUnread = messageDao.getLatestUnreadServerId(conversationId, currentUserId())

            messageDao.markConversationRead(conversationId, currentUserId(), System.currentTimeMillis())

            latestUnread?.let { serverId ->
                val sent = webSocketManager.sendReadReceipt(serverId, conversationId)
                if (sent.isFailure) {
                    // Socket down: keep it, so the sender still gets their blue
                    // ticks once the connection is back.
                    operationQueue.enqueueReadReceipt(conversationId, serverId)
                }
            }
            Result.success(Unit)
        }
    }

    // The receipt frame itself carries 0 for the actor — the server derives the
    // identity from the session — but the local query needs the real id: with 0,
    // `senderId != :currentUserId` matched every row, so opening a chat marked
    // your own outgoing messages read.
    private fun currentUserId(): Long = UserSession.currentUserId

    override fun observeMessages(conversationId: Long): kotlinx.coroutines.flow.Flow<List<Message>> {
        return database.messageDao().observeWithMedia(conversationId)
            .map { rows ->
                rows.map { mapToMessage(it) }
            }
            .distinctUntilChanged()
    }

    override fun observeMessageStatus(messageId: Long): Flow<MessageStatus> {
        return database.messageDao().getById(messageId)
            .map { entity ->
                entity?.let { MessageStatus.fromStorage(it.status) } ?: MessageStatus.PENDING
            }
            .distinctUntilChanged()
    }

    private fun mapToMessage(dto: MessageDto): Message {
        return Message(
            id = dto.id,
            conversationId = dto.conversation_id,
            senderId = dto.sender_id,
            senderName = dto.sender_name,
            type = MessageType.fromStorage(dto.type),
            text = dto.text,
            media = dto.media?.let { mapToMedia(it) },
            replyTo = dto.reply_to?.let { mapToMessage(it) },
            status = MessageStatus.fromStorage(dto.status),
            createdAt = dto.created_at,
            updatedAt = dto.updated_at,
            deliveredAt = dto.delivered_at,
            readAt = dto.read_at,
            deletedAt = dto.deleted_at
        )
    }

    private fun mapToMessage(row: MessageWithMedia): Message {
        val entity = row.message
        return Message(
            id = entity.id,
            conversationId = entity.conversationId,
            senderId = entity.senderId,
            senderName = entity.senderName,
            type = MessageType.fromStorage(entity.type),
            text = entity.text,
            // Resolved by the @Relation join on media.serverId. A message whose
            // media row is missing (deleted asset) keeps media = null and renders
            // as its placeholder rather than crashing.
            media = row.media?.let { mapToMedia(it) },
            replyTo = null,
            status = MessageStatus.fromStorage(entity.status),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deliveredAt = entity.deliveredAt,
            readAt = entity.readAt,
            deletedAt = entity.deletedAt,
            tempId = entity.tempId,
            isOptimistic = entity.isOptimistic
        )
    }

    private fun mapToMedia(entity: MediaEntity): Media = entity.toDomain()

    private fun mapToMedia(dto: MediaDto): Media {
        return Media(
            id = dto.id,
            messageId = dto.message_id ?: 0,
            cloudinaryPublicId = dto.cloudinary_public_id,
            resourceType = MediaResourceType.fromStorage(dto.resource_type),
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
    }

    private suspend fun saveMessage(message: Message) {
        // A message with a server id may already be cached — it can arrive on the
        // socket before the REST page that also contains it. Updating that row
        // keeps one row per message; inserting again showed every message twice,
        // and (because media is keyed by message id) cached the attachment twice.
        val existing = if (!message.isOptimistic && message.id > 0) {
            database.messageDao().getByServerId(message.id).first()
        } else {
            null
        }

        val entity = MessageEntity(
            id = existing?.id ?: 0,
            serverId = if (message.isOptimistic) null else message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            senderName = message.senderName,
            type = message.type.storageValue,
            text = message.text,
            mediaId = message.media?.id,
            replyToId = message.replyTo?.id,
            status = message.status.storageValue,
            createdAt = message.createdAt,
            updatedAt = message.updatedAt,
            deliveredAt = message.deliveredAt,
            readAt = message.readAt,
            deletedAt = message.deletedAt,
            tempId = message.tempId ?: existing?.tempId,
            isOptimistic = message.isOptimistic
        )

        // The row id the insert assigned, not `entity.id` — that stays 0 for a
        // new optimistic message, and using it as the media row's messageId
        // wrote 0 for every attachment. `media.messageId` is uniquely indexed, so
        // the second image in a conversation failed the insert outright.
        val localMessageId = if (existing != null) {
            database.messageDao().update(entity)
            entity.id
        } else {
            database.messageDao().insert(entity)
        }

        message.media?.let { media ->
            // Exactly one media row per message: the relation expects a single
            // match, and `serverId` carries no unique constraint, so re-saving a
            // message (a REST page refresh, a WS echo of the same message) would
            // otherwise append a duplicate that the @Relation resolution chokes on.
            database.mediaDao().deleteByMessageId(localMessageId)

            val mediaEntity = MediaEntity(
                serverId = if (media.id > 0) media.id else null,
                messageId = localMessageId,
                cloudinaryPublicId = media.cloudinaryPublicId,
                resourceType = media.resourceType.storageValue,
                secureUrl = media.secureUrl,
                originalFilename = media.originalFilename,
                mimeType = media.mimeType,
                fileSize = media.fileSize,
                width = media.width,
                height = media.height,
                duration = media.duration,
                sha256 = media.sha256,
                thumbnailUrl = media.thumbnailUrl,
                createdAt = media.createdAt
            )
            database.mediaDao().insert(mediaEntity)
        }
    }
}