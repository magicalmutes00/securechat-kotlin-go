package com.securechat.core.push

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securechat.core.utils.UserSession
import com.securechat.domain.repository.PushRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Firebase Messaging service. Handles:
 *
 * 1. [onNewToken] — registers the FCM registration token with the backend via
 *    [PushRepository] so the server can send pushes to this device.
 * 2. [onMessageReceived] — displays a local notification when a push arrives
 *    while the app is in the background or foreground.
 *
 * Push notifications never contain message plaintext. The FCM payload carries
 * only a conversation id; tapping the notification opens the conversation and
 * the app fetches the message over TLS/WSS.
 */
@AndroidEntryPoint
class FcmMessagingService @Inject constructor(
    private val pushRepository: PushRepository
) : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    /**
     * Called when the FCM registration token is generated or refreshed. The
     * token is cached locally and registered with the backend via
     * [PushRepository]. Runs on a background thread, so [runBlocking] is safe.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        runBlocking {
            pushRepository.storeFcmToken(token)
            if (UserSession.currentUserId > 0L) {
                pushRepository.registerPushToken(token, "android")
            }
        }
    }

    /**
     * Called when an FCM message arrives. Displays a local notification if the
     * user has notifications enabled. Message plaintext is never in the push
     * payload — only a conversation id — so the server remains a blind relay.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val conversationId = data["conversation_id"]?.toLongOrNull() ?: return
        val senderName = data["sender_name"] ?: null
        val messageId = data["message_id"]?.toLongOrNull() ?: 0L

        runBlocking {
            val enabled = pushRepository.isPushEnabled().getOrElse { true }
            if (!enabled) return@runBlocking
        }

        NotificationHelper.showNewMessageNotification(
            this@FcmMessagingService,
            conversationId,
            senderName,
            messageId
        )
    }

    companion object {
        @JvmStatic
        fun isPushEnabled(context: Context): Boolean {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
