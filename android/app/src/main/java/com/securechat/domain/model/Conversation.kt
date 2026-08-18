package com.securechat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: Long,
    val type: ConversationType,
    val participants: List<ConversationParticipant>,
    val lastMessage: Message?,
    val unreadCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun getOtherParticipant(currentUserId: Long): ConversationParticipant? {
        return participants.find { it.userId != currentUserId }
    }

    fun getDisplayName(currentUserId: Long): String {
        val other = getOtherParticipant(currentUserId)
        return other?.user?.displayName ?: "Unknown"
    }

    fun getAvatarUrl(currentUserId: Long): String? {
        val other = getOtherParticipant(currentUserId)
        return other?.user?.let { null }
    }
}

@Serializable
data class ConversationParticipant(
    val conversationId: Long,
    val userId: Long,
    val joinedAt: Long,
    val leftAt: Long? = null,
    val user: User? = null
)

enum class ConversationType {
    DIRECT, GROUP
}