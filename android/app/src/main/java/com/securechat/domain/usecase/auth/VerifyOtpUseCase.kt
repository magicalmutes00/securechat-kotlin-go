package com.securechat.domain.usecase.auth

import com.securechat.core.common.Result
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VerifyOtpUseCase @javax.inject.Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        phoneNumber: String,
        otp: String,
        deviceName: String,
        deviceIdentifier: String
    ): Result<AuthRepository.AuthResult> {
        return withContext(Dispatchers.IO) {
            if (!isValidPhoneNumber(phoneNumber)) {
                Result.failure(IllegalArgumentException("Invalid phone number format"))
            } else if (!isValidOtp(otp)) {
                Result.failure(IllegalArgumentException("OTP must be 6 digits"))
            } else if (deviceName.isBlank()) {
                Result.failure(IllegalArgumentException("Device name is required"))
            } else if (deviceIdentifier.isBlank()) {
                Result.failure(IllegalArgumentException("Device identifier is required"))
            } else {
                authRepository.verifyOtp(phoneNumber, otp, deviceName, deviceIdentifier)
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$".toRegex())
    }

    private fun isValidOtp(otp: String): Boolean {
        return otp.matches("^\\d{6}$".toRegex())
    }
}