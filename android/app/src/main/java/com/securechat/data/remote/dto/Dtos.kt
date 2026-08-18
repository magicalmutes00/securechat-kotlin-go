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
@Serializable
data class UserDto(
    val id: Long,
    val phone_number: String?,
    val email: String? = null,
    val username: String?,
    val display_name: String,
    val profile_image_id: Long?,
    val last_seen: Long?,
    val is_online: Boolean,
    val created_at: Long,
    val updated_at: Long
)

@Serializable
data class UpdateProfileRequest(
    val display_name: String,
    val username: String?
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
    val last_message: MessageDto?,
    val unread_count: Int,
    val created_at: Long,
    val updated_at: Long
)

@Serializable
data class ConversationParticipantDto(
    val conversation_id: Long,
    val user_id: Long,
    val joined_at: Long,
    val left_at: Long?,
    val user: UserDto?
)

@Serializable
data class CreateConversationRequest(
    val participant_phone: String
)

// Message DTOs
@Serializable
data class MessageDto(
    val id: Long,
    val conversation_id: Long,
    val sender_id: Long,
    val type: String,
    val text: String?,
    val media: MediaDto?,
    val reply_to: MessageDto?,
    val status: String,
    val created_at: Long,
    val updated_at: Long,
    val delivered_at: Long?,
    val read_at: Long?,
    val deleted_at: Long?
)

@Serializable
data class MessagePageDto(
    val messages: List<MessageDto>,
    val next_cursor: Long?,
    val has_more: Boolean
)

// Media DTOs
@Serializable
data class MediaDto(
    val id: Long,
    val message_id: Long,
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