package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.UserDao
import com.securechat.data.local.dao.UserSettingsDao
import com.securechat.data.local.entity.UserEntity
import com.securechat.data.local.entity.UserSettingsEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.UserDto
import com.securechat.data.remote.dto.UserSettings
import com.securechat.domain.model.User
import com.securechat.domain.model.UserSettings as DomainUserSettings
import com.securechat.domain.model.ThemeMode
import com.securechat.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val userDao: UserDao,
    private val userSettingsDao: UserSettingsDao
) : UserRepository {

    override suspend fun getCurrentUser(): Result<User> {
        return withContext(Dispatchers.IO) {
            val localEntity = database.userDao().getAll().first().firstOrNull()
            if (localEntity != null) {
                Result.success(mapToUser(localEntity))
            } else {
                apiService.getCurrentUser()
                    .map { dto ->
                        val user = mapToUser(dto)
                        database.userDao().insert(mapToEntity(user))
                        user
                    }
            }
        }
    }

    override suspend fun updateProfile(
        displayName: String,
        username: String?,
        profileImageId: Long?,
        avatarUrl: String?
    ): Result<User> {
        return withContext(Dispatchers.IO) {
            apiService.updateProfile(com.securechat.data.remote.dto.UpdateProfileRequest(
                display_name = displayName,
                username = username,
                profile_image_id = profileImageId
            )).map { dto ->
                val user = mapToUser(dto)
                database.userDao().insert(mapToEntity(user))
                user
            }
        }
    }

    override suspend fun updateAvatar(imageId: Long): Result<User> {
        return withContext(Dispatchers.IO) {
            val current = database.userDao().getAll().first().firstOrNull()
            val displayName = current?.displayName ?: return@withContext Result.failure(IllegalStateException("User not found"))
            val username = current?.username
            apiService.updateProfile(com.securechat.data.remote.dto.UpdateProfileRequest(
                display_name = displayName,
                username = username,
                profile_image_id = imageId
            )).map { dto ->
                val user = mapToUser(dto)
                database.userDao().insert(mapToEntity(user))
                user
            }
        }
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<User>> {
        return withContext(Dispatchers.IO) {
            apiService.searchUsers(query, limit)
                .map { dtos ->
                    dtos.map { mapToUser(it) }
                }
        }
    }

    override suspend fun getUserSettings(): Result<DomainUserSettings> {
        return withContext(Dispatchers.IO) {
            val settingsEntity = database.userSettingsDao().getAll().first().firstOrNull()
            if (settingsEntity != null) {
                Result.success(mapToSettings(settingsEntity))
            } else {
                Result.success(DomainUserSettings(
                    userId = 0,
                    theme = ThemeMode.SYSTEM,
                    notificationsEnabled = true,
                    mediaAutoDownload = true,
                    language = "en"
                ))
            }
        }
    }

    override suspend fun updateSettings(settings: DomainUserSettings): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val entity = UserSettingsEntity(
                userId = settings.userId,
                theme = settings.theme.name.lowercase(),
                notificationsEnabled = settings.notificationsEnabled,
                mediaAutoDownload = settings.mediaAutoDownload,
                language = settings.language,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.userSettingsDao().insert(entity)
            Result.success(Unit)
        }
    }

    override fun observeCurrentUser(): kotlinx.coroutines.flow.Flow<User?> {
        return database.userDao().getAll()
            .map { entities ->
                entities.firstOrNull()?.let { mapToUser(it) }
            }
            .distinctUntilChanged()
    }

    override fun observeUserSettings(): kotlinx.coroutines.flow.Flow<DomainUserSettings?> {
        return database.userSettingsDao().getAll()
            .map { entities ->
                entities.firstOrNull()?.let { mapToSettings(it) }
            }
            .distinctUntilChanged()
    }

    private fun mapToUser(dto: UserDto): User {
        return User(
            id = dto.id,
            phoneNumber = dto.phone_number,
            username = dto.username,
            displayName = dto.display_name,
            profileImageId = dto.profile_image_id,
            avatarUrl = dto.avatar_url,
            lastSeen = dto.last_seen,
            isOnline = dto.is_online,
            createdAt = dto.created_at,
            updatedAt = dto.updated_at
        )
    }

    private fun mapToUser(entity: UserEntity): User {
        return User(
            id = entity.id,
            phoneNumber = entity.phoneNumber,
            username = entity.username,
            displayName = entity.displayName,
            profileImageId = entity.profileImageId,
            avatarUrl = entity.avatarUrl,
            lastSeen = entity.lastSeen,
            isOnline = entity.isOnline,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun mapToEntity(user: User): UserEntity {
        return UserEntity(
            serverId = user.id,
            phoneNumber = user.phoneNumber,
            username = user.username,
            displayName = user.displayName,
            profileImageId = user.profileImageId,
            avatarUrl = user.avatarUrl,
            lastSeen = user.lastSeen,
            isOnline = user.isOnline,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt
        )
    }

    private fun mapToSettings(entity: UserSettingsEntity): DomainUserSettings {
        return DomainUserSettings(
            userId = entity.userId,
            theme = ThemeMode.valueOf(entity.theme.uppercase()),
            notificationsEnabled = entity.notificationsEnabled,
            mediaAutoDownload = entity.mediaAutoDownload,
            language = entity.language
        )
    }
}