package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.model.Media
import com.securechat.domain.repository.MediaRepository
import com.securechat.domain.repository.MediaRepository.UploadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadMediaUseCase @javax.inject.Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(
        media: Media,
        destinationPath: String,
        progressCallback: (UploadProgress) -> Unit
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (destinationPath.isBlank()) {
                Result.failure(IllegalArgumentException("Destination path is required"))
            } else {
                mediaRepository.downloadMedia(media, destinationPath, progressCallback)
            }
        }
    }
}