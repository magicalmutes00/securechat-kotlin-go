package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.repository.MediaRepository
import com.securechat.domain.repository.MediaRepository.UploadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadMediaUseCase @javax.inject.Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(
        params: MediaRepository.SignedUploadParams,
        filePath: String,
        progressCallback: (UploadProgress) -> Unit
    ): Result<MediaRepository.CloudinaryUploadResult> {
        return withContext(Dispatchers.IO) {
            if (filePath.isBlank()) {
                Result.failure(IllegalArgumentException("File path is required"))
            } else {
                mediaRepository.uploadMedia(params, filePath, progressCallback)
            }
        }
    }
}