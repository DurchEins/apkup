package com.apkup.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        const val UpdateAction = "com.apkup.action.OPEN_UPDATES"
        const val RETRY_FAILED_ACTION = "com.apkup.action.RETRY_FAILED_UPDATES"
        const val UPDATE_CHANNEL_ID = "update_notification_channel"
        const val CHECK_PROGRESS_NOTIFICATION_ID = 43
        private const val CHECK_PROGRESS_CHANNEL_ID = "update_check_progress_channel"
    }

    private val notificationManager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val updateId = 42
    private val checkProgressId = CHECK_PROGRESS_NOTIFICATION_ID

    private fun openUpdatesPendingIntent(requestCode: Int, action: String = UpdateAction): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            this.action = action
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildCheckProgressNotification(
        checkedApps: Int? = null,
        totalApps: Int? = null,
        activeSource: String? = null
    ): Notification {
        createCheckProgressChannel()

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

        return NotificationCompat.Builder(context, CHECK_PROGRESS_CHANNEL_ID)
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
            .setContentIntent(openUpdatesPendingIntent(requestCode = 1))
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showCheckProgressNotification(
        checkedApps: Int?,
        totalApps: Int?,
        activeSource: String?
    ) {
        if (!areNotificationsEnabled()) return
        NotificationManagerCompat.from(context).notify(
            checkProgressId,
            buildCheckProgressNotification(checkedApps, totalApps, activeSource)
        )
    }

    fun cancelCheckProgressNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(checkProgressId)
        }
    }

    @SuppressLint("MissingPermission")
    fun showUpToDateNotification() {
        if (!areNotificationsEnabled()) return
        createNotificationChannel()
        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(context.getString(R.string.notification_up_to_date_title))
            .setContentText(context.getString(R.string.notification_up_to_date_desc))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openUpdatesPendingIntent(requestCode = 5))
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(updateId, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showUpdateCheckFailedNotification() {
        if (!areNotificationsEnabled()) return
        createNotificationChannel()
        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(context.getString(R.string.updates_error_title))
            .setContentText(context.getString(R.string.updates_error_message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openUpdatesPendingIntent(requestCode = 6))
            .setAutoCancel(true)
        NotificationManagerCompat.from(context).notify(updateId, builder.build())
    }

    @SuppressLint("MissingPermission")
    override fun showUpdateNotification(num: Int) {
        if (!areNotificationsEnabled()) return
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = UpdateAction
        }

        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(context.getString(R.string.notification_update_title))
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

        val prefs = runCatching { org.koin.java.KoinJavaComponent.getKoin().getOrNull<com.apkup.prefs.Prefs>() }.getOrNull()
        val canSilentInstall = prefs != null && (prefs.useShizukuInstaller.get() || prefs.useRootInstaller.get())
        if (canSilentInstall && num > 0) {
            val installIntent = Intent(context, com.apkup.worker.NotificationInstallAllReceiver::class.java).apply {
                setPackage(context.packageName)
            }
            val installPendingIntent = PendingIntent.getBroadcast(
                context,
                4,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_install,
                context.getString(R.string.notification_install_all_action),
                installPendingIntent
            )
        }

        NotificationManagerCompat.from(context).notify(updateId, builder.build())
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
        if (!areNotificationsEnabled()) return

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

        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = UpdateAction
        }

        val builder = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
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

        if (manual.isNotEmpty()) {
            val retryIntent = Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = RETRY_FAILED_ACTION
            }
            val retryPendingIntent = PendingIntent.getActivity(
                context,
                3,
                retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_install,
                context.getString(R.string.batch_retry_failed_short),
                retryPendingIntent
            )
        }

        NotificationManagerCompat.from(context).notify(updateId, builder.build())
    }

    private fun areNotificationsEnabled() = NotificationManagerCompat
        .from(context)
        .areNotificationsEnabled()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createCheckProgressChannel() {
        val channel = NotificationChannel(
            CHECK_PROGRESS_CHANNEL_ID,
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
