package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun getMessages(
        conversationId: Long,
        limit: Int,
        cursor: Long?
    ): Result<MessagePage>

    suspend fun sendMessage(message: Message): Result<Message>
    suspend fun resendMessage(messageId: Long): Result<Unit>
    suspend fun deleteMessage(messageId: Long): Result<Unit>
    suspend fun markAsRead(conversationId: Long, messageId: Long): Result<Unit>
    suspend fun markConversationAsRead(conversationId: Long): Result<Unit>

    fun observeMessages(conversationId: Long): Flow<List<Message>>
    fun observeMessageStatus(messageId: Long): Flow<MessageStatus>

    enum class MessageStatus {
        PENDING, SENDING, SENT, DELIVERED, READ, FAILED
    }

    data class MessagePage(
        val messages: List<Message>,
        val nextCursor: Long?,
        val hasMore: Boolean
    )
}