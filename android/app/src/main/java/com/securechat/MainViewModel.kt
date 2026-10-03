package com.securechat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.security.TokenStorage
import com.securechat.data.sync.WebSocketEventProcessor
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.domain.model.User
import com.securechat.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val authRepository: AuthRepository,
    private val webSocketManager: WebSocketManager,
    private val webSocketEventProcessor: WebSocketEventProcessor
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState = _authState.asStateFlow()

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
                        // Keep the realtime channel alive and apply incoming
                        // events to the local cache for as long as we're signed in.
                        webSocketManager.start()
                        webSocketEventProcessor.start(viewModelScope)
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
                _authState.value = AuthState.Authenticated(user)
            }.onFailure {
                // Tokens may have expired while the app was backgrounded.
                tryRefreshToken()
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
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

    private suspend fun loadUserOrDefault() {
        val result = authRepository.getCurrentUser()
        _authState.value = AuthState.Authenticated(
            result.getOrElse {
                User(
                    id = 0,
                    phoneNumber = "",
                    username = null,
                    displayName = "User",
                    profileImageId = null,
                    lastSeen = null,
                    isOnline = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        )
        val user = (_authState.value as AuthState.Authenticated).user
        com.securechat.core.utils.UserSession.update(user.id, user.displayName)
    }
}
