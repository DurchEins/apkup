package com.apkup.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.work.WorkManager
import com.apkup.R
import com.apkup.data.ui.CachedUpdateSnapshot
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
            }.onFailure {
                notifyWidgetDataChanged(context, isChecking = false)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.apkup.action.WIDGET_REFRESH"

        fun notifyWidgetDataChanged(context: Context, isChecking: Boolean = false) {
            runCatching {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(ComponentName(appContext, UpdatesWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    updateWidgets(appContext, manager, ids, isChecking = isChecking)
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
            val updateCount = if (prefs != null) {
                countVisibleWidgetUpdates(snapshot, prefs)
            } else {
                snapshot?.updates?.count { it.oldVersionCode >= 0L } ?: 0
            }
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
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
                    setViewVisibility(R.id.widget_refresh_button, if (isChecking) View.GONE else View.VISIBLE)
                    setViewVisibility(R.id.widget_refresh_progress, if (isChecking) View.VISIBLE else View.GONE)
                    setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                    setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

internal fun countVisibleWidgetUpdates(
    snapshot: CachedUpdateSnapshot?,
    prefs: Prefs
): Int {
    val rawUpdates = snapshot?.updates.orEmpty()
    if (rawUpdates.isEmpty()) return 0
    val versionsFilterActive = prefs.ignoreVersionsFilterActive.get()
    val appsFilterActive = prefs.ignoreAppsFilterActive.get()
    val ignoredVersionIds = if (versionsFilterActive) prefs.ignoredVersions.get().toSet() else emptySet()
    val ignoredEntries = if (versionsFilterActive) prefs.readIgnoredVersionEntriesSafe() else emptyList()
    val ignoredPackages = if (appsFilterActive) prefs.ignoredApps.get().toSet() else emptySet()
    return rawUpdates.count { dto ->
        if (dto.oldVersionCode < 0L || dto.packageName.isBlank()) return@count false
        if (dto.packageName in ignoredPackages) return@count false
        if (!prefs.isPreReleaseCandidateAllowed(dto.packageName, dto.version)) return@count false
        if (prefs.isUpdateSnoozed(dto.packageName, dto.versionCode, dto.version)) return@count false
        val computedId = "${dto.sourceName}.${dto.packageName}.${dto.versionCode}.${dto.version}".hashCode()
        val matchesEntry = ignoredEntries.any { entry ->
            entry.matchesUpdate(dto.packageName, dto.versionCode, dto.version, computedId)
        }
        computedId !in ignoredVersionIds && !matchesEntry
    }
}
