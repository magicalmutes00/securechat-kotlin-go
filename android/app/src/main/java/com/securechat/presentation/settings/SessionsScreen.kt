package com.securechat.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.core.utils.formatTimestamp
import com.securechat.domain.model.Device
import com.securechat.presentation.theme.Theme

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onRevokeDevice: (Long) -> Unit
) {
    // Sample data - would come from ViewModel
    val currentDeviceId = 1L
    val devices = listOf(
        Device(1, 1, "Pixel 8 Pro", "android_abc123", "android", System.currentTimeMillis() - 3600000, System.currentTimeMillis()),
        Device(2, 1, "iPhone 15", "ios_xyz789", "ios", System.currentTimeMillis() - 86400000, System.currentTimeMillis() - 86400000),
        Device(3, 1, "Windows Desktop", "win_def456", "windows", System.currentTimeMillis() - 604800000, System.currentTimeMillis() - 604800000),
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Active Sessions",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        )
        
        Text(
            text = "Manage your active sessions across all devices",
            fontSize = 14.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(devices) { device ->
                SessionItem(
                    device = device,
                    isCurrent = device.id == currentDeviceId,
                    onRevoke = { onRevokeDevice(device.id) }
                )
                
                if (device != devices.last()) Divider(modifier = Modifier.padding(start = 72.dp))
            }
        }
        
        // Security notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            androidx.compose.material3.Text(
                text = "If you see an unfamiliar device, revoke it immediately and change your password.",
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
fun SessionItem(
    device: Device,
    isCurrent: Boolean,
    onRevoke: () -> Unit
) {
    val platformIcon = when (device.platform.lowercase()) {
        "android" -> Icons.Default.PhoneAndroid
        "ios" -> Icons.Default.PhoneIphone
        "windows" -> Icons.Default.Computer
        "mac" -> Icons.Default.Computer
        "linux" -> Icons.Default.Computer
        else -> Icons.Default.Devices
    }

    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            androidx.compose.material3.Icon(
                imageVector = platformIcon,
                contentDescription = device.platform,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp).padding(end = 16.dp)
            )
        },
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = device.deviceName, fontSize = 16.sp)
                if (isCurrent) {
                    androidx.compose.material3.Text(
                        text = "Current",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = device.platform.replaceFirstChar { it.uppercase() },
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lastActiveText = device.lastSeen?.let { it.formatTimestamp() } ?: "Unknown"
                Text(
                    text = "Last active: $lastActiveText",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        trailingContent = {
            if (!isCurrent) {
                androidx.compose.material3.IconButton(
                    onClick = onRevoke,
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Revoke session",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}