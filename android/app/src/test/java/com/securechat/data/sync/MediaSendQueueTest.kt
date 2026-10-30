package com.securechat.data.sync

import com.securechat.data.local.dao.PendingUploadDao
import com.securechat.data.local.entity.PendingUploadEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The durable attachment queue: what a failed upload leaves behind, and what the
 * sync pass is able to reconstruct from it.
 */
class MediaSendQueueTest {

    private val dao = mockk<PendingUploadDao>(relaxed = true)
    private val queue = MediaSendQueue(dao)

    private val payload = PendingMediaSend(
        uploadId = "upload-1",
        conversationId = 42L,
        senderId = 7L,
        filePath = "/cache/outgoing/photo.jpg",
        fileName = "photo.jpg",
        mimeType = "image/jpeg",
        fileSize = 4096L,
        sha256 = "a".repeat(64)
    )

    @Test
    fun `enqueue persists a payload the sync pass can decode again`() = runTest {
        val captured = slot<PendingUploadEntity>()
        coEvery { dao.insert(capture(captured)) } returns 1L

        queue.enqueue(payload)

        val stored = captured.captured
        assertEquals("upload-1", stored.id)
        assertEquals(MediaSendQueue.TYPE_MEDIA_SEND, stored.type)
        assertEquals(MediaSendQueue.STATUS_PENDING, stored.status)

        // The round trip is the contract: the row is the only copy that survives
        // process death, so a payload that does not decode is a send that is
        // silently lost.
        val decoded = queue.decode(stored)
        assertNotNull(decoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `complete drops the row so it is never retried`() = runTest {
        coEvery { dao.deleteById("upload-1") } returns 1
        queue.complete("upload-1")
    }

    @Test
    fun `markAttemptFailed bumps the count and defers the next attempt`() = runTest {
        val original = PendingUploadEntity(
            id = "upload-1",
            type = MediaSendQueue.TYPE_MEDIA_SEND,
            payloadJson = "{}",
            retryCount = 0,
            nextRetryAt = 0L
        )
        every { dao.getById("upload-1") } returns flowOf(original)
        val updated = slot<PendingUploadEntity>()
        coEvery { dao.update(capture(updated)) } returns 1

        val before = System.currentTimeMillis()
        queue.markAttemptFailed("upload-1")

        assertEquals(1, updated.captured.retryCount)
        assertTrue(
            "the retry must be scheduled in the future, not immediately",
            updated.captured.nextRetryAt > before
        )
        // Still pending: a failed attempt is not a terminal state, or the row
        // would stop counting as outstanding and the user would see an upload
        // that vanished.
        assertEquals(MediaSendQueue.STATUS_PENDING, updated.captured.status)
    }

    @Test
    fun `due skips rows whose payload cannot be read`() = runTest {
        val good = PendingUploadEntity(
            id = "upload-1",
            type = MediaSendQueue.TYPE_MEDIA_SEND,
            payloadJson = """
                {"uploadId":"upload-1","conversationId":42,"senderId":7,
                 "filePath":"/cache/outgoing/photo.jpg","fileName":"photo.jpg",
                 "mimeType":"image/jpeg","fileSize":4096,"sha256":"${"a".repeat(64)}"}
            """.trimIndent()
        )
        val corrupt = good.copy(id = "upload-2", payloadJson = "not json")

        coEvery { dao.getPendingForRetry(any()) } returns listOf(good, corrupt)

        val due = queue.due()

        assertEquals(1, due.size)
        assertEquals("upload-1", due.first().uploadId)
    }

    @Test
    fun `decode returns null for an unreadable payload`() {
        val corrupt = PendingUploadEntity(
            id = "upload-2",
            type = MediaSendQueue.TYPE_MEDIA_SEND,
            payloadJson = "{oops"
        )
        assertNull(queue.decode(corrupt))
    }
}
