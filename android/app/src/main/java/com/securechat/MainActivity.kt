package com.securechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.securechat.core.common.Result
import com.securechat.core.security.TokenStorage
import com.securechat.di.AppModule
import com.securechat.presentation.auth.AuthScreen
import com.securechat.presentation.home.HomeScreen
import com.securechat.presentation.navigation.AppNavHost
import com.securechat.presentation.theme.Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStorage: TokenStorage

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Theme {
                AppNavHost(
                    onAuthSuccess = { viewModel.onAuthSuccess() },
                    onLogout = { viewModel.onLogout() }
                )
            }
        }

        checkAuthState()
    }

    private fun checkAuthState() {
        lifecycleScope.launch {
            val result = tokenStorage.getAccessToken()
            when (result) {
                is Result.Success -> {
                    if (!tokenStorage.isAccessTokenExpired()) {
                        viewModel.onAuthSuccess()
                    } else {
                        // Try to refresh token
                        viewModel.tryRefreshToken()
                    }
                }
                is Result.Failure -> {
                    // No token, stay on auth screen
                }
            }
        }
    }
}