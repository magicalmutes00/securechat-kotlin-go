package com.securechat.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted libsignal session state for a recipient device.
 *
 * One row per (recipient, device) pair. The `record` column holds the serialized
 * SessionRecord from libsignal, which includes the ratchet root key, sending/
 * receiving chain keys, and message counters. Survives app restarts so the
 * device can decrypt history after rebooting.
 */
@Entity(
    tableName = "signal_sessions",
    primaryKeys = ["recipientName", "deviceId"]
)
data class E2eeSessionEntity(
    val recipientName: String,
    val deviceId: Int,
    /** Serialized SessionRecord, base64-encoded for storage. */
    val record: ByteArray,
    val updatedAt: Long = System.currentTimeMillis()
)
