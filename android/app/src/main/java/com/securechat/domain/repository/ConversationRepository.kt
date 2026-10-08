package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    suspend fun getConversations(): Result<List<Conversation>>
    suspend fun getConversation(conversationId: Long): Result<Conversation>
    suspend fun createDirectConversation(participantPhone: String): Result<Conversation>
    suspend fun createDirectConversationWithUser(userId: Long): Result<Conversation>
    suspend fun deleteConversation(conversationId: Long): Result<Unit>
    
    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: Long): Flow<Conversation?>
    fun observeUnreadCount(): Flow<Int>
}