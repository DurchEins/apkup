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

internal data class AutoUpdateSummaryContent(
    val title: String,
    val contentText: String,
    val bigText: String
)

internal fun formatAutoUpdateSummary(
    updatedAppNames: List<String>,
    manualRequiredAppNames: List<String>,
    completeTitle: String,
    partialTitle: String,
    completeDescProvider: (Int) -> String,
    partialDescProvider: (Int, Int) -> String,
    updatedListProvider: (String) -> String,
    manualReadyListProvider: (String) -> String
): AutoUpdateSummaryContent? {
    val updated = updatedAppNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val manual = manualRequiredAppNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (updated.isEmpty()) return null

    return if (manual.isEmpty()) {
        val contentText = completeDescProvider(updated.size)
        val bigText = buildString {
            appendLine(contentText)
            append(updatedListProvider(updated.joinToString(", ")))
        }
        AutoUpdateSummaryContent(
            title = completeTitle,
            contentText = contentText,
            bigText = bigText
        )
    } else {
        val contentText = partialDescProvider(updated.size, manual.size)
        val bigText = buildString {
            appendLine(contentText)
            appendLine(updatedListProvider(updated.joinToString(", ")))
            append(manualReadyListProvider(manual.joinToString(", ")))
        }
        AutoUpdateSummaryContent(
            title = partialTitle,
            contentText = contentText,
            bigText = bigText
        )
    }
}

interface AutoUpdateNotifier {
    fun showUpdateNotification(num: Int)
    fun showAutoUpdateSummaryNotification(
        updatedAppNames: List<String>,
        manualRequiredAppNames: List<String>
    )
}

open class UpdatesNotification(private val context: Context) : AutoUpdateNotifier {

    companion object {
        const val UpdateAction = "updateAction"
    }

    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId by lazy { context.getString(R.string.notification_channel_id) }
    private val channelName by lazy { context.getString(R.string.notification_channel_name) }
    private val updateTitle by lazy { context.getString(R.string.notification_update_title) }
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
                checkedApps.coerceAtMost(totalApps),
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
                if (hasDeterminateProgress) totalApps else 0,
                if (hasDeterminateProgress) checkedApps.coerceIn(0, totalApps) else 0,
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
    override fun showUpdateNotification(num: Int) {
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

    @SuppressLint("MissingPermission")
    override fun showAutoUpdateSummaryNotification(
        updatedAppNames: List<String>,
        manualRequiredAppNames: List<String>
    ) {
        val updated = updatedAppNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val manual = manualRequiredAppNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (updated.isEmpty() && manual.isEmpty()) return
        if (updated.isEmpty()) {
            showUpdateNotification(manual.size)
            return
        }

        val summary = formatAutoUpdateSummary(
            updatedAppNames = updated,
            manualRequiredAppNames = manual,
            completeTitle = context.getString(R.string.notification_auto_update_complete_title),
            partialTitle = context.getString(R.string.notification_auto_update_partial_title),
            completeDescProvider = { count ->
                context.resources.getQuantityString(
                    R.plurals.notification_auto_update_complete_desc,
                    count,
                    count
                )
            },
            partialDescProvider = { updatedCount, manualCount ->
                context.getString(
                    R.string.notification_auto_update_partial_desc,
                    updatedCount,
                    manualCount
                )
            },
            updatedListProvider = { names ->
                context.getString(R.string.notification_auto_update_updated_list, names)
            },
            manualReadyListProvider = { names ->
                context.getString(R.string.notification_auto_update_manual_ready_list, names)
            }
        ) ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = UpdateAction
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(summary.title)
            .setContentText(summary.contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    2,
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
