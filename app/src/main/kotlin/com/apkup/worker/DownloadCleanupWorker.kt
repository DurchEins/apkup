package com.apkup.worker

/**
 * A periodic WorkManager job that runs daily to clean up leftover download files.
 * It delegates the actual deletion logic to [DownloadStorageManager] to ensure the device
 * storage isn't cluttered by old, abandoned, or already installed APK files.
 */
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkup.util.DownloadStorageManager
import java.util.concurrent.TimeUnit

class DownloadCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val UNIQUE_WORK_NAME = "DownloadCleanupWorker"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<DownloadCleanupWorker>(24, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        DownloadStorageManager.cleanExpiredDownloads(applicationContext)
        return Result.success()
    }
}
