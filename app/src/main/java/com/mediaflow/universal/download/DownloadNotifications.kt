package com.mediaflow.universal.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mediaflow.universal.R
import com.mediaflow.universal.ui.MainActivity

object DownloadNotifications {
    const val CHANNEL_ID = "mediaflow_downloads"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notification_channel_desc) }
        )
    }

    /** The persistent notification used by our own foreground service while downloads run. */
    fun foregroundNotification(context: Context): android.app.Notification {
        createChannel(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            flags
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.downloads_running))
            .setContentText(context.getString(R.string.tap_to_open))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    /** Posts a per-file progress notification. */
    fun postProgress(
        context: Context,
        notificationId: Int,
        title: String,
        progress: Int,
        indeterminate: Boolean = false
    ) {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            flags
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notification, title))
            .setContentText(if (indeterminate) context.getString(R.string.analyzing) else "$progress%")
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()
        runCatching { manager.notify(notificationId, notification) }
    }

    fun cancel(context: Context, notificationId: Int) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }
    }
}
