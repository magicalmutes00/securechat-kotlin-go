package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securechat.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: UserSettingsEntity): Long

    @Update
    suspend fun update(settings: UserSettingsEntity): Int

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    fun getByUserId(userId: Long): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings")
    fun getAll(): Flow<List<UserSettingsEntity>>

    @Query("DELETE FROM user_settings WHERE userId = :userId")
    suspend fun deleteByUserId(userId: Long): Int

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll(): Int
}