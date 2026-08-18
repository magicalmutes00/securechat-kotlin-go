package com.securechat.domain.usecase.sync

import com.securechat.core.common.Result
import com.securechat.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncPendingMediaUseCase @javax.inject.Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            syncRepository.syncPendingMedia()
        }
    }
}