package com.securechat.domain.usecase.auth

import com.securechat.core.common.Result
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSignInUseCase @javax.inject.Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        idToken: String,
        deviceName: String,
        deviceIdentifier: String
    ): Result<AuthRepository.AuthResult> {
        return withContext(Dispatchers.IO) {
            when {
                idToken.isBlank() -> Result.failure(IllegalArgumentException("Google sign-in failed: missing token"))
                deviceName.isBlank() -> Result.failure(IllegalArgumentException("Device name is required"))
                deviceIdentifier.isBlank() -> Result.failure(IllegalArgumentException("Device identifier is required"))
                else -> authRepository.googleSignIn(idToken, deviceName, deviceIdentifier)
            }
        }
    }
}