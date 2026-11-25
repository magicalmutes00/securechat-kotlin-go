package com.securechat.domain.repository

import com.securechat.core.common.Result
import kotlinx.coroutines.flow.Flow

/**
 * Push notification device-token management.
 *
 * FCM tokens are registered with the backend so the server can send pushes to
 * this device when the user is offline. Tokens are scoped to the current
 * session's device (the device id comes from the JWT, so the client only
 * sends the FCM token and platform).
 */
interface PushRepository {

    /** Registers the FCM token with the backend for the current device. */
    suspend fun registerPushToken(fcmToken: String, platform: String): Result<Unit>

    /** Deactivates the FCM token on the backend (logout / notifications disabled). */
    suspend fun unregisterPushToken(fcmToken: String): Result<Unit>

    /** Deactivates all FCM tokens for the current user (logout all devices). */
    suspend fun unregisterAllTokens(): Result<Unit>

    /** Whether the user has push notifications enabled (local preference). */
    suspend fun isPushEnabled(): Result<Boolean>

    /** Sets whether the user wants push notifications. Writes locally and,
     *  when disabled, unregisters the token from the backend. */
    suspend fun setPushEnabled(enabled: Boolean): Result<Unit>

    /** Observes the push-enabled state. */
    fun observePushEnabled(): Flow<Boolean>

    /** The locally cached FCM token, or null when not registered. */
    val storedFcmToken: String?

    /** Caches the FCM token locally. */
    fun storeFcmToken(token: String)

    /** Clears the locally cached FCM token. */
    fun clearFcmToken()
}
