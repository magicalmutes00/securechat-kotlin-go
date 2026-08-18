package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversations: List<ConversationEntity>): List<Long>

    @Update
    suspend fun update(conversation: ConversationEntity): Int

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun getById(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE serverId = :serverId")
    fun getByServerId(serverId: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id IN (SELECT conversationId FROM conversation_participants WHERE userId = :userId) ORDER BY updatedAt DESC")
    fun getByParticipant(userId: Long): Flow<List<ConversationEntity>>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM conversations")
    suspend fun deleteAll(): Int
}