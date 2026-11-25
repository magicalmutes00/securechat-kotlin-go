package com.securechat.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.securechat.R
import com.securechat.MainActivity

/**
 * Builds and displays push notifications for incoming messages.
 *
 * The notification channel is created once on app start; subsequent calls are
 * idempotent (creating an existing channel is a no-op). The notification
 * payload never contains message plaintext — it carries only a generic alert
 * and a conversation id, so tapping opens the chat and the app fetches the
 * message over TLS/WSS.
 */
object NotificationHelper {

    private const val NOTIFICATION_ID = 1
    private const val REQUEST_CODE = 1001

    /** Key for the conversation id extra carried on the notification tap intent. */
    const val EXTRA_CONVERSATION_ID = "conversation_id"

    /**
     * Creates the "securechat_messages" notification channel. Call once at app
     * initialization (e.g. in [FcmMessagingService] onCreate).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "securechat_messages",
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Displays a notification for a new message. Tapping opens [MainActivity],
     * which navigates to the conversation identified by [conversationId].
     * Callers must ensure [createNotificationChannel] has been called first.
     */
    fun showNewMessageNotification(
        context: Context,
        conversationId: Long,
        senderName: String?,
        messageId: Long
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = if (senderName?.isNotBlank() == true) {
            context.getString(R.string.notification_new_message_from, senderName)
        } else {
            context.getString(R.string.notification_new_message_title)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra("message_id", messageId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, "securechat_messages")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_tap_to_view))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        manager.notify(NOTIFICATION_ID, builder.build())
    }
}
