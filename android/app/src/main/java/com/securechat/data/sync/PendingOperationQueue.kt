package com.securechat.data.sync

import com.securechat.data.local.dao.PendingOperationDao
import com.securechat.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small state-changing action that has to reach the server sooner or later.
 *
 * Only the two actions that a user can trigger while the socket is down and that
 * cannot be reconstructed from local state live here. Sending a message is not
 * one of them: the message row is already the record, and
 * `syncPendingMessages` replays those.
 */
@Serializable
data class QueuedOperation(
    val type: String,
    val conversationId: Long = 0,
    val messageId: Long = 0
)

/**
 * Durable queue for receipts and deletions, persisted in `pending_operations`.
 *
 * The table, entity and DAO existed with nothing writing to them, so
 * `syncReadReceipts` was a hardcoded no-op that reported success. A read receipt
 * sent while the socket was down was simply lost, leaving the other side on one
 * grey tick forever.
 */
@Singleton
class PendingOperationQueue @Inject constructor(
    private val pendingOperationDao: PendingOperationDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enqueueReadReceipt(conversationId: Long, messageId: Long) {
        enqueue(QueuedOperation(TYPE_READ_RECEIPT, conversationId, messageId))
    }

    suspend fun enqueueMessageDelete(conversationId: Long, serverMessageId: Long) {
        enqueue(QueuedOperation(TYPE_MESSAGE_DELETE, conversationId, serverMessageId))
    }

    suspend fun complete(operationId: String) {
        pendingOperationDao.deleteById(operationId)
    }

    suspend fun markAttemptFailed(operationId: String) {
        val entity = pendingOperationDao.getById(operationId).first() ?: return
        val attempts = entity.retryCount + 1
        pendingOperationDao.update(
            entity.copy(
                retryCount = attempts,
                nextRetryAt = System.currentTimeMillis() + backoffFor(attempts)
            )
        )
    }

    /** Operations whose retry time has arrived, paired with their stored id. */
    suspend fun due(now: Long = System.currentTimeMillis()): List<Pair<String, QueuedOperation>> =
        pendingOperationDao.getPendingForRetry(now).mapNotNull { entity ->
            runCatching { json.decodeFromString<QueuedOperation>(entity.payloadJson) }
                .getOrNull()
                ?.let { entity.id to it }
        }

    suspend fun outstanding(): Int = pendingOperationDao.countPending()

    private suspend fun enqueue(operation: QueuedOperation) {
        val now = System.currentTimeMillis()
        pendingOperationDao.insert(
            PendingOperationEntity(
                id = UUID.randomUUID().toString(),
                type = operation.type,
                payloadJson = json.encodeToString(operation),
                status = STATUS_PENDING,
                createdAt = now,
                nextRetryAt = now
            )
        )
    }

    private fun backoffFor(attempts: Int): Long =
        minOf(BASE_BACKOFF_MS shl minOf(attempts, MAX_BACKOFF_SHIFT), MAX_BACKOFF_MS)

    companion object {
        const val TYPE_READ_RECEIPT = "read_receipt"
        const val TYPE_MESSAGE_DELETE = "message_delete"
        private const val STATUS_PENDING = "pending"

        private const val BASE_BACKOFF_MS = 15_000L
        private const val MAX_BACKOFF_SHIFT = 6
        private const val MAX_BACKOFF_MS = 15 * 60_000L
    }
}
