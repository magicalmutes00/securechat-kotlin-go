package com.securechat.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.theme.Theme

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onLogoutAll: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "John Doe", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Text(text = "@johndoe", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "+1 555 123 4567", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            
            Avatar(url = null, name = "John Doe", modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(60.dp))
        }
        
        Divider()
        
        // Settings Sections
        SettingsSection(title = "Account") {
            SettingsItem(
                icon = Icons.Default.Person,
                title = "Profile",
                subtitle = "Edit your profile and avatar",
                onClick = { /* Navigate to profile */ }
            )
            
            SettingsItem(
                icon = Icons.Default.Security,
                title = "Privacy & Security",
                subtitle = "Two-factor auth, blocked users, sessions",
                onClick = { /* Navigate to privacy */ }
            )
            
            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                subtitle = "Message tones, vibration, preview",
                onClick = { /* Navigate to notifications */ }
            )
        }
        
        SettingsSection(title = "Chat") {
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = "Light, Dark, System default",
                onClick = { /* Navigate to theme */ }
            )
            
            SettingsItem(
                icon = Icons.Default.Chat,
                title = "Chat Settings",
                subtitle = "Media auto-download, font size, wallpaper",
                onClick = { /* Navigate to chat settings */ }
            )
            
            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Storage Usage",
                subtitle = "Manage media, clear cache",
                onClick = { /* Navigate to storage */ }
            )
        }
        
        SettingsSection(title = "Advanced") {
            SettingsItem(
                icon = Icons.Default.Language,
                title = "Language",
                subtitle = "English (US)",
                onClick = { /* Navigate to language */ }
            )
            
            SettingsItem(
                icon = Icons.Default.Info,
                title = "About SecureChat",
                subtitle = "Version 1.0.0",
                onClick = { /* Navigate to about */ }
            )
        }
        
        SettingsSection(title = "Danger Zone") {
            SettingsItem(
                icon = Icons.Default.Logout,
                title = "Logout",
                subtitle = "Sign out of this device",
                titleColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                onClick = onLogout
            )
            
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "Logout All Devices",
                subtitle = "Sign out everywhere",
                titleColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                onClick = onLogoutAll
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
        
        content()
        
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp).padding(end = 16.dp)
            )
        },
        headlineContent = {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp
            )
        },
        supportingContent = {
            Text(text = subtitle, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    )
}