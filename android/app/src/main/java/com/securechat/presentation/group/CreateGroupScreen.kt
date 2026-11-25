package com.securechat.presentation.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.domain.model.User
import com.securechat.presentation.components.AppleAvatar
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleEmptyState
import com.securechat.presentation.components.AppleFormField
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleProgress
import com.securechat.presentation.components.AppleSearchField
import com.securechat.presentation.components.AppleSelectionMark
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

/**
 * New group: search for people, select the members, name it, create.
 *
 * Deliberately mirrors the contacts screen's search behaviour (server-side,
 * debounced) so the two flows feel the same.
 */
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onGroupCreated: (conversationId: Long) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val query = viewModel.query.value
    val results = viewModel.results.value
    val selectedIds = viewModel.selectedIds.value
    val groupName = viewModel.groupName.value
    val isLoading = viewModel.isLoading.value
    val isCreating = viewModel.isCreating.value
    val errorMessage = viewModel.errorMessage.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemBackground)
    ) {
        AppleTopBar(title = "New Group", onBack = onBack)

        AppleFormField(
            value = groupName,
            onValueChange = viewModel::onGroupNameChanged,
            placeholder = "Group Name",
            modifier = Modifier.padding(horizontal = AppleSpace.Lg)
        )

        Spacer(modifier = Modifier.height(AppleSpace.Sm))

        AppleSearchField(
            value = query,
            onValueChange = { viewModel.onQueryChanged(it) },
            placeholder = "Search people to add",
            modifier = Modifier.padding(horizontal = AppleSpace.Lg)
        )

        if (selectedIds.isNotEmpty()) {
            Text(
                text = "${selectedIds.size} Selected",
                style = AppleType.Footnote,
                color = AppleColor.SystemBlue,
                modifier = Modifier.padding(start = AppleSpace.Lg, top = AppleSpace.Sm)
            )
        }

        errorMessage?.let { message ->
            AppleInlineError(
                message = message,
                modifier = Modifier.padding(horizontal = AppleSpace.Lg, vertical = AppleSpace.Sm)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppleProgress(size = 22)
                }

                results.isEmpty() -> {
                    AppleEmptyState(
                        icon = Icons.Filled.PersonAdd,
                        title = if (query.isBlank()) "Add People" else "No Results",
                        message = if (query.isBlank()) {
                            "Search by name, username or phone number to add members."
                        } else {
                            "No one matched “$query”."
                        },
                        modifier = Modifier.padding(top = AppleSpace.Huge)
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { user ->
                        SelectablePersonItem(
                            user = user,
                            selected = user.id in selectedIds,
                            subtitle = null,
                            enabled = true,
                            onClick = { viewModel.toggleMember(user.id) }
                        )
                    }
                }
            }
        }

        AppleButton(
            text = if (isCreating) "Creating…" else "Create Group",
            enabled = !isCreating && groupName.isNotBlank() && selectedIds.isNotEmpty(),
            onClick = { viewModel.createGroup(onGroupCreated) },
            modifier = Modifier.padding(AppleSpace.Lg)
        )
    }
}

/** A person row with a multi-select mark; reused by the add-members flow. */
@Composable
internal fun SelectablePersonItem(
    user: User,
    selected: Boolean,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val name = user.displayName.ifBlank { user.username ?: "User" }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = AppleSpace.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleAvatar(url = user.avatarUrl, name = name, size = 44)
            Spacer(modifier = Modifier.width(AppleSpace.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppleType.ListItemTitle,
                    color = if (enabled) AppleColor.Label else AppleColor.Label3,
                    maxLines = 1
                )
                val detail = subtitle ?: user.username?.let { "@$it" }
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = AppleType.Footnote,
                        color = AppleColor.Label3,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(AppleSpace.Sm))
            AppleSelectionMark(selected = selected)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 72.dp)
                .height(0.5.dp)
                .background(AppleColor.Separator)
        )
    }
}
