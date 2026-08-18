package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.User
import com.securechat.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): Result<User>
    suspend fun updateProfile(displayName: String, username: String?): Result<User>
    suspend fun updateAvatar(imageId: Long): Result<User>
    suspend fun searchUsers(query: String, limit: Int): Result<List<User>>
    suspend fun getUserSettings(): Result<UserSettings>
    suspend fun updateSettings(settings: UserSettings): Result<Unit>
    
    fun observeCurrentUser(): Flow<User?>
    fun observeUserSettings(): Flow<UserSettings?>
}