package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaList: List<MediaEntity>): List<Long>

    @Update
    suspend fun update(media: MediaEntity): Int

    @Query("SELECT * FROM media WHERE id = :id")
    fun getById(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE serverId = :serverId")
    fun getByServerId(serverId: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE messageId = :messageId")
    fun getByMessageId(messageId: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE sha256 = :sha256")
    suspend fun getBySha256(sha256: String): MediaEntity?

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM media WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: Long): Int

    @Query("DELETE FROM media")
    suspend fun deleteAll(): Int
}