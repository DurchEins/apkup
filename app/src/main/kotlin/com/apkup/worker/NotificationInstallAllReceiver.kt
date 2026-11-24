package com.apkup.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class NotificationInstallAllReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(42)
        }
        val request = OneTimeWorkRequestBuilder<ShizukuAutoUpdateWorker>()
            .setInputData(workDataOf(ShizukuAutoUpdateWorker.KEY_FORCE_IMMEDIATE to true))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "NotificationInstallAll",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
