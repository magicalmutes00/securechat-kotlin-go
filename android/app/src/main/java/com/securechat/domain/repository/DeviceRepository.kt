package com.securechat.domain.repository

import com.securechat.core.common.Result
import com.securechat.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    suspend fun getDevices(): Result<List<Device>>
    suspend fun revokeDevice(deviceId: Long): Result<Unit>
    suspend fun revokeAllOtherDevices(): Result<Unit>
    
    fun observeDevices(): Flow<List<Device>>
}