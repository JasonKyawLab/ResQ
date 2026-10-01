package com.resq.app.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.resq.app.MainActivity
import com.resq.app.R

const val NOTIFICATION_CHANNEL_ID = "resq_radio"
const val FOREGROUND_NOTIFICATION_ID = 1

/** Builds the single low-priority notification a foreground service in Android requires. */
object ResQNotifications {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.service_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, isSending: Boolean, isReceiving: Boolean): android.app.Notification {
        ensureChannel(context)

        val title = when {
            isSending && isReceiving -> context.getString(R.string.service_notification_title_both)
            isSending -> context.getString(R.string.service_notification_title_broadcasting)
            else -> context.getString(R.string.service_notification_title_listening)
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, ResQForegroundService::class.java)
                .setAction(ResQForegroundService.ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.service_notification_stop_action), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
