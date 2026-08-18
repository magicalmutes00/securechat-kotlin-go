package com.securechat.domain.usecase.auth

import com.securechat.core.common.Result
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogoutUseCase @javax.inject.Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(logoutAllDevices: Boolean = false): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (logoutAllDevices) {
                authRepository.logoutAllDevices()
            } else {
                authRepository.logout()
            }
        }
    }
}