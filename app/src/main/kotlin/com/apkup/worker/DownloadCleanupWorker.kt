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
import com.apkup.prefs.Prefs
import com.apkup.util.DownloadStorageManager
import com.apkup.util.RollbackBackupManager
import org.koin.java.KoinJavaComponent
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
        runCatching {
            val prefs = KoinJavaComponent.getKoin().getOrNull<Prefs>()
                ?: Prefs(com.apkup.prefs.TinkSecurePrefsBackend.create(applicationContext))
            val manager = KoinJavaComponent.getKoin().getOrNull<RollbackBackupManager>()
                ?: RollbackBackupManager(applicationContext)
            val maxBudgetBytes = RollbackBackupManager.budgetMbToBytes(prefs.rollbackMaxBudgetMb.get())
            val maxAgeDays = RollbackBackupManager.normalizeMaxAgeDays(prefs.rollbackMaxAgeDays.get())
            manager.cleanExpiredBackups(maxAgeDays = maxAgeDays, maxTotalBytes = maxBudgetBytes)
        }
        return Result.success()
    }
}
