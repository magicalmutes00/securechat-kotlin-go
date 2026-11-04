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
import com.securechat.data.local.dao.E2eeSessionDao
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
import com.securechat.data.local.entity.E2eeSessionEntity
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
        UserSettingsEntity::class,
        E2eeSessionEntity::class
    ],
    version = 8,
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
    abstract fun e2eeSessionDao(): E2eeSessionDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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

        // Version 3 adds the avatar URL (resolved server-side from the media
        // table) so the profile header can render the photo without a lookup.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE users ADD COLUMN avatarUrl TEXT")
            }
        }

        // Version 4 adds the group conversation title, mirroring the server's
        // conversations.name (server migration 007). Direct conversations keep it
        // NULL and continue to derive their title from the other participant.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversations ADD COLUMN name TEXT")
            }
        }

        // Version 5 caches the message author's display name, mirroring the
        // server's messages payload. Without it a group chat loses its authors
        // the moment the app restarts, because the local participant rows carry
        // ids but not names.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN senderName TEXT")
            }
        }

        // Version 6 caches per-participant presence so the chat header shows the
        // real online state instead of a hardcoded "Online", and so the
        // USER_ONLINE / USER_OFFLINE socket events have somewhere to land.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversation_participants ADD COLUMN isOnline INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE conversation_participants ADD COLUMN lastSeen INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Version 7 adds E2EE envelope columns to messages so each device can
        // persist its own per-recipient ciphertext for history decryption.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN contentType TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN envelopeType INTEGER")
                database.execSQL("ALTER TABLE messages ADD COLUMN envelopeCiphertext TEXT")
            }
        }

        // Version 8 adds the signal_sessions table, which persists libsignal
        // session state (ratchet keys, chain keys, counters) per recipient so a
        // device can decrypt E2EE history after restarting.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS signal_sessions (
                        recipient_name TEXT NOT NULL,
                        device_id INTEGER NOT NULL,
                        record BLOB NOT NULL,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (recipient_name, device_id)
                    )
                """.trimIndent())
            }
        }
    }
}
