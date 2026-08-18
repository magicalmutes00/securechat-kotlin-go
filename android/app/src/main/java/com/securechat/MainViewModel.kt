package com.securechat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.common.Result
import com.securechat.core.security.TokenStorage
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState = _authState.asStateFlow()

    sealed interface AuthState {
        data class Authenticated(val user: User) : AuthState
        object Unauthenticated : AuthState
        object Unknown : AuthState
    }

    fun observeAuthState(onChange: (AuthState) -> Unit) {
        viewModelScope.launch {
            authState.collect(onChange)
        }
    }

    private fun setAuthState(newState: AuthState) {
        _authState.value = newState
    }

    fun onAuthSuccess() {
        // Load current user from repository
        viewModelScope.launch {
            val result = authRepository.getCurrentUser() // This would need to be added to AuthRepository
            result.onSuccess { user ->
                setAuthState(AuthState.Authenticated(user))
            }.onFailure {
                // Try to get from token storage
                checkAuthState()
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch {
            authRepository.logout()
            setAuthState(AuthState.Unauthenticated)
        }
    }

    fun checkAuthState() {
        viewModelScope.launch {
            val tokenResult = tokenStorage.getAccessToken()
            if (tokenResult.isSuccess && !tokenStorage.isAccessTokenExpired()) {
                // We have a valid token, but need to load user
                setAuthState(AuthState.Authenticated(User(
                    id = 0,
                    phoneNumber = "",
                    username = null,
                    displayName = "User",
                    profileImageId = null,
                    lastSeen = null,
                    isOnline = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )))
            } else {
                setAuthState(AuthState.Unauthenticated)
            }
        }
    }

    fun tryRefreshToken() {
        viewModelScope.launch {
            val result = authRepository.refreshToken()
            result.onSuccess {
                setAuthState(AuthState.Authenticated(User(
                    id = 0,
                    phoneNumber = "",
                    username = null,
                    displayName = "User",
                    profileImageId = null,
                    lastSeen = null,
                    isOnline = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )))
            }.onFailure {
                authRepository.logout()
                setAuthState(AuthState.Unauthenticated)
            }
        }
    }
}