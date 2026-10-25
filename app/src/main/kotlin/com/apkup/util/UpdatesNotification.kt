package com.apkup.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.apkup.R
import com.apkup.ui.activity.MainActivity

class UpdatesNotification(private val context: Context) {

    companion object {
        const val UpdateAction = "updateAction"
    }

    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = context.getString(R.string.notification_channel_id)
    private val channelName = context.getString(R.string.notification_channel_name)
    private val updateTitle = context.getString(R.string.notification_update_title)
    private val updateId = 42
    private val checkProgressChannelId = "update_check_progress_channel"
    private val checkProgressId = 43

    @SuppressLint("MissingPermission")
    fun showCheckProgressNotification(
        checkedApps: Int?,
        totalApps: Int?,
        activeSource: String?
    ) {
        if (!areNotificationsEnabled()) return
        createCheckProgressChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = UpdateAction
        }

        val title = if (!activeSource.isNullOrBlank()) {
            context.getString(R.string.notification_check_title_source, activeSource)
        } else {
            context.getString(R.string.notification_check_title)
        }

        val hasDeterminateProgress = checkedApps != null && totalApps != null && totalApps > 0
        val contentText = if (hasDeterminateProgress) {
            context.getString(
                R.string.notification_check_progress_text,
                checkedApps!!.coerceAtMost(totalApps!!),
                totalApps
            )
        } else {
            context.getString(R.string.updates_checking)
        }

        val builder = NotificationCompat.Builder(context, checkProgressChannelId)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(
                if (hasDeterminateProgress) totalApps!! else 0,
                if (hasDeterminateProgress) checkedApps!!.coerceIn(0, totalApps!!) else 0,
                !hasDeterminateProgress
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

        NotificationManagerCompat.from(context).notify(checkProgressId, builder.build())
    }

    fun cancelCheckProgressNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(checkProgressId)
        }
    }

    @SuppressLint("MissingPermission")
    fun showUpdateNotification(num: Int) {
        // Intent for the notification click
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = UpdateAction
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(updateTitle)
            .setContentText(context.resources.getQuantityString(R.plurals.notification_update_description, num, num))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)

        createNotificationChannel()
        if (areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(updateId, builder.build())
        }
    }

    fun checkNotificationPermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!areNotificationsEnabled()) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun areNotificationsEnabled() = NotificationManagerCompat
        .from(context)
        .areNotificationsEnabled()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createCheckProgressChannel() {
        val channel = NotificationChannel(
            checkProgressChannelId,
            context.getString(R.string.notification_check_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_check_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

}
