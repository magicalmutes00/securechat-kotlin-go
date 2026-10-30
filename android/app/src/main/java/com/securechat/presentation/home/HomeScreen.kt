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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.core.utils.UserSession
import com.securechat.core.utils.formatListTimestamp
import com.securechat.domain.model.Conversation
import com.securechat.domain.model.ConversationType
import com.securechat.domain.model.MessageType
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.components.EmptyState
import com.securechat.presentation.components.FilterChip
import com.securechat.presentation.components.HomeTab
import com.securechat.presentation.components.HomeTabBar
import com.securechat.presentation.components.SearchField
import com.securechat.presentation.components.SignalIconButton
import com.securechat.presentation.theme.Skin
import com.securechat.presentation.theme.SkinType
import com.securechat.presentation.theme.Theme

/** Inbox filters from the design's chip row. */
private enum class InboxFilter { ALL, UNREAD, GROUPS }

@Composable
fun HomeScreen(
    @Suppress("UNUSED_PARAMETER") onLogout: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onSettings: () -> Unit,
    onConversationClick: (conversationId: Long, otherUserName: String) -> Unit
) {
    val viewModel = androidx.hilt.navigation.compose.hiltViewModel<HomeViewModel>()
    val currentUserId = UserSession.currentUserId

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadConversations() }

    val conversations = viewModel.conversations.value
    var filter by remember { mutableStateOf(InboxFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var composeMenu by remember { mutableStateOf(false) }

    val unreadTotal = conversations.sumOf { it.unreadCount }

    // Filtering happens here rather than in the ViewModel: the list is already in
    // memory, and a search keystroke should not trigger a refetch.
    val visible = conversations.filter { conversation ->
        val matchesFilter = when (filter) {
            InboxFilter.ALL -> true
            InboxFilter.UNREAD -> conversation.unreadCount > 0
            InboxFilter.GROUPS -> conversation.type == ConversationType.GROUP
        }
        val matchesQuery = query.isBlank() ||
            conversation.getDisplayName(currentUserId).contains(query, ignoreCase = true) ||
            conversation.lastMessage?.text?.contains(query, ignoreCase = true) == true
        matchesFilter && matchesQuery
    }

    Column(modifier = Modifier.fillMaxSize().background(Skin.Void)) {
        // Title row: "Messages" with the lime compose action.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Messages", style = SkinType.ScreenTitle, color = Skin.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildSubline(unreadTotal, conversations.size),
                    style = SkinType.Subline,
                    color = Skin.InkDim
                )
            }

            Box {
                SignalIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "New conversation",
                    size = 38,
                    onClick = { composeMenu = true }
                )
                DropdownMenu(
                    expanded = composeMenu,
                    onDismissRequest = { composeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New chat") },
                        leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
                        onClick = {
                            composeMenu = false
                            onNewChat()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New group") },
                        leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                        onClick = {
                            composeMenu = false
                            onNewGroup()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search people and messages",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip("All", filter == InboxFilter.ALL) { filter = InboxFilter.ALL }
            FilterChip(
                label = if (unreadTotal > 0) "Unread $unreadTotal" else "Unread",
                active = filter == InboxFilter.UNREAD
            ) { filter = InboxFilter.UNREAD }
            FilterChip("Groups", filter == InboxFilter.GROUPS) { filter = InboxFilter.GROUPS }
        }

        Spacer(Modifier.height(8.dp))

        viewModel.errorMessage.value?.let { error ->
            Text(
                text = error,
                style = SkinType.Subline,
                color = Skin.Error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        when {
            viewModel.isLoading.value && conversations.isEmpty() -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Skin.Signal, strokeWidth = 2.dp)
                }
            }

            visible.isEmpty() -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.ChatBubbleOutline,
                        title = if (conversations.isEmpty()) "No conversations yet" else "Nothing here",
                        subtitle = if (conversations.isEmpty()) {
                            "Start a chat and it will show up here."
                        } else {
                            "No conversation matches this filter."
                        },
                        actionText = if (conversations.isEmpty()) "New chat" else null,
                        onAction = onNewChat
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(visible, key = { it.id }) { conversation ->
                        ConversationRow(
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

        HomeTabBar(
            active = HomeTab.CHATS,
            hasUnread = unreadTotal > 0,
            onSelect = { tab -> if (tab == HomeTab.SETTINGS) onSettings() }
        )
    }
}

private fun buildSubline(unread: Int, threads: Int): String {
    val threadsText = if (threads == 1) "1 secure thread" else "$threads secure threads"
    return if (unread > 0) "$unread unread  ·  $threadsText" else threadsText
}

/**
 * One inbox row: avatar, name, snippet, time and unread badge.
 *
 * The row is a plain [Row] rather than a Material [androidx.compose.material3.ListItem]
 * because the design fixes its height (72dp) and its own type ramp; ListItem's
 * internal paddings and type styles fought both.
 */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    currentUserId: Long,
    onClick: () -> Unit
) {
    val title = conversation.getDisplayName(currentUserId)
    val isGroup = conversation.type == ConversationType.GROUP
    val unread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .background(if (unread) Skin.RowSelected else Skin.Void)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            url = conversation.getAvatarUrl(currentUserId),
            name = title,
            size = 46,
            initialsSize = 12
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = SkinType.RowName,
                color = if (unread) Skin.Ink else Skin.InkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snippetFor(conversation, currentUserId),
                style = SkinType.RowSnippet,
                color = Skin.InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = conversation.updatedAt.formatListTimestamp(),
                style = SkinType.RowTime,
                color = Skin.InkFaint
            )
            Spacer(Modifier.height(6.dp))
            if (unread) {
                Badge(conversation.unreadCount)
            } else if (isGroup) {
                // Groups stay identifiable without an unread count.
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = Skin.Line,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** Lime pill with the unread count knocked out in ink, as designed. */
@Composable
private fun Badge(count: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(Skin.Signal, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.coerceAtMost(99).toString(),
            style = SkinType.Badge,
            color = Skin.OnSignal
        )
    }
}

/**
 * Row preview. A group prefixes the author so three people saying "ok" stay
 * distinguishable; attachments are described by kind rather than by filename.
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
        conversation.type != ConversationType.GROUP -> ""
        message.senderId == currentUserId -> "You: "
        !message.senderName.isNullOrBlank() -> "${message.senderName}: "
        else -> ""
    }
    return prefix + body
}
