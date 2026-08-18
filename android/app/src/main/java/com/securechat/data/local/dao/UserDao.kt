package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>): List<Long>

    @Update
    suspend fun update(user: UserEntity): Int

    @Query("SELECT * FROM users WHERE id = :id")
    fun getById(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE serverId = :serverId")
    fun getByServerId(serverId: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE phoneNumber = :phoneNumber")
    suspend fun getByPhoneNumber(phoneNumber: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<UserEntity>>

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM users")
    suspend fun deleteAll(): Int
}