package com.securechat.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.securechat.data.local.dao.ConversationDao
import com.securechat.data.local.dao.ConversationParticipantDao
import com.securechat.data.local.dao.DeviceDao
import com.securechat.data.local.dao.MediaDao
import com.securechat.data.local.dao.MessageDao
import com.securechat.data.local.dao.PendingOperationDao
import com.securechat.data.local.dao.PendingUploadDao
import com.securechat.data.local.dao.SessionDao
import com.securechat.data.local.dao.UserDao
import com.securechat.data.local.dao.UserSettingsDao
import com.securechat.data.local.entity.ConversationEntity
import com.securechat.data.local.entity.ConversationParticipantEntity
import com.securechat.data.local.entity.DeviceEntity
import com.securechat.data.local.entity.MediaEntity
import com.securechat.data.local.entity.MessageEntity
import com.securechat.data.local.entity.PendingOperationEntity
import com.securechat.data.local.entity.PendingUploadEntity
import com.securechat.data.local.entity.SessionEntity
import com.securechat.data.local.entity.UserEntity
import com.securechat.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        UserEntity::class,
        DeviceEntity::class,
        SessionEntity::class,
        ConversationEntity::class,
        ConversationParticipantEntity::class,
        MessageEntity::class,
        MediaEntity::class,
        PendingUploadEntity::class,
        PendingOperationEntity::class,
        UserSettingsEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SecureChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun deviceDao(): DeviceDao
    abstract fun sessionDao(): SessionDao
    abstract fun conversationDao(): ConversationDao
    abstract fun conversationParticipantDao(): ConversationParticipantDao
    abstract fun messageDao(): MessageDao
    abstract fun mediaDao(): MediaDao
    abstract fun pendingUploadDao(): PendingUploadDao
    abstract fun pendingOperationDao(): PendingOperationDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: SecureChatDatabase? = null

        fun getInstance(context: Context): SecureChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureChatDatabase::class.java,
                    "securechat.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Version 2's schema is identical to version 1 (the bump only
        // registered the exported schema), so this migration is a no-op kept
        // for version continuity. Destructive migration is deliberately not
        // enabled: user data must survive app updates.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No schema change between versions 1 and 2.
            }
        }
    }
}