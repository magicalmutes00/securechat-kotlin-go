package com.securechat.core.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.securechat.R
import com.securechat.domain.usecase.sync.SyncPendingMessagesUseCase
import com.securechat.domain.usecase.sync.SyncPendingMediaUseCase
import com.securechat.domain.usecase.sync.SyncReadReceiptsUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncForegroundService : LifecycleService() {

    @Inject
    lateinit var syncPendingMessagesUseCase: SyncPendingMessagesUseCase
    
    @Inject
    lateinit var syncPendingMediaUseCase: SyncPendingMediaUseCase
    
    @Inject
    lateinit var syncReadReceiptsUseCase: SyncReadReceiptsUseCase

    private var wakeLock: PowerManager.WakeLock? = null
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "sync_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Perform sync
        performSync()
        
        return START_STICKY
    }

    private fun performSync() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Sync pending messages
                syncPendingMessagesUseCase()
                
                // Sync pending media
                syncPendingMediaUseCase()
                
                // Sync read receipts
                syncReadReceiptsUseCase()
                
            } catch (e: Exception) {
                // Log error
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Message Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization of messages and media"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SecureChat")
            .setContentText("Syncing messages...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SecureChat::SyncWakeLock"
        ).apply {
            acquire(30 * 60 * 1000) // 30 minutes max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopForeground(true)
        super.onDestroy()
    }
}