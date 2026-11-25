package com.securechat.domain.usecase.push

import com.securechat.core.common.Result
import com.securechat.domain.repository.PushRepository
import javax.inject.Inject

/**
 * Unregisters the device's FCM token from the backend. Called on logout or
 * when the user disables push notifications.
 */
class UnregisterPushTokenUseCase @Inject constructor(
    private val pushRepository: PushRepository
) {
    suspend operator fun invoke(fcmToken: String): Result<Unit> {
        return pushRepository.unregisterPushToken(fcmToken)
    }
}
