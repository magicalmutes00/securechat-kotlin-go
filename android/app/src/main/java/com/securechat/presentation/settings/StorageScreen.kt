package com.securechat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.core.utils.formatFileSize
import com.securechat.presentation.theme.Theme

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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Storage Overview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "Storage Usage", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                
                Text(text = "${formatFileSize(usedStorage)} of ${formatFileSize(totalStorage)} used", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                
                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(usedStorage.toFloat() / totalStorage.toFloat())
                            .height(8.dp)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    )
                }
            }
        }
        
        androidx.compose.material3.Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Storage Breakdown
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Breakdown", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            
            StorageCategoryItem(
                icon = androidx.compose.material.icons.Icons.Default.Image,
                title = "Images & Videos",
                size = mediaStorage,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
            
            StorageCategoryItem(
                icon = androidx.compose.material.icons.Icons.Default.InsertDriveFile,
                title = "Documents",
                size = documentsStorage,
                color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
            )
            
            StorageCategoryItem(
                icon = androidx.compose.material.icons.Icons.Default.CloudDownload,
                title = "Cache",
                size = cacheStorage,
                color = androidx.compose.material3.MaterialTheme.colorScheme.secondary
            )
            
            androidx.compose.material3.Divider()
            
            // Actions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { /* Clear cache */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Cache (${formatFileSize(cacheStorage)})")
                }
                
                androidx.compose.material3.OutlinedButton(
                    onClick = { /* Manage media */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Media")
                }
                
                androidx.compose.material3.OutlinedButton(
                    onClick = { /* Auto-download settings */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto-download Settings")
                }
            }
        }
    }
}

@Composable
fun StorageCategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    size: Long,
    color: androidx.compose.ui.graphics.Color
) {
    androidx.compose.material3.ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color
                )
            }
        },
        headlineContent = {
            Text(text = title, fontSize = 16.sp)
        },
        supportingContent = {
            Text(text = formatFileSize(size), fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}