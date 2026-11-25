package com.securechat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.presentation.components.AppleAvatar
import com.securechat.presentation.components.AppleCell
import com.securechat.presentation.components.AppleGroupedSection
import com.securechat.presentation.components.AppleIconTile
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onSessionsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onThemeClick: () -> Unit,
    onChatSettingsClick: () -> Unit,
    onStorageClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogout: () -> Unit,
    onLogoutAll: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val loggingOutAll by viewModel.isLoggingOutAll.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemGroupedBackground)
    ) {
        AppleTopBar(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            /* Profile cell, edge to edge like iOS Settings. */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProfileClick)
                    .background(AppleColor.SystemGroupedCell)
                    .padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppleAvatar(
                    url = user?.avatarUrl,
                    name = user?.displayName ?: "User",
                    size = 60
                )
                Spacer(modifier = Modifier.width(AppleSpace.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.displayName ?: "User",
                        style = AppleType.Title3,
                        color = AppleColor.Label
                    )
                    user?.username?.let {
                        Text(
                            text = "@$it",
                            style = AppleType.Footnote,
                            color = AppleColor.Label3
                        )
                    }
                    user?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = AppleType.Footnote,
                            color = AppleColor.Label3
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppleColor.Chevron,
                    modifier = Modifier.size(16.dp)
                )
            }

            AppleGroupedSection(header = "Account") {
                SettingItem("Profile", Icons.Filled.Person, AppleColor.SystemBlue, onProfileClick)
                SettingItem(
                    "Active Sessions",
                    Icons.Filled.Devices,
                    AppleColor.SystemGray,
                    onSessionsClick,
                    showDivider = false
                )
            }

            AppleGroupedSection(header = "Preferences") {
                SettingItem("Notifications", Icons.Filled.Notifications, AppleColor.SystemRed, onNotificationsClick)
                SettingItem("Appearance", Icons.Filled.Palette, AppleColor.SystemIndigo, onThemeClick)
                SettingItem("Chat Settings", Icons.AutoMirrored.Filled.Chat, AppleColor.SystemGreen, onChatSettingsClick)
                SettingItem("Storage Usage", Icons.Filled.Storage, AppleColor.SystemOrange, onStorageClick)
                SettingItem("Language", Icons.Filled.Language, AppleColor.SystemBlue, onLanguageClick, showDivider = false)
            }

            AppleGroupedSection(header = "About") {
                SettingItem("About SecureChat", Icons.Filled.Info, AppleColor.SystemGray, onAboutClick, showDivider = false)
            }

            AppleGroupedSection(header = "Session", footer = "Logging out of all devices ends every active session, including this one.") {
                AppleCell(
                    title = "Log Out",
                    leading = { AppleIconTile(Icons.AutoMirrored.Filled.Logout, background = AppleColor.SystemRed) },
                    onClick = onLogout
                )
                AppleCell(
                    title = "Log Out of All Devices",
                    leading = { AppleIconTile(Icons.Filled.DeleteForever, background = AppleColor.SystemRed) },
                    enabled = !loggingOutAll,
                    showDivider = false,
                    destructive = true,
                    onClick = {
                        viewModel.logoutAllDevices()
                        onLogoutAll()
                    }
                )
            }

            Spacer(modifier = Modifier.padding(bottom = AppleSpace.Xxxl))
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tileColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    AppleCell(
        title = title,
        leading = { AppleIconTile(icon, background = tileColor) },
        showChevron = true,
        showDivider = showDivider,
        dividerInset = 57.dp,
        onClick = onClick
    )
}
