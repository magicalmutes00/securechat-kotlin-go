package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetSignedUploadParamsUseCase @javax.inject.Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(request: MediaRepository.SignedUploadRequest): Result<MediaRepository.SignedUploadParams> {
        return withContext(Dispatchers.IO) {
            if (request.fileSize <= 0) {
                Result.failure(IllegalArgumentException("File size must be positive"))
            } else if (request.filename.isBlank()) {
                Result.failure(IllegalArgumentException("Filename is required"))
            } else if (request.mimeType.isBlank()) {
                Result.failure(IllegalArgumentException("MIME type is required"))
            } else {
                mediaRepository.getSignedUploadParams(request)
            }
        }
    }
}