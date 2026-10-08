package com.securechat.presentation.chat

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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.core.utils.formatTimestamp
import com.securechat.core.utils.UserSession
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.model.MessageType
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.theme.Theme

@Composable
fun ChatScreen(
    conversationId: Long,
    otherUserName: String,
    otherUserAvatar: String?,
    onBack: () -> Unit
) {
    val viewModel: ChatViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    var messageText by remember { mutableStateOf("") }

    // Load history and mark the conversation read once per conversation entry.
    androidx.compose.runtime.LaunchedEffect(conversationId) {
        viewModel.initialize(conversationId, UserSession.currentUserId)
    }

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
                    text = otherUserName,
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = "Online", // Would come from presence
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Avatar(url = otherUserAvatar, name = otherUserName, modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(40.dp))
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
                MessageBubble(
                    message = message,
                    isOwn = message.senderId == viewModel.currentUserId,
                    onRetry = { viewModel.retryMessage(message.id) },
                    onDelete = { viewModel.deleteMessage(message.id) }
                )
            }
        }

        // Message Input
        MessageInput(
            text = messageText,
            onTextChange = { messageText = it },
            onSend = {
                viewModel.sendTextMessage(it)
                messageText = ""
            },
            onAttach = { /* Show attachment picker */ }
        )
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    onRetry: () -> Unit,
    onDelete: () -> Unit
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
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
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
                                    .background(Color.Gray)
                            ) {
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
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play audio",
                                    tint = textColor
                                )
                                Spacer(modifier = Modifier.padding(8.dp))
                                Text(
                                    text = "Audio message",
                                    color = textColor
                                )
                            }
                        }
                    }
                    MessageType.DOCUMENT -> {
                        message.media?.let { media ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
    onAttach: () -> Unit
) {
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
            IconButton(onClick = onAttach) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach"
                )
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