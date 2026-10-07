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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
    suspend fun getConversations(cursor: String?, limit: Int): Result<List<ConversationDto>>
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
    private val tokenProvider: () -> String?,
    // Invoked when an authenticated request 401s: rotates the session via the
    // refresh endpoint and reports whether a retry may proceed. Supplied by
    // the DI layer to keep this class free of repository dependencies.
    private val refreshAuth: suspend () -> Boolean
) : ApiService {

    private fun authHeader(): Map<String, String> {
        return tokenProvider()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Single-flight guard: parallel 401s must not race the server's refresh
    // token rotation (a second refresh with the already-consumed token fails
    // and would strand the caller).
    private val refreshMutex = Mutex()

    // Every backend success payload arrives as {"success": true, "data": {...}},
    // so the envelope is unwrapped once, here, and the payload decoded into the
    // endpoint's DTO. Non-2xx responses become ApiException carrying the raw
    // error body — ViewModels inspect that text for specific messages (e.g.
    // "rate limit", "expired"). expectSuccess stays off, so the status check
    // below is what produces ApiException.
    private suspend inline fun <reified T> executeRequest(
        allowRefresh: Boolean = true,
        noinline block: suspend () -> HttpResponse
    ): Result<T> {
        return try {
            var response = block()
            // Access tokens expire after ~15 minutes. On 401, rotate the
            // session once and retry with the fresh token. Pre-auth endpoints
            // (no token yet) and the refresh call itself opt out, otherwise a
            // bad OTP or an invalid refresh token would trigger recursion.
            if (response.status == HttpStatusCode.Unauthorized &&
                allowRefresh && tokenProvider() != null
            ) {
                val staleToken = tokenProvider()
                val canRetry = try {
                    refreshMutex.withLock {
                        // Another caller may have rotated the session while we
                        // waited — the retry then only needs the new token.
                        if (tokenProvider() != staleToken) true else refreshAuth()
                    }
                } catch (e: Exception) {
                    false
                }
                if (canRetry) response = block()
            }
            if (!response.status.isSuccess()) {
                Result.failure(ApiException(response.status, response.bodyAsText()))
            } else {
                val root = json.parseToJsonElement(response.bodyAsText())
                val payload = (root as? JsonObject)?.get("data") ?: root
                if (T::class == Unit::class) {
                    @Suppress("UNCHECKED_CAST")
                    Result.success(Unit as T)
                } else {
                    Result.success(json.decodeFromString(payload.toString()))
                }
            }
        } catch (e: io.ktor.client.plugins.ResponseException) {
            Result.failure(ApiException(e.response.status, e.response.bodyAsText()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendOtp(request: SendOtpRequest): Result<SendOtpResponse> = executeRequest {
        client.post("$baseUrl/auth/send-otp") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun verifyOtp(request: VerifyOtpRequest): Result<VerifyOtpResponse> = executeRequest {
        client.post("$baseUrl/auth/verify-otp") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun googleSignIn(request: GoogleSignInRequest): Result<VerifyOtpResponse> = executeRequest {
        client.post("$baseUrl/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // allowRefresh = false: a rejected refresh token must surface as an error,
    // never trigger another refresh attempt.
    override suspend fun refreshToken(request: RefreshTokenRequest): Result<RefreshTokenResponse> = executeRequest(allowRefresh = false) {
        client.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun logout(): Result<Unit> = executeRequest {
        client.post("$baseUrl/auth/logout") {
            header("Authorization", "Bearer ${tokenProvider()}")
        }
    }

    override suspend fun getCurrentUser(): Result<UserDto> = executeRequest {
        client.get("$baseUrl/users/me") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun updateProfile(request: UpdateProfileRequest): Result<UserDto> = executeRequest {
        client.patch("$baseUrl/users/me") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<UserDto>> = executeRequest {
        client.get("$baseUrl/users/search") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            parameter("q", query)
            parameter("limit", limit.toString())
        }
    }

    override suspend fun getConversations(cursor: String?, limit: Int): Result<List<ConversationDto>> = executeRequest {
        client.get("$baseUrl/conversations") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            if (cursor != null) parameter("cursor", cursor)
            parameter("limit", limit.toString())
        }
    }

    override suspend fun createConversation(request: CreateConversationRequest): Result<ConversationDto> = executeRequest {
        client.post("$baseUrl/conversations") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun getConversation(id: Long): Result<ConversationDto> = executeRequest {
        client.get("$baseUrl/conversations/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun deleteConversation(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/conversations/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun getMessages(conversationId: Long, cursor: Long?, limit: Int): Result<MessagePageDto> = executeRequest {
        client.get("$baseUrl/conversations/$conversationId/messages") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            if (cursor != null) parameter("cursor", cursor.toString())
            parameter("limit", limit.toString())
        }
    }

    override suspend fun deleteMessage(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/messages/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun signUpload(request: SignUploadRequest): Result<SignUploadResponse> = executeRequest {
        client.post("$baseUrl/media/sign-upload") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun completeUpload(request: CompleteUploadRequest): Result<MediaDto> = executeRequest {
        client.post("$baseUrl/media/complete") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun getMedia(id: Long): Result<MediaDto> = executeRequest {
        client.get("$baseUrl/media/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun deleteMedia(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/media/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun getDevices(): Result<List<DeviceDto>> = executeRequest {
        client.get("$baseUrl/devices") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }

    override suspend fun revokeDevice(id: Long): Result<Unit> = executeRequest {
        client.delete("$baseUrl/devices/$id") {
            headers { authHeader().forEach { (k, v) -> append(k, v) } }
        }
    }
}

class ApiException(val status: HttpStatusCode, val body: String) : Exception("API Error: $status - $body")