package com.securechat.presentation.contacts

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
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.domain.model.User
import com.securechat.presentation.components.AppleAvatar
import com.securechat.presentation.components.AppleEmptyState
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleProgress
import com.securechat.presentation.components.AppleSearchField
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onContactClick: (userId: Long, name: String) -> Unit
) {
    val viewModel = hiltViewModel<ContactsViewModel>()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemBackground)
    ) {
        AppleTopBar(title = "New Chat", onBack = onBack)

        AppleSearchField(
            value = viewModel.query.value,
            onValueChange = { viewModel.onQueryChanged(it) },
            placeholder = "Name, username or phone",
            modifier = Modifier.padding(horizontal = AppleSpace.Lg)
        )

        Spacer(modifier = Modifier.height(AppleSpace.Sm))

        when {
            viewModel.isLoading.value -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppleProgress(size = 22)
                }
            }

            viewModel.errorMessage.value != null -> {
                AppleInlineError(
                    message = viewModel.errorMessage.value.orEmpty(),
                    modifier = Modifier.padding(horizontal = AppleSpace.Lg)
                )
            }

            viewModel.results.value.isEmpty() -> {
                AppleEmptyState(
                    icon = Icons.Filled.PersonSearch,
                    title = if (viewModel.query.value.isBlank()) "Find People" else "No Results",
                    message = if (viewModel.query.value.isBlank()) {
                        "Search by name, username or phone number to start a conversation."
                    } else {
                        "No one matched “${viewModel.query.value}”."
                    },
                    modifier = Modifier.padding(top = AppleSpace.Huge)
                )
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.results.value, key = { it.id }) { user ->
                        ContactItem(
                            user = user,
                            onClick = { onContactClick(user.id, user.bestName()) }
                        )
                    }
                }
            }
        }
    }
}

private fun User.bestName(): String =
    displayName.ifBlank { username?.let { "@$it" } ?: "User" }

@Composable
private fun ContactItem(
    user: User,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = AppleSpace.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleAvatar(
                url = user.avatarUrl,
                name = user.bestName(),
                size = 44
            )
            Spacer(modifier = Modifier.width(AppleSpace.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.bestName(),
                    style = AppleType.ListItemTitle,
                    color = AppleColor.Label
                )
                if (!user.username.isNullOrBlank()) {
                    Text(
                        text = "@${user.username}",
                        style = AppleType.Footnote,
                        color = AppleColor.Label3
                    )
                }
            }
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
