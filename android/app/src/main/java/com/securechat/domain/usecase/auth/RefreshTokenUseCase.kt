package com.securechat.domain.usecase.auth

import com.securechat.core.common.Result
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RefreshTokenUseCase @javax.inject.Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<AuthRepository.TokenPair> {
        return withContext(Dispatchers.IO) {
            authRepository.refreshToken()
        }
    }
}