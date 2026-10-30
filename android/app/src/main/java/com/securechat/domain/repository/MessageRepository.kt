package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun getMessages(
        conversationId: Long,
        limit: Int,
        cursor: String?
    ): Result<MessagePage>

    suspend fun sendMessage(message: Message): Result<Message>
    suspend fun resendMessage(messageId: Long): Result<Unit>
    suspend fun deleteMessage(messageId: Long): Result<Unit>
    suspend fun markAsRead(conversationId: Long, messageId: Long): Result<Unit>
    suspend fun markConversationAsRead(conversationId: Long): Result<Unit>

    fun observeMessages(conversationId: Long): Flow<List<Message>>
    fun observeMessageStatus(messageId: Long): Flow<MessageStatus>

    data class MessagePage(
        val messages: List<Message>,
        /**
         * Opaque keyset cursor from the server. It encodes (created_at, id), so
         * it must be echoed back verbatim — parsing it into a Long is what made
         * the previous whole-second cursor drop messages that shared a second.
         */
        val nextCursor: String?,
        val hasMore: Boolean
    )
}