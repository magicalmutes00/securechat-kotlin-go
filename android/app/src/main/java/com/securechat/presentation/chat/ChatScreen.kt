package com.securechat.presentation.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.securechat.core.utils.UserSession
import com.securechat.core.utils.formatClockTime
import com.securechat.core.utils.formatFileSize
import com.securechat.domain.model.Media
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.presentation.components.AppleActionSheet
import com.securechat.presentation.components.AppleAlertDialog
import com.securechat.presentation.components.AppleAvatar
import com.securechat.presentation.components.AppleDateSeparator
import com.securechat.presentation.components.AppleFormField
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleProgressBar
import com.securechat.presentation.components.AppleSheetAction
import com.securechat.presentation.components.AppleTypingIndicator
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType
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
    var showAttachMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
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
    // reverseLayout draws the last item at the top, so the list is reversed too.
    val display = remember(messages) { messages.reversed() }

    Box(modifier = Modifier.fillMaxSize().background(AppleColor.SystemBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(AppleColor.Separator))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = AppleSpace.Md,
                    end = AppleSpace.Md,
                    top = AppleSpace.Sm,
                    bottom = AppleSpace.Sm
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(display, key = { _, message -> message.id }) { index, message ->
                    val newer = display.getOrNull(index - 1)
                    MessageRow(
                        message = message,
                        isOwn = message.senderId == viewModel.currentUserId,
                        showAuthor = isGroup && message.senderId != viewModel.currentUserId,
                        tail = newer?.senderId != message.senderId,
                        onRetry = { viewModel.retryMessage(message.id) },
                        onMediaClick = { media ->
                            onOpenMedia(media.id, message.type == MessageType.DOCUMENT)
                        }
                    )
                }

                if (viewModel.isAnyoneTyping) {
                    item(key = "typing") {
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(AppleColor.BubbleIncoming)
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                            ) {
                                AppleTypingIndicator()
                            }
                        }
                    }
                }

                if (messages.isNotEmpty()) {
                    item(key = "date") {
                        AppleDateSeparator("Today")
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
                AppleInlineError(
                    message = error,
                    modifier = Modifier.padding(horizontal = AppleSpace.Md, vertical = AppleSpace.Xxs)
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
                onAttachClick = { showAttachMenu = true }
            )
        }

        AppleActionSheet(
            visible = showAttachMenu,
            onDismiss = { showAttachMenu = false },
            actions = listOf(
                AppleSheetAction(
                    label = "Photo or Video",
                    onClick = {
                        attachError = null
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ),
                AppleSheetAction(
                    label = "Document",
                    onClick = {
                        attachError = null
                        pickDocument.launch(arrayOf("*/*"))
                    }
                )
            )
        )
    }

    AppleActionSheet(
        visible = showGroupMenu,
        onDismiss = { showGroupMenu = false },
        title = "Group Options",
        actions = listOf(
            AppleSheetAction("Add Members", onClick = onAddMembers),
            AppleSheetAction("Rename Group", onClick = { showRenameDialog = true }),
            AppleSheetAction("Leave Group", destructive = true, onClick = { showLeaveDialog = true })
        )
    )

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
        AppleAlertDialog(
            title = "Leave Group",
            message = "You will no longer receive messages from this group.",
            confirmLabel = "Leave",
            dismissLabel = "Cancel",
            destructive = true,
            onConfirm = {
                showLeaveDialog = false
                viewModel.leaveGroup(onConversationLeft)
            },
            onDismiss = { showLeaveDialog = false }
        )
    }
}

/** iOS Messages header: back chevron, avatar + name, and group options. */
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
            .padding(horizontal = AppleSpace.Xxs, vertical = AppleSpace.Xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = AppleColor.SystemBlue,
                modifier = Modifier.size(24.dp)
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleAvatar(url = avatarUrl, name = title, size = 30)
            Spacer(Modifier.width(AppleSpace.Sm))
            Column {
                Text(
                    text = title,
                    style = AppleType.ListItemTitle,
                    color = AppleColor.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText.ifBlank { "Offline" },
                        style = AppleType.Caption1,
                        color = if (online || typing) AppleColor.SystemGreen else AppleColor.Label3,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(AppleSpace.Xxs))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AppleColor.Label4,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "Encrypted",
                        style = AppleType.Caption1,
                        color = AppleColor.Label4
                    )
                }
            }
        }

        if (showMenu) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Group options",
                    tint = AppleColor.SystemBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Spacer(Modifier.width(44.dp))
        }
    }
}

/** A message, with the author's avatar and name when it is a group run. */
@Composable
private fun MessageRow(
    message: Message,
    isOwn: Boolean,
    showAuthor: Boolean,
    tail: Boolean,
    onRetry: () -> Unit,
    onMediaClick: (Media) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn && showAuthor) {
            AppleAvatar(url = null, name = message.senderName ?: "?", size = 26)
            Spacer(Modifier.width(AppleSpace.Sm))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
        ) {
            if (showAuthor && !message.senderName.isNullOrBlank()) {
                Text(
                    text = message.senderName.orEmpty(),
                    style = AppleType.Footnote,
                    color = AppleColor.Label3,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            when (message.type) {
                MessageType.DOCUMENT -> message.media?.let {
                    FileBubble(it, message, isOwn, tail, onMediaClick)
                } ?: TextBubble(message, isOwn, tail, onRetry)

                MessageType.AUDIO -> VoiceBubble(message, isOwn, tail, onMediaClick)

                MessageType.IMAGE, MessageType.VIDEO -> message.media?.let {
                    MediaBubble(it, message, isOwn, tail, onMediaClick)
                } ?: TextBubble(message, isOwn, tail, onRetry)

                MessageType.TEXT -> TextBubble(message, isOwn, tail, onRetry)
            }
        }
    }
}

/** The last bubble in a run squares the corner on its own side, iMessage-style. */
private fun bubbleShape(isOwn: Boolean, tail: Boolean): RoundedCornerShape =
    if (!tail) {
        RoundedCornerShape(18.dp)
    } else if (isOwn) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 4.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    }

/** Plain text bubble: blue outgoing, grey incoming, tail corner squared. */
@Composable
private fun TextBubble(message: Message, isOwn: Boolean, tail: Boolean, onRetry: () -> Unit) {
    val textColor = if (isOwn) Color.White else AppleColor.Label
    val metaColor = if (isOwn) Color.White.copy(alpha = 0.75f) else AppleColor.Label3

    Column(
        modifier = Modifier
            .clip(bubbleShape(isOwn, tail))
            .background(if (isOwn) AppleColor.BubbleOutgoing else AppleColor.BubbleIncoming)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        message.text?.let {
            Text(text = it, style = AppleType.Bubble, color = textColor)
        }
        Spacer(Modifier.height(3.dp))
        Row(
            // End-aligned, not full-width, so a short message hugs its text
            // instead of rendering as a wide slab.
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOwn) {
                StatusGlyph(message.status, metaColor, onRetry)
                Spacer(Modifier.width(AppleSpace.Xxs))
            } else {
                // Receipt labels differ: "Delivered" is the recipient's device
                // accepting it, "Read" is them opening the chat.
                val receipt = when (message.status) {
                    MessageStatus.DELIVERED -> "Delivered"
                    MessageStatus.READ -> "Read"
                    else -> null
                }
                receipt?.let {
                    Text(it, style = AppleType.BubbleMeta, color = metaColor)
                    Spacer(Modifier.width(AppleSpace.Xs))
                }
            }
            Text(
                text = message.createdAt.formatClockTime(),
                style = AppleType.BubbleMeta,
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
    tail: Boolean,
    onMediaClick: (Media) -> Unit
) {
    val textColor = if (isOwn) Color.White else AppleColor.Label
    val metaColor = if (isOwn) Color.White.copy(alpha = 0.75f) else AppleColor.Label3

    Column(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .clip(bubbleShape(isOwn, tail))
            .background(if (isOwn) AppleColor.BubbleOutgoing else AppleColor.BubbleIncoming)
            .clickable { onMediaClick(media) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = AppleColor.BubbleOutgoing,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.originalFilename,
                    style = AppleType.Subhead,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${media.mimeType.substringAfterLast('/').uppercase()} · ${formatFileSize(media.fileSize)}",
                    style = AppleType.Caption1,
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
                Spacer(Modifier.width(AppleSpace.Xxs))
            }
            Text(
                text = message.createdAt.formatClockTime(),
                style = AppleType.BubbleMeta,
                color = metaColor
            )
        }
    }
}

/** Voice note: play disc, waveform, duration. */
@Composable
private fun VoiceBubble(
    message: Message,
    isOwn: Boolean,
    tail: Boolean,
    onMediaClick: (Media) -> Unit
) {
    val media = message.media
    val metaColor = if (isOwn) Color.White.copy(alpha = 0.75f) else AppleColor.Label3
    val barColor = if (isOwn) Color.White else AppleColor.Label2

    // Fixed waveform: the message stores a duration but not samples, so any
    // amplitude shape here would be invented. This reads as "audio", not as data.
    val bars = listOf(8, 14, 20, 12, 22, 16, 9, 18, 12, 20, 10, 15)

    Row(
        modifier = Modifier
            .widthIn(min = 246.dp)
            .clip(bubbleShape(isOwn, tail))
            .background(if (isOwn) AppleColor.BubbleOutgoing else AppleColor.BubbleIncoming)
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
                tint = AppleColor.BubbleOutgoing,
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
            style = AppleType.BubbleMeta,
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
    tail: Boolean,
    onMediaClick: (Media) -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(bubbleShape(isOwn, tail))
            .clickable { onMediaClick(media) }
    ) {
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
                style = AppleType.BubbleMeta,
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
                tint = AppleColor.SystemYellow,
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
            .padding(horizontal = AppleSpace.Md, vertical = AppleSpace.Xxs)
            .clip(RoundedCornerShape(12.dp))
            .background(AppleColor.SystemGroupedCell)
            .padding(AppleSpace.Md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (failed) AppleColor.SystemRed else AppleColor.SystemBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(AppleSpace.Sm))
            Text(
                text = fileName,
                style = AppleType.Subhead,
                color = AppleColor.Label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (failed) {
                Box(
                    modifier = Modifier.size(28.dp).clickable { onRetry() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry upload",
                        tint = AppleColor.SystemBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.size(28.dp).clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss upload",
                    tint = AppleColor.Label3,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (failed) {
            Text(
                text = error ?: "Upload failed",
                style = AppleType.Caption1,
                color = AppleColor.SystemRed,
                modifier = Modifier.padding(top = AppleSpace.Xxs)
            )
        } else {
            Spacer(Modifier.height(AppleSpace.Sm))
            AppleProgressBar(progress = progress / 100f)
        }
    }
}

/** Composer: attach disc, rounded message field, and the blue send arrow. */
@Composable
private fun Composer(
    text: String,
    placeholder: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttachClick: () -> Unit
) {
    val canSend = text.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppleSpace.Md, vertical = AppleSpace.Sm),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AppleColor.SystemSecondaryBackground)
                .clickable(onClick = onAttachClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Attach",
                tint = AppleColor.Label2,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(AppleSpace.Sm))

        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppleColor.SystemSecondaryBackground)
                .border(0.5.dp, AppleColor.SeparatorOpaque, RoundedCornerShape(18.dp))
                .padding(horizontal = AppleSpace.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = AppleType.Body2,
                        color = AppleColor.Label4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = AppleType.Body2.copy(color = AppleColor.Label),
                    cursorBrush = SolidColor(AppleColor.SystemBlue),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend(text) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.width(AppleSpace.Sm))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (canSend) AppleColor.SystemBlue else Color.Transparent)
                .clickable(enabled = canSend) { onSend(text) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) Color.White else AppleColor.Label4,
                modifier = Modifier.size(18.dp)
            )
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

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(270.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppleColor.SystemSheetBackground)
        ) {
            Text(
                text = "Rename Group",
                style = AppleType.ListItemTitle,
                color = AppleColor.Label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppleSpace.Lg, end = AppleSpace.Lg, top = AppleSpace.Xl)
            )

            AppleFormField(
                value = value,
                onValueChange = { value = it },
                placeholder = "Group Name",
                modifier = Modifier.padding(AppleSpace.Lg)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppleColor.Separator)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Cancel", style = AppleType.Body, color = AppleColor.SystemBlue)
                }
                Box(
                    modifier = Modifier.width(0.5.dp).height(44.dp).background(AppleColor.Separator)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable(enabled = value.isNotBlank()) { onConfirm(value.trim()) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Rename",
                        style = AppleType.Body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (value.isNotBlank()) AppleColor.SystemBlue else AppleColor.Label4
                    )
                }
            }
        }
    }
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
