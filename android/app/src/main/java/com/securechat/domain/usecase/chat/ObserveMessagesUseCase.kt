package com.securechat.domain.usecase.chat

import com.securechat.domain.model.Message
import com.securechat.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Exposes the live Room-backed message stream for a conversation so the UI
 * updates as sends/receipts/incoming messages are persisted.
 */
class ObserveMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository
) {
    operator fun invoke(conversationId: Long): Flow<List<Message>> {
        return messageRepository.observeMessages(conversationId)
    }
}
