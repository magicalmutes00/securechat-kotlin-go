package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>): List<Long>

    @Update
    suspend fun update(session: SessionEntity): Int

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE serverId = :serverId")
    fun getByServerId(serverId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE userId = :userId AND revokedAt IS NULL AND expiresAt > :now ORDER BY createdAt DESC")
    fun getActiveByUserId(userId: Long, now: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE deviceId = :deviceId AND revokedAt IS NULL ORDER BY createdAt DESC")
    fun getActiveByDeviceId(deviceId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM sessions WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long): Int

    @Query("DELETE FROM sessions")
    suspend fun deleteAll(): Int
}