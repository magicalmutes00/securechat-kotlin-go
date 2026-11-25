package com.securechat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.core.utils.DeviceInfo
import com.securechat.core.utils.formatTimestamp
import com.securechat.domain.model.Device
import com.securechat.presentation.components.AppleCell
import com.securechat.presentation.components.AppleEmptyState
import com.securechat.presentation.components.AppleGroupedSection
import com.securechat.presentation.components.AppleIconTile
import com.securechat.presentation.components.AppleTextButton
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemGroupedBackground)
    ) {
        AppleTopBar(title = "Active Sessions", onBack = onBack)

        if (devices.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.padding(top = AppleSpace.Huge))
                AppleEmptyState(
                    icon = Icons.Filled.Devices,
                    title = "No Active Sessions",
                    message = "Devices signed in to your account will appear here."
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                AppleGroupedSection(
                    header = "Signed In",
                    footer = "Signing out of a session ends its access immediately. " +
                        "You cannot sign out of this device here."
                ) {
                    devices.forEachIndexed { index, device ->
                        SessionItem(
                            device = device,
                            isCurrent = device.deviceIdentifier == currentIdentifier,
                            showDivider = index != devices.lastIndex,
                            onRevoke = { viewModel.revokeDevice(device.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.padding(bottom = AppleSpace.Xxxl))
            }
        }
    }
}

@Composable
private fun SessionItem(
    device: Device,
    isCurrent: Boolean,
    showDivider: Boolean,
    onRevoke: () -> Unit
) {
    val lastActive = device.lastSeen?.formatTimestamp() ?: "Unknown"
    val platform = device.platform.replaceFirstChar { it.uppercase() }

    AppleCell(
        title = device.deviceName,
        subtitle = "$platform  •  Last active $lastActive",
        leading = { AppleIconTile(platformIcon(device.platform), background = AppleColor.SystemGray) },
        trailing = {
            if (isCurrent) {
                Text(
                    text = "This Device",
                    style = AppleType.Caption1,
                    color = AppleColor.Label3
                )
            } else {
                AppleTextButton(
                    text = "Sign Out",
                    onClick = onRevoke,
                    color = AppleColor.SystemRed
                )
            }
        },
        showDivider = showDivider,
        dividerInset = 57.dp
    )
}

private fun platformIcon(platform: String): ImageVector = when (platform.lowercase()) {
    "android" -> Icons.Filled.PhoneAndroid
    "ios" -> Icons.Filled.PhoneIphone
    "windows", "mac", "linux", "desktop" -> Icons.Filled.Computer
    else -> Icons.Filled.Devices
}
