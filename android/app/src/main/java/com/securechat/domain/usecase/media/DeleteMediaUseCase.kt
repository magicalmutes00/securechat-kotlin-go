package com.securechat.domain.usecase.media

import com.securechat.core.common.Result
import com.securechat.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeleteMediaUseCase @javax.inject.Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(mediaId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (mediaId <= 0) {
                Result.failure(IllegalArgumentException("Invalid media ID"))
            } else {
                mediaRepository.deleteMedia(mediaId)
            }
        }
    }
}