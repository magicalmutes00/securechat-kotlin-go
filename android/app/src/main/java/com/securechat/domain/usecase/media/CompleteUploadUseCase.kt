package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.model.Message
import com.securechat.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CompleteUploadUseCase @javax.inject.Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(request: MediaRepository.CompleteUploadRequest): Result<Message> {
        return withContext(Dispatchers.IO) {
            if (request.conversationId <= 0) {
                Result.failure(IllegalArgumentException("Invalid conversation ID"))
            } else if (request.cloudinaryPublicId.isBlank()) {
                Result.failure(IllegalArgumentException("Cloudinary public ID is required"))
            } else if (request.secureUrl.isBlank()) {
                Result.failure(IllegalArgumentException("Secure URL is required"))
            } else if (request.fileSize <= 0) {
                Result.failure(IllegalArgumentException("File size must be positive"))
            } else if (request.sha256.isBlank()) {
                Result.failure(IllegalArgumentException("SHA256 hash is required"))
            } else {
                mediaRepository.completeUpload(request)
            }
        }
    }
}