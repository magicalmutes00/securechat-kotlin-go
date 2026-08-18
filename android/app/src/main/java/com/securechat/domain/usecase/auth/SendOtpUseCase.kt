package com.securechat.domain.usecase.auth

import com.securechat.core.common.Result
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SendOtpUseCase @javax.inject.Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phoneNumber: String): Result<AuthRepository.OtpSentResult> {
        return withContext(Dispatchers.IO) {
            if (!isValidPhoneNumber(phoneNumber)) {
                Result.failure(IllegalArgumentException("Invalid phone number format"))
            } else {
                authRepository.sendOtp(phoneNumber)
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        // E.164 format: +[country code][number]
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$".toRegex())
    }
}