package com.exmworkspace.exmwsmail.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.exmworkspace.exmwsmail.MainActivity
import com.exmworkspace.exmwsmail.R

object MailNotifications {

    const val CHANNEL_NEW = "exm_mail_new"
    const val EXTRA_FOLDER = "notification_folder"
    const val EXTRA_UID = "notification_uid"
    private const val NEW_NOTIFICATION_ID = 200

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_NEW) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_NEW,
                    "Correos nuevos",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifica cuando llega un correo nuevo."
                    enableVibration(true)
                    // Feeds the launcher icon badge (the dot on stock Android; the number on
                    // launchers that draw one). True is the default, but the badge is a
                    // feature here, not an accident — so it is stated.
                    setShowBadge(true)
                },
            )
        }
    }

    fun postNewMailNotification(
        context: Context,
        title: String,
        text: String,
        folder: String? = null,
        uid: String? = null,
        /** Unread mails in the folder; launchers that draw numeric badges display it (99+ capped by the launcher, as Gmail's is). */
        unreadCount: Int = 0,
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            folder?.let { putExtra(EXTRA_FOLDER, it) }
            uid?.let { putExtra(EXTRA_UID, it) }
        }
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_NEW)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .apply { if (unreadCount > 0) setNumber(unreadCount) }
            .build()
        try {
            nm.notify(NEW_NOTIFICATION_ID, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+.
        }
    }
}
