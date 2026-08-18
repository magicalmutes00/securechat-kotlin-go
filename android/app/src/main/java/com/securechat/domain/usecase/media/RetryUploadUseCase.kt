package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RetryUploadUseCase @javax.inject.Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(uploadId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (uploadId.isBlank()) {
                Result.failure(IllegalArgumentException("Upload ID is required"))
            } else {
                mediaRepository.retryUpload(uploadId)
            }
        }
    }
}