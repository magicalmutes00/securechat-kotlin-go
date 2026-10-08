package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import com.securechat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Starts a 1:1 conversation from a user id — used after searching for a person,
 * whose phone number the backend deliberately never returns.
 */
class CreateConversationByUserUseCase @javax.inject.Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(userId: Long): Result<Conversation> {
        return withContext(Dispatchers.IO) {
            if (userId <= 0) {
                Result.failure(IllegalArgumentException("A valid user id is required"))
            } else {
                conversationRepository.createDirectConversationWithUser(userId)
            }
        }
    }
}