package com.securechat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long,
    val conversationId: Long,
    val senderId: Long,
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
    TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT
}

enum class MessageStatus {
    PENDING, SENDING, SENT, DELIVERED, READ, FAILED
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
    IMAGE, VIDEO, RAW, AUDIO
}