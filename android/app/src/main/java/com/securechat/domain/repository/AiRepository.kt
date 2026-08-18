package com.securechat.domain.repository

import com.securechat.core.common.Result
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    suspend fun getSmartReplies(conversationId: Long, lastMessages: List<String>): Result<List<String>>
    suspend fun summarizeConversation(conversationId: Long, messageCount: Int): Result<String>
    suspend fun summarizeDocument(documentUrl: String): Result<String>
    suspend fun searchMessages(query: String, conversationId: Long?): Result<List<SearchResult>>
    suspend fun chatWithAssistant(message: String, context: String?): Result<String>
    
    fun observeAiAvailability(): Flow<Boolean>
}

data class SearchResult(
    val messageId: Long,
    val conversationId: Long,
    val snippet: String,
    val timestamp: Long,
    val relevanceScore: Double
)