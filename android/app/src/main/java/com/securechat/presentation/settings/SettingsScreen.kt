package com.securechat.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.presentation.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile header reflects the signed-in user.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onProfileClick)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    url = user?.avatarUrl,
                    name = user?.displayName ?: "User",
                    size = 64
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.displayName ?: "User",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    user?.username?.let {
                        Text(
                            text = "@$it",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    user?.phoneNumber?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            SettingsSection(title = "Account") {
                SettingItem("Profile", Icons.Default.Person, onProfileClick)
                SettingItem("Active Sessions", Icons.Default.Devices, onSessionsClick)
            }

            SettingsSection(title = "Preferences") {
                SettingItem("Notifications", Icons.Default.Notifications, onNotificationsClick)
                SettingItem("Theme", Icons.Default.Palette, onThemeClick)
                SettingItem("Chat Settings", Icons.Default.Chat, onChatSettingsClick)
                SettingItem("Storage Usage", Icons.Default.Storage, onStorageClick)
                SettingItem("Language", Icons.Default.Language, onLanguageClick)
            }

            SettingsSection(title = "About") {
                SettingItem("About SecureChat", Icons.Default.Info, onAboutClick)
            }

            SettingsSection(title = "Session") {
                SettingItem("Log out", Icons.Default.Logout, onLogout)
                SettingItem("Log out of all devices", Icons.Default.DeleteForever) {
                    viewModel.logoutAllDevices()
                    onLogoutAll()
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun SettingItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        headlineContent = { Text(text = title, fontSize = 16.sp) },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}