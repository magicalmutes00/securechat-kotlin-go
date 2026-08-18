package com.securechat.presentation.auth

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.common.Result
import com.securechat.core.security.TokenStorage
import com.securechat.domain.usecase.auth.SendOtpUseCase
import com.securechat.domain.usecase.auth.VerifyOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhoneLoginViewModel @Inject constructor(
    private val sendOtpUseCase: SendOtpUseCase,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    var phoneNumber: MutableState<String> = mutableStateOf("")
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)
    var otpSent: MutableState<Boolean> = mutableStateOf(false)
    var resendCooldown: MutableState<Int> = mutableStateOf(0)

    fun onPhoneNumberChanged(number: String) {
        phoneNumber.value = number
        errorMessage.value = null
    }

    fun sendOtp(onSuccess: () -> Unit) {
        if (phoneNumber.value.isBlank()) {
            errorMessage.value = "Please enter a phone number"
            return
        }
        
        if (!isValidPhoneNumber(phoneNumber.value)) {
            errorMessage.value = "Please enter a valid phone number with country code"
            return
        }

        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = sendOtpUseCase(phoneNumber.value)
            isLoading.value = false
            
            result.onSuccess { response ->
                otpSent.value = true
                resendCooldown.value = response.resendCooldown
                onSuccess()
            }.onFailure { e ->
                errorMessage.value = when {
                    e.message?.contains("rate limit") == true -> "Too many requests. Please try again later."
                    else -> "Failed to send OTP. Please check your connection."
                }
            }
        }
    }

    fun startResendTimer(onTick: (Int) -> Unit, onFinish: () -> Unit) {
        viewModelScope.launch {
            var remaining = resendCooldown.value
            while (remaining > 0) {
                resendCooldown.value = remaining
                onTick(remaining)
                delay(1000)
                remaining--
            }
            resendCooldown.value = 0
            onFinish()
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$".toRegex())
    }
}

@HiltViewModel
class OtpVerifyViewModel @Inject constructor(
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    var otp: MutableState<String> = mutableStateOf("")
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)
    var attemptsRemaining: MutableState<Int> = mutableStateOf(5)
    var resendCooldown: MutableState<Int> = mutableStateOf(0)

    fun onOtpChanged(code: String) {
        otp.value = code
        errorMessage.value = null
    }

    fun verifyOtp(
        phoneNumber: String,
        deviceName: String,
        deviceIdentifier: String,
        onSuccess: (Boolean) -> Unit
    ) {
        if (otp.value.length != 6) {
            errorMessage.value = "Please enter the 6-digit code"
            return
        }

        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = verifyOtpUseCase(phoneNumber, otp.value, deviceName, deviceIdentifier)
            isLoading.value = false
            
            result.onSuccess { authResult ->
                onSuccess(true)
            }.onFailure { e ->
                val message = when {
                    e.message?.contains("invalid") == true -> "Invalid OTP. Please try again."
                    e.message?.contains("expired") == true -> "OTP has expired. Please request a new one."
                    e.message?.contains("attempts") == true -> "Too many failed attempts. Please request a new OTP."
                    else -> "Verification failed. Please try again."
                }
                errorMessage.value = message
                attemptsRemaining.value = maxOf(0, attemptsRemaining.value - 1)
                onSuccess(false)
            }
        }
    }

    fun resendOtp(phoneNumber: String, deviceName: String, deviceIdentifier: String, onSent: () -> Unit) {
        // This would call sendOtpUseCase again
        // Implementation delegated to PhoneLoginViewModel
    }
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val userRepository: com.securechat.domain.repository.UserRepository
) : ViewModel() {

    var displayName: MutableState<String> = mutableStateOf("")
    var username: MutableState<String> = mutableStateOf("")
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)

    fun onDisplayNameChanged(name: String) {
        displayName.value = name
        errorMessage.value = null
    }

    fun onUsernameChanged(name: String) {
        username.value = name
        errorMessage.value = null
    }

    fun completeProfile(onSuccess: () -> Unit) {
        if (displayName.value.isBlank()) {
            errorMessage.value = "Display name is required"
            return
        }

        if (username.value.isNotBlank() && !isValidUsername(username.value)) {
            errorMessage.value = "Username can only contain letters, numbers, and underscores"
            return
        }

        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = userRepository.updateProfile(displayName.value, username.value.ifBlank { null })
            isLoading.value = false
            
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                errorMessage.value = "Failed to update profile. Please try again."
            }
        }
    }

    fun skipProfileSetup(onSkip: () -> Unit) {
        onSkip()
    }

    private fun isValidUsername(username: String): Boolean {
        return username.matches("^[a-zA-Z0-9_]{3,30}$".toRegex())
    }
}