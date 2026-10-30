package com.securechat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: Long,
    val type: ConversationType,
    /** Set for groups; a direct conversation leaves it null. */
    val name: String? = null,
    val participants: List<ConversationParticipant>,
    val lastMessage: Message?,
    val unreadCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun getOtherParticipant(currentUserId: Long): ConversationParticipant? {
        return participants.find { it.userId != currentUserId }
    }

    /**
     * Title shown in the list and the chat header.
     *
     * A group is titled by its chosen name. Falling back to a member's name when
     * the name is missing keeps an unnamed group renderable rather than blank.
     * A direct conversation is titled by the other participant — with three or
     * more members `find` would return an arbitrary one, which is why this is
     * gated on the conversation type rather than on participant count.
     */
    fun getDisplayName(currentUserId: Long): String {
        if (type == ConversationType.GROUP) {
            return name?.takeIf { it.isNotBlank() }
                ?: participants.filter { it.userId != currentUserId }
                    .joinToString(", ") { it.user?.displayName ?: "Unknown" }
                ?: "Group"
        }
        return getOtherParticipant(currentUserId)?.user?.displayName ?: "Unknown"
    }

    fun getAvatarUrl(currentUserId: Long): String? {
        // Group avatars are not implemented yet; direct avatars come from the
        // other participant's profile image.
        return getOtherParticipant(currentUserId)?.user?.let { null }
    }

    val memberCount: Int get() = participants.size
}

@Serializable
data class ConversationParticipant(
    val conversationId: Long,
    val userId: Long,
    val joinedAt: Long,
    val leftAt: Long? = null,
    /** Mirrors the server's presence state for this participant. */
    val isOnline: Boolean = false,
    /** Unix millis of the participant's last activity, 0 when unknown. */
    val lastSeen: Long = 0L,
    val user: User? = null
)

enum class ConversationType {
    DIRECT, GROUP;

    /** Lowercase form used by the wire protocol and Room. */
    val storageValue: String get() = name.lowercase()

    companion object {
        /**
         * Decodes a type from the wire or the database, tolerating either casing
         * and falling back to [DIRECT] for anything unknown. `valueOf` on a raw
         * server string throws, and that exception escapes the `Result.map`
         * transform into `viewModelScope` rather than surfacing as a failure.
         */
        fun fromStorage(raw: String?): ConversationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DIRECT
    }
}