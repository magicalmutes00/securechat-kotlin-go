package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.dao.PendingOperationDao
import com.securechat.data.local.dao.PendingUploadDao
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.local.entity.PendingOperationEntity
import com.securechat.data.local.entity.PendingUploadEntity
import com.securechat.data.remote.websocket.MessageSendPayload
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.data.remote.websocket.WsEvent
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: SecureChatDatabase,
    private val messageDao: MessageDao,
    private val pendingUploadDao: PendingUploadDao,
    private val pendingOperationDao: PendingOperationDao,
    private val webSocketManager: WebSocketManager
) : SyncRepository {

    private val _syncStatus = MutableStateFlow(SyncRepository.SyncStatus.IDLE)
    override fun observeSyncStatus(): kotlinx.coroutines.flow.Flow<SyncRepository.SyncStatus> = _syncStatus.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)
    override fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int> = _pendingCount.asStateFlow()

    override suspend fun syncPendingMessages(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            _syncStatus.value = SyncRepository.SyncStatus.SYNCING
            
            var syncedCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            // Get pending messages
            val pendingMessages = database.messageDao().getFailedMessages()
            
            for (message in pendingMessages) {
                try {
                    // Retry sending via WebSocket
                    val wsEvent = WsEvent.MessageSend(
                        id = message.tempId ?: UUID.randomUUID().toString(),
                        payload = MessageSendPayload(
                            conversation_id = message.conversationId,
                            type = message.type,
                            text = message.text,
                            media_id = message.mediaId,
                            reply_to_id = message.replyToId,
                            temp_id = message.tempId ?: UUID.randomUUID().toString()
                        )
                    )
                    
                    webSocketManager.send(wsEvent)
                    
                    // Update status to sending
                    val updated = message.copy(
                        status = MessageStatus.SENDING.name,
                        updatedAt = System.currentTimeMillis()
                    )
                    database.messageDao().update(updated)
                    
                    syncedCount++
                } catch (e: Exception) {
                    failedCount++
                    errors.add("Failed to sync message ${message.id}: ${e.message}")
                }
            }
            
            updatePendingCount()
            _syncStatus.value = if (failedCount == 0) SyncRepository.SyncStatus.SUCCESS else SyncRepository.SyncStatus.FAILED
            
            Result.success(SyncRepository.SyncResult(syncedCount, failedCount, errors))
        }
    }

    override suspend fun syncPendingMedia(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            _syncStatus.value = SyncRepository.SyncStatus.SYNCING
            
            var syncedCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
// Get pending uploads
            val pendingUploads = database.pendingUploadDao().getByStatus("pending").first()

            for (upload in pendingUploads) {
                // Upload logic would go here
                // For now, just count
                syncedCount++
            }
            
            updatePendingCount()
            _syncStatus.value = if (failedCount == 0) SyncRepository.SyncStatus.SUCCESS else SyncRepository.SyncStatus.FAILED
            
            Result.success(SyncRepository.SyncResult(syncedCount, failedCount, errors))
        }
    }

    override suspend fun syncReadReceipts(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            // Send pending read receipts via WebSocket
            Result.success(SyncRepository.SyncResult(0, 0, emptyList()))
        }
    }

    override suspend fun forceFullSync(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            _syncStatus.value = SyncRepository.SyncStatus.SYNCING
            
            // Sync all pending operations
            val messagesResult = syncPendingMessages()
            val mediaResult = syncPendingMedia()
            val receiptsResult = syncReadReceipts()
            
val totalSynced = messagesResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.syncedCount +
                mediaResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.syncedCount +
                receiptsResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.syncedCount

            val totalFailed = messagesResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.failedCount +
                mediaResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.failedCount +
                receiptsResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.failedCount

            val allErrors = messagesResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.errors +
                mediaResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.errors +
                receiptsResult.getOrElse { SyncRepository.SyncResult(0, 0, emptyList()) }.errors
            
            updatePendingCount()
            _syncStatus.value = if (totalFailed == 0) SyncRepository.SyncStatus.SUCCESS else SyncRepository.SyncStatus.FAILED
            
            Result.success(SyncRepository.SyncResult(totalSynced, totalFailed, allErrors))
        }
    }

    private fun updatePendingCount() {
        // Count pending items
        // This would query the database for pending items
        _pendingCount.value = 0 // Simplified
    }
}