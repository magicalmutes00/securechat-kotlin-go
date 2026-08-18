package com.securechat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securechat.MainViewModel
import com.securechat.presentation.auth.AuthScreen
import com.securechat.presentation.auth.OtpVerifyScreen
import com.securechat.presentation.auth.ProfileSetupScreen
import com.securechat.presentation.chat.ChatScreen
import com.securechat.presentation.contacts.ContactsScreen
import com.securechat.presentation.home.HomeScreen
import com.securechat.presentation.media.DocumentViewerScreen
import com.securechat.presentation.media.MediaViewerScreen
import com.securechat.presentation.profile.ProfileScreen
import com.securechat.presentation.settings.AboutScreen
import com.securechat.presentation.settings.SettingsScreen
import com.securechat.presentation.settings.SessionsScreen
import com.securechat.presentation.settings.StorageScreen

@Composable
fun AppNavHost(
    onAuthSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val mainViewModel = hiltViewModel<MainViewModel>()
    var authState by remember {
        mutableStateOf<MainViewModel.AuthState>(MainViewModel.AuthState.Unknown)
    }

    LaunchedEffect(mainViewModel) {
        mainViewModel.observeAuthState { state ->
            authState = state
        }
    }

    NavHost(navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(onAuthSuccess = onAuthSuccess)
        }

        composable("otp") {
            OtpVerifyScreen(
                phoneNumber = "",
                deviceName = "",
                deviceIdentifier = "",
                onVerifySuccess = { navController.navigate("profile") { popUpTo("auth") { inclusive = true } } },
                onResendOtp = { /* Handled in screen */ }
            )
        }

        composable("profile") {
            ProfileSetupScreen(
                onComplete = { navController.navigate("home") { popUpTo("auth") { inclusive = true } } },
                onSkip = { navController.navigate("home") { popUpTo("auth") { inclusive = true } } }
            )
        }

        composable("home") {
            HomeScreen(
                onLogout = onLogout,
                onNewChat = { navController.navigate("contacts") },
                onSettings = { navController.navigate("settings") }
            )
        }

        composable(
            route = "conversation/{conversationId}",
            arguments = listOf(androidx.navigation.navArgument("conversationId") { type = NavType.LongType })
        ) {
            val conversationId = navController.currentBackStackEntry
                ?.arguments?.getLong("conversationId") ?: 0L
            if (authState is MainViewModel.AuthState.Authenticated) {
                ChatScreen(
                    conversationId = conversationId,
                    otherUserName = "Contact", // Would come from conversation data
                    otherUserAvatar = null,
                    onBack = { navController.popBackStack() }
                )
            } else {
                navController.navigate("auth") { popUpTo("auth") { inclusive = true } }
            }
        }

        composable("contacts") {
            ContactsScreen(
                onContactClick = { phone ->
                    navController.navigate("conversation/new?phone=$phone")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onLogout = onLogout,
                onLogoutAll = { /* Handled in screen */ },
                onBack = { navController.popBackStack() }
            )
        }

        composable("profile/edit") {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        composable("storage") {
            StorageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("sessions") {
            SessionsScreen(
                onBack = { navController.popBackStack() },
                onRevokeDevice = { deviceId -> /* Handle revoke */ }
            )
        }

        composable("about") {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "media/viewer",
            arguments = listOf(
                androidx.navigation.navArgument("media") { type = NavType.StringType } // JSON serialized media
            )
        ) {
            // MediaViewerScreen(
            //     media = parseMedia(getString("media")),
            //     onClose = { navController.popBackStack() },
            //     onDownload = { /* Handle download */ }
            // )
        }

        composable(
            route = "document/viewer",
            arguments = listOf(
                androidx.navigation.navArgument("media") { type = NavType.StringType }
            )
        ) {
            // DocumentViewerScreen(
            //     media = parseMedia(getString("media")),
            //     onClose = { navController.popBackStack() },
            //     onDownload = { /* Handle download */ },
            //     onShare = { /* Handle share */ }
            // )
        }

        // New conversation flow
        composable(
            route = "conversation/new",
            arguments = listOf(
                androidx.navigation.navArgument("phone") { type = NavType.StringType }
            )
        ) {
            // This would create a conversation and navigate to chat
        }
    }
}