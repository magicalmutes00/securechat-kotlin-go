package com.securechat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import kotlinx.serialization.Serializable

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["username"], unique = true)
    ]
)
@Serializable
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val phoneNumber: String,
    val username: String?,
    val displayName: String,
    val profileImageId: Long? = null,
    val lastSeen: Long? = null,
    val isOnline: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "devices",
    indices = [
        Index(value = ["userId", "deviceIdentifier"], unique = true),
        Index(value = ["userId"])
    ]
)
@Serializable
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val userId: Long,
    val deviceName: String,
    val deviceIdentifier: String,
    val platform: String = "android",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeen: Long? = null
)

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["deviceId"]),
        Index(value = ["expiresAt"]),
        Index(value = ["revokedAt"])
    ]
)
@Serializable
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val userId: Long,
    val deviceId: Long,
    val refreshTokenHash: String,
    val expiresAt: Long,
    val revokedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["updatedAt"])
    ]
)
@Serializable
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val type: String = "direct",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversation_participants",
    primaryKeys = ["conversationId", "userId"],
    indices = [
        Index(value = ["userId"])
    ]
)
@Serializable
data class ConversationParticipantEntity(
    val conversationId: Long,
    val userId: Long,
    val joinedAt: Long = System.currentTimeMillis(),
    val leftAt: Long? = null
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "createdAt"]),
        Index(value = ["senderId"]),
        Index(value = ["replyToId"]),
        Index(value = ["status"]),
        Index(value = ["serverId"])
    ]
)
@Serializable
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val conversationId: Long,
    val senderId: Long,
    val type: String,
    val text: String? = null,
    val mediaId: Long? = null,
    val replyToId: Long? = null,
    val status: String = MessageStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val deletedAt: Long? = null,
    val tempId: String? = null,
    val isOptimistic: Boolean = true
)

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["sha256"])
    ]
)
@Serializable
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: Long? = null,
    val messageId: Long,
    val cloudinaryPublicId: String,
    val resourceType: String,
    val secureUrl: String,
    val originalFilename: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val sha256: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pending_uploads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["nextRetryAt"])
    ]
)
@Serializable
data class PendingUploadEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val nextRetryAt: Long = System.currentTimeMillis(),
    val status: String = "pending"
)

@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["status"]),
        Index(value = ["nextRetryAt"])
    ]
)
@Serializable
data class PendingOperationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payloadJson: String,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val nextRetryAt: Long = System.currentTimeMillis(),
    val status: String = "pending"
)

@Entity(
    tableName = "user_settings",
    primaryKeys = ["userId"]
)
@Serializable
data class UserSettingsEntity(
    val userId: Long,
    val theme: String = "system",
    val notificationsEnabled: Boolean = true,
    val mediaAutoDownload: Boolean = true,
    val language: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)