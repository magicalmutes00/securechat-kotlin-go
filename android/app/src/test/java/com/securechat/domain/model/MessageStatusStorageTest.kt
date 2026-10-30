package com.securechat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the message status/type storage encoding.
 *
 * These exist because the app previously kept two representations of the same
 * status — uppercase enum `name` in memory and lowercase literals in the Room
 * DAO queries — and call sites mixed them. The concrete symptoms were that
 * `resendMessage` could never match a failed row, and rows written as "SENDING"
 * were invisible to the pending sweep. Both were silent: no crash, no log, just
 * messages that never retried and never synced.
 */
class MessageStatusStorageTest {

    @Test
    fun `storageValue is lowercase for every status`() {
        MessageStatus.entries.forEach { status ->
            assertEquals(
                "${status.name} must persist as lowercase so the DAO's literal queries match",
                status.name.lowercase(),
                status.storageValue
            )
        }
    }

    @Test
    fun `fromStorage decodes every status from its stored form`() {
        MessageStatus.entries.forEach { status ->
            assertEquals(status, MessageStatus.fromStorage(status.storageValue))
        }
    }

    @Test
    fun `fromStorage accepts both casings`() {
        // The server sends lowercase; older rows and any legacy writer used
        // uppercase. Both must resolve to the same status.
        assertEquals(MessageStatus.FAILED, MessageStatus.fromStorage("failed"))
        assertEquals(MessageStatus.FAILED, MessageStatus.fromStorage("FAILED"))
        assertEquals(MessageStatus.FAILED, MessageStatus.fromStorage("Failed"))
        assertEquals(MessageStatus.SENDING, MessageStatus.fromStorage("sending"))
        assertEquals(MessageStatus.SENDING, MessageStatus.fromStorage("SENDING"))
    }

    @Test
    fun `fromStorage never throws on unknown or null input`() {
        // A status introduced by a newer server must not crash a Result.map
        // transform, which does not catch exceptions.
        assertEquals(MessageStatus.SENT, MessageStatus.fromStorage("queued"))
        assertEquals(MessageStatus.SENT, MessageStatus.fromStorage(""))
        assertEquals(MessageStatus.SENT, MessageStatus.fromStorage(null))
    }

    @Test
    fun `the failed comparison that retry depends on now holds`() {
        // This is the exact expression resendMessage evaluates. It compared
        // `entity.status == MessageStatus.FAILED.name` ("failed" == "FAILED"),
        // which was never true, so retry always failed.
        val storedInRoom = MessageStatus.FAILED.storageValue

        assertTrue(
            "retry must recognise a failed row",
            MessageStatus.fromStorage(storedInRoom) == MessageStatus.FAILED
        )
        assertFalse(
            "the old comparison must be shown to be broken",
            storedInRoom == MessageStatus.FAILED.name
        )
    }

    @Test
    fun `a sending row is visible to the pending sweep`() {
        // MessageDao.getPendingMessages filters status IN ('pending','sending').
        val pendingSweepValues = setOf("pending", "sending")

        assertTrue(MessageStatus.SENDING.storageValue in pendingSweepValues)
        assertTrue(MessageStatus.PENDING.storageValue in pendingSweepValues)
    }

    @Test
    fun `message type round trips through storage`() {
        MessageType.entries.forEach { type ->
            assertEquals(type, MessageType.fromStorage(type.storageValue))
            assertEquals(type.storageValue, type.name.lowercase())
        }
        assertEquals(MessageType.TEXT, MessageType.fromStorage("never-heard-of-it"))
        assertEquals(MessageType.VIDEO, MessageType.fromStorage("VIDEO"))
    }

    @Test
    fun `media resource type round trips through storage`() {
        MediaResourceType.entries.forEach { type ->
            assertEquals(type, MediaResourceType.fromStorage(type.storageValue))
        }
        assertEquals(MediaResourceType.RAW, MediaResourceType.fromStorage("unknown"))
    }

    @Test
    fun `message helpers agree with the status enum`() {
        val base = Message(
            id = 1,
            conversationId = 2,
            senderId = 3,
            type = MessageType.TEXT,
            text = "hi",
            media = null,
            replyTo = null,
            status = MessageStatus.SENDING,
            createdAt = 0,
            updatedAt = 0,
            deliveredAt = null,
            readAt = null,
            deletedAt = null
        )

        assertTrue(base.isPending)
        assertFalse(base.isFailed)
        assertTrue(base.withStatus(MessageStatus.FAILED).isFailed)
        assertEquals(4L, base.withServerId(4).id)
        assertFalse(base.withServerId(4).isOptimistic)
    }
}
