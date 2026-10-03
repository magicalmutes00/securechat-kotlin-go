package com.securechat.core.utils

/**
 * Process-wide holder for the signed-in user's principal, populated by
 * MainViewModel when the auth state resolves. UI code uses this for local
 * predicates (e.g. which conversation participant is "the other user").
 */
object UserSession {
    var currentUserId: Long = 0L
        private set
    var displayName: String = ""
        private set

    fun update(userId: Long, name: String) {
        currentUserId = userId
        displayName = name
    }

    fun clear() {
        currentUserId = 0L
        displayName = ""
    }
}
