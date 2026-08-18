package com.securechat.data.remote.api

import com.securechat.core.common.Result
import com.securechat.data.remote.dto.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*

interface ApiService {
    // Auth
    suspend fun sendOtp(request: SendOtpRequest): Result<SendOtpResponse>
    suspend fun verifyOtp(request: VerifyOtpRequest): Result<VerifyOtpResponse>
    suspend fun googleSignIn(request: GoogleSignInRequest): Result<VerifyOtpResponse>
    suspend fun refreshToken(request: RefreshTokenRequest): Result<RefreshTokenResponse>
    suspend fun logout(): Result<Unit>

    // Users
    suspend fun getCurrentUser(): Result<UserDto>
    suspend fun updateProfile(request: UpdateProfileRequest): Result<UserDto>
    suspend fun searchUsers(query: String, limit: Int): Result<List<UserDto>>

    // Conversations
    suspend fun getConversations(cursor: String?, limit: Int): Result<CursorPage<ConversationDto>>
    suspend fun createConversation(request: CreateConversationRequest): Result<ConversationDto>
    suspend fun getConversation(id: Long): Result<ConversationDto>
    suspend fun deleteConversation(id: Long): Result<Unit>

    // Messages
    suspend fun getMessages(conversationId: Long, cursor: Long?, limit: Int): Result<MessagePageDto>
    suspend fun deleteMessage(id: Long): Result<Unit>

    // Media
    suspend fun signUpload(request: SignUploadRequest): Result<SignUploadResponse>
suspend fun completeUpload(request: CompleteUploadRequest): Result<MediaDto>
    suspend fun getMedia(id: Long): Result<MediaDto>
    suspend fun deleteMedia(id: Long): Result<Unit>

    // Devices
    suspend fun getDevices(): Result<List<DeviceDto>>
    suspend fun revokeDevice(id: Long): Result<Unit>
}

class ApiServiceImpl(
    private val client: io.ktor.client.HttpClient,
    private val baseUrl: String,
    private val tokenProvider: () -> String?
) : ApiService {

    private fun authHeader(): Map<String, String> {
        return tokenProvider()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
    }

    private suspend fun <T> executeRequest(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: io.ktor.client.plugins.ResponseException) {
            val errorBody = e.response.bodyAsText()
            Result.failure(ApiException(e.response.status, errorBody))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendOtp(request: SendOtpRequest): Result<SendOtpResponse> = executeRequest {
        client.post("$baseUrl/auth/send-otp") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun verifyOtp(request: VerifyOtpRequest): Result<VerifyOtpResponse> = executeRequest {
        client.post("$baseUrl/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun googleSignIn(request: GoogleSignInRequest): Result<VerifyOtpResponse> = executeRequest {
        client.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun refreshToken(request: RefreshTokenRequest): Result<RefreshTokenResponse> = executeRequest {
        client.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun logout(): Result<Unit> = executeRequest {
        client.post("$baseUrl/auth/logout") {
            header("Authorization", "Bearer ${tokenProvider()}")
        }.body()
    }

    override suspend fun getCurrentUser(): Result<UserDto> = executeRequest {
        client.get("$baseUrl/users/me") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun updateProfile(request: UpdateProfileRequest): Result<UserDto> = executeRequest {
        client.patch("$baseUrl/users/me") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<UserDto>> = executeRequest {
        client.get("$baseUrl/users/search") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            parameter("q", query)
            parameter("limit", limit.toString())
        }.body()
    }

    override suspend fun getConversations(cursor: String?, limit: Int): Result<CursorPage<ConversationDto>> = executeRequest {
        client.get("$baseUrl/conversations") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit.toString())
        }.body()
    }

    override suspend fun createConversation(request: CreateConversationRequest): Result<ConversationDto> = executeRequest {
        client.post("$baseUrl/conversations") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun getConversation(id: Long): Result<ConversationDto> = executeRequest {
        client.get("$baseUrl/conversations/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun deleteConversation(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/conversations/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun getMessages(conversationId: Long, cursor: Long?, limit: Int): Result<MessagePageDto> = executeRequest {
        client.get("$baseUrl/conversations/$conversationId/messages") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            if (cursor != null) parameter("cursor", cursor.toString())
            parameter("limit", limit.toString())
        }.body()
    }

    override suspend fun deleteMessage(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/messages/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun signUpload(request: SignUploadRequest): Result<SignUploadResponse> = executeRequest {
        client.post("$baseUrl/media/sign-upload") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun completeUpload(request: CompleteUploadRequest): Result<MediaDto> = executeRequest {
        client.post("$baseUrl/media/complete") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun getMedia(id: Long): Result<MediaDto> = executeRequest {
        client.get("$baseUrl/media/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun deleteMedia(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/media/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun getDevices(): Result<List<DeviceDto>> = executeRequest {
        client.get("$baseUrl/devices") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }

    override suspend fun revokeDevice(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/devices/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }.body()
    }
}

class ApiException(val status: HttpStatusCode, val body: String) : Exception("API Error: $status - $body")