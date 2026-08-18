package com.securechat.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val phoneNumber: String,
    val username: String?,
    val displayName: String,
    val profileImageId: Long?,
    val lastSeen: Long?,
    val isOnline: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toBuilder() = Builder(
        id = id,
        phoneNumber = phoneNumber,
        username = username,
        displayName = displayName,
        profileImageId = profileImageId,
        lastSeen = lastSeen,
        isOnline = isOnline,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    data class Builder(
        val id: Long,
        val phoneNumber: String,
        var username: String? = null,
        var displayName: String,
        var profileImageId: Long? = null,
        var lastSeen: Long? = null,
        var isOnline: Boolean = false,
        var createdAt: Long = System.currentTimeMillis(),
        var updatedAt: Long = System.currentTimeMillis()
    ) {
        fun build(): User = User(
            id = id,
            phoneNumber = phoneNumber,
            username = username,
            displayName = displayName,
            profileImageId = profileImageId,
            lastSeen = lastSeen,
            isOnline = isOnline,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

@Serializable
data class UserSettings(
    val userId: Long,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val mediaAutoDownload: Boolean = true,
    val language: String = "en"
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}