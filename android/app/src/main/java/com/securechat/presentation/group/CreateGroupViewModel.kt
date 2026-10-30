package com.securechat.presentation.group

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.utils.UserSession
import com.securechat.domain.model.User
import com.securechat.domain.repository.UserRepository
import com.securechat.domain.usecase.chat.CreateGroupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "New group" screen: search for people, select two or more, name the
 * group, create it.
 *
 * Members are held as an ordered set of ids so re-selecting a row toggles it
 * rather than adding a duplicate — the server rejects a member list containing
 * the caller, and a duplicate id would otherwise fail the participants primary
 * key with an opaque error.
 */
@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val createGroupUseCase: CreateGroupUseCase
) : ViewModel() {

    var query: MutableState<String> = mutableStateOf("")
        private set
    var results: MutableState<List<User>> = mutableStateOf(emptyList())
        private set
    var selectedIds: MutableState<Set<Long>> = mutableStateOf(emptySet())
        private set
    var groupName: MutableState<String> = mutableStateOf("")
        private set
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
        private set
    var isCreating: MutableState<Boolean> = mutableStateOf(false)
        private set
    var errorMessage: MutableState<String?> = mutableStateOf(null)
        private set

    private var searchJob: Job? = null

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

    fun onGroupNameChanged(value: String) {
        groupName.value = value
    }

    fun toggleMember(userId: Long) {
        selectedIds.value = if (userId in selectedIds.value) {
            selectedIds.value - userId
        } else {
            selectedIds.value + userId
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    /** Creates the group and reports the new conversation id via [onCreated]. */
    fun createGroup(onCreated: (Long) -> Unit) {
        if (isCreating.value) return

        val name = groupName.value.trim()
        val members = selectedIds.value.toList()
        if (name.isEmpty()) {
            errorMessage.value = "Give the group a name"
            return
        }
        if (members.isEmpty()) {
            errorMessage.value = "Select at least one member"
            return
        }

        isCreating.value = true
        errorMessage.value = null
        viewModelScope.launch {
            val result = createGroupUseCase(name, members)
            isCreating.value = false
            result.onSuccess { conversation ->
                onCreated(conversation.id)
            }.onFailure { error ->
                errorMessage.value = error.message ?: "Couldn't create the group. Try again."
            }
        }
    }

    companion object {
        private const val LIMIT = 25
    }
}
