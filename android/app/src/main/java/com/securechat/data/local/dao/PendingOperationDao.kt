package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<PendingOperationEntity>): List<Long>

    @Update
    suspend fun update(operation: PendingOperationEntity): Int

    @Query("SELECT * FROM pending_operations WHERE id = :id")
    fun getById(id: String): Flow<PendingOperationEntity?>

    @Query("SELECT * FROM pending_operations WHERE status = :status")
    fun getByStatus(status: String): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations WHERE status = 'pending' AND nextRetryAt <= :now ORDER BY nextRetryAt")
    suspend fun getPendingForRetry(now: Long): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PendingOperationEntity>>

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM pending_operations WHERE status IN ('completed', 'failed', 'cancelled') AND createdAt < :olderThan")
    suspend fun cleanupOld(olderThan: Long): Int

    @Query("DELETE FROM pending_operations")
    suspend fun deleteAll(): Int
}