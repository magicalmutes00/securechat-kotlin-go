package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.dao.PendingUploadDao
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.websocket.MessageSendPayload
import com.securechat.data.remote.websocket.WebSocketManager
import com.securechat.data.remote.websocket.WsEvent
import com.securechat.data.sync.MediaMessageSender
import com.securechat.data.sync.MediaSendQueue
import com.securechat.data.sync.PendingOperationQueue
import com.securechat.domain.model.MessageStatus
import com.securechat.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao,
    private val pendingUploadDao: PendingUploadDao,
    private val mediaSendQueue: MediaSendQueue,
    private val mediaMessageSender: MediaMessageSender,
    private val operationQueue: PendingOperationQueue,
    private val webSocketManager: WebSocketManager,
    private val apiService: ApiService
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

            // Only failed rows are retried here. Pending/sending ones are already
            // sitting in the socket's outbound queue, which flushes on reconnect;
            // resending those would duplicate every message sent while offline.
            val pendingMessages = messageDao.getFailedMessages()

            for (message in pendingMessages) {
                try {
                    val tempId = message.tempId ?: UUID.randomUUID().toString()

                    val wsEvent = WsEvent.MessageSend(
                        id = tempId,
                        payload = MessageSendPayload(
                            conversation_id = message.conversationId,
                            type = message.type,
                            text = message.text,
                            media_id = message.mediaId,
                            reply_to_id = message.replyToId,
                            temp_id = tempId
                        )
                    )

                    // The send Result used to be discarded, so a message that
                    // the socket refused was still counted as synced.
                    val sendResult = webSocketManager.send(wsEvent)
                    if (sendResult.isFailure) {
                        failedCount++
                        val reason = (sendResult as? Result.Failure)?.exception?.message
                        errors.add("Failed to sync message ${message.id}: $reason")
                        continue
                    }

                    // storageValue (lowercase) is what the DAO filters on;
                    // writing `name` produced rows getPendingMessages never saw.
                    messageDao.update(
                        message.copy(
                            status = MessageStatus.SENDING.storageValue,
                            tempId = tempId,
                            updatedAt = System.currentTimeMillis()
                        )
                    )

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

    /**
     * Re-runs attachment sends that never completed.
     *
     * Each attempt goes through [MediaMessageSender], the same sign → upload →
     * complete → send sequence the chat screen uses, so a retry produces a real
     * message rather than a dangling asset. Previously this loop counted every
     * queued upload as failed with "automatic upload retry is not implemented".
     */
    override suspend fun syncPendingMedia(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            _syncStatus.value = SyncRepository.SyncStatus.SYNCING

            var syncedCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()

            val due = mediaSendQueue.due()
            for (payload in due) {
                val result = mediaMessageSender.send(payload)
                when (result) {
                    is Result.Success -> {
                        mediaSendQueue.complete(payload.uploadId)
                        syncedCount++
                    }
                    is Result.Failure -> {
                        mediaSendQueue.markAttemptFailed(payload.uploadId)
                        failedCount++
                        errors.add(
                            "Media upload ${payload.fileName} failed: ${result.exception.message}"
                        )
                    }
                }
            }

            updatePendingCount()
            _syncStatus.value = if (failedCount == 0) SyncRepository.SyncStatus.SUCCESS else SyncRepository.SyncStatus.FAILED

            Result.success(SyncRepository.SyncResult(syncedCount, failedCount, errors))
        }
    }

    /**
     * Flushes queued read receipts and message deletions.
     *
     * Both were lost while the socket was down: receipts went out exactly once,
     * with the Result discarded, and a delete that the API refused left the row
     * deleted locally while the server kept it.
     */
    override suspend fun syncReadReceipts(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            var syncedCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()

            for ((operationId, operation) in operationQueue.due()) {
                val result: Result<Unit> = when (operation.type) {
                    PendingOperationQueue.TYPE_READ_RECEIPT -> webSocketManager.sendReadReceipt(
                        messageId = operation.messageId,
                        conversationId = operation.conversationId
                    )

                    PendingOperationQueue.TYPE_MESSAGE_DELETE -> apiService.deleteMessage(operation.messageId)

                    else -> Result.failure(IllegalArgumentException("Unknown operation ${operation.type}"))
                }

                when (result) {
                    is Result.Success -> {
                        operationQueue.complete(operationId)
                        syncedCount++
                    }
                    is Result.Failure -> {
                        operationQueue.markAttemptFailed(operationId)
                        failedCount++
                        errors.add("${operation.type} failed: ${result.exception.message}")
                    }
                }
            }

            updatePendingCount()
            Result.success(SyncRepository.SyncResult(syncedCount, failedCount, errors))
        }
    }

    override suspend fun forceFullSync(): Result<SyncRepository.SyncResult> {
        return withContext(Dispatchers.IO) {
            _syncStatus.value = SyncRepository.SyncStatus.SYNCING

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

    private suspend fun updatePendingCount() {
        // Previously hardcoded to 0, so the pending badge never appeared no
        // matter how much was queued.
        _pendingCount.value = try {
            messageDao.countUnsynced() +
                pendingUploadDao.countOutstanding() +
                operationQueue.outstanding()
        } catch (e: Exception) {
            0
        }
    }
}
