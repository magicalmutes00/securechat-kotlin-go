package com.securechat.data.sync

import com.securechat.data.local.dao.PendingUploadDao
import com.securechat.data.local.entity.PendingUploadEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A media message waiting for its upload to finish.
 *
 * Everything the retry needs is here, because the sender's process may be gone by
 * the time the retry runs: the conversation to post into, the local file, what it
 * is, and the hash the backend validates on completion.
 */
@Serializable
data class PendingMediaSend(
    val uploadId: String,
    val conversationId: Long,
    val senderId: Long,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val sha256: String
)

/**
 * Durable queue of attachment sends, persisted in `pending_uploads`.
 *
 * This table and its DAO already existed but nothing ever wrote to it, so
 * `SyncRepositoryImpl.syncPendingMedia` could only read an always-empty set —
 * which it then reported as "upload retry is not implemented". Attachments are
 * now recorded before the transfer starts and cleared when the message is
 * accepted, so a failure anywhere (app killed, socket dropped, Cloudinary down)
 * leaves a row the sync pass can find.
 */
@Singleton
class MediaSendQueue @Inject constructor(
    private val pendingUploadDao: PendingUploadDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Records the send before any bytes move, so a crash cannot lose it. */
    suspend fun enqueue(item: PendingMediaSend) {
        val now = System.currentTimeMillis()
        pendingUploadDao.insert(
            PendingUploadEntity(
                id = item.uploadId,
                type = TYPE_MEDIA_SEND,
                payloadJson = json.encodeToString(item),
                status = STATUS_PENDING,
                createdAt = now,
                nextRetryAt = now
            )
        )
    }

    /** The message was accepted: nothing left to retry. */
    suspend fun complete(uploadId: String) {
        pendingUploadDao.deleteById(uploadId)
    }

    /**
     * Schedules the next attempt with exponential backoff. A permanently failing
     * upload keeps its row (and so keeps counting as outstanding) rather than
     * being dropped silently, but stops being retried on every sync pass.
     */
    suspend fun markAttemptFailed(uploadId: String) {
        val entity = pendingUploadDao.getById(uploadId).first() ?: return
        val attempts = entity.retryCount + 1
        pendingUploadDao.update(
            entity.copy(
                retryCount = attempts,
                status = STATUS_PENDING,
                nextRetryAt = System.currentTimeMillis() + backoffFor(attempts)
            )
        )
    }

    /** Sends whose retry time has arrived. */
    suspend fun due(now: Long = System.currentTimeMillis()): List<PendingMediaSend> =
        pendingUploadDao.getPendingForRetry(now).mapNotNull { decode(it) }

    suspend fun outstanding(): Int = pendingUploadDao.countOutstanding()

    /** A row whose payload cannot be read is unusable; the caller drops it. */
    fun decode(entity: PendingUploadEntity): PendingMediaSend? = runCatching {
        json.decodeFromString<PendingMediaSend>(entity.payloadJson)
    }.getOrNull()

    private fun backoffFor(attempts: Int): Long =
        minOf(BASE_BACKOFF_MS shl minOf(attempts, MAX_BACKOFF_SHIFT), MAX_BACKOFF_MS)

    companion object {
        const val TYPE_MEDIA_SEND = "media_send"
        const val STATUS_PENDING = "pending"

        private const val BASE_BACKOFF_MS = 30_000L
        private const val MAX_BACKOFF_SHIFT = 6
        private const val MAX_BACKOFF_MS = 30 * 60_000L
    }
}
