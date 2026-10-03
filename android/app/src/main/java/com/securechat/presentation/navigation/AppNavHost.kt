package com.securechat.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.securechat.MainViewModel
import com.securechat.presentation.auth.AuthScreen
import com.securechat.presentation.auth.OtpVerifyScreen
import com.securechat.presentation.auth.ProfileSetupScreen
import com.securechat.presentation.chat.ChatScreen
import com.securechat.presentation.contacts.ContactsScreen
import com.securechat.presentation.home.HomeScreen
import com.securechat.presentation.home.HomeViewModel
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
    val authState by mainViewModel.authState.collectAsState()

    // Gate the whole graph on auth state: signed-in users land on home,
    // signed-out users on auth, and the unknown state shows a splash.
    LaunchedEffect(authState) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (authState) {
            is MainViewModel.AuthState.Authenticated -> {
                if (currentRoute == "splash" || currentRoute == "auth" || currentRoute == "otp") {
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            is MainViewModel.AuthState.Unauthenticated -> {
                if (currentRoute != "auth" && currentRoute != "splash") {
                    navController.navigate("auth") { popUpTo(0) { inclusive = true } }
                } else if (currentRoute == "splash") {
                    navController.navigate("auth") { popUpTo("splash") { inclusive = true } }
                }
            }
            is MainViewModel.AuthState.Unknown -> Unit
        }
    }

    NavHost(navController, startDestination = "splash") {
        composable("splash") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        composable("auth") {
            AuthScreen(
                onAuthSuccess = onAuthSuccess,
                onOtpSent = { phone ->
                    val deviceName = android.os.Build.MODEL
                    navController.navigate("otp?phone=$phone&deviceName=$deviceName") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "otp?phone={phone}&deviceName={deviceName}",
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType; defaultValue = "" },
                navArgument("deviceName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            OtpVerifyScreen(
                phoneNumber = entry.arguments?.getString("phone") ?: "",
                deviceName = entry.arguments?.getString("deviceName") ?: "",
                deviceIdentifier = com.securechat.core.utils.DeviceInfo.deviceIdentifier(navController.context),
                onVerifySuccess = {
                    navController.navigate("profile") { popUpTo("auth") { inclusive = true } }
                },
                onResendOtp = { /* Handled in screen */ }
            )
        }

        composable("profile") {
            ProfileSetupScreen(
                onComplete = { navController.navigate("home") { popUpTo(0) { inclusive = true } } },
                onSkip = { navController.navigate("home") { popUpTo(0) { inclusive = true } } }
            )
        }

        composable("home") {
            HomeScreen(
                onLogout = onLogout,
                onNewChat = { navController.navigate("contacts") },
                onSettings = { navController.navigate("settings") },
                onConversationClick = { conversationId, otherUserName ->
                    navController.navigate("conversation/$conversationId?name=${android.net.Uri.encode(otherUserName)}")
                }
            )
        }

        composable(
            route = "conversation/{conversationId}?name={name}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType; defaultValue = "Chat" }
            )
        ) { entry ->
            val conversationId = entry.arguments?.getLong("conversationId") ?: 0L
            ChatScreen(
                conversationId = conversationId,
                otherUserName = entry.arguments?.getString("name") ?: "Chat",
                otherUserAvatar = null,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "conversation/new?phone={phone}",
            arguments = listOf(
                navArgument("phone") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            val phone = entry.arguments?.getString("phone") ?: ""
            val homeViewModel = hiltViewModel<HomeViewModel>()
            androidx.compose.runtime.LaunchedEffect(phone) {
                if (phone.isNotBlank()) {
                    homeViewModel.createNewConversation(phone) { conversation ->
                        // Pop this placeholder route so back returns to the list.
                        navController.navigate(
                            "conversation/${conversation.id}?name=${android.net.Uri.encode(conversation.getDisplayName(0))}"
                        ) {
                            popUpTo("conversation/new?phone={phone}") { inclusive = true }
                        }
                    }
                } else {
                    navController.popBackStack()
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                navArgument("media") { type = NavType.StringType } // JSON serialized media
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
                navArgument("media") { type = NavType.StringType }
            )
        ) {
            // DocumentViewerScreen(
            //     media = parseMedia(getString("media")),
            //     onClose = { navController.popBackStack() },
            //     onDownload = { /* Handle download */ },
            //     onShare = { /* Handle share */ }
            // )
        }
    }
}
