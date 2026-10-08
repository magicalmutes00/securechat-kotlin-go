package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.ConversationDao
import com.securechat.data.local.dao.ConversationParticipantDao
import com.securechat.data.local.entity.ConversationEntity
import com.securechat.data.local.entity.ConversationParticipantEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.ConversationDto
import com.securechat.domain.model.Conversation
import com.securechat.domain.model.ConversationParticipant
import com.securechat.domain.model.ConversationType
import com.securechat.domain.model.User
import com.securechat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val conversationDao: ConversationDao,
    private val participantDao: ConversationParticipantDao
) : ConversationRepository {

    override suspend fun getConversations(): Result<List<Conversation>> {
        return withContext(Dispatchers.IO) {
            apiService.getConversations(cursor = null, limit = 50)
                .map { conversations ->
                    conversations.map { dto ->
                        val conversation = mapToConversation(dto)
                        saveConversation(conversation)
                        conversation
                    }
                }
        }
    }

    override suspend fun getConversation(conversationId: Long): Result<Conversation> {
        return withContext(Dispatchers.IO) {
            val entity = database.conversationDao().getById(conversationId).first()
            if (entity != null && entity.serverId != null) {
                Result.success(mapToConversation(entity))
            } else {
                apiService.getConversation(conversationId)
                    .map { dto ->
                        val conversation = mapToConversation(dto)
                        saveConversation(conversation)
                        conversation
                    }
            }
        }
    }

    override suspend fun createDirectConversation(participantPhone: String): Result<Conversation> {
        return withContext(Dispatchers.IO) {
            apiService.createConversation(com.securechat.data.remote.dto.CreateConversationRequest(participant_phone = participantPhone))
                .map { dto ->
                    val conversation = mapToConversation(dto)
                    saveConversation(conversation)
                    conversation
                }
        }
    }

    override suspend fun deleteConversation(conversationId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            apiService.deleteConversation(conversationId)
                .flatMap {
                    database.conversationDao().deleteById(conversationId)
                    Result.success(Unit)
                }
        }
    }

    override fun observeConversations(): kotlinx.coroutines.flow.Flow<List<Conversation>> {
        return database.conversationDao().getAll()
            .map { entities ->
                entities.map { mapToConversation(it) }
            }
            .distinctUntilChanged()
    }

    override fun observeConversation(conversationId: Long): kotlinx.coroutines.flow.Flow<Conversation?> {
        return database.conversationDao().getById(conversationId)
            .map { it?.let { mapToConversation(it) } }
            .distinctUntilChanged()
    }

    override fun observeUnreadCount(): kotlinx.coroutines.flow.Flow<Int> {
        // This would need a query that joins conversations and messages
        // For now, return 0
        return kotlinx.coroutines.flow.flowOf(0)
    }

    private fun mapToConversation(dto: ConversationDto): Conversation {
        // The server sends participants flat (user fields inlined, no join
        // metadata), so the nested domain shape is assembled here: the
        // conversation id comes from the parent, and join timestamps simply
        // aren't on the wire yet.
        val participants = dto.participants.map { p ->
            ConversationParticipant(
                conversationId = dto.id,
                userId = p.user_id,
                joinedAt = 0,
                leftAt = null,
                user = mapToUser(
                    com.securechat.data.remote.dto.UserDto(
                        id = p.user_id,
                        username = p.username,
                        display_name = p.display_name,
                        profile_image_id = p.profile_image_id,
                        is_online = p.is_online
                    )
                )
            )
        }
        return Conversation(
            id = dto.id,
            type = ConversationType.valueOf(dto.type.uppercase()),
            participants = participants,
            lastMessage = dto.last_message?.let { mapToPreview(it, dto.id) },
            unreadCount = dto.unread_count,
            createdAt = dto.created_at,
            updatedAt = dto.updated_at
        )
    }

    private fun mapToPreview(
        dto: com.securechat.data.remote.dto.MessagePreviewDto,
        conversationId: Long
    ): com.securechat.domain.model.Message {
        // The preview projection carries no conversation_id/updated_at — the
        // parent conversation id and the creation time stand in for them.
        return com.securechat.domain.model.Message(
            id = dto.id,
            conversationId = conversationId,
            senderId = dto.sender_id,
            type = com.securechat.domain.model.MessageType.valueOf(dto.type.uppercase()),
            text = dto.text,
            media = null,
            replyTo = null,
            status = com.securechat.domain.model.MessageStatus.valueOf(dto.status.uppercase()),
            createdAt = dto.created_at,
            updatedAt = dto.created_at,
            deliveredAt = null,
            readAt = null,
            deletedAt = null
        )
    }

    private fun mapToConversation(entity: ConversationEntity): Conversation {
        // Need to load participants and last message
        // Simplified for now
        return Conversation(
            id = entity.id,
            type = ConversationType.valueOf(entity.type.uppercase()),
            participants = emptyList(),
            lastMessage = null,
            unreadCount = 0,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun mapToUser(dto: com.securechat.data.remote.dto.UserDto): User {
        return User(
            id = dto.id,
            phoneNumber = dto.phone_number,
            username = dto.username,
            displayName = dto.display_name,
            profileImageId = dto.profile_image_id,
            lastSeen = dto.last_seen,
            isOnline = dto.is_online,
            createdAt = dto.created_at,
            updatedAt = dto.updated_at
        )
    }

    private fun mapToMessage(dto: com.securechat.data.remote.dto.MessageDto): com.securechat.domain.model.Message {
        return com.securechat.domain.model.Message(
            id = dto.id,
            conversationId = dto.conversation_id,
            senderId = dto.sender_id,
            type = com.securechat.domain.model.MessageType.valueOf(dto.type.uppercase()),
            text = dto.text,
            media = dto.media?.let { mapToMedia(it) },
            replyTo = dto.reply_to?.let { mapToMessage(it) },
            status = com.securechat.domain.model.MessageStatus.valueOf(dto.status.uppercase()),
            createdAt = dto.created_at,
            updatedAt = dto.updated_at,
            deliveredAt = dto.delivered_at,
            readAt = dto.read_at,
            deletedAt = dto.deleted_at
        )
    }

    private fun mapToMedia(dto: com.securechat.data.remote.dto.MediaDto): com.securechat.domain.model.Media {
        return com.securechat.domain.model.Media(
            id = dto.id,
            messageId = dto.message_id ?: 0,
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

    private suspend fun saveConversation(conversation: Conversation) {
        val entity = ConversationEntity(
            serverId = conversation.id,
            type = conversation.type.name.lowercase(),
            createdAt = conversation.createdAt,
            updatedAt = conversation.updatedAt
        )
        database.conversationDao().insert(entity)
        
        conversation.participants.forEach { participant ->
            val participantEntity = ConversationParticipantEntity(
                conversationId = conversation.id,
                userId = participant.userId,
                joinedAt = participant.joinedAt,
                leftAt = participant.leftAt
            )
            database.conversationParticipantDao().insert(participantEntity)
        }
    }
}