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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.R
import com.securechat.core.utils.formatTimestamp
import com.securechat.core.utils.UserSession
import com.securechat.domain.model.Media
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.theme.Theme
import kotlinx.coroutines.CoroutineScope
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
    /**
     * Opens an attachment full-screen. [isDocument] picks the viewer: a PDF needs
     * file actions (download/share), an image or video needs a canvas.
     */
    onOpenMedia: (mediaId: Long, isDocument: Boolean) -> Unit = { _, _ -> }
) {
    val viewModel: ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    var messageText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachError by remember { mutableStateOf<String?>(null) }

    // Photo Picker: no storage permission, and the user only ever grants access to
    // what they pick. Documents go through SAF for the same reason.
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

    // Load history and mark the conversation read once per conversation entry.
    androidx.compose.runtime.LaunchedEffect(conversationId) {
        viewModel.initialize(conversationId, UserSession.currentUserId)
    }

    // Group actions. The title updates in place after a rename because the
    // ViewModel re-reads the conversation row from Room.
    var showGroupMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            
            Column(
                modifier = Modifier.padding(start = 56.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = viewModel.conversationName.value ?: otherUserName,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    // Typing wins over presence: it is the more urgent signal, and
                    // the two would otherwise fight for the same line.
                    text = if (viewModel.isAnyoneTyping) {
                        "typing\u2026"
                    } else {
                        viewModel.presenceText.value.ifBlank {
                            if (viewModel.isGroup.value) "0 members" else ""
                        }
                    },
                    fontSize = 12.sp,
                    color = if (viewModel.isAnyoneTyping) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            Avatar(url = otherUserAvatar, name = otherUserName, modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(40.dp))

            // Group actions: rename and leave. A 1:1 chat has neither.
            if (viewModel.isGroup.value) {
                IconButton(
                    onClick = { showGroupMenu = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Group options"
                    )
                }
                DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add members") },
                        onClick = {
                            showGroupMenu = false
                            onAddMembers()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename group") },
                        onClick = {
                            showGroupMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Leave group") },
                        onClick = {
                            showGroupMenu = false
                            showLeaveDialog = true
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.padding(8.dp))

        // Message List — weight(1f) so the list takes only the space left
        // after the input row below; fillMaxSize would consume the column's
        // remaining height and push MessageInput off-screen entirely.
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.messages.value.reversed()) { message ->
                val isOwn = message.senderId == viewModel.currentUserId
                MessageBubble(
                    message = message,
                    isOwn = isOwn,
                    // Only a group attributes its messages: with two participants
                    // a name above every bubble is noise.
                    showSenderName = viewModel.isGroup.value && !isOwn,
                    onRetry = { viewModel.retryMessage(message.id) },
                    onDelete = { viewModel.deleteMessage(message.id) },
                    onMediaClick = { media ->
                        onOpenMedia(media.id, message.type == MessageType.DOCUMENT)
                    }
                )
            }
        }

        // Attachment in flight: progress, or a failure the user can act on.
        viewModel.attachmentSend.value?.let { state ->
            AttachmentSendBanner(
                state = state,
                onCancel = { viewModel.cancelAttachment() },
                onRetry = { viewModel.retryAttachment() }
            )
        }

        attachError?.let { message ->
            Text(
                text = message,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }

        // Message Input
        MessageInput(
            text = messageText,
            onTextChange = {
                messageText = it
                // Signals the other side; throttled inside the ViewModel.
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
                // "*/*" rather than a list of MIME types: a legitimately untyped
                // file would otherwise never appear in the picker.
                pickDocument.launch(arrayOf("*/*"))
            }
        )
    }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = viewModel.conversationName.value ?: otherUserName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                viewModel.renameGroup(newName)
            }
        )
    }

    if (showLeaveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave group") },
            text = { Text("You will no longer receive messages from this group.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLeaveDialog = false
                    viewModel.leaveGroup(onConversationLeft)
                }) {
                    Text("Leave", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RenameGroupDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(currentName) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename group") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Group name") }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    showSenderName: Boolean = false,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onMediaClick: (Media) -> Unit = {}
) {
    val statusColor = when (message.status) {
        MessageStatus.PENDING, MessageStatus.SENDING -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        MessageStatus.SENT -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        MessageStatus.DELIVERED -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        MessageStatus.READ -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        MessageStatus.FAILED -> androidx.compose.material3.MaterialTheme.colorScheme.error
    }

    val bubbleColor = if (isOwn) {
        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isOwn) {
        androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 0.dp, max = 280.dp)
                .padding(vertical = 4.dp),
            contentAlignment = if (isOwn) Alignment.TopEnd else Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bubbleColor, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                // Author line: group messages only, and only for other people.
                val authorName = message.senderName
                if (showSenderName && !authorName.isNullOrBlank()) {
                    Text(
                        text = authorName,
                        color = senderNameColor(message.senderId),
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // Message content based on type
                when (message.type) {
                    MessageType.TEXT -> {
                        message.text?.let { text ->
                            Text(
                                text = text,
                                color = textColor,
                                fontSize = 16.sp
                            )
                        }
                    }
                    MessageType.IMAGE -> {
                        message.media?.let { media ->
                            // The real asset. This used to draw the launcher icon
                            // as a placeholder and ignore media.secureUrl entirely,
                            // so every image looked identical.
                            AsyncImage(
                                model = media.secureUrl.ifBlank { media.thumbnailUrl },
                                contentDescription = media.originalFilename,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onMediaClick(media) },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    MessageType.VIDEO -> {
                        message.media?.let { media ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .clickable { onMediaClick(media) }
                            ) {
                                // Video frames come from the eager transformation the
                                // backend signs at upload; the full file plays in
                                // the viewer.
                                AsyncImage(
                                    model = media.thumbnailUrl ?: media.secureUrl,
                                    contentDescription = media.originalFilename,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Play video",
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.Center).size(48.dp)
                                )
                            }
                        }
                    }
                    MessageType.AUDIO -> {
                        message.media?.let { media ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onMediaClick(media) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play audio",
                                    tint = textColor
                                )
                                Spacer(modifier = Modifier.padding(8.dp))
                                Text(
                                    text = media.originalFilename.ifBlank { "Audio message" },
                                    color = textColor
                                )
                            }
                        }
                    }
                    MessageType.DOCUMENT -> {
                        message.media?.let { media ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onMediaClick(media) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = "Document",
                                    tint = textColor
                                )
                                Spacer(modifier = Modifier.padding(8.dp))
                                Column {
                                    Text(text = media.originalFilename, color = textColor)
                                    Text(text = "${(media.fileSize / 1024)} KB", color = textColor.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Timestamp and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = message.createdAt.formatTimestamp(),
                        color = textColor.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                    
                    if (isOwn) {
                        Spacer(modifier = Modifier.padding(4.dp))
                        Icon(
                            imageVector = when (message.status) {
                                MessageStatus.PENDING, MessageStatus.SENDING -> Icons.Default.AccessTime
                                MessageStatus.SENT -> Icons.Default.Check
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                MessageStatus.READ -> Icons.Default.DoneAll
                                MessageStatus.FAILED -> Icons.Default.Error
                            },
                            contentDescription = "Message status",
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        if (message.status == MessageStatus.FAILED) {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onAttachPhoto: () -> Unit,
    onAttachDocument: () -> Unit
) {
    // The paperclip opens a chooser rather than a single picker: a photo goes
    // through the Photo Picker, a file through SAF, and they are different
    // contracts with different results.
    var showAttachMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = { showAttachMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach"
                    )
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Photo or video") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            onAttachPhoto()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Document") },
                        leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
                        onClick = {
                            showAttachMenu = false
                            onAttachDocument()
                        }
                    )
                }
            }
            
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = { Text("Message") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (text.isNotBlank()) onSend(text) }
                )
            )
            
            if (text.isNotBlank()) {
                IconButton(onClick = { onSend(text) }) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * A stable colour per sender id, so each member of a group is visually distinct
 * the way WhatsApp attributes messages. The palette is fixed rather than derived
 * from the theme so a given member keeps their colour across sessions.
 */
private fun senderNameColor(senderId: Long): androidx.compose.ui.graphics.Color {
    val palette = arrayOf(
        0xFF0B8043, // green
        0xFF1A73E8, // blue
        0xFF8E24AA, // purple
        0xFFD93025, // red
        0xFFE8710A, // orange
        0xFF007B83, // teal
        0xFFC2185B, // pink
        0xFF795548  // brown
    )
    // Spread the index: consecutive ids then map to adjacent, contrasting hues
    // rather than all landing on the first entry.
    return androidx.compose.ui.graphics.Color(palette[(senderId * 31 and 0x7FFFFFFF).toInt() % palette.size])
}

/**
 * Shows the attachment being sent: progress while it uploads, or the failure with
 * retry and dismiss once it has stopped.
 */
@Composable
private fun AttachmentSendBanner(
    state: AttachmentSendState,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.fileName,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            if (state.failed) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry upload",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = if (state.failed) "Dismiss" else "Cancel upload",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (state.failed) {
            Text(
                text = state.error ?: "Upload failed",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                fontSize = 11.sp
            )
        } else {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }
    }
}

/**
 * Copies a picked `content://` Uri into app cache and starts the send.
 *
 * The transfer streams from a file path — OkHttp needs a known length and a
 * seekable source — and the picker's read grant is temporary, so the bytes are
 * staged first. Runs on IO: `contentResolver` and the copy both block, and the
 * caller launches this from the composition's scope, which is main-threaded.
 *
 * @return an error message to display, or null when the send was started.
 */
private suspend fun stageAttachment(
    context: Context,
    uri: Uri,
    viewModel: ChatViewModel
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val displayName = queryDisplayName(context, uri)
            ?: "attachment_${System.currentTimeMillis()}"

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

/** The picker's user-facing filename, which is not derivable from the Uri. */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }

/**
 * Strips path separators and anything else that could escape the cache directory
 * — a display name is attacker-controlled content, not a safe filename.
 */
private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', '_')
        .takeIf { it.isNotBlank() }
        ?: "attachment"
