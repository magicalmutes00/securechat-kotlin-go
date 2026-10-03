package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    @Update
    suspend fun update(message: MessageEntity): Int

    @Query("SELECT * FROM messages WHERE id = :id")
    fun getById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE serverId = :serverId")
    fun getByServerId(serverId: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE tempId = :tempId")
    suspend fun getByTempId(tempId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getByConversationId(conversationId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND deletedAt IS NULL AND createdAt < :cursor ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getByConversationIdBeforeCursor(conversationId: Long, cursor: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeByConversationId(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND status IN ('pending', 'sending') AND deletedAt IS NULL")
    suspend fun getPendingMessages(conversationId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = 'failed' AND deletedAt IS NULL")
    suspend fun getFailedMessages(): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND readAt IS NULL AND deletedAt IS NULL AND senderId != :currentUserId")
    suspend fun getUnreadCount(conversationId: Long, currentUserId: Long): Int

    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: Long): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll(): Int

    @Query("UPDATE messages SET status = 'delivered', deliveredAt = :deliveredAt, updatedAt = :deliveredAt WHERE serverId = :serverId AND deliveredAt IS NULL")
    suspend fun markDeliveredByServerId(serverId: Long, deliveredAt: Long): Int

    @Query("UPDATE messages SET status = 'read', readAt = :readAt, updatedAt = :readAt WHERE serverId = :serverId AND readAt IS NULL")
    suspend fun markReadByServerId(serverId: Long, readAt: Long): Int

    @Query("UPDATE messages SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE serverId = :serverId")
    suspend fun markDeletedByServerId(serverId: Long, deletedAt: Long): Int

    @Query("UPDATE messages SET status = 'read', readAt = :readAt WHERE conversationId = :conversationId AND senderId != :currentUserId AND readAt IS NULL AND deletedAt IS NULL")
    suspend fun markConversationRead(conversationId: Long, currentUserId: Long, readAt: Long): Int

    @Query("SELECT serverId FROM messages WHERE conversationId = :conversationId AND senderId != :currentUserId AND status != 'read' AND deletedAt IS NULL AND serverId IS NOT NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestUnreadServerId(conversationId: Long, currentUserId: Long): Long?
}