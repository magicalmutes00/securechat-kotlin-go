package com.securechat.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: JsonElement? = null
)

// Pagination
@Serializable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean
)

// Auth DTOs
@Serializable
data class SendOtpRequest(
    val phone_number: String
)

@Serializable
data class SendOtpResponse(
    val expires_in: Int,
    val resend_cooldown: Int
)

@Serializable
data class VerifyOtpRequest(
    val phone_number: String,
    val otp: String,
    val device_name: String,
    val device_identifier: String
)

@Serializable
data class VerifyOtpResponse(
    val access_token: String,
    val refresh_token: String,
    val access_expires_in: Int,
    val refresh_expires_in: Int,
    val user: UserDto
)

@Serializable
data class GoogleSignInRequest(
    val id_token: String,
    val device_name: String,
    val device_identifier: String
)

@Serializable
data class FirebaseSignInRequest(
    val id_token: String,
    val device_name: String,
    val device_identifier: String
)

@Serializable
data class RefreshTokenRequest(
    val refresh_token: String
)

@Serializable
data class RefreshTokenResponse(
    val access_token: String,
    val refresh_token: String,
    val access_expires_in: Int,
    val refresh_expires_in: Int
)

// User DTOs
// Nullable fields carry defaults because the backend uses omitempty for them
// (a Google user, for instance, has no username/profile image yet) and the
// auth endpoints omit last_seen/is_online/created_at/updated_at entirely —
// kotlinx treats a nullable field without a default as a required key.
@Serializable
data class UserDto(
    val id: Long,
    val phone_number: String? = null,
    val email: String? = null,
    val username: String? = null,
    val display_name: String = "",
    val profile_image_id: Long? = null,
    val avatar_url: String? = null,
    val last_seen: Long? = null,
    val is_online: Boolean = false,
    val created_at: Long = 0,
    val updated_at: Long = 0
)

@Serializable
data class UpdateProfileRequest(
    val display_name: String,
    val username: String? = null,
    val profile_image_id: Long? = null
)

@Serializable
data class UserSettings(
    val userId: Long,
    val theme: String,
    val notificationsEnabled: Boolean,
    val mediaAutoDownload: Boolean,
    val language: String
)

// Conversation DTOs
@Serializable
data class ConversationDto(
    val id: Long,
    val type: String,
    val participants: List<ConversationParticipantDto>,
    val last_message: MessagePreviewDto? = null,
    val unread_count: Int,
    val created_at: Long,
    val updated_at: Long
)

// The server's participant payload is flat (user fields inlined, no join
// metadata) — see ParticipantResponse in the Go conversations handler.
@Serializable
data class ConversationParticipantDto(
    val user_id: Long,
    val display_name: String,
    val username: String? = null,
    val profile_image_id: Long? = null,
    val is_online: Boolean = false
)

// Server-side MessagePreviewResponse: a trimmed projection of a message —
// deliberately not a full MessageDto (no conversation_id/updated_at).
@Serializable
data class MessagePreviewDto(
    val id: Long,
    val sender_id: Long,
    val type: String,
    val text: String? = null,
    val status: String,
    val created_at: Long
)

@Serializable
data class CreateConversationRequest(
    val participant_phone: String? = null,
    val participant_id: Long? = null
)

// Message DTOs
// Nullable fields default to null because the backend uses omitempty for
// them (and never sends deleted_at at all) — kotlinx treats a nullable field
// without a default as a required key.
@Serializable
data class MessageDto(
    val id: Long,
    val conversation_id: Long,
    val sender_id: Long,
    val type: String,
    val text: String? = null,
    val media: MediaDto? = null,
    val reply_to: MessageDto? = null,
    val status: String,
    val created_at: Long,
    val updated_at: Long,
    val delivered_at: Long? = null,
    val read_at: Long? = null,
    val deleted_at: Long? = null
)

@Serializable
data class MessagePageDto(
    val messages: List<MessageDto>,
    val next_cursor: Long? = null,
    val has_more: Boolean
)

// Media DTOs
@Serializable
data class MediaDto(
    val id: Long,
    // Avatar media has no message_id yet (server returns it null via omitempty);
    // message-attached media links it afterwards. Nullable so both decode.
    val message_id: Long? = null,
    val cloudinary_public_id: String,
    val resource_type: String,
    val secure_url: String,
    val original_filename: String,
    val mime_type: String,
    val file_size: Long,
    val width: Int?,
    val height: Int?,
    val duration: Double?,
    val sha256: String,
    val thumbnail_url: String?,
    val created_at: Long
)

@Serializable
data class SignUploadRequest(
    val resource_type: String,
    val filename: String,
    val mime_type: String,
    val file_size: Long
)

@Serializable
data class SignUploadResponse(
    val upload_url: String,
    val public_id: String,
    val signature: String,
    val timestamp: Long,
    val api_key: String,
    val folder: String,
    val resource_type: String,
    val allowed_formats: List<String>,
    val max_file_size: Long,
    val transformation: String?
)

@Serializable
data class CompleteUploadRequest(
    val conversation_id: Long,
    val cloudinary_public_id: String,
    val resource_type: String,
    val secure_url: String,
    val original_filename: String,
    val mime_type: String,
    val file_size: Long,
    val width: Int?,
    val height: Int?,
    val duration: Double?,
    val sha256: String
)

// Device DTOs
@Serializable
data class DeviceDto(
    val id: Long,
    val user_id: Long,
    val device_name: String,
    val device_identifier: String,
    val platform: String,
    val created_at: Long,
    val last_seen: Long?
)