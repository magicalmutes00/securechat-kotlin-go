package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import com.securechat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateConversationUseCase @javax.inject.Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(participantPhone: String): Result<Conversation> {
        return withContext(Dispatchers.IO) {
            if (participantPhone.isBlank()) {
                Result.failure(IllegalArgumentException("Participant phone number is required"))
            } else if (!isValidPhoneNumber(participantPhone)) {
                Result.failure(IllegalArgumentException("Invalid phone number format"))
            } else {
                conversationRepository.createDirectConversation(participantPhone)
            }
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$".toRegex())
    }
}