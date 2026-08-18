package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.model.Message
import com.securechat.domain.model.MessageType
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SendMessageUseCase @javax.inject.Inject constructor(
    private val messageRepository: MessageRepository
) {
    suspend operator fun invoke(message: Message): Result<Message> {
        return withContext(Dispatchers.IO) {
            if (message.conversationId <= 0) {
                Result.failure(IllegalArgumentException("Invalid conversation ID"))
            } else if (message.type == MessageType.TEXT && (message.text == null || message.text!!.isBlank())) {
                Result.failure(IllegalArgumentException("Text message cannot be empty"))
            } else if (message.type != MessageType.TEXT && message.media == null) {
                Result.failure(IllegalArgumentException("Media message must have media"))
            } else {
                // Generate temp ID if not present
                val messageWithTempId = if (message.tempId.isNullOrBlank()) {
                    message.copy(tempId = UUID.randomUUID().toString())
                } else {
                    message
                }
                messageRepository.sendMessage(messageWithTempId)
            }
        }
    }
}