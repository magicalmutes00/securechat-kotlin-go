package com.securechat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
    /** Author's display name; null when unknown. Used to attribute group messages. */
    val senderName: String? = null,
    val type: MessageType,
    val text: String?,
    val media: Media?,
    val replyTo: Message?,
    val status: MessageStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val deliveredAt: Long?,
    val readAt: Long?,
    val deletedAt: Long?,
    val tempId: String? = null,
    val isOptimistic: Boolean = false
) {
    fun withStatus(newStatus: MessageStatus): Message = copy(
        status = newStatus,
        updatedAt = System.currentTimeMillis()
    )

    fun withServerId(serverId: Long): Message = copy(
        id = serverId,
        isOptimistic = false
    )

    fun isSentByMe(currentUserId: Long): Boolean = senderId == currentUserId

    val isPending: Boolean
        get() = status == MessageStatus.PENDING || status == MessageStatus.SENDING

    val isFailed: Boolean
        get() = status == MessageStatus.FAILED
}

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT;

    /** Lowercase form used by the wire protocol and Room. */
    val storageValue: String get() = name.lowercase()

    companion object {
        /**
         * Decodes a type from the wire or the database, tolerating either
         * casing and falling back to [TEXT] for anything unknown. `valueOf` on a
         * raw server string throws, and because the transform runs inside
         * `Result.map` (which does not guard it) that crash escaped into
         * `viewModelScope` rather than surfacing as a failed Result.
         */
        fun fromStorage(raw: String?): MessageType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: TEXT
    }
}

enum class MessageStatus {
    PENDING, SENDING, SENT, DELIVERED, READ, FAILED;

    /**
     * Lowercase form persisted in Room and exchanged with the server.
     *
     * This is the single source of truth for the stored representation. The DAO
     * queries filter lowercase literals ('pending', 'sending', 'failed'), so any
     * call site that wrote `name` (uppercase) created rows those queries could
     * never match — which silently broke message retry and the pending sweep.
     */
    val storageValue: String get() = name.lowercase()

    companion object {
        /**
         * Decodes a status from the database or the server, tolerating either
         * casing and falling back to [SENT] for unknown values instead of
         * throwing on a status newer than this build.
         */
        fun fromStorage(raw: String?): MessageStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SENT
    }
}

@Serializable
data class Media(
    val id: Long,
    val messageId: Long,
    val cloudinaryPublicId: String,
    val resourceType: MediaResourceType,
    val secureUrl: String,
    val originalFilename: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int?,
    val height: Int?,
    val duration: Double?,
    val sha256: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long
) {
    val isImage: Boolean
        get() = resourceType == MediaResourceType.IMAGE

    val isVideo: Boolean
        get() = resourceType == MediaResourceType.VIDEO

    val isAudio: Boolean
        get() = resourceType == MediaResourceType.AUDIO

    val isDocument: Boolean
        get() = resourceType == MediaResourceType.RAW
}

enum class MediaResourceType {
    IMAGE, VIDEO, RAW, AUDIO;

    /** Lowercase form used by the wire protocol and Room. */
    val storageValue: String get() = name.lowercase()

    companion object {
        /** Decodes a resource type from the wire/DB, falling back to [RAW]. */
        fun fromStorage(raw: String?): MediaResourceType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: RAW
    }
}