package com.securechat.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.core.utils.formatTimestamp
import com.securechat.domain.model.Conversation
import com.securechat.domain.model.ConversationParticipant
import com.securechat.domain.model.ConversationType
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.components.DateSeparator
import com.securechat.presentation.components.EmptyState
import com.securechat.presentation.theme.Theme

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onConversationClick: (conversationId: Long, otherUserName: String) -> Unit
) {
    val viewModel = androidx.hilt.navigation.compose.hiltViewModel<HomeViewModel>()
    val currentUserId = com.securechat.core.utils.UserSession.currentUserId

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadConversations()
    }

    val conversations = viewModel.conversations.value

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Top App Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SecureChat",
                    fontSize = 20.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onNewChat) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Chat,
                            contentDescription = "New Chat"
                        )
                    }

                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            }

            Divider()

            viewModel.errorMessage.value?.let { error ->
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Conversations List
            if (viewModel.isLoading.value) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            } else if (conversations.isEmpty()) {
                EmptyState(
                    icon = androidx.compose.material.icons.Icons.Default.ChatBubbleOutline,
                    title = "No conversations yet",
                    subtitle = "Start a new chat to begin messaging securely",
                    actionText = "New Chat",
                    onAction = onNewChat
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(conversations) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            currentUserId = currentUserId,
                            onClick = {
                                onConversationClick(
                                    conversation.id,
                                    conversation.getDisplayName(currentUserId)
                                )
                            }
                        )
                        Divider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    currentUserId: Long,
    onClick: () -> Unit
) {
    val otherParticipant = conversation.getOtherParticipant(currentUserId)
    
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        leadingContent = {
                Avatar(
                    url = conversation.getAvatarUrl(currentUserId),
                    name = otherParticipant?.user?.displayName ?: "Unknown",
                    size = 56
                )
        },
        headlineContent = {
            Text(
                text = otherParticipant?.user?.displayName ?: "Unknown",
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        },
        supportingContent = {
            // Last message preview
            conversation.lastMessage?.let { msg ->
                Text(
                    text = when (msg.type) {
                        com.securechat.domain.model.MessageType.TEXT -> msg.text ?: ""
                        com.securechat.domain.model.MessageType.IMAGE -> "📷 Image"
                        com.securechat.domain.model.MessageType.VIDEO -> "🎥 Video"
                        com.securechat.domain.model.MessageType.AUDIO -> "🎵 Audio"
                        com.securechat.domain.model.MessageType.DOCUMENT -> "📄 Document"
                    },
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } ?: Text(
                text = "No messages yet",
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = conversation.updatedAt.formatTimestamp(),
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.padding(4.dp))
                    Box(
                        modifier = Modifier
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(6.dp)
                            .widthIn(min = 24.dp)
                    ) {
                        androidx.compose.material3.Text(
                            text = conversation.unreadCount.toString(),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    )
}