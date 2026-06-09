package com.example.exmwsmail.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.exmwsmail.MainActivity
import com.example.exmwsmail.R

object MailNotifications {

    // v2 because we cannot change importance of an already-registered channel.
    const val CHANNEL_SYNC = "exm_mail_sync_v2"
    const val CHANNEL_NEW = "exm_mail_new"
    const val FG_NOTIFICATION_ID = 100
    private const val NEW_NOTIFICATION_ID = 200

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_SYNC) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SYNC,
                    "Sincronización en segundo plano",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Mantiene la conexión para recibir correo en tiempo real."
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_NEW) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_NEW,
                    "Correos nuevos",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifica cuando llega un correo nuevo."
                    enableVibration(true)
                },
            )
        }
    }

    fun buildForegroundNotification(context: Context): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle("EXM WS Mail")
            .setContentText("Conectado — vigilando correos nuevos")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .build()
    }

    fun postNewMailNotification(
        context: Context,
        title: String,
        text: String,
        count: Int,
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_NEW)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setNumber(count)
            .setContentIntent(openIntent)
            .build()
        try {
            nm.notify(NEW_NOTIFICATION_ID, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS no concedido en Android 13+
        }
    }
}
