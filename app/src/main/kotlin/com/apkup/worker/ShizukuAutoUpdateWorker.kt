package com.apkup.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.data.ui.isWatchedApp
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolutionResult
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.RepoMappingManager
import com.apkup.repository.UpdatesRepository
import com.apkup.util.ApkIntegrityException
import com.apkup.util.ApkIntegrityVerifier
import com.apkup.util.ApkSignatureCheckResult
import com.apkup.util.ApkSignatureVerifier
import com.apkup.util.ApkmExtractor
import com.apkup.util.AppJournal
import com.apkup.util.AutoUpdateNotifier
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import kotlinx.coroutines.withTimeoutOrNull
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.UpdatesNotification
import com.apkup.util.buildUpdateHistoryEntry
import com.aurora.gplayapi.model.AppFile
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException

internal data class AutoUpdateExecutionSummary(
    val updatedPackages: List<String>,
    val manualRequiredPackages: List<String>,
    val shizukuWasReady: Boolean
)

internal class ShizukuAutoUpdateRunner(
    private val storageBaseDir: File,
    private val prefs: Prefs,
    private val downloader: Downloader,
    private val apkMirrorResolver: ApkMirrorResolver,
    private val installer: SessionInstaller,
    private val notification: AutoUpdateNotifier,
    private val repoMappingManager: RepoMappingManager? = null,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "ShizukuAutoUpdateRunner"
    }

    suspend fun execute(updates: List<AppUpdate>): AutoUpdateExecutionSummary {
        val installableUpdates = updates.filterNot {
            it.isWatchedApp() || prefs.isVersionMarkedIncompatible(it.packageName, it.versionCode)
        }
        if (installableUpdates.isEmpty()) {
            return AutoUpdateExecutionSummary(
                updatedPackages = emptyList(),
                manualRequiredPackages = emptyList(),
                shizukuWasReady = installer.isSilentInstallerReady()
            )
        }

        val autoInstallEnabled = (prefs.useShizukuInstaller.get() || prefs.useRootInstaller.get()) &&
            prefs.autoInstallUpdates.get()
        if (!autoInstallEnabled || !installer.isSilentInstallerReady()) {
            runCatching {
                Log.i(TAG, "Silent auto-update skipped (enabled=$autoInstallEnabled, silentInstallerReady=${installer.isSilentInstallerReady()}); showing standard notification")
            }
            notification.showUpdateNotification(installableUpdates.size)
            return AutoUpdateExecutionSummary(
                updatedPackages = emptyList(),
                manualRequiredPackages = installableUpdates.map { it.packageName },
                shizukuWasReady = false
            )
        }

        downloader.cleanupOrphanStagingFiles(storageBaseDir)

        val updatedAppNames = mutableListOf<String>()
        val manualRequiredAppNames = mutableListOf<String>()
        val updatedPackages = mutableSetOf<String>()
        val manualRequiredPackages = mutableListOf<String>()

        for (update in installableUpdates) {
            val appLabel = update.name.ifBlank { update.packageName }
            val stagingDir = File(storageBaseDir, "download_staging_${update.id}").apply { mkdirs() }

            if (update.source == ApkMirrorSource && !prefs.directApkMirrorDownloads.get()) {
                if (!downloader.hasReadyCachedApk(storageBaseDir, update.id, update.versionCode)) {
                    runCatching { stagingDir.deleteRecursively() }
                }
                manualRequiredAppNames += appLabel
                manualRequiredPackages += update.packageName
                continue
            }

            try {
                val cachedFiles = downloader.getReadyStagingFiles(storageBaseDir, update.id, update.versionCode)
                    ?.takeIf { it.isNotEmpty() }
                val wasCached = cachedFiles != null
                val readyFiles = cachedFiles ?: run {
                    val downloaded = downloadUpdateSilently(update, stagingDir)
                    if (downloaded.isEmpty()) {
                        throw IOException("No APK files downloaded for ${update.packageName}")
                    }
                    downloaded
                }

                val expectedSha256 = if (readyFiles.size == 1) {
                    (update.link as? Link.Url)?.expectedSha256
                } else {
                    null
                }
                ApkIntegrityVerifier.verifyStagedApkFiles(
                    context = context,
                    expectedPackageName = update.packageName,
                    stagedFiles = readyFiles.map { it to expectedSha256 }
                )
                if (!wasCached) {
                    downloader.markStagingReady(stagingDir, update.versionCode)
                }

                if (context != null) {
                    val compat = com.apkup.util.ApkCompatibilityChecker.checkStagedTarget(
                        context = context,
                        target = if (readyFiles.size == 1) readyFiles.first() else stagingDir
                    )
                    if (!compat.isCompatible) {
                        prefs.markVersionIncompatible(update.packageName, update.versionCode)
                        AppJournal.record(
                            "Compatibility",
                            "Auto-update blocked incompatible APK for ${update.packageName} v${update.version}: ${compat.failureReason}"
                        )
                        runCatching { stagingDir.deleteRecursively() }
                        continue
                    }

                    val sigCheck = ApkSignatureVerifier.verifyApkFilesAgainstInstalled(
                        context = context,
                        packageName = update.packageName,
                        apkFiles = readyFiles
                    )
                    if (sigCheck is ApkSignatureCheckResult.Mismatch) {
                        runCatching {
                            Log.w(TAG, "Skipping auto-update for ${update.packageName}: APK signature mismatch")
                        }
                        runCatching { stagingDir.deleteRecursively() }
                        if (update.source == GitHubSource || update.source == GitLabSource || update.source == CodebergSource) {
                            val binding = repoMappingManager?.getBinding(update.packageName)
                                ?: (update.link as? Link.Url)?.link?.let { repoMappingManager?.parseRepoUrl(it) }
                            if (binding != null) {
                                repoMappingManager?.markSignatureIncompatible(
                                    update.packageName,
                                    binding,
                                    sigCheck.installedSignersSha256.firstOrNull()
                                )
                            }
                        }
                        continue
                    }
                }

                val parts = readyFiles.map { file ->
                    InstallPart(
                        name = file.name,
                        sizeBytes = file.length(),
                        expectedSha256 = expectedSha256,
                        backingFile = file,
                        openStream = { file.inputStream() }
                    )
                }
                if (context != null && prefs.enableRollbackBackups.get()) {
                    val maxBudgetBytes = com.apkup.util.RollbackBackupManager.budgetMbToBytes(
                        prefs.rollbackMaxBudgetMb.get()
                    )
                    val maxAgeDays = com.apkup.util.RollbackBackupManager.normalizeMaxAgeDays(
                        prefs.rollbackMaxAgeDays.get()
                    )
                    com.apkup.util.RollbackBackupManager(context)
                        .backupInstalledPackageBeforeUpdate(
                            packageName = update.packageName,
                            maxTotalBytes = maxBudgetBytes,
                            maxAgeDays = maxAgeDays
                        )
                }
                val installed = installer.installPartsSilently(update.id, update.packageName, parts)
                if (installed) {
                    runCatching { stagingDir.deleteRecursively() }
                    val repoBinding = repoMappingManager?.getBinding(update.packageName)
                        ?: prefs.readCustomRepoBindingsSafe()[update.packageName]
                    val installMode = if (installer.isShizukuReady()) {
                        UpdateInstallMode.AUTO_SHIZUKU
                    } else {
                        UpdateInstallMode.AUTO_ROOT
                    }
                    prefs.recordUpdateHistory(
                        buildUpdateHistoryEntry(
                            update = update,
                            installMode = installMode,
                            repoBinding = repoBinding
                        )
                    )
                    updatedAppNames += appLabel
                    updatedPackages += update.packageName
                } else {
                    // Preserve downloaded .ready APK files in persistent 24h storage for instant manual retry
                    manualRequiredAppNames += appLabel
                    manualRequiredPackages += update.packageName
                }
            } catch (cancellation: CancellationException) {
                if (!downloader.hasReadyCachedApk(storageBaseDir, update.id, update.versionCode)) {
                    runCatching { stagingDir.deleteRecursively() }
                }
                throw cancellation
            } catch (integrityErr: ApkIntegrityException) {
                runCatching {
                    Log.w(TAG, "Skipping auto-update for ${update.packageName}: APK integrity failed (${integrityErr.reason}): ${integrityErr.message}")
                }
                AppJournal.record(
                    "Security",
                    "Auto-update APK integrity failed for ${update.packageName} v${update.version} (${update.source.name}, reason=${integrityErr.reason}): ${integrityErr.message.orEmpty()}"
                )
                runCatching { stagingDir.deleteRecursively() }
                if (integrityErr.reason == ApkIntegrityException.Reason.SIGNATURE_MISMATCH) {
                    val installedSha = context?.let { ApkSignatureVerifier.getInstalledPrimarySha256(it, update.packageName) }
                    if (update.source == GitHubSource || update.source == GitLabSource || update.source == CodebergSource) {
                        val binding = repoMappingManager?.getBinding(update.packageName)
                            ?: (update.link as? Link.Url)?.link?.let { repoMappingManager?.parseRepoUrl(it) }
                        if (binding != null) {
                            repoMappingManager?.markSignatureIncompatible(
                                update.packageName,
                                binding,
                                installedSha
                            )
                        }
                    }
                }
                continue
            } catch (error: Throwable) {
                runCatching {
                    Log.w(TAG, "Background auto-update failed for ${update.packageName}: ${error.message}")
                }
                if (!downloader.hasReadyCachedApk(storageBaseDir, update.id, update.versionCode)) {
                    runCatching { stagingDir.deleteRecursively() }
                }
                manualRequiredAppNames += appLabel
                manualRequiredPackages += update.packageName
            }
        }

        if (updatedPackages.isNotEmpty()) {
            pruneUpdatedAppsFromSnapshot(updatedPackages)
        }

        notification.showAutoUpdateSummaryNotification(
            updatedAppNames = updatedAppNames,
            manualRequiredAppNames = manualRequiredAppNames
        )

        return AutoUpdateExecutionSummary(
            updatedPackages = updatedPackages.toList(),
            manualRequiredPackages = manualRequiredPackages,
            shizukuWasReady = true
        )
    }

    private fun pruneUpdatedAppsFromSnapshot(updatedPackages: Set<String>) {
        prefs.pruneCachedSnapshotPackages(updatedPackages)
    }

    private suspend fun downloadUpdateSilently(
        update: AppUpdate,
        stagingDir: File
    ): List<File> {
        val callTag = "auto_update_${update.id}"
        return when (val link = update.link) {
            Link.Empty -> throw IOException("Empty download link for ${update.packageName}")
            is Link.Play -> {
                val playFiles = link.getInstallFiles()
                if (playFiles.isEmpty()) {
                    throw IOException("No Play Store APK files returned for ${update.packageName}")
                }
                val sortedFiles = playFiles.sortedWith(
                    compareByDescending<AppFile> { it.type == AppFile.Type.BASE }
                        .thenBy { it.name }
                )
                sortedFiles.mapIndexed { index, playFile ->
                    val partName = if (playFile.type == AppFile.Type.BASE) {
                        "base.apk"
                    } else {
                        ApkIntegrityVerifier.sanitizePartFileName(playFile.name, index.coerceAtLeast(1))
                    }
                    val targetFile = File(stagingDir, partName)
                    val ok = downloader.downloadToFile(playFile.url, targetFile, callTag)
                    if (!ok || targetFile.length() <= 0L) {
                        throw IOException("Failed downloading Play split ${playFile.name} for ${update.packageName}")
                    }
                    targetFile
                }
            }
            is Link.Url -> {
                if (link.link.isBlank()) {
                    throw IOException("Blank download URL for ${update.packageName}")
                }
                if (update.source == ApkMirrorSource) {
                    when (val resolution = apkMirrorResolver.resolveDirectDownload(link.link, callTag, update.packageName)) {
                        is ApkMirrorResolutionResult.CloudflareBlocked -> {
                            throw IOException("APKMirror Cloudflare challenge blocked silent download for ${update.packageName}")
                        }
                        is ApkMirrorResolutionResult.Failed -> {
                            throw IOException("APKMirror resolution failed for ${update.packageName}: ${resolution.error}")
                        }
                        is ApkMirrorResolutionResult.Success -> {
                            val isApkmHint = resolution.isApkm || resolution.filename.endsWith(".apkm", ignoreCase = true)
                            val tempPayload = File(stagingDir, if (isApkmHint) "bundle.apkm" else "download.bin")
                            val ok = downloader.downloadToFile(
                                url = resolution.directUrl,
                                targetFile = tempPayload,
                                callTag = callTag
                            )
                            if (!ok || tempPayload.length() <= 0L) {
                                throw IOException("Failed downloading APKMirror payload for ${update.packageName}")
                            }
                            if (ApkmExtractor.isApkmArchive(tempPayload)) {
                                val extractedFiles = ApkmExtractor.extractApkm(tempPayload, stagingDir)
                                tempPayload.delete()
                                extractedFiles
                            } else {
                                val baseApk = File(stagingDir, "base.apk")
                                if (tempPayload != baseApk) {
                                    if (baseApk.exists()) baseApk.delete()
                                    tempPayload.renameTo(baseApk)
                                }
                                listOf(baseApk)
                            }
                        }
                    }
                } else {
                    val targetFile = File(stagingDir, "base.apk")
                    val ok = downloader.downloadToFile(
                        url = link.link,
                        targetFile = targetFile,
                        callTag = callTag
                    )
                    if (!ok || targetFile.length() <= 0L) {
                        throw IOException("Failed downloading APK URL for ${update.packageName}")
                    }
                    listOf(targetFile)
                }
            }
        }
    }
}

class ShizukuAutoUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), KoinComponent {

    companion object {
        const val WORK_NAME = "ShizukuAutoUpdateWorker"

        fun enqueue(
            workManager: WorkManager,
            wifiOnly: Boolean,
            chargingOnly: Boolean,
            initialDelayMinutes: Int = 0
        ) {
            val constraints = buildWorkConstraints(wifiOnly = wifiOnly, chargingOnly = chargingOnly)
            val builder = OneTimeWorkRequestBuilder<ShizukuAutoUpdateWorker>()
                .setConstraints(constraints)
            if (initialDelayMinutes > 0) {
                builder.setInitialDelay(
                    initialDelayMinutes * 60_000L + 30_000L,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
            }
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, builder.build())
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }

        internal fun buildWorkConstraints(
            wifiOnly: Boolean,
            chargingOnly: Boolean
        ): Constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(!chargingOnly)
            .build()
    }

    private val prefs: Prefs by inject()
    private val updatesRepository: UpdatesRepository by inject()
    private val downloader: Downloader by inject()
    private val apkMirrorResolver: ApkMirrorResolver by inject()
    private val installer: SessionInstaller by inject()
    private val notification: UpdatesNotification by inject()
    private val repoMappingManager: RepoMappingManager by inject()

    override suspend fun doWork(): Result {
        if ((!prefs.useShizukuInstaller.get() && !prefs.useRootInstaller.get()) || !prefs.autoInstallUpdates.get()) {
            return Result.success()
        }
        if (!prefs.isUpdateWindowAllowedNow()) {
            val delayMins = prefs.minutesUntilNextUpdateWindow()
            if (delayMins > 0) {
                enqueue(
                    workManager = WorkManager.getInstance(applicationContext),
                    wifiOnly = prefs.autoInstallWifiOnly.get(),
                    chargingOnly = prefs.autoInstallChargingOnly.get(),
                    initialDelayMinutes = delayMins
                )
            }
            return Result.success()
        }

        val installedAppsResult = updatesRepository.getInstalledApps()
        val installedApps = installedAppsResult.getOrNull()
        val cachedCheckResult = updatesRepository.restoreCachedResult(installedApps)?.second

        val resolvedResult: UpdateCheckResult<AppUpdate> = if (cachedCheckResult != null && cachedCheckResult.updates.isNotEmpty()) {
            cachedCheckResult
        } else {
            var freshResult: UpdateCheckResult<AppUpdate>? = null
            var lastEmission: UpdateCheckResult<AppUpdate>? = null
            withTimeoutOrNull(90_000L) {
                updatesRepository.updates(installedAppsResult.takeIf { it.isSuccess }).collect { emission ->
                    lastEmission = emission
                    if (!emission.isChecking) {
                        freshResult = emission
                    }
                }
            }
            freshResult ?: lastEmission ?: return Result.retry()
        }

        val notifiableUpdates = filterNotifiableUpdates(
            result = resolvedResult,
            ignoredVersionIds = if (prefs.ignoreVersionsFilterActive.get()) prefs.ignoredVersions.get() else emptyList(),
            ignoredPackages = if (prefs.ignoreAppsFilterActive.get()) prefs.ignoredApps.get() else emptyList()
        ).filterNot { prefs.isUpdateSnoozed(it.packageName, it.versionCode, it.version) }
        if (notifiableUpdates.isEmpty()) {
            return Result.success()
        }

        val storageDir = DownloadStorageManager.getDownloadDir(applicationContext)
        val safetyStatus = com.apkup.util.checkBatchSafetyStatus(applicationContext, storageDir)
        if (safetyStatus.hasAnyWarning) {
            Log.i(
                "ShizukuAutoUpdateWorker",
                "Pausing background auto-update due to safety guard (battery=${safetyStatus.batteryPercent}%, charging=${safetyStatus.isCharging}, freeBytes=${safetyStatus.freeStorageBytes})"
            )
            return Result.retry()
        }

        val runner = ShizukuAutoUpdateRunner(
            storageBaseDir = storageDir,
            prefs = prefs,
            downloader = downloader,
            apkMirrorResolver = apkMirrorResolver,
            installer = installer,
            notification = notification,
            repoMappingManager = repoMappingManager,
            context = applicationContext
        )
        runner.execute(notifiableUpdates)
        return Result.success()
    }
}

internal fun isUnmeteredWifiOrEthernet(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}.getOrDefault(false)

internal fun isAnyNetworkConnected(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(false)

internal fun isDeviceCharging(context: Context): Boolean = runCatching {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    if (bm != null && bm.isCharging) return true
    val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL ||
        plugged != 0
}.getOrDefault(false)

internal fun isBatteryNotLow(context: Context): Boolean = runCatching {
    val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level >= 0 && scale > 0) {
        (level * 100) / scale > 15
    } else {
        true
    }
}.getOrDefault(true)

internal fun areAutoInstallConstraintsMet(
    context: Context,
    wifiOnly: Boolean,
    chargingOnly: Boolean
): Boolean {
    if (wifiOnly) {
        if (!isUnmeteredWifiOrEthernet(context)) return false
    } else {
        if (!isAnyNetworkConnected(context)) return false
    }
    if (chargingOnly) {
        if (!isDeviceCharging(context)) return false
    } else {
        if (!isBatteryNotLow(context)) return false
    }
    return true
}
