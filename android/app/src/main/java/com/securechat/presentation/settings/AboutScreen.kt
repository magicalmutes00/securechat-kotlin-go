package com.securechat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.presentation.theme.Theme

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // App Icon and Name
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                
                Text(text = "SecureChat", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(text = "Version 1.0.0", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "Private & Secure Messaging", fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Divider()
        
        // Description
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "About SecureChat", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "SecureChat is a private messaging application built with security and privacy as top priorities. " +
                        "All messages are encrypted end-to-end, media is stored securely on Cloudinary with signed URLs, " +
                        "and your data never leaves your control.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.padding(16.dp))
        
        // Features
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "Key Features", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                
                FeatureRow(
                    icon = Icons.Default.Lock,
                    title = "End-to-End Encryption",
                    description = "Messages encrypted on device"
                )
                
                FeatureRow(
                    icon = Icons.Default.Cloud,
                    title = "Secure Media Storage",
                    description = "Cloudinary with signed URLs"
                )
                
                FeatureRow(
                    icon = Icons.Default.OfflinePin,
                    title = "Offline-First",
                    description = "Works without internet"
                )
                
                FeatureRow(
                    icon = Icons.Default.Delete,
                    title = "Self-Destructing Messages",
                    description = "Optional disappearing messages"
                )
            }
        }
        
        Spacer(modifier = Modifier.padding(16.dp))
        
        // Links
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "Legal & Links", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                
                LinkRow(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy",
                    onClick = { /* Open privacy policy */ }
                )
                
                LinkRow(
                    icon = Icons.Default.Description,
                    title = "Terms of Service",
                    onClick = { /* Open terms */ }
                )
                
                LinkRow(
                    icon = Icons.Default.Code,
                    title = "Open Source Licenses",
                    onClick = { /* Open licenses */ }
                )
                
                LinkRow(
                    icon = Icons.Default.BugReport,
                    title = "Report a Bug",
                    onClick = { /* Open bug report */ }
                )
            }
        }
        
        Spacer(modifier = Modifier.padding(32.dp))
        
        // Built with love
        Text(
            text = "Built with ❤️ using Kotlin, Jetpack Compose, Go, and MySQL",
            fontSize = 12.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        )
    }
}

@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
        },
        headlineContent = {
            Text(text = title, fontSize = 16.sp)
        },
        supportingContent = {
            Text(text = description, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

@Composable
fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
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
            Text(text = title, fontSize = 16.sp)
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    )
}