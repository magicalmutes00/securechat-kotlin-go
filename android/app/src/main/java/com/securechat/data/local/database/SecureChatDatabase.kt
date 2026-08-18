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
    version = 1,
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
                    .addMigrations(MIGRATION_1_2) // Add future migrations here
                    .fallbackToDestructiveMigration() // For development only
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Future migrations will be added here
        // Example migration from version 1 to 2:
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         // Add migration SQL here
        //     }
        // }
        
        // Placeholder for future migrations
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // No-op placeholder - real migrations will be added when schema changes
            }
        }
    }
}