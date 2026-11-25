package com.securechat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.security.TokenStorage
import com.securechat.core.push.FcmMessagingService
import com.securechat.data.sync.WebSocketEventProcessor
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.domain.model.ThemeMode
import com.securechat.domain.model.User
import com.securechat.domain.repository.AuthRepository
import com.securechat.domain.repository.PushRepository
import com.securechat.domain.repository.UserRepository
import com.securechat.domain.usecase.push.RegisterPushTokenUseCase
import com.securechat.domain.usecase.push.UnregisterPushTokenUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val e2eeRepository: com.securechat.domain.repository.E2eeRepository,
    private val webSocketManager: WebSocketManager,
    private val webSocketEventProcessor: WebSocketEventProcessor,
    private val pushRepository: PushRepository,
    private val registerPushToken: RegisterPushTokenUseCase,
    private val unregisterPushToken: UnregisterPushTokenUseCase
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState = _authState.asStateFlow()

    // Set when a freshly authenticated user still has no username, meaning they
    // have not completed first-time profile setup. Returning users who skipped
    // setup are NOT nagged on cold start (loadUserOrDefault leaves this false).
    private val _needsProfileSetup = MutableStateFlow(false)
    val needsProfileSetup = _needsProfileSetup.asStateFlow()

    // Theme is applied at the Activity level and driven by the persisted setting.
    val themeMode: StateFlow<ThemeMode> = userRepository.observeUserSettings()
        .map { it?.theme ?: ThemeMode.SYSTEM }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    sealed interface AuthState {
        data class Authenticated(val user: User) : AuthState
        object Unauthenticated : AuthState
        object Unknown : AuthState
    }

    init {
        checkAuthState()

        viewModelScope.launch {
            authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        // Publish this device's Signal Protocol keys before the
                        // realtime channel opens: a peer that wants to start a
                        // session needs them, and publishing is idempotent.
                        e2eeRepository.ensureRegistered().onFailure { error ->
                            android.util.Log.w("MainViewModel", "E2EE key publish failed", error)
                        }
                        // Keep the realtime channel alive and apply incoming
                        // events to the local cache for as long as we're signed in.
                        webSocketManager.start()
                        webSocketEventProcessor.start(viewModelScope)
                        // Register the FCM push token so the server can notify
                        // this device when it is offline. Firebase delivers the
                        // token via FcmMessagingService.onNewToken, which caches
                        // it locally; if a token was already cached (e.g. from a
                        // previous app start), register it now.
                        viewModelScope.launch {
                            val token = pushRepository.storedFcmToken
                            if (token != null) {
                                registerPushToken(token, "android")
                            }
                        }
                    }
                    is AuthState.Unauthenticated -> {
                        webSocketEventProcessor.stop()
                        webSocketManager.disconnect()
                    }
                    is AuthState.Unknown -> Unit
                }
            }
        }
    }

    fun observeAuthState(onChange: (AuthState) -> Unit) {
        viewModelScope.launch {
            authState.collect(onChange)
        }
    }

    fun onAuthSuccess() {
        viewModelScope.launch {
            val result = authRepository.getCurrentUser()
            result.onSuccess { user ->
                _needsProfileSetup.value = user.username == null
                _authState.value = AuthState.Authenticated(user)
            }.onFailure {
                // Tokens may have expired while the app was backgrounded.
                tryRefreshToken()
            }
        }
    }

    fun onProfileSetupHandled() {
        _needsProfileSetup.value = false
    }

    fun onLogout() {
        viewModelScope.launch {
            // Unregister the push token before signing out so the server stops
            // sending pushes to this device.
            val token = pushRepository.storedFcmToken
            if (token != null) {
                unregisterPushToken(token)
            }
            pushRepository.clearFcmToken()
            authRepository.logout()
            com.securechat.core.utils.UserSession.clear()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun checkAuthState() {
        viewModelScope.launch {
            val tokenResult = tokenStorage.getAccessToken()
            if (tokenResult.isSuccess && !tokenStorage.isAccessTokenExpired()) {
                loadUserOrDefault()
            } else {
                tryRefreshToken()
            }
        }
    }

    fun tryRefreshToken() {
        viewModelScope.launch {
            val result = authRepository.refreshToken()
            result.onSuccess {
                loadUserOrDefault()
            }.onFailure {
                authRepository.logout()
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    /**
     * Resolves the signed-in identity, or signs the user out.
     *
     * This used to fall back to a fabricated `User(id = 0, phoneNumber = "")`
     * while still reporting [AuthState.Authenticated]. Everything downstream —
     * "is this message mine", read receipts, avatar lookups — trusted that
     * identity, so a failed profile fetch produced a signed-in shell that was
     * quietly wrong. A failed fetch now means "no usable session": rotate the
     * token once and retry, then sign out.
     */
    private suspend fun loadUserOrDefault() {
        val result = authRepository.getCurrentUser()
        result.onSuccess { user ->
            _needsProfileSetup.value = user.username == null
            _authState.value = AuthState.Authenticated(user)
            com.securechat.core.utils.UserSession.update(user.id, user.displayName)
        }.onFailure {
            // Tokens may have expired while the app was backgrounded; a single
            // rotation is worth trying before giving up on the session.
            val refreshed = authRepository.refreshToken()
            if (refreshed.isSuccess) {
                authRepository.getCurrentUser()
                    .onSuccess { user ->
                        _needsProfileSetup.value = user.username == null
                        _authState.value = AuthState.Authenticated(user)
                        com.securechat.core.utils.UserSession.update(user.id, user.displayName)
                    }
                    .onFailure { signOut() }
            } else {
                signOut()
            }
        }
    }

    private suspend fun signOut() {
        authRepository.logout()
        com.securechat.core.utils.UserSession.clear()
        _authState.value = AuthState.Unauthenticated
    }
}