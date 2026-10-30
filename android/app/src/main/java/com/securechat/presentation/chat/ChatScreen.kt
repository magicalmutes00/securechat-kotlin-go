package com.securechat.presentation.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securechat.core.utils.UserSession
import com.securechat.core.utils.formatClockTime
import com.securechat.core.utils.formatFileSize
import com.securechat.domain.model.Media
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.components.DatePill
import com.securechat.presentation.components.SignalIconButton
import com.securechat.presentation.components.TypingDots
import com.securechat.presentation.theme.Skin
import com.securechat.presentation.theme.SkinType
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatScreen(
    conversationId: Long,
    otherUserName: String,
    otherUserAvatar: String?,
    onBack: () -> Unit,
    onConversationLeft: () -> Unit = {},
    onAddMembers: () -> Unit = {},
    onOpenMedia: (mediaId: Long, isDocument: Boolean) -> Unit = { _, _ -> }
) {
    val viewModel: ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    var messageText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachError by remember { mutableStateOf<String?>(null) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(conversationId) {
        viewModel.initialize(conversationId, UserSession.currentUserId)
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { attachError = stageAttachment(context, uri, viewModel) }
    }
    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { attachError = stageAttachment(context, uri, viewModel) }
    }

    val messages = viewModel.messages.value
    val isGroup = viewModel.isGroup.value
    val title = viewModel.conversationName.value ?: otherUserName

    Column(modifier = Modifier.fillMaxSize().background(Skin.Void)) {
        ConversationHeader(
            title = title,
            avatarUrl = otherUserAvatar,
            online = viewModel.presenceText.value.startsWith("Online"),
            statusText = if (viewModel.isAnyoneTyping) "typing…" else viewModel.presenceText.value,
            typing = viewModel.isAnyoneTyping,
            showMenu = isGroup,
            onBack = onBack,
            onMenu = { showGroupMenu = true }
        )

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Skin.Line))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // reverseLayout draws the last item at the top, so the date pill is
            // added last in order to appear above the history.
            items(messages.reversed(), key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    isOwn = message.senderId == viewModel.currentUserId,
                    showAuthor = isGroup && message.senderId != viewModel.currentUserId,
                    onRetry = { viewModel.retryMessage(message.id) },
                    onMediaClick = { media ->
                        onOpenMedia(media.id, message.type == MessageType.DOCUMENT)
                    }
                )
            }

            if (viewModel.isAnyoneTyping) {
                item(key = "typing") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(Skin.BubbleIn)
                                .background(Skin.Primary)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            TypingDots()
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(text = "typing…", style = SkinType.Typing, color = Skin.InkFaint)
                    }
                }
            }

            if (messages.isNotEmpty()) {
                item(key = "date") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        DatePill("Today")
                    }
                }
            }
        }

        viewModel.attachmentSend.value?.let { state ->
            AttachmentBanner(
                fileName = state.fileName,
                progress = state.progress,
                failed = state.failed,
                error = state.error,
                onCancel = { viewModel.cancelAttachment() },
                onRetry = { viewModel.retryAttachment() }
            )
        }

        attachError?.let { error ->
            Text(
                text = error,
                style = SkinType.Subline,
                color = Skin.Error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }

        Composer(
            text = messageText,
            placeholder = "Message ${title.substringBefore(' ')}…",
            onTextChange = {
                messageText = it
                if (it.isNotBlank()) viewModel.onUserTyping()
            },
            onSend = {
                viewModel.onUserStoppedTyping()
                viewModel.sendTextMessage(it)
                messageText = ""
            },
            onAttachPhoto = {
                attachError = null
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onAttachDocument = {
                attachError = null
                pickDocument.launch(arrayOf("*/*"))
            }
        )
    }

    if (showGroupMenu) {
        GroupMenu(
            onDismiss = { showGroupMenu = false },
            onAddMembers = {
                showGroupMenu = false
                onAddMembers()
            },
            onRename = {
                showGroupMenu = false
                showRenameDialog = true
            },
            onLeave = {
                showGroupMenu = false
                showLeaveDialog = true
            }
        )
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                viewModel.renameGroup(newName)
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = Skin.SurfaceCard,
            titleContentColor = Skin.Ink,
            textContentColor = Skin.InkDim,
            title = { Text("Leave group", style = SkinType.Title) },
            text = { Text("You will no longer receive messages from this group.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveGroup(onConversationLeft)
                }) { Text("Leave", color = Skin.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel", color = Skin.InkDim)
                }
            }
        )
    }
}

/**
 * Header: back chevron, gradient avatar, name and a status line that turns lime
 * while the other side is online or typing — the design's "online · encrypted".
 */
@Composable
private fun ConversationHeader(
    title: String,
    avatarUrl: String?,
    online: Boolean,
    statusText: String,
    typing: Boolean,
    showMenu: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Skin.Ink,
                modifier = Modifier.size(20.dp)
            )
        }

        Avatar(url = avatarUrl, name = title, size = 38, initialsSize = 10)
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SkinType.HeaderName,
                color = Skin.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText.ifBlank { "offline" },
                    style = SkinType.HeaderStatus,
                    color = if (online || typing) Skin.Signal else Skin.InkDim
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Skin.InkDim,
                    modifier = Modifier.size(9.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(text = "encrypted", style = SkinType.HeaderMeta, color = Skin.InkDim)
            }
        }

        if (showMenu) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onMenu() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Group options",
                    tint = Skin.InkDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** A message, with the author's avatar and name when it is a group run. */
@Composable
private fun MessageRow(
    message: Message,
    isOwn: Boolean,
    showAuthor: Boolean,
    onRetry: () -> Unit,
    onMediaClick: (Media) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        if (!isOwn && showAuthor) {
            Avatar(url = null, name = message.senderName ?: "?", size = 28, initialsSize = 9)
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
        ) {
            if (showAuthor && !message.senderName.isNullOrBlank()) {
                Text(
                    text = message.senderName.orEmpty(),
                    style = SkinType.Meta,
                    color = Skin.InkDim,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                )
            }

            when (message.type) {
                MessageType.DOCUMENT -> message.media?.let {
                    FileBubble(it, message, isOwn, onMediaClick)
                } ?: TextBubble(message, isOwn, onRetry)

                MessageType.AUDIO -> VoiceBubble(message, isOwn, onMediaClick)

                MessageType.IMAGE, MessageType.VIDEO -> message.media?.let {
                    MediaBubble(it, message, isOwn, onMediaClick)
                } ?: TextBubble(message, isOwn, onRetry)

                MessageType.TEXT -> TextBubble(message, isOwn, onRetry)
            }
        }
    }
}

/** Plain text bubble: blue incoming, lime outgoing, tail corner squared. */
@Composable
private fun TextBubble(message: Message, isOwn: Boolean, onRetry: () -> Unit) {
    val shape = if (isOwn) Skin.BubbleOut else Skin.BubbleIn
    val background = if (isOwn) Skin.Signal else Skin.Primary
    val textColor = if (isOwn) Skin.OnSignal else Skin.OnPrimary
    val metaColor = if (isOwn) Skin.OnSignal.copy(alpha = 0.75f) else Skin.OnPrimarySoft

    Column(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        message.text?.let {
            Text(
                text = it,
                style = if (isOwn) SkinType.BubbleOut else SkinType.BubbleIn,
                color = textColor
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            // End-aligned, not full-width: fillMaxWidth made the meta row claim
            // the whole 300dp cap, so a one-word message rendered as a wide slab
            // instead of hugging its text the way the design does.
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOwn) {
                StatusGlyph(message.status, metaColor, onRetry)
                Spacer(Modifier.width(4.dp))
            } else {
                // Receipt labels differ: "Delivered" is the recipient's device
                // accepting it, "Read" is them opening the chat. Collapsing both
                // into "Delivered" told the sender less than the status carried.
                val receipt = when (message.status) {
                    MessageStatus.DELIVERED -> "Delivered"
                    MessageStatus.READ -> "Read"
                    else -> null
                }
                receipt?.let {
                    Text(it, style = SkinType.BubbleMetaIn, color = metaColor)
                    Spacer(Modifier.width(6.dp))
                }
            }
            Text(
                text = message.createdAt.formatClockTime(),
                style = if (isOwn) SkinType.BubbleMetaOut else SkinType.BubbleMetaIn,
                color = metaColor
            )
        }
    }
}

/** Attachment bubble: file tile, name, kind, size. Tapping opens the viewer. */
@Composable
private fun FileBubble(
    media: Media,
    message: Message,
    isOwn: Boolean,
    onMediaClick: (Media) -> Unit
) {
    val shape = if (isOwn) Skin.BubbleOut else Skin.BubbleIn
    val background = if (isOwn) Skin.Signal else Skin.Primary
    val textColor = if (isOwn) Skin.OnSignal else Skin.OnPrimary
    val metaColor = if (isOwn) Skin.OnSignal.copy(alpha = 0.75f) else Skin.OnPrimarySoft

    Column(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .clip(shape)
            .background(background)
            .clickable { onMediaClick(media) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = Skin.OnSignal,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.originalFilename,
                    style = SkinType.FileName,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${media.mimeType.substringAfterLast('/').uppercase()} · ${formatFileSize(media.fileSize)}",
                    style = SkinType.FileMeta,
                    color = metaColor
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOwn) {
                StatusGlyph(message.status, metaColor, onRetry = {})
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = message.createdAt.formatClockTime(),
                style = SkinType.BubbleMetaIn,
                color = metaColor
            )
        }
    }
}

/** Voice note: play disc, waveform, duration. */
@Composable
private fun VoiceBubble(message: Message, isOwn: Boolean, onMediaClick: (Media) -> Unit) {
    val media = message.media
    val shape = if (isOwn) Skin.BubbleOut else Skin.BubbleIn
    val background = if (isOwn) Skin.Signal else Skin.Primary
    val metaColor = if (isOwn) Skin.OnSignal.copy(alpha = 0.75f) else Skin.OnPrimarySoft
    val barColor = if (isOwn) Skin.OnSignal else Color.White

    // Fixed waveform: the message stores a duration but not samples, so any
    // amplitude shape here would be invented. This reads as "audio", not as data.
    val bars = listOf(8, 14, 20, 12, 22, 16, 9, 18, 12, 20, 10, 15)

    Row(
        modifier = Modifier
            .widthIn(min = 246.dp)
            .clip(shape)
            .background(background)
            .clickable { media?.let(onMediaClick) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play voice message",
                tint = Skin.OnSignal,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bars.forEach { barHeight ->
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(barColor.copy(alpha = 0.9f))
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = media?.duration?.let { formatDurationSeconds(it) } ?: "0:24",
            style = SkinType.BubbleMetaOut,
            color = metaColor
        )
    }
}

/** Image or video thumbnail; tapping opens the full-screen viewer. */
@Composable
private fun MediaBubble(
    media: Media,
    message: Message,
    isOwn: Boolean,
    onMediaClick: (Media) -> Unit
) {
    val shape = if (isOwn) Skin.BubbleOut else Skin.BubbleIn
    Box(modifier = Modifier.widthIn(max = 260.dp).clip(shape).clickable { onMediaClick(media) }) {
        AsyncImage(
            model = media.secureUrl.ifBlank { media.thumbnailUrl },
            contentDescription = media.originalFilename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
        if (message.type == MessageType.VIDEO) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.createdAt.formatClockTime(),
                style = SkinType.BubbleMetaIn,
                color = Color.White
            )
        }
    }
}

/** Tick / clock / error glyph in the bubble's meta row. */
@Composable
private fun StatusGlyph(status: MessageStatus, tint: Color, onRetry: () -> Unit) {
    when (status) {
        MessageStatus.PENDING, MessageStatus.SENDING -> Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = "Sending",
            tint = tint,
            modifier = Modifier.size(11.dp)
        )

        MessageStatus.SENT -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Sent",
            tint = tint,
            modifier = Modifier.size(11.dp)
        )

        MessageStatus.DELIVERED, MessageStatus.READ -> Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = if (status == MessageStatus.READ) "Read" else "Delivered",
            tint = tint,
            modifier = Modifier.size(12.dp)
        )

        MessageStatus.FAILED -> Box(
            modifier = Modifier.size(14.dp).clickable { onRetry() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Retry sending",
                tint = Skin.OnSignal,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/** Upload progress for the attachment in flight, with cancel and retry. */
@Composable
private fun AttachmentBanner(
    fileName: String,
    progress: Int,
    failed: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Skin.SurfaceCard)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (failed) Skin.Error else Skin.Signal,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = fileName,
                style = SkinType.FileMeta,
                color = Skin.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (failed) {
                Box(
                    modifier = Modifier.size(24.dp).clickable { onRetry() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry upload",
                        tint = Skin.Signal,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.size(24.dp).clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss upload",
                    tint = Skin.InkDim,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (failed) {
            Text(text = error ?: "Upload failed", style = SkinType.FileMeta, color = Skin.Error)
        } else {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                color = Skin.Signal,
                trackColor = Skin.Line,
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
        }
    }
}

/**
 * Composer: rounded field with attach, input and mic, plus the lime send button.
 * The plus opens the photo/document chooser.
 */
@Composable
private fun Composer(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttachPhoto: () -> Unit,
    onAttachDocument: () -> Unit
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Skin.Field)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier.size(28.dp).clickable { showAttachMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = Skin.InkDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Photo or video") },
                        onClick = {
                            showAttachMenu = false
                            onAttachPhoto()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Document") },
                        onClick = {
                            showAttachMenu = false
                            onAttachDocument()
                        }
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = SkinType.ComposePlaceholder,
                        color = Skin.InkGhost,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = SkinType.Body.copy(color = Skin.Ink),
                    cursorBrush = SolidColor(Skin.Signal),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (text.isNotBlank()) onSend(text) }
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Record voice message",
                tint = Skin.InkFaint,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        SignalIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            size = 40,
            onClick = { if (text.isNotBlank()) onSend(text) }
        )
    }
}

@Composable
private fun GroupMenu(
    onDismiss: () -> Unit,
    onAddMembers: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit
) {
    Box {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(text = { Text("Add members") }, onClick = onAddMembers)
            DropdownMenuItem(text = { Text("Rename group") }, onClick = onRename)
            DropdownMenuItem(text = { Text("Leave group") }, onClick = onLeave)
        }
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Skin.SurfaceCard,
        titleContentColor = Skin.Ink,
        title = { Text("Rename group", style = SkinType.Title) },
        text = {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = SkinType.Body.copy(color = Skin.Ink),
                cursorBrush = SolidColor(Skin.Signal),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Skin.Field)
                    .padding(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) {
                Text("Rename", color = Skin.Signal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Skin.InkDim) }
        }
    )
}

private fun formatDurationSeconds(seconds: Double): String {
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Copies a picked `content://` Uri into app cache and starts the send. The upload
 * streams from a file path and the picker grant is temporary, so bytes are staged.
 */
private suspend fun stageAttachment(
    context: Context,
    uri: Uri,
    viewModel: ChatViewModel
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(context, uri) ?: "attachment_${System.currentTimeMillis()}"

        val directory = File(context.cacheDir, "outgoing").apply { mkdirs() }
        val target = File(directory, sanitizeFileName(displayName))

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext "Couldn't read that file."

        if (target.length() <= 0L) {
            target.delete()
            return@withContext "That file is empty."
        }

        viewModel.sendMediaMessage(
            filePath = target.absolutePath,
            mimeType = mimeType,
            fileName = displayName,
            fileSize = target.length()
        )
        null
    } catch (e: Exception) {
        e.message ?: "Couldn't attach that file."
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }

/** A display name is remote content, not a safe filename. */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_').takeIf { it.isNotBlank() } ?: "attachment"
