package com.securechat.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.domain.model.Media
import com.securechat.presentation.theme.Theme

@Composable
fun DocumentViewerScreen(
    media: Media,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Document Preview Area
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Document type icon
                val (icon, typeColor) = when (media.mimeType) {
                    "application/pdf" -> Icons.Default.PictureAsPdf to Color.Red
                    "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Icons.Default.Description to Color.Blue
                    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Icons.Default.TableChart to Color.Green
                    "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Icons.Default.Slideshow to Color(0xFFFF9800)
                    else -> Icons.Default.InsertDriveFile to Color.Gray
                }
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(80.dp)
                )
                
                // File name
                Text(
                    text = media.originalFilename,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                
                // File size
                Text(
                    text = "${(media.fileSize / 1024).toInt()} KB",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        
        // Bottom Action Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download"
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text("Download")
                }
                
                Button(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share"
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text("Share")
                }
                
                IconButton(onClick = { /* Open with */ }) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open with",
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}