package com.securechat.data.repository

import com.securechat.core.common.Result
import com.securechat.data.local.database.SecureChatDatabase
import com.securechat.data.local.dao.DeviceDao
import com.securechat.data.local.entity.DeviceEntity
import com.securechat.data.remote.api.ApiService
import com.securechat.data.remote.dto.DeviceDto
import com.securechat.domain.model.Device
import com.securechat.domain.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: SecureChatDatabase,
    private val deviceDao: DeviceDao
) : DeviceRepository {

    override suspend fun getDevices(): Result<List<Device>> {
        return withContext(Dispatchers.IO) {
            apiService.getDevices()
                .map { dtos ->
                    // Mirror the server's device list into Room so the observed
                    // flow (used by the sessions screen) reflects the refresh.
                    val entities = dtos.map { mapToEntity(it) }
                    database.deviceDao().deleteAll()
                    database.deviceDao().insertAll(entities)
                    entities.map { mapToDevice(it) }
                }
        }
    }

    override suspend fun revokeDevice(deviceId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            apiService.revokeDevice(deviceId)
                .flatMap {
                    database.deviceDao().deleteById(deviceId)
                    Result.success(Unit)
                }
        }
    }

    override suspend fun revokeAllOtherDevices(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // This would be an API call to revoke all other devices
            // For now, just return success
            Result.success(Unit)
        }
    }

    override fun observeDevices(): kotlinx.coroutines.flow.Flow<List<Device>> {
        return database.deviceDao().getAll()
            .map { entities ->
                entities.map { mapToDevice(it) }
            }
            .distinctUntilChanged()
    }

    private fun mapToDevice(dto: DeviceDto): Device {
        return Device(
            id = dto.id,
            userId = dto.user_id,
            deviceName = dto.device_name,
            deviceIdentifier = dto.device_identifier,
            platform = dto.platform,
            createdAt = dto.created_at,
            lastSeen = dto.last_seen
        )
    }

    private fun mapToDevice(entity: DeviceEntity): Device {
        return Device(
            id = entity.id,
            userId = entity.userId,
            deviceName = entity.deviceName,
            deviceIdentifier = entity.deviceIdentifier,
            platform = entity.platform,
            createdAt = entity.createdAt,
            lastSeen = entity.lastSeen
        )
    }

    private fun mapToEntity(dto: DeviceDto): DeviceEntity {
        return DeviceEntity(
            // Use the server id as the local primary key so revoke calls
            // (which need the server device id) reference the right row.
            id = dto.id,
            serverId = dto.id,
            userId = dto.user_id,
            deviceName = dto.device_name,
            deviceIdentifier = dto.device_identifier,
            platform = dto.platform,
            createdAt = dto.created_at,
            lastSeen = dto.last_seen
        )
    }
}