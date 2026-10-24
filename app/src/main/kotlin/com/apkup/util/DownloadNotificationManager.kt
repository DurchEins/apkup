package com.apkup.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import com.apkup.R
import com.apkup.service.DownloadInstallService
import com.apkup.ui.activity.MainActivity

class DownloadNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "download_install_channel"
        const val NOTIFICATION_ID = 2001
        private const val THROTTLE_MS = 250L
    }

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private var lastUpdateTime = 0L
    private var lastPercent = -1

    init {
        createNotificationChannel()
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_downloads_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Determines whether to throttle a notification update based on elapsed time and progress delta.
     */
    fun shouldThrottle(percent: Int, force: Boolean = false): Boolean {
        if (force) {
            lastUpdateTime = System.currentTimeMillis()
            lastPercent = percent
            return false
        }
        val now = System.currentTimeMillis()
        val timeDelta = now - lastUpdateTime
        val percentDelta = kotlin.math.abs(percent - lastPercent)

        return if (timeDelta >= THROTTLE_MS || percentDelta >= 2 || percent == 100 || percent == 0) {
            lastUpdateTime = now
            lastPercent = percent
            false
        } else {
            true
        }
    }

    /**
     * Builds ongoing notification for active download or installation.
     */
    fun buildNotification(
        appName: String,
        currentBytes: Long,
        totalBytes: Long,
        isInstalling: Boolean,
        activeUpdateId: Int?,
        batchIndex: Int = 1,
        batchTotal: Int = 1
    ): Notification {
        val title = if (batchTotal > 1) {
            context.getString(R.string.notification_batch_title, appName, batchIndex, batchTotal)
        } else {
            appName
        }

        val queueRemaining = (batchTotal - batchIndex).coerceAtLeast(0)

        val (contentText, percent, indeterminate) = when {
            isInstalling -> {
                Triple(context.getString(R.string.notification_installing_named, appName), 0, true)
            }
            totalBytes > 0L -> {
                val pct = ((currentBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                val currFormatted = Formatter.formatFileSize(context, currentBytes)
                val totFormatted = Formatter.formatFileSize(context, totalBytes)
                val text = if (queueRemaining > 0) {
                    context.getString(R.string.notification_download_progress_queued, currFormatted, totFormatted, pct, queueRemaining)
                } else {
                    context.getString(R.string.notification_download_progress, currFormatted, totFormatted, pct)
                }
                Triple(text, pct, false)
            }
            currentBytes == 0L && totalBytes == 0L -> {
                Triple(context.getString(R.string.notification_download_starting), 0, true)
            }
            else -> {
                Triple(context.getString(R.string.notification_download_unknown), 0, true)
            }
        }

        // Tap notification intent -> Brings MainActivity to front
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = UpdatesNotification.UpdateAction
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isInstalling) R.drawable.ic_install else R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentPendingIntent)

        if (indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent, false)
        }

        // Cancel action PendingIntent targeting DownloadInstallService (only during download)
        if (!isInstalling && activeUpdateId != null) {
            val cancelIntent = Intent(context, DownloadInstallService::class.java).apply {
                action = DownloadInstallService.ACTION_CANCEL_DOWNLOAD
                putExtra(DownloadInstallService.EXTRA_UPDATE_ID, activeUpdateId)
            }
            val cancelPendingIntent = PendingIntent.getService(
                context,
                activeUpdateId,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_cancel),
                cancelPendingIntent
            )
        }

        return builder.build()
    }

    fun update(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
