package com.securechat.presentation.contacts

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
 * Backs the "New chat" contacts screen. Search is server-side (username,
 * display name or phone); results never include the signed-in user. A short
 * debounce avoids a request per keystroke.
 */
@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    var query: MutableState<String> = mutableStateOf("")
        private set
    var results: MutableState<List<User>> = mutableStateOf(emptyList())
        private set
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
        private set
    var errorMessage: MutableState<String?> = mutableStateOf(null)
        private set

    private var searchJob: Job? = null

    fun onQueryChanged(value: String) {
        query.value = value
        searchJob?.cancel()

        val query = value.trim()
        if (query.isEmpty()) {
            results.value = emptyList()
            isLoading.value = false
            errorMessage.value = null
            return
        }

        isLoading.value = true
        errorMessage.value = null
        searchJob = viewModelScope.launch {
            // Debounce: cancel the previous in-flight "typing" job above and
            // only hit the API once the user pauses typing.
            delay(300)
            val result = userRepository.searchUsers(query, LIMIT)
            isLoading.value = false
            result.onSuccess { users ->
                results.value = users.filter { it.id != UserSession.currentUserId }
            }.onFailure {
                errorMessage.value = "Couldn't search right now. Try again."
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }

    companion object {
        private const val LIMIT = 25
    }
}