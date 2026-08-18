package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeleteMessageUseCase @javax.inject.Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(messageId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (messageId <= 0) {
                Result.failure(IllegalArgumentException("Invalid message ID"))
            } else {
                messageRepository.deleteMessage(messageId)
            }
        }
    }
}