package com.securechat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.securechat.data.local.entity.E2eeSessionEntity

@Dao
interface E2eeSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: E2eeSessionEntity): Long

    @Query("SELECT * FROM signal_sessions WHERE recipientName = :recipientName AND deviceId = :deviceId")
    suspend fun get(recipientName: String, deviceId: Int): E2eeSessionEntity?

    @Query("DELETE FROM signal_sessions WHERE recipientName = :recipientName AND deviceId = :deviceId")
    suspend fun delete(recipientName: String, deviceId: Int): Int

    @Query("DELETE FROM signal_sessions WHERE deviceId = :deviceId")
    suspend fun deleteAllForDevice(deviceId: Int): Int
}
