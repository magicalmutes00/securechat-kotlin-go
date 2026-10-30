package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import com.securechat.domain.model.ConversationParticipant
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    suspend fun getConversations(): Result<List<Conversation>>
    suspend fun getConversation(conversationId: Long): Result<Conversation>
    suspend fun createDirectConversation(participantPhone: String): Result<Conversation>
    suspend fun createDirectConversationWithUser(userId: Long): Result<Conversation>
    /** Creates a named group; [memberIds] are the other members, not the caller. */
    suspend fun createGroup(name: String, memberIds: List<Long>): Result<Conversation>
    /** Retitles a group. Returns the updated conversation. */
    suspend fun renameConversation(conversationId: Long, name: String): Result<Conversation>
    /** Adds members to a group; returns the updated conversation. */
    suspend fun addMembers(conversationId: Long, userIds: List<Long>): Result<Conversation>
    /** Removes one member from a group; returns the updated conversation. */
    suspend fun removeMember(conversationId: Long, userId: Long): Result<Conversation>
    suspend fun deleteConversation(conversationId: Long): Result<Unit>

    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(conversationId: Long): Flow<Conversation?>
    /**
     * Live participant presence for a conversation, sourced from the local cache
     * and updated by USER_ONLINE / USER_OFFLINE socket events.
     */
    fun observeParticipants(conversationId: Long): Flow<List<ConversationParticipant>>
    fun observeUnreadCount(): Flow<Int>
}