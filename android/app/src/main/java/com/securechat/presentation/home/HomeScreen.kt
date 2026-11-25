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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.securechat.core.utils.UserSession
import com.securechat.core.utils.formatListTimestamp
import com.securechat.domain.model.Conversation
import com.securechat.domain.model.MessageType
import com.securechat.presentation.components.AppleActionSheet
import com.securechat.presentation.components.AppleAvatar
import com.securechat.presentation.components.AppleEmptyState
import com.securechat.presentation.components.AppleIconButton
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleProgress
import com.securechat.presentation.components.AppleSearchField
import com.securechat.presentation.components.AppleSheetAction
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun HomeScreen(
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onSettings: () -> Unit,
    onConversationClick: (conversationId: Long, otherUserName: String) -> Unit
) {
    val viewModel = androidx.hilt.navigation.compose.hiltViewModel<HomeViewModel>()
    val currentUserId = UserSession.currentUserId

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadConversations() }

    val conversations = viewModel.conversations.value
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var composeMenu by rememberSaveable { mutableStateOf(false) }

    val unreadTotal = conversations.sumOf { it.unreadCount }

    val visible = conversations.filter { conversation ->
        searchQuery.isBlank() ||
            conversation.getDisplayName(currentUserId).contains(searchQuery, ignoreCase = true) ||
            conversation.lastMessage?.text?.contains(searchQuery, ignoreCase = true) == true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppleColor.SystemGroupedBackground)
        ) {
            /* iOS Messages header: account avatar, large title, compose. */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppleSpace.Md,
                        top = AppleSpace.Lg,
                        end = AppleSpace.Md
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) {
                    AppleAvatar(
                        url = null,
                        name = UserSession.displayName.ifBlank { "Me" },
                        size = 32
                    )
                }

                Spacer(Modifier.width(AppleSpace.Sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Messages",
                        style = AppleType.LargeTitle,
                        color = AppleColor.Label
                    )
                    Text(
                        text = buildSubline(unreadTotal, conversations.size),
                        style = AppleType.Footnote,
                        color = AppleColor.Label3
                    )
                }

                AppleIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "New message",
                    onClick = { composeMenu = true }
                )
            }

            Spacer(Modifier.height(AppleSpace.Md))

            AppleSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search",
                modifier = Modifier.padding(horizontal = AppleSpace.Lg)
            )

            Spacer(Modifier.height(AppleSpace.Md))

            viewModel.errorMessage.value?.let { error ->
                AppleInlineError(
                    message = error,
                    modifier = Modifier.padding(horizontal = AppleSpace.Lg)
                )
                Spacer(Modifier.height(AppleSpace.Sm))
            }

            when {
                viewModel.isLoading.value && conversations.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppleProgress(size = 22)
                    }
                }

                visible.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppleEmptyState(
                            icon = Icons.Filled.ChatBubbleOutline,
                            title = if (conversations.isEmpty()) "No conversations" else "No results",
                            message = if (conversations.isEmpty()) {
                                "Start a conversation and it will appear here."
                            } else {
                                "Try adjusting your search."
                            },
                            actionLabel = if (conversations.isEmpty()) "New Conversation" else null,
                            onAction = if (conversations.isEmpty()) onNewChat else null
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = AppleSpace.Md,
                            vertical = AppleSpace.Xxs
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppleSpace.Xxs)
                    ) {
                        items(visible, key = { it.id }) { conversation ->
                            AppleConversationRow(
                                conversation = conversation,
                                currentUserId = currentUserId,
                                onClick = {
                                    onConversationClick(
                                        conversation.id,
                                        conversation.getDisplayName(currentUserId)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        AppleActionSheet(
            visible = composeMenu,
            onDismiss = { composeMenu = false },
            title = "New Message",
            actions = listOf(
                AppleSheetAction(
                    label = "New Conversation",
                    onClick = onNewChat
                ),
                AppleSheetAction(
                    label = "New Group",
                    onClick = onNewGroup
                )
            )
        )
    }
}

private fun buildSubline(unread: Int, threads: Int): String {
    val threadsText = if (threads == 1) "1 conversation" else "$threads conversations"
    return if (unread > 0) "$unread unread  •  $threadsText" else threadsText
}

/**
 * iOS Messages conversation row: avatar, name and timestamp, two-line snippet,
 * blue unread dot on the right, hairline separator inset to the text column.
 */
@Composable
private fun AppleConversationRow(
    conversation: Conversation,
    currentUserId: Long,
    onClick: () -> Unit
) {
    val title = conversation.getDisplayName(currentUserId)
    val unread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppleAvatar(
            url = conversation.getAvatarUrl(currentUserId),
            name = title,
            size = 44
        )
        Spacer(Modifier.width(AppleSpace.Sm))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = AppleType.ListItemTitle,
                    color = AppleColor.Label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = conversation.updatedAt.formatListTimestamp(),
                    style = AppleType.Caption2,
                    color = AppleColor.Label3,
                    maxLines = 1,
                    modifier = Modifier.padding(start = AppleSpace.Sm)
                )
            }
            Spacer(Modifier.height(AppleSpace.Xxs))
            Text(
                text = snippetFor(conversation, currentUserId),
                style = AppleType.ListItemSubtitle,
                color = AppleColor.Label2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 1.2.em
            )
        }

        if (unread) {
            Spacer(Modifier.width(AppleSpace.Sm))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(AppleColor.SystemBlue, CircleShape)
            )
        }
    }
}

/**
 * Row preview: group prefix by author, attachment by kind.
 */
private fun snippetFor(conversation: Conversation, currentUserId: Long): String {
    val message = conversation.lastMessage ?: return "No messages yet"
    val body = when (message.type) {
        MessageType.TEXT -> message.text.orEmpty()
        MessageType.IMAGE -> "Photo"
        MessageType.VIDEO -> "Video"
        MessageType.AUDIO -> "Voice message"
        MessageType.DOCUMENT -> message.media?.originalFilename ?: "Document"
    }
    val prefix = when {
        conversation.type != com.securechat.domain.model.ConversationType.GROUP -> ""
        message.senderId == currentUserId -> "You: "
        !message.senderName.isNullOrBlank() -> "${message.senderName}: "
        else -> ""
    }
    return prefix + body
}
