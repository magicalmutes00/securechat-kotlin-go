package com.securechat.core.security

import com.securechat.core.common.Result

interface TokenStorage {
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long): Result<Unit>
    suspend fun getAccessToken(): Result<String>
    suspend fun getRefreshToken(): Result<String>
    suspend fun clear(): Result<Unit>
    suspend fun isAccessTokenExpired(): Boolean

    /**
     * Persists the id of the user these tokens belong to.
     *
     * The local user cache is otherwise unattributable: without a recorded
     * owner, "the only row in the users table" is the best guess available, and
     * after an account switch that guess is the *previous* account.
     */
    suspend fun saveUserId(userId: Long): Result<Unit>

    /** The authenticated user id, or null when no session has been stored. */
    suspend fun getUserId(): Long?
}