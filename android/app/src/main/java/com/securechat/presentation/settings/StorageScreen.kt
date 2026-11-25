package com.securechat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securechat.core.utils.formatFileSize
import com.securechat.presentation.components.AppleCell
import com.securechat.presentation.components.AppleGroupedSection
import com.securechat.presentation.components.AppleIconTile
import com.securechat.presentation.components.AppleProgressBar
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun StorageScreen(
    onBack: () -> Unit
) {
    // Sample data - would come from ViewModel
    val totalStorage = 2L * 1024 * 1024 * 1024 // 2 GB
    val usedStorage = 1L * 1024 * 1024 * 1024 // 1 GB
    val mediaStorage = 800L * 1024 * 1024 // 800 MB
    val documentsStorage = 150L * 1024 * 1024 // 150 MB
    val cacheStorage = 50L * 1024 * 1024 // 50 MB

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemGroupedBackground)
    ) {
        AppleTopBar(title = "Storage", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Lg)
            ) {
                Text(
                    text = "${formatFileSize(usedStorage)} of ${formatFileSize(totalStorage)} used",
                    style = AppleType.Body,
                    color = AppleColor.Label2
                )
                Spacer(modifier = Modifier.height(AppleSpace.Sm))
                AppleProgressBar(
                    progress = usedStorage.toFloat() / totalStorage.toFloat()
                )
            }

            AppleGroupedSection(header = "Breakdown") {
                AppleCell(
                    title = "Images & Videos",
                    leading = { AppleIconTile(Icons.Filled.Image, background = AppleColor.SystemBlue) },
                    value = formatFileSize(mediaStorage)
                )
                AppleCell(
                    title = "Documents",
                    leading = { AppleIconTile(Icons.Filled.InsertDriveFile, background = AppleColor.SystemOrange) },
                    value = formatFileSize(documentsStorage)
                )
                AppleCell(
                    title = "Cache",
                    leading = { AppleIconTile(Icons.Filled.CloudDownload, background = AppleColor.SystemGray) },
                    value = formatFileSize(cacheStorage),
                    showDivider = false
                )
            }

            AppleGroupedSection(
                header = "Manage",
                footer = "Storage management is not wired up in this build."
            ) {
                AppleCell(title = "Clear Cache", enabled = false, onClick = null)
                AppleCell(title = "Manage Media", enabled = false, onClick = null)
                AppleCell(
                    title = "Auto-Download Settings",
                    enabled = false,
                    showDivider = false,
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.padding(bottom = AppleSpace.Xxxl))
        }
    }
}
