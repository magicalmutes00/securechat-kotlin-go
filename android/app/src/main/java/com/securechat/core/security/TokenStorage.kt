package com.securechat.core.security

import com.securechat.core.common.Result

interface TokenStorage {
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long): Result<Unit>
    suspend fun getAccessToken(): Result<String>
    suspend fun getRefreshToken(): Result<String>
    suspend fun clear(): Result<Unit>
    suspend fun isAccessTokenExpired(): Boolean
}