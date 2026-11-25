package com.securechat.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.securechat.core.utils.formatFileSize
import com.securechat.domain.model.Media
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleIconButton
import com.securechat.presentation.components.AppleTertiaryButton
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun DocumentViewerScreen(
    media: Media,
    onClose: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit = {},
    statusMessage: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemBackground)
    ) {
        AppleTopBar(
            title = "Document",
            onBack = onClose,
            actions = {
                AppleIconButton(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open with",
                    onClick = onOpen
                )
            }
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppleSpace.Md),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = AppleSpace.Xxl)
            ) {
                val (icon, typeColor) = when (media.mimeType) {
                    "application/pdf" -> Icons.Default.PictureAsPdf to AppleColor.SystemRed
                    "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Icons.Default.Description to AppleColor.SystemBlue
                    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Icons.Default.TableChart to AppleColor.SystemGreen
                    "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Icons.Default.Slideshow to AppleColor.SystemOrange
                    else -> Icons.AutoMirrored.Filled.InsertDriveFile to AppleColor.Label3
                }

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(AppleColor.SystemSecondaryBackground, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(Modifier.height(AppleSpace.Xs))

                Text(
                    text = media.originalFilename,
                    style = AppleType.Title3,
                    color = AppleColor.Label,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = formatFileSize(media.fileSize),
                    style = AppleType.Footnote,
                    color = AppleColor.Label3
                )

                statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = AppleType.Footnote,
                        color = AppleColor.SystemBlue,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppleSpace.Lg),
            horizontalArrangement = Arrangement.spacedBy(AppleSpace.Sm)
        ) {
            AppleTertiaryButton(
                text = "Share",
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
            AppleButton(
                text = "Download",
                onClick = onDownload,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
