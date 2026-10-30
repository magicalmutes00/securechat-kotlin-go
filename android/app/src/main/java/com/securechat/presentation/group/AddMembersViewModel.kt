package com.securechat.presentation.group

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.utils.UserSession
import com.securechat.domain.model.User
import com.securechat.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Adds people to an existing group.
 *
 * Reuses the create-group search so both flows feel identical. Members already in
 * the group are offered but disabled: the server skips them silently, and a
 * disabled row tells the user why nothing happened.
 */
@HiltViewModel
class AddMembersViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: com.securechat.domain.repository.ConversationRepository
) : ViewModel() {

    var query: MutableState<String> = mutableStateOf("")
        private set
    var results: MutableState<List<User>> = mutableStateOf(emptyList())
        private set
    var selectedIds: MutableState<Set<Long>> = mutableStateOf(emptySet())
        private set
    var existingIds: MutableState<Set<Long>> = mutableStateOf(emptySet())
        private set
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
        private set
    var isSaving: MutableState<Boolean> = mutableStateOf(false)
        private set
    var errorMessage: MutableState<String?> = mutableStateOf(null)
        private set

    private var conversationId: Long = 0L
    private var searchJob: Job? = null

    fun initialize(conversationId: Long) {
        if (this.conversationId == conversationId) return
        this.conversationId = conversationId

        // Seed the "already in the group" set from the cached conversation so the
        // list can disable them without a server round trip.
        viewModelScope.launch {
            conversationRepository.getConversation(conversationId).onSuccess { conversation ->
                existingIds.value = conversation.participants.map { it.userId }.toSet()
            }
        }
    }

    fun onQueryChanged(value: String) {
        query.value = value
        searchJob?.cancel()

        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            results.value = emptyList()
            isLoading.value = false
            errorMessage.value = null
            return
        }

        isLoading.value = true
        errorMessage.value = null
        searchJob = viewModelScope.launch {
            delay(300)
            val result = userRepository.searchUsers(trimmed, LIMIT)
            isLoading.value = false
            result.onSuccess { users ->
                results.value = users.filter { it.id != UserSession.currentUserId }
            }.onFailure {
                errorMessage.value = "Couldn't search right now. Try again."
            }
        }
    }

    fun toggleMember(userId: Long) {
        // Already a member: nothing to toggle, and the row is rendered disabled.
        if (userId in existingIds.value) return
        selectedIds.value = if (userId in selectedIds.value) {
            selectedIds.value - userId
        } else {
            selectedIds.value + userId
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun addMembers(onDone: () -> Unit) {
        if (isSaving.value) return
        if (selectedIds.value.isEmpty()) {
            errorMessage.value = "Select at least one person to add"
            return
        }

        isSaving.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val result = conversationRepository.addMembers(conversationId, selectedIds.value.toList())
            isSaving.value = false
            if (result.isFailure) {
                val reason = (result as? com.securechat.core.common.Result.Failure)?.exception?.message
                errorMessage.value = reason ?: "Couldn't add members. Try again."
                return@launch
            }
            onDone()
        }
    }

    companion object {
        private const val LIMIT = 25
    }
}
