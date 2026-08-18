package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: DeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>): List<Long>

    @Update
    suspend fun update(device: DeviceEntity): Int

    @Query("SELECT * FROM devices WHERE id = :id")
    fun getById(id: Long): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE serverId = :serverId")
    fun getByServerId(serverId: Long): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE userId = :userId ORDER BY lastSeen DESC")
    fun getByUserId(userId: Long): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE userId = :userId AND deviceIdentifier = :deviceIdentifier")
    suspend fun getByUserIdAndIdentifier(userId: Long, deviceIdentifier: String): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("DELETE FROM devices WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM devices WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long): Int

    @Query("DELETE FROM devices")
    suspend fun deleteAll(): Int
}