package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.model.Message
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetMessagesUseCase @javax.inject.Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(
        conversationId: Long,
        limit: Int = 30,
        cursor: Long? = null
    ): Result<MessageRepository.MessagePage> {
        return withContext(Dispatchers.IO) {
            if (conversationId <= 0) {
                Result.failure(IllegalArgumentException("Invalid conversation ID"))
            } else if (limit <= 0 || limit > 100) {
                Result.failure(IllegalArgumentException("Limit must be between 1 and 100"))
            } else {
                messageRepository.getMessages(conversationId, limit, cursor)
            }
        }
    }
}