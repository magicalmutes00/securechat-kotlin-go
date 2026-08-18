package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarkAsReadUseCase @javax.inject.Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(conversationId: Long, messageId: Long? = null): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (conversationId <= 0) {
                Result.failure(IllegalArgumentException("Invalid conversation ID"))
            } else if (messageId != null) {
                messageRepository.markAsRead(conversationId, messageId)
            } else {
                messageRepository.markConversationAsRead(conversationId)
            }
        }
    }
}