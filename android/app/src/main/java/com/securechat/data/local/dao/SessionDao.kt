package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Refresh-token sessions, mirroring the server's `sessions` table.
 *
 * This interface used to be a duplicate of the Signal-protocol session DAO: every
 * query targeted a `signal_sessions` table that was never created, while the
 * entity returned was the auth-session row. Room rejected the whole schema at
 * build time, and nothing could have used it if it had compiled.
 */
@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>): List<Long>

    @Update
    suspend fun update(session: SessionEntity): Int

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE userId = :userId ORDER BY createdAt DESC")
    fun getByUserId(userId: Long): Flow<List<SessionEntity>>

    /** Sessions that are neither revoked nor expired, newest first. */
    @Query("SELECT * FROM sessions WHERE userId = :userId AND revokedAt IS NULL AND expiresAt > :now ORDER BY createdAt DESC")
    suspend fun getActiveForUser(userId: Long, now: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET revokedAt = :revokedAt WHERE id = :id")
    suspend fun revoke(id: Long, revokedAt: Long): Int

    @Query("UPDATE sessions SET revokedAt = :revokedAt WHERE userId = :userId AND revokedAt IS NULL")
    suspend fun revokeAllForUser(userId: Long, revokedAt: Long): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM sessions WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long): Int

    @Query("DELETE FROM sessions")
    suspend fun deleteAll(): Int
}
