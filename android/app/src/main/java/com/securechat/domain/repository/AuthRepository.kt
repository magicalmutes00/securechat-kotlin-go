package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun sendOtp(phoneNumber: String): Result<OtpSentResult>
    suspend fun verifyOtp(phoneNumber: String, otp: String, deviceName: String, deviceIdentifier: String): Result<AuthResult>
    suspend fun refreshToken(): Result<TokenPair>
    suspend fun logout(): Result<Unit>
    suspend fun logoutAllDevices(): Result<Unit>
    suspend fun getCurrentUser(): Result<User>

    val currentUser: Flow<User?>
    val isAuthenticated: Boolean

    data class OtpSentResult(
        val expiresIn: Int,
        val resendCooldown: Int
    )

    data class AuthResult(
        val user: User,
        val accessToken: String,
        val refreshToken: String,
        val accessExpiresIn: Int,
        val refreshExpiresIn: Int
    )

    data class TokenPair(
        val accessToken: String,
        val refreshToken: String,
        val accessExpiresIn: Int,
        val refreshExpiresIn: Int
    )
}