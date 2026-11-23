package com.apkup.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.WorkManager
import com.apkup.R
import com.apkup.prefs.Prefs
import com.apkup.prefs.TinkSecurePrefsBackend
import com.apkup.ui.activity.MainActivity
import com.apkup.util.UpdatesNotification
import com.apkup.worker.UpdatesWorker
import org.koin.java.KoinJavaComponent
import java.text.DateFormat
import java.util.Date

class UpdatesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds, isChecking = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UpdatesWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids, isChecking = true)
            }
            runCatching {
                UpdatesWorker.launchImmediate(WorkManager.getInstance(context.applicationContext))
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.apkup.action.WIDGET_REFRESH"

        fun notifyWidgetDataChanged(context: Context) {
            runCatching {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(ComponentName(appContext, UpdatesWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    updateWidgets(appContext, manager, ids, isChecking = false)
                }
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            isChecking: Boolean
        ) {
            val prefs = runCatching { KoinJavaComponent.getKoin().getOrNull<Prefs>() }.getOrNull()
                ?: runCatching { Prefs(TinkSecurePrefsBackend.create(context.applicationContext)) }.getOrNull()

            val snapshot = runCatching { prefs?.readCachedSnapshotSafe() }.getOrNull()
            val updateCount = snapshot?.updates?.size ?: 0
            val lastCheckedMs = snapshot?.timestampMillis ?: 0L

            val statusText = when {
                isChecking -> context.getString(R.string.widget_updates_checking)
                lastCheckedMs <= 0L -> context.getString(R.string.widget_updates_never_checked)
                updateCount == 0 -> context.getString(R.string.widget_updates_up_to_date)
                else -> context.resources.getQuantityString(
                    R.plurals.widget_updates_available,
                    updateCount,
                    updateCount
                )
            }

            val timeText = if (lastCheckedMs > 0L) {
                val formatted = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastCheckedMs))
                context.getString(R.string.widget_updates_last_checked, formatted)
            } else {
                context.getString(R.string.widget_updates_tap_to_check)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = UpdatesNotification.UpdateAction
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val refreshIntent = Intent(context, UpdatesWidgetProvider::class.java).apply {
                setPackage(context.packageName)
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                11,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_updates).apply {
                    setTextViewText(R.id.widget_status_title, statusText)
                    setTextViewText(R.id.widget_last_checked, timeText)
                    setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                    setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
