package com.securechat.presentation.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleEmptyState
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleProgress
import com.securechat.presentation.components.AppleSearchField
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

/**
 * Adds people to an existing group. Members already in the group appear disabled
 * so it is obvious why selecting them does nothing.
 */
@Composable
fun AddMembersScreen(
    conversationId: Long,
    onBack: () -> Unit,
    onMembersAdded: () -> Unit,
    viewModel: AddMembersViewModel = hiltViewModel()
) {
    LaunchedEffect(conversationId) {
        viewModel.initialize(conversationId)
    }

    val query = viewModel.query.value
    val results = viewModel.results.value
    val selectedIds = viewModel.selectedIds.value
    val existingIds = viewModel.existingIds.value
    val isLoading = viewModel.isLoading.value
    val isSaving = viewModel.isSaving.value
    val errorMessage = viewModel.errorMessage.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemBackground)
    ) {
        AppleTopBar(title = "Add Members", onBack = onBack)

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

        Spacer(modifier = Modifier.height(AppleSpace.Sm))

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
                        val alreadyMember = user.id in existingIds
                        SelectablePersonItem(
                            user = user,
                            selected = user.id in selectedIds,
                            subtitle = if (alreadyMember) "Already in this group" else null,
                            enabled = !alreadyMember,
                            onClick = { viewModel.toggleMember(user.id) }
                        )
                    }
                }
            }
        }

        AppleButton(
            text = if (isSaving) "Adding…" else "Add to Group",
            enabled = !isSaving && selectedIds.isNotEmpty(),
            onClick = { viewModel.addMembers(onMembersAdded) },
            modifier = Modifier.padding(AppleSpace.Lg)
        )
    }
}
