package com.apkup.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.apkup.util.DownloadNotificationManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * WorkManager User-Initiated Data Transfer (UIDT) worker (`F-21`) replacing the legacy
 * `DownloadInstallService` so interactive user downloads and batch updates run reliably
 * across Android 14, 15, 16, and 17 without ForegroundService timeout crashes.
 */
class UpdateDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    private val coordinator: DownloadCoordinator by inject()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return wrapForegroundInfo(coordinator.buildInitialForegroundNotification())
    }

    override suspend fun doWork(): Result {
        if (!coordinator.hasActiveTasks()) {
            return Result.success()
        }

        runCatching {
            setForeground(getForegroundInfo())
        }

        coordinator.awaitActiveQueueDrain { notification ->
            runCatching {
                setForegroundAsync(wrapForegroundInfo(notification))
            }
        }

        // If a new task arrived right as the previous queue finished draining, re-arm UIDT worker.
        if (coordinator.hasActiveTasks()) {
            coordinator.ensureWorkerScheduled(replaceExisting = true)
        }
        return Result.success()
    }

    private fun wrapForegroundInfo(notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                DownloadNotificationManager.NOTIFICATION_ID,
                notification
            )
        }
    }
}
