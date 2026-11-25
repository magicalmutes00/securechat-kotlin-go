package com.securechat.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securechat.core.utils.formatFileSize
import com.securechat.domain.model.Media
import com.securechat.domain.model.MediaResourceType
import com.securechat.presentation.components.AppleCircleButton
import com.securechat.presentation.components.AppleProgressBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun MediaViewerScreen(
    media: Media,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    statusMessage: String? = null,
    downloadProgress: Int? = null
) {
    val scale by remember { mutableStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (media.resourceType) {
            MediaResourceType.IMAGE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                ) {
                    AsyncImage(
                        model = media.secureUrl,
                        contentDescription = media.originalFilename,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            MediaResourceType.VIDEO -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // The eager transformation signed at upload produced a poster
                    // frame; showing it is honest about what is on screen instead
                    // of a text placeholder pretending to be a player.
                    AsyncImage(
                        model = media.thumbnailUrl ?: media.secureUrl,
                        contentDescription = media.originalFilename,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppleSpace.Sm),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = media.originalFilename,
                            style = AppleType.Title3,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Download to play",
                            style = AppleType.Footnote,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            MediaResourceType.AUDIO -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppleSpace.Md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = media.originalFilename,
                            style = AppleType.Title3,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Audio Player",
                            style = AppleType.Footnote,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            MediaResourceType.RAW -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AppleSpace.Md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = media.originalFilename,
                            style = AppleType.Title3,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = formatFileSize(media.fileSize),
                            style = AppleType.Footnote,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Top bar: close, filename, download — glass discs over the media.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppleSpace.Md, end = AppleSpace.Md, top = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleCircleButton(
                icon = Icons.Default.Close,
                contentDescription = "Close",
                onClick = onClose,
                background = Color.White.copy(alpha = 0.15f)
            )

            Text(
                text = media.originalFilename,
                style = AppleType.Subhead,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = AppleSpace.Md)
            )

            AppleCircleButton(
                icon = Icons.Default.Download,
                contentDescription = "Download",
                onClick = onDownload,
                background = Color.White.copy(alpha = 0.15f)
            )
        }

        // Download feedback: progress while it runs, then the result.
        if (downloadProgress != null) {
            AppleProgressBar(
                progress = downloadProgress / 100f,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AppleSpace.Lg)
            )
        } else if (statusMessage != null) {
            Text(
                text = statusMessage,
                style = AppleType.Footnote,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(AppleSpace.Xl)
            )
        }
    }
}
