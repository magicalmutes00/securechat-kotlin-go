package com.securechat.presentation.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import com.securechat.domain.usecase.chat.GetConversationsUseCase
import com.securechat.domain.usecase.chat.CreateConversationUseCase
import com.securechat.domain.usecase.chat.CreateConversationByUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val createConversationUseCase: CreateConversationUseCase,
    private val createConversationByUserUseCase: CreateConversationByUserUseCase
) : ViewModel() {

    var conversations: MutableState<List<Conversation>> = mutableStateOf(emptyList())
    var isLoading: MutableState<Boolean> = mutableStateOf(false)
    var errorMessage: MutableState<String?> = mutableStateOf(null)

    fun loadConversations() {
        isLoading.value = true
        errorMessage.value = null

        viewModelScope.launch {
            val result = getConversationsUseCase()
            isLoading.value = false
            
            result.onSuccess { conversations ->
                this@HomeViewModel.conversations.value = conversations
            }.onFailure { e ->
                errorMessage.value = "Failed to load conversations"
            }
        }
    }

    fun createNewConversation(
        participantPhone: String,
        onSuccess: (Conversation) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = createConversationUseCase(participantPhone)
            result.onSuccess { conversation ->
                conversations.value = conversations.value + conversation
                onSuccess(conversation)
            }.onFailure { e ->
                errorMessage.value = "Failed to create conversation"
                // The caller shows a blocking spinner while this runs — it has
                // to be dismissed or a failed creation strands the UI.
                onFailure()
            }
        }
    }

    fun createNewConversationWithUser(
        userId: Long,
        onSuccess: (Conversation) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = createConversationByUserUseCase(userId)
            result.onSuccess { conversation ->
                conversations.value = conversations.value + conversation
                onSuccess(conversation)
            }.onFailure { _ ->
                errorMessage.value = "Failed to create conversation"
                onFailure()
            }
        }
    }

    fun refresh() {
        loadConversations()
    }
}