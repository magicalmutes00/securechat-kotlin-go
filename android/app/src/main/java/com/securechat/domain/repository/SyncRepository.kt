package com.securechat.domain.repository

import com.securechat.core.common.Result
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun syncPendingMessages(): Result<SyncResult>
    suspend fun syncPendingMedia(): Result<SyncResult>
    suspend fun syncReadReceipts(): Result<SyncResult>
    suspend fun forceFullSync(): Result<SyncResult>

    fun observeSyncStatus(): Flow<SyncStatus>
    fun observePendingCount(): Flow<Int>

    data class SyncResult(
        val syncedCount: Int,
        val failedCount: Int,
        val errors: List<String>
    )

    enum class SyncStatus {
        IDLE, SYNCING, SUCCESS, FAILED, OFFLINE
    }
}