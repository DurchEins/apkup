package com.apkup.worker

/**
 * A periodic WorkManager background job that checks for updates of installed apps.
 * It silently connects to the configured repositories (GitHub, Play Store, etc.) in the background,
 * compares versions, and triggers a system notification or autonomous Shizuku installation if new updates are available.
 */
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.RepoMappingManager
import com.apkup.repository.UpdatesRepository
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import com.apkup.util.SessionInstaller
import com.apkup.util.UpdatesNotification
import com.apkup.util.millisUntilHour
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.withTimeoutOrNull


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
            val networkType = if (prefs.backgroundCheckWifiOnly.get()) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
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
    private val downloader: Downloader by inject()
    private val apkMirrorResolver: ApkMirrorResolver by inject()
    private val installer: SessionInstaller by inject()
    private val repoMappingManager: RepoMappingManager by inject()

    override suspend fun doWork(): Result {
        var finalResult: UpdateCheckResult<com.apkup.data.ui.AppUpdate>? = null
        var lastEmission: UpdateCheckResult<com.apkup.data.ui.AppUpdate>? = null
        withTimeoutOrNull(90_000L) {
            updatesRepository.updates().collect { emission ->
                lastEmission = emission
                if (!emission.isChecking) {
                    finalResult = emission
                }
            }
        }
        val completed = finalResult ?: lastEmission ?: return Result.retry()
        val notifiableUpdates = filterNotifiableUpdates(
            result = completed,
            ignoredVersionIds = if (prefs.ignoreVersionsFilterActive.get()) prefs.ignoredVersions.get() else emptyList(),
            ignoredPackages = if (prefs.ignoreAppsFilterActive.get()) prefs.ignoredApps.get() else emptyList()
        )
        if (notifiableUpdates.isNotEmpty()) {
            val autoInstallEnabled = prefs.useShizukuInstaller.get() && prefs.autoInstallUpdates.get()
            if (autoInstallEnabled) {
                val wifiOnly = prefs.autoInstallWifiOnly.get()
                val chargingOnly = prefs.autoInstallChargingOnly.get()
                if (areAutoInstallConstraintsMet(applicationContext, wifiOnly, chargingOnly)) {
                    val runner = ShizukuAutoUpdateRunner(
                        storageBaseDir = DownloadStorageManager.getDownloadDir(applicationContext),
                        prefs = prefs,
                        downloader = downloader,
                        apkMirrorResolver = apkMirrorResolver,
                        installer = installer,
                        notification = notification,
                        repoMappingManager = repoMappingManager
                    )
                    runner.execute(notifiableUpdates)
                } else {
                    ShizukuAutoUpdateWorker.enqueue(
                        workManager = WorkManager.getInstance(applicationContext),
                        wifiOnly = wifiOnly,
                        chargingOnly = chargingOnly
                    )
                }
            } else {
                notification.showUpdateNotification(notifiableUpdates.size)
            }
        }
        return if (completed.isCompleteFailure && runAttemptCount < 3) Result.retry() else Result.success()
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
