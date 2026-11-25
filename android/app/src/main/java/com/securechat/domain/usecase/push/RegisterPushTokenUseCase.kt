package com.securechat.domain.usecase.push

import com.securechat.core.common.Result
import com.securechat.domain.repository.PushRepository
import javax.inject.Inject

/**
 * Registers the device's FCM token with the backend so the server can send
 * push notifications when the user is offline. The device id is taken from the
 * session JWT — the client only sends the token and platform.
 */
class RegisterPushTokenUseCase @Inject constructor(
    private val pushRepository: PushRepository
) {
    suspend operator fun invoke(fcmToken: String, platform: String): Result<Unit> {
        return pushRepository.registerPushToken(fcmToken, platform)
    }
}
