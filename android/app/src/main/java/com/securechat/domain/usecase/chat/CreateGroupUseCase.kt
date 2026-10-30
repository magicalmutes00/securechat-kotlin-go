package com.securechat.domain.usecase.chat

import com.securechat.core.common.Result
import com.securechat.domain.model.Conversation
import com.securechat.domain.repository.ConversationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates a named group conversation.
 *
 * [memberIds] are the *other* members: the server adds the caller automatically,
 * and rejects the request if the caller is listed, the name is blank, or a member
 * does not exist. Validating the shape here as well means the obvious mistakes
 * fail immediately instead of costing a network round trip.
 */
class CreateGroupUseCase @javax.inject.Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(name: String, memberIds: List<Long>): Result<Conversation> {
        return withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            when {
                trimmed.isEmpty() ->
                    Result.failure(IllegalArgumentException("A group name is required"))
                memberIds.isEmpty() ->
                    Result.failure(IllegalArgumentException("Select at least one member"))
                memberIds.any { it <= 0 } ->
                    Result.failure(IllegalArgumentException("Invalid member selected"))
                else ->
                    conversationRepository.createGroup(trimmed, memberIds.distinct())
            }
        }
    }
}
