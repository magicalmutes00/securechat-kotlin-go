package com.securechat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.firebase.messaging.FirebaseMessaging
import com.securechat.domain.model.Device
import com.securechat.domain.model.ThemeMode
import com.securechat.domain.model.User
import com.securechat.domain.model.UserSettings
import com.securechat.domain.repository.AuthRepository
import com.securechat.domain.repository.DeviceRepository
import com.securechat.domain.repository.PushRepository
import com.securechat.domain.repository.UserRepository
import com.securechat.domain.usecase.push.RegisterPushTokenUseCase
import com.securechat.domain.usecase.push.UnregisterPushTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the settings hub and its detail screens. Preferences are persisted in
 * the local user_settings table (read back by [com.securechat.MainViewModel] to
 * drive the app theme), while active sessions come from [DeviceRepository].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
    private val authRepository: AuthRepository,
    private val pushRepository: PushRepository,
    private val registerPushToken: RegisterPushTokenUseCase,
    private val unregisterPushToken: UnregisterPushTokenUseCase
) : ViewModel() {

    val user: StateFlow<User?> = userRepository.observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<UserSettings?> = userRepository.observeUserSettings()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val devices: StateFlow<List<Device>> = deviceRepository.observeDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoggingOutAll = MutableStateFlow(false)
    val isLoggingOutAll = _isLoggingOutAll.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun setTheme(theme: ThemeMode) = updateSettings { it.copy(theme = theme) }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                // Register the FCM token with the backend so pushes can be
                // delivered when the user is offline. The token is cached
                // locally by FcmMessagingService.onNewToken; if it is not yet
                // available, registration will happen on the next token
                // refresh.
                val token = pushRepository.storedFcmToken
                if (token != null) {
                    registerPushToken(token, "android")
                }
            } else {
                // Unregister the token so the server stops pushing to this
                // device.
                val token = pushRepository.storedFcmToken
                if (token != null) {
                    unregisterPushToken(token)
                }
                pushRepository.clearFcmToken()
            }
            updateSettings { it.copy(notificationsEnabled = enabled) }
        }
    }

    fun setMediaAutoDownload(enabled: Boolean) =
        updateSettings { it.copy(mediaAutoDownload = enabled) }

    fun setLanguage(language: String) = updateSettings { it.copy(language = language) }

    /**
     * Applies [transform] to the current settings row. A row is created on first
     * write using the signed-in user's id, so preferences always have an owner.
     */
    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            val current = settings.value ?: UserSettings(userId = user.value?.id ?: 0L)
            val updated = transform(current)
            userRepository.updateSettings(updated)
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            deviceRepository.getDevices()
        }
    }

    fun revokeDevice(deviceId: Long) {
        viewModelScope.launch {
            deviceRepository.revokeDevice(deviceId)
        }
    }

    /** Signs out of every device, including this one. */
    fun logoutAllDevices() {
        viewModelScope.launch {
            _isLoggingOutAll.value = true
            authRepository.logoutAllDevices()
            _isLoggingOutAll.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}