package com.securechat.presentation.auth

import android.app.Activity
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.securechat.core.common.Result
import com.securechat.domain.repository.AuthRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the state of a Firebase phone verification while the user moves from the
 * phone-entry screen to the OTP screen. Firebase sends SMS itself (no server OTP
 * round trip); once the user enters the code we mint a Firebase ID token and
 * exchange it for a SecureChat session via [AuthRepository.firebaseSignIn].
 *
 * The verificationId/resend token cannot survive as navigation arguments (the
 * token is a typed object), so a singleton session holder is the natural fit.
 * It never stores an Activity, so it cannot leak one.
 */
@Singleton
class FirebasePhoneAuthManager @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    var verificationId: String? = null
        private set
    var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null
        private set
    var phoneNumber: String = ""
        private set

    /** Builds the verification options for the start/resend calls. */
    fun buildOptions(
        activity: Activity,
        e164: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ): PhoneAuthOptions = PhoneAuthOptions.newBuilder(auth)
        .setPhoneNumber(e164)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
        .build()

    /** Persists the values needed by the OTP screen and for resending. */
    fun recordCodeSent(
        e164: String,
        verificationId: String,
        token: PhoneAuthProvider.ForceResendingToken
    ) {
        this.phoneNumber = e164
        this.verificationId = verificationId
        this.forceResendingToken = token
    }

    fun phoneNumberForVerification(): String = phoneNumber

    /**
     * Completes sign-in with the code the user typed, then exchanges the Firebase
     * ID token for a SecureChat session.
     */
    suspend fun verifyCode(
        code: String,
        deviceName: String,
        deviceIdentifier: String
    ): Result<Unit> {
        val vid = verificationId
            ?: return Result.failure(IllegalStateException("No phone verification in progress"))
        return signIn(PhoneAuthProvider.getCredential(vid, code), deviceName, deviceIdentifier)
    }

    /**
     * Signs the Firebase credential in and exchanges the resulting ID token for a
     * SecureChat session. Also used for instant verification, where Firebase
     * auto-retrieves the SMS and hands us a ready credential.
     */
    suspend fun signIn(
        credential: PhoneAuthCredential,
        deviceName: String,
        deviceIdentifier: String
    ): Result<Unit> {
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException("Firebase sign-in returned no user"))
            val token = firebaseUser.getIdToken(false).await().token
                ?: return Result.failure(IllegalStateException("Firebase returned no ID token"))
            authRepository.firebaseSignIn(token, deviceName, deviceIdentifier).map { Unit }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Clears any in-flight verification (e.g. after success or cancellation). */
    fun clear() {
        verificationId = null
        forceResendingToken = null
        phoneNumber = ""
    }

    /** Maps a Firebase error to a message the user can act on. */
    fun mapError(e: Throwable): String {
        if (e is FirebaseException || e.message != null) {
            val msg = (e.message ?: "").lowercase()
            return when {
                msg.contains("invalid") || msg.contains("format") ->
                    "Enter a valid phone number."
                msg.contains("quota") || msg.contains("too-many") || msg.contains("too many") ->
                    "Too many attempts. Please try again later."
                msg.contains("app not authorized") || msg.contains("not authorized") ->
                    "This build is not authorized for Firebase phone sign-in. Check the SHA-1 registration."
                msg.contains("network") || msg.contains("timeout") ->
                    "Network error. Check your connection and try again."
                e is FirebaseException -> e.message ?: "Phone verification failed."
                else -> "Phone verification failed. Please try again."
            }
        }
        return "Phone verification failed. Please try again."
    }

    private suspend fun <T : Any> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val value = task.result
                if (value != null) cont.resume(value)
                else cont.resumeWithException(IllegalStateException("Firebase task returned null"))
            } else {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase task failed")
                )
            }
        }
    }
}