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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.core.utils.DeviceInfo
import com.securechat.core.utils.formatTimestamp
import com.securechat.domain.model.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsState()
    val currentIdentifier = DeviceInfo.deviceIdentifier(context)

    LaunchedEffect(Unit) {
        viewModel.refreshDevices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Active Sessions") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "Manage the devices signed in to your account.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
            )

            if (devices.isEmpty()) {
                Text(
                    text = "No active sessions found.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(20.dp)
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(devices, key = { it.id }) { device ->
                        SessionItem(
                            device = device,
                            isCurrent = device.deviceIdentifier == currentIdentifier,
                            onRevoke = { viewModel.revokeDevice(device.id) }
                        )
                        if (device != devices.last()) {
                            Divider(modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
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
        "android" -> androidx.compose.material.icons.Icons.Default.PhoneAndroid
        "ios" -> androidx.compose.material.icons.Icons.Default.PhoneIphone
        "windows", "mac", "linux", "desktop" -> androidx.compose.material.icons.Icons.Default.Computer
        else -> androidx.compose.material.icons.Icons.Default.Devices
    }

    androidx.compose.material3.ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            androidx.compose.material3.Icon(
                imageVector = platformIcon,
                contentDescription = device.platform,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp).padding(end = 16.dp)
            )
        },
        headlineContent = {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Text(text = device.deviceName, fontSize = 16.sp)
                if (isCurrent) {
                    Text(
                        text = "Current",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        supportingContent = {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = device.platform.replaceFirstChar { it.uppercase() },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val lastActiveText = device.lastSeen?.let { it.formatTimestamp() } ?: "Unknown"
                Text(
                    text = "Last active: $lastActiveText",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        trailingContent = {
            if (!isCurrent) {
                androidx.compose.material3.IconButton(onClick = onRevoke) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Logout,
                        contentDescription = "Revoke session",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}