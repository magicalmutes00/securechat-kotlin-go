package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.PendingUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUploadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: PendingUploadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(uploads: List<PendingUploadEntity>): List<Long>

    @Update
    suspend fun update(upload: PendingUploadEntity): Int

    @Query("SELECT * FROM pending_uploads WHERE id = :id")
    fun getById(id: String): Flow<PendingUploadEntity?>

    @Query("SELECT * FROM pending_uploads WHERE status = :status")
    fun getByStatus(status: String): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads WHERE status = 'pending' AND nextRetryAt <= :now ORDER BY nextRetryAt")
    suspend fun getPendingForRetry(now: Long): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PendingUploadEntity>>

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM pending_uploads WHERE status IN ('completed', 'failed', 'cancelled') AND createdAt < :olderThan")
    suspend fun cleanupOld(olderThan: Long): Int

    @Query("DELETE FROM pending_uploads")
    suspend fun deleteAll(): Int
}