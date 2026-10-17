package com.apkup.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.prefs.Prefs
import com.apkup.repository.UpdatesRepository
import com.apkup.util.UpdatesNotification
import com.apkup.util.millisUntilHour
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.random.Random


class UpdatesWorker(
    context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context, workerParams), KoinComponent {

    companion object: KoinComponent {
        private const val TAG = "UpdatesWorker"
        private val prefs: Prefs by inject()

        fun cancel(workManager: WorkManager) = workManager.cancelUniqueWork(TAG)

        fun launch(workManager: WorkManager) {
            val intervalHours = resolvePeriodicIntervalHours(prefs.backgroundCheckIntervalHours.get())
            val initialDelayMillis = if (intervalHours >= 24L) {
                (millisUntilHour(12) + randomDelay()).coerceAtLeast(0L)
            } else {
                (intervalHours * 60L * 60L * 1_000L / 2L + randomDelay()).coerceAtLeast(15L * 60L * 1_000L)
            }
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(intervalHours, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun randomDelay() = if (prefs.useApkMirror.get())
            Random.nextLong(0, 59 * 60 * 1_000)
        else
            Random.nextLong(-5 * 60 * 1_000, 5 * 60 * 1_000)

    }

    private val updatesRepository: UpdatesRepository by inject()
    private val notification: UpdatesNotification by inject()

    override suspend fun doWork(): Result {
        var finalResult: UpdateCheckResult<com.apkup.data.ui.AppUpdate>? = null
        updatesRepository.updates().collect { emission ->
            if (!emission.isChecking) {
                finalResult = emission
            }
        }
        val completed = finalResult ?: return Result.retry()
        val notifiableUpdates = filterNotifiableUpdates(
            result = completed,
            ignoredVersionIds = prefs.ignoredVersions.get(),
            ignoredPackages = prefs.ignoredApps.get()
        )
        if (notifiableUpdates.isNotEmpty()) {
            notification.showUpdateNotification(notifiableUpdates.size)
        }
        return if (completed.hasFailures) Result.retry() else Result.success()
    }

}

internal fun filterNotifiableUpdates(
    result: UpdateCheckResult<com.apkup.data.ui.AppUpdate>,
    ignoredVersionIds: Collection<Int>,
    ignoredPackages: Collection<String> = emptySet()
): List<com.apkup.data.ui.AppUpdate> {
    if (result.isChecking) return emptyList()
    val ignoredIdSet = ignoredVersionIds.toSet()
    val ignoredPackageSet = ignoredPackages.toSet()
    return result.updates.filter { update ->
        com.apkup.data.ui.isVerifiableVersionUpgrade(
            installedVersion = update.oldVersion,
            availableVersion = update.version,
            installedVersionCode = update.oldVersionCode,
            availableVersionCode = update.versionCode
        ) &&
            update.id !in ignoredIdSet &&
            update.packageName !in ignoredPackageSet
    }
}

internal fun resolvePeriodicIntervalHours(rawHours: Int): Long =
    com.apkup.data.ui.BackgroundCheckInterval.fromHours(rawHours).hours.toLong()

