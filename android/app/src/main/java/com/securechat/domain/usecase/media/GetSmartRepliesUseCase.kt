package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetSmartRepliesUseCase @javax.inject.Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(conversationId: Long, lastMessages: List<String>): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            if (conversationId <= 0) {
                Result.failure(IllegalArgumentException("Invalid conversation ID"))
            } else if (lastMessages.isEmpty()) {
                Result.failure(IllegalArgumentException("Last messages list cannot be empty"))
            } else {
                aiRepository.getSmartReplies(conversationId, lastMessages)
            }
        }
    }
}