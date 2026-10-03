package com.securechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.securechat.presentation.navigation.AppNavHost
import com.securechat.presentation.theme.Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auth state is resolved in MainViewModel's init and observed by AppNavHost.
        setContent {
            Theme {
                AppNavHost(
                    onAuthSuccess = { viewModel.onAuthSuccess() },
                    onLogout = { viewModel.onLogout() }
                )
            }
        }
    }
}
