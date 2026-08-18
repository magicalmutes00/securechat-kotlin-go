package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.ConversationParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationParticipantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(participant: ConversationParticipantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(participants: List<ConversationParticipantEntity>): List<Long>

    @Update
    suspend fun update(participant: ConversationParticipantEntity): Int

    @Query("SELECT * FROM conversation_participants WHERE conversationId = :conversationId")
    fun getByConversationId(conversationId: Long): Flow<List<ConversationParticipantEntity>>

    @Query("SELECT * FROM conversation_participants WHERE userId = :userId")
    fun getByUserId(userId: Long): Flow<List<ConversationParticipantEntity>>

    @Query("SELECT * FROM conversation_participants WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun getByConversationAndUser(conversationId: Long, userId: Long): ConversationParticipantEntity?

    @Query("DELETE FROM conversation_participants WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun removeParticipant(conversationId: Long, userId: Long): Int

    @Query("DELETE FROM conversation_participants WHERE conversationId = :conversationId")
    suspend fun removeAllParticipants(conversationId: Long): Int

    @Query("DELETE FROM conversation_participants")
    suspend fun deleteAll(): Int
}