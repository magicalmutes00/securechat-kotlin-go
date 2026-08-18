package com.securechat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: Long,
    val userId: Long,
    val deviceName: String,
    val deviceIdentifier: String,
    val platform: String,
    val createdAt: Long,
    val lastSeen: Long?
)

@Serializable
data class Session(
    val id: Long,
    val userId: Long,
    val deviceId: Long,
    val refreshTokenHash: String,
    val expiresAt: Long,
    val revokedAt: Long?,
    val createdAt: Long
) {
    val isActive: Boolean
        get() = revokedAt == null && System.currentTimeMillis() < expiresAt

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt
}