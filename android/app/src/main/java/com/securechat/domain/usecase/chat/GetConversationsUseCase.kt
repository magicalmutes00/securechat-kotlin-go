package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import com.securechat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetConversationsUseCase @javax.inject.Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(): Result<List<Conversation>> {
        return withContext(Dispatchers.IO) {
            conversationRepository.getConversations()
        }
    }
}