package com.securechat.presentation.auth

import android.app.Activity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.securechat.core.security.TokenStorage
import com.securechat.domain.usecase.auth.GoogleSignInUseCase
import com.securechat.domain.usecase.auth.SendOtpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phone sign-in backed by Firebase Auth. Firebase owns SMS delivery and code
 * verification; the server only trusts the resulting ID token.
 */
@HiltViewModel
class PhoneLoginViewModel @Inject constructor(
    private val phoneAuthManager: FirebasePhoneAuthManager
) : ViewModel() {

    /** Defaults to India (+91) per product requirement; user can change it. */
    var countryCode: MutableState<String> = mutableStateOf(CountryCodes.default.code)
    var phoneNumber: MutableState<String> = mutableStateOf("")
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)

    fun onCountryCodeChanged(code: String) {
        countryCode.value = code
        errorMessage.value = null
    }

    fun onPhoneNumberChanged(number: String) {
        phoneNumber.value = number
        errorMessage.value = null
    }

    /** Full E.164 number built from the selected country code + digits. */
    fun buildE164(): String =
        countryCode.value + phoneNumber.value.filter { it.isDigit() }

    /**
     * Starts Firebase phone verification. [onCodeSent] runs when the SMS was
     * dispatched and the user should type the code; [onAuthenticated] runs when
     * Firebase auto-verifies the number (instant verification / SMS retrieval)
     * and the backend session is already established.
     */
    fun sendOtp(
        activity: Activity,
        deviceName: String,
        deviceIdentifier: String,
        onCodeSent: () -> Unit,
        onAuthenticated: () -> Unit
    ) {
        val e164 = buildE164()
        if (!isValidPhoneNumber(e164)) {
            errorMessage.value = "Please enter a valid phone number."
            return
        }

        isLoading.value = true
        errorMessage.value = null
        phoneAuthManager.clear()

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Firebase read the SMS automatically; finish sign-in now.
                viewModelScope.launch {
                    val result = phoneAuthManager.signIn(credential, deviceName, deviceIdentifier)
                    isLoading.value = false
                    result.onSuccess { onAuthenticated() }
                        .onFailure { errorMessage.value = phoneAuthManager.mapError(it) }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                isLoading.value = false
                errorMessage.value = phoneAuthManager.mapError(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                phoneAuthManager.recordCodeSent(e164, verificationId, token)
                isLoading.value = false
                onCodeSent()
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                // Manual entry still works with the stored verificationId.
                isLoading.value = false
            }
        }

        try {
            PhoneAuthProvider.verifyPhoneNumber(
                phoneAuthManager.buildOptions(activity, e164, callbacks)
            )
        } catch (e: Exception) {
            isLoading.value = false
            errorMessage.value = phoneAuthManager.mapError(e)
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$".toRegex())
    }
}

@HiltViewModel
class OtpVerifyViewModel @Inject constructor(
    private val phoneAuthManager: FirebasePhoneAuthManager
) : ViewModel() {

    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)
    var resendCooldown: MutableState<Int> = mutableStateOf(0)

    /** The E.164 number currently being verified, for display on the OTP screen. */
    val displayPhone: String
        get() = phoneAuthManager.phoneNumberForVerification()

    fun clearError() {
        errorMessage.value = null
    }

    /**
     * Verifies the Firebase SMS code and, on success, exchanges the Firebase ID
     * token for a SecureChat session.
     */
    fun verifyOtp(
        code: String,
        deviceName: String,
        deviceIdentifier: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        if (code.length != 6) {
            errorMessage.value = "Please enter the 6-digit code."
            return
        }

        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = phoneAuthManager.verifyCode(code, deviceName, deviceIdentifier)
            isLoading.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                errorMessage.value = phoneAuthManager.mapError(e)
                onFailure()
            }
        }
    }

    /**
     * Requests a new SMS using the original resend token. A resend may also be
     * auto-verified by Firebase, in which case we complete sign-in immediately.
     */
    fun resendCode(
        activity: Activity,
        deviceName: String,
        deviceIdentifier: String,
        onResent: () -> Unit,
        onAuthenticated: () -> Unit
    ) {
        val e164 = phoneAuthManager.phoneNumberForVerification()
        if (e164.isBlank()) {
            errorMessage.value = "Missing phone number. Go back and try again."
            return
        }

        errorMessage.value = null
        startResendCountdown()

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    val result = phoneAuthManager.signIn(credential, deviceName, deviceIdentifier)
                    result.onSuccess { onAuthenticated() }.onFailure {
                        errorMessage.value = phoneAuthManager.mapError(it)
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                errorMessage.value = phoneAuthManager.mapError(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                phoneAuthManager.recordCodeSent(e164, verificationId, token)
                onResent()
            }
        }

        val options = phoneAuthManager.buildOptions(activity, e164, callbacks)
        val token = phoneAuthManager.forceResendingToken
        if (token != null) {
            // Options-based resend isn't exposed by this Firebase SDK version,
            // so use the legacy overload that accepts a resend token.
            PhoneAuthProvider.getInstance().verifyPhoneNumber(
                e164,
                60L,
                java.util.concurrent.TimeUnit.SECONDS,
                activity,
                callbacks,
                token
            )
        } else {
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    /**
     * Arms the resend cooldown and ticks it down once per second. Guarded so
     * repeated resends while a cooldown is active cannot stack loops.
     */
    private fun startResendCountdown() {
        if (resendCooldown.value > 0) return
        resendCooldown.value = 60
        viewModelScope.launch {
            while (resendCooldown.value > 0) {
                delay(1000)
                resendCooldown.value -= 1
            }
        }
    }
}

@HiltViewModel
class GoogleLoginViewModel @Inject constructor(
    private val googleSignInUseCase: GoogleSignInUseCase,
    @Suppress("unused") private val tokenStorage: TokenStorage
) : ViewModel() {

    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)

    fun googleSignIn(
        idToken: String,
        deviceName: String,
        deviceIdentifier: String,
        onSuccess: () -> Unit
    ) {
        if (idToken.isBlank()) {
            errorMessage.value = "Google sign-in failed. Please try again."
            return
        }

        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = googleSignInUseCase(idToken, deviceName, deviceIdentifier)
            isLoading.value = false

            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                errorMessage.value = when {
                    e.message?.contains("not configured") == true -> "Google sign-in is not configured yet."
                    else -> "Google sign-in failed. Please try again."
                }
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
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
            errorMessage.value = "Please enter a display name."
            return
        }

        if (username.value.isNotBlank() && !isValidUsername(username.value)) {
            errorMessage.value = "Username must be 3-30 characters (letters, numbers, underscore)."
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
                errorMessage.value = when {
                    e.message?.contains("taken") == true -> "That username is already taken."
                    else -> "Failed to update profile. Please try again."
                }
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

/**
 * Common dial codes for the login picker. +91 (India) is first and is the
 * default selected value.
 */
data class CountryCode(val name: String, val code: String)

object CountryCodes {
    val default = CountryCode("India", "+91")
    val all = listOf(
        CountryCode("India", "+91"),
        CountryCode("United States", "+1"),
        CountryCode("United Kingdom", "+44"),
        CountryCode("United Arab Emirates", "+971"),
        CountryCode("Singapore", "+65"),
        CountryCode("Australia", "+61"),
        CountryCode("Canada", "+1"),
        CountryCode("Germany", "+49"),
        CountryCode("France", "+33"),
        CountryCode("Brazil", "+55"),
        CountryCode("Japan", "+81"),
        CountryCode("China", "+86"),
    )
}