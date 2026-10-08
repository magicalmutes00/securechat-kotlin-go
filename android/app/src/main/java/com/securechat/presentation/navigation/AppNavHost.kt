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
import com.securechat.presentation.profile.ProfileScreen
import com.securechat.presentation.settings.AboutScreen
import com.securechat.presentation.settings.ChatSettingsScreen
import com.securechat.presentation.settings.LanguageScreen
import com.securechat.presentation.settings.NotificationsScreen
import com.securechat.presentation.settings.SessionsScreen
import com.securechat.presentation.settings.SettingsScreen
import com.securechat.presentation.settings.StorageScreen
import com.securechat.presentation.settings.ThemeScreen

@Composable
fun AppNavHost(
    onAuthSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    val mainViewModel = hiltViewModel<MainViewModel>()
    val authState by mainViewModel.authState.collectAsState()
    val needsProfileSetup by mainViewModel.needsProfileSetup.collectAsState()

    // Gate the whole graph on auth state. Brand-new accounts (no username yet)
    // are sent through first-time profile setup before landing on home.
    LaunchedEffect(authState, needsProfileSetup) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        when (authState) {
            is MainViewModel.AuthState.Authenticated -> {
                when {
                    needsProfileSetup && currentRoute != "profile" ->
                        navController.navigate("profile") { popUpTo(0) { inclusive = true } }

                    !needsProfileSetup &&
                        (currentRoute == "splash" || currentRoute == "auth" || currentRoute == "otp") ->
                        navController.navigate("home") { popUpTo(0) { inclusive = true } }
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
                onOtpSent = { _ ->
                    navController.navigate("otp") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        composable("otp") {
            OtpVerifyScreen(
                onVerifySuccess = onAuthSuccess,
                onResendOtp = { /* Handled in screen */ }
            )
        }

        composable("profile") {
            ProfileSetupScreen(
                onComplete = {
                    mainViewModel.onProfileSetupHandled()
                    navController.navigate("home") { popUpTo(0) { inclusive = true } }
                },
                onSkip = {
                    mainViewModel.onProfileSetupHandled()
                    navController.navigate("home") { popUpTo(0) { inclusive = true } }
                }
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
            LaunchedEffect(phone) {
                if (phone.isNotBlank()) {
                    homeViewModel.createNewConversation(
                        phone,
                        onSuccess = { conversation ->
                            val otherName = conversation.getDisplayName(
                                com.securechat.core.utils.UserSession.currentUserId
                            )
                            navController.navigate(
                                "conversation/${conversation.id}?name=${android.net.Uri.encode(otherName)}"
                            ) {
                                popUpTo("conversation/new?phone={phone}") { inclusive = true }
                            }
                        },
                        onFailure = { navController.popBackStack() }
                    )
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
                onBack = { navController.popBackStack() },
                onProfileClick = { navController.navigate("profile/edit") },
                onSessionsClick = { navController.navigate("sessions") },
                onNotificationsClick = { navController.navigate("notifications") },
                onThemeClick = { navController.navigate("theme") },
                onChatSettingsClick = { navController.navigate("chat-settings") },
                onStorageClick = { navController.navigate("storage") },
                onLanguageClick = { navController.navigate("language") },
                onAboutClick = { navController.navigate("about") },
                onLogout = onLogout,
                onLogoutAll = onLogout
            )
        }

        composable("profile/edit") {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        composable("notifications") {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable("theme") {
            ThemeScreen(onBack = { navController.popBackStack() })
        }

        composable("chat-settings") {
            ChatSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("language") {
            LanguageScreen(onBack = { navController.popBackStack() })
        }

        composable("storage") {
            StorageScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("sessions") {
            SessionsScreen(
                onBack = { navController.popBackStack() }
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
                navArgument("media") { type = NavType.StringType }
            )
        ) {
            // Media viewer is not yet wired up.
        }

        composable(
            route = "document/viewer",
            arguments = listOf(
                navArgument("media") { type = NavType.StringType }
            )
        ) {
            // Document viewer is not yet wired up.
        }
    }
}