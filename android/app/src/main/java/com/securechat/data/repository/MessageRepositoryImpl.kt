package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.entity.MediaEntity
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.MessageDto
import com.securechat.data.remote.dto.MediaDto
import com.securechat.domain.model.Message
import com.securechat.domain.model.Media
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val messageDao: MessageDao,
    private val mediaDao: MediaDao
) : MessageRepository {

    override suspend fun getMessages(
        conversationId: Long,
        limit: Int,
        cursor: Long?
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
            
            // Send via WebSocket (handled by WebSocket manager)
            // For now, also send via API as fallback
            val messageType = when (message.type) {
                MessageType.TEXT -> "text"
                MessageType.IMAGE -> "image"
                MessageType.VIDEO -> "video"
                MessageType.AUDIO -> "audio"
                MessageType.DOCUMENT -> "document"
            }
            
            // This would be sent via WebSocket in real implementation
            // For now, we just return the optimistic message
            Result.success(optimisticMessage)
        }
    }

    override suspend fun resendMessage(messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val entity = database.messageDao().getById(messageId).first()
            if (entity != null) {
                if (entity.status == MessageStatus.FAILED.name) {
                    val updatedEntity = entity.copy(
                        status = MessageStatus.SENDING.name,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.messageDao().update(updatedEntity)
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Message is not in failed state"))
                }
            } else {
                Result.failure(IllegalArgumentException("Message not found"))
            }
        }
    }

    override suspend fun deleteMessage(messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            apiService.deleteMessage(messageId)
                .flatMap {
                    database.messageDao().deleteById(messageId)
                    Result.success(Unit)
                }
        }
    }

    override suspend fun markAsRead(conversationId: Long, messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // This would be sent via WebSocket
            Result.success(Unit)
        }
    }

    override suspend fun markConversationAsRead(conversationId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Update local messages as read
            Result.success(Unit)
        }
    }

    override fun observeMessages(conversationId: Long): kotlinx.coroutines.flow.Flow<List<Message>> {
        return database.messageDao().observeByConversationId(conversationId)
            .map { entities ->
                entities.map { mapToMessage(it) }
            }
            .distinctUntilChanged()
    }

    override fun observeMessageStatus(messageId: Long): kotlinx.coroutines.flow.Flow<MessageRepository.MessageStatus> {
        return database.messageDao().getById(messageId)
            .map { entity ->
                entity?.let { MessageRepository.MessageStatus.valueOf(it.status.uppercase()) }
                    ?: MessageRepository.MessageStatus.PENDING
            }
            .distinctUntilChanged()
    }

    private fun mapToMessage(dto: MessageDto): Message {
        return Message(
            id = dto.id,
            conversationId = dto.conversation_id,
            senderId = dto.sender_id,
            type = MessageType.valueOf(dto.type.uppercase()),
            text = dto.text,
            media = dto.media?.let { mapToMedia(it) },
            replyTo = dto.reply_to?.let { mapToMessage(it) },
            status = MessageStatus.valueOf(dto.status.uppercase()),
            createdAt = dto.created_at,
            updatedAt = dto.updated_at,
            deliveredAt = dto.delivered_at,
            readAt = dto.read_at,
            deletedAt = dto.deleted_at
        )
    }

    private fun mapToMessage(entity: MessageEntity): Message {
        return Message(
            id = entity.id,
            conversationId = entity.conversationId,
            senderId = entity.senderId,
            type = MessageType.valueOf(entity.type.uppercase()),
            text = entity.text,
            media = null, // Loaded separately
            replyTo = null,
            status = MessageStatus.valueOf(entity.status.uppercase()),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deliveredAt = entity.deliveredAt,
            readAt = entity.readAt,
            deletedAt = entity.deletedAt,
            tempId = entity.tempId,
            isOptimistic = entity.isOptimistic
        )
    }

    private fun mapToMedia(dto: MediaDto): Media {
        return Media(
            id = dto.id,
            messageId = dto.message_id,
            cloudinaryPublicId = dto.cloudinary_public_id,
            resourceType = com.securechat.domain.model.MediaResourceType.valueOf(dto.resource_type.uppercase()),
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
        val entity = MessageEntity(
            serverId = if (message.isOptimistic) null else message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            type = message.type.name.lowercase(),
            text = message.text,
            mediaId = message.media?.id,
            replyToId = message.replyTo?.id,
            status = message.status.name.lowercase(),
            createdAt = message.createdAt,
            updatedAt = message.updatedAt,
            deliveredAt = message.deliveredAt,
            readAt = message.readAt,
            deletedAt = message.deletedAt,
            tempId = message.tempId,
            isOptimistic = message.isOptimistic
        )
        database.messageDao().insert(entity)
        
        message.media?.let { media ->
            val mediaEntity = MediaEntity(
                serverId = if (media.id > 0) media.id else null,
                messageId = entity.id,
                cloudinaryPublicId = media.cloudinaryPublicId,
                resourceType = media.resourceType.name.lowercase(),
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