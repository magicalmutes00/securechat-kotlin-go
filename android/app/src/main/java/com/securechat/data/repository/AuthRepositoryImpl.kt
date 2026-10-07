package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.core.security.TokenStorage
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.UserDao
import com.securechat.data.local.entity.UserEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.UserDto
import com.securechat.data.remote.dto.VerifyOtpResponse
import com.securechat.domain.model.User
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val tokenStorage: TokenStorage,
    private val userDao: UserDao,
    private val database: SecureChatDatabase
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser = _currentUser.asStateFlow()
    override val isAuthenticated: Boolean
        get() = _currentUser.value != null

    override suspend fun sendOtp(phoneNumber: String): Result<AuthRepository.OtpSentResult> {
        return withContext(Dispatchers.IO) {
            apiService.sendOtp(com.securechat.data.remote.dto.SendOtpRequest(phone_number = phoneNumber))
                .map { response ->
                    AuthRepository.OtpSentResult(
                        expiresIn = response.expires_in,
                        resendCooldown = response.resend_cooldown
                    )
                }
        }
    }

    override suspend fun verifyOtp(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        deviceIdentifier: String
    ): Result<AuthRepository.AuthResult> {
        return withContext(Dispatchers.IO) {
            apiService.verifyOtp(com.securechat.data.remote.dto.VerifyOtpRequest(
                phone_number = phoneNumber,
                otp = otp,
                device_name = deviceName,
                device_identifier = deviceIdentifier
            )).flatMap { response ->
                persistAuthResult(response)
            }
        }
    }

    override suspend fun googleSignIn(
        idToken: String,
        deviceName: String,
        deviceIdentifier: String
    ): Result<AuthRepository.AuthResult> {
        return withContext(Dispatchers.IO) {
            apiService.googleSignIn(com.securechat.data.remote.dto.GoogleSignInRequest(
                id_token = idToken,
                device_name = deviceName,
                device_identifier = deviceIdentifier
            )).flatMap { response ->
                persistAuthResult(response)
            }
        }
    }

    private suspend fun persistAuthResult(response: VerifyOtpResponse): Result<AuthRepository.AuthResult> {
        // Save tokens
        return tokenStorage.saveTokens(
            response.access_token,
            response.refresh_token,
            response.access_expires_in.toLong()
        ).flatMap {
            // Save user to local DB
            val userEntity = UserEntity(
                serverId = response.user.id,
                phoneNumber = response.user.phone_number,
                username = response.user.username,
                displayName = response.user.display_name,
                profileImageId = response.user.profile_image_id,
                lastSeen = response.user.last_seen,
                isOnline = response.user.is_online,
                createdAt = response.user.created_at,
                updatedAt = response.user.updated_at
            )
            database.userDao().insert(userEntity)

            val user = mapToUser(response.user)
            _currentUser.value = user

            Result.success(AuthRepository.AuthResult(
                user = user,
                accessToken = response.access_token,
                refreshToken = response.refresh_token,
                accessExpiresIn = response.access_expires_in,
                refreshExpiresIn = response.refresh_expires_in
            ))
        }
    }

    override suspend fun refreshToken(): Result<AuthRepository.TokenPair> {
        return withContext(Dispatchers.IO) {
            tokenStorage.getRefreshToken().flatMap { refreshToken ->
                apiService.refreshToken(com.securechat.data.remote.dto.RefreshTokenRequest(refresh_token = refreshToken))
                    .flatMap { response ->
                        tokenStorage.saveTokens(
                            response.access_token,
                            response.refresh_token,
                            response.access_expires_in.toLong()
                        ).map {
                            AuthRepository.TokenPair(
                                accessToken = response.access_token,
                                refreshToken = response.refresh_token,
                                accessExpiresIn = response.access_expires_in,
                                refreshExpiresIn = response.refresh_expires_in
                            )
                        }
                    }
            }
        }
    }

    override suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Best-effort server-side revocation: local state is cleared even
            // when the call fails (expired access token, offline), because the
            // user asked to log out on THIS device regardless. Otherwise stale
            // tokens survive and every later launch replays refresh/logout.
            apiService.logout()
            tokenStorage.clear().flatMap {
                _currentUser.value = null
                Result.success(Unit)
            }
        }
    }

    override suspend fun logoutAllDevices(): Result<Unit> {
        // Not implemented in API yet
        return logout()
    }

    override suspend fun getCurrentUser(): Result<User> {
        return withContext(Dispatchers.IO) {
            // Try local first
            val entity = database.userDao().getAll().first().firstOrNull()
            if (entity != null) {
                return@withContext Result.success(mapToUser(entity))
            }
            
            // Fallback to API
            apiService.getCurrentUser()
                .map { dto ->
                    val user = mapToUser(dto)
                    database.userDao().insert(mapToEntity(user))
                    _currentUser.value = user
                    user
                }
        }
    }

    private fun mapToUser(dto: UserDto): User {
        return User(
            id = dto.id,
            phoneNumber = dto.phone_number,
            username = dto.username,
            displayName = dto.display_name,
            profileImageId = dto.profile_image_id,
            lastSeen = dto.last_seen,
            isOnline = dto.is_online,
            createdAt = dto.created_at,
            updatedAt = dto.updated_at
        )
    }

    private fun mapToUser(entity: UserEntity): User {
        return User(
            id = entity.id,
            phoneNumber = entity.phoneNumber,
            username = entity.username,
            displayName = entity.displayName,
            profileImageId = entity.profileImageId,
            lastSeen = entity.lastSeen,
            isOnline = entity.isOnline,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun mapToEntity(user: User): UserEntity {
        return UserEntity(
            serverId = user.id,
            phoneNumber = user.phoneNumber,
            username = user.username,
            displayName = user.displayName,
            profileImageId = user.profileImageId,
            lastSeen = user.lastSeen,
            isOnline = user.isOnline,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }
}