package com.securechat.data.repository

import android.content.SharedPreferences
import com.securechat.core.common.Result
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.RegisterPushTokenRequest
import com.securechat.data.remote.dto.UnregisterPushTokenRequest
import com.securechat.domain.model.UserSettings
import com.securechat.domain.repository.PushRepository
import com.securechat.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val sharedPreferences: SharedPreferences,
    private val userRepository: UserRepository
) : PushRepository {

    override suspend fun registerPushToken(fcmToken: String, platform: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            apiService.registerPushToken(
                RegisterPushTokenRequest(
                    fcm_token = fcmToken,
                    platform = platform
                )
            ).map { Unit }
        }
    }

    override suspend fun unregisterPushToken(fcmToken: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            apiService.unregisterPushToken(
                UnregisterPushTokenRequest(fcm_token = fcmToken)
            )
        }
    }

    override suspend fun unregisterAllTokens(): Result<Unit> {
        sharedPreferences.edit().remove("fcm_token").apply()
        return Result.success(Unit)
    }

    override suspend fun isPushEnabled(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            Result.success(sharedPreferences.getBoolean("push_enabled", true))
        }
    }

    override suspend fun setPushEnabled(enabled: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putBoolean("push_enabled", enabled)
                .apply()
            userRepository.getCurrentUser().onSuccess { user ->
                userRepository.updateSettings(
                    UserSettings(
                        userId = user.id,
                        notificationsEnabled = enabled
                    )
                )
            }
            Result.success(Unit)
        }
    }

    override fun observePushEnabled(): kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.flow {
            emit(sharedPreferences.getBoolean("push_enabled", true))
        }

    override val storedFcmToken: String?
        get() = sharedPreferences.getString("fcm_token", null)

    override fun storeFcmToken(token: String) {
        sharedPreferences.edit().putString("fcm_token", token).apply()
    }

    override fun clearFcmToken() {
        sharedPreferences.edit().remove("fcm_token").apply()
    }
}
