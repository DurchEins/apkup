package com.apkup.worker

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallFailureReason
import com.apkup.data.ui.Link
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolutionResult
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.RepoMappingManager
import com.apkup.service.DownloadTaskState
import com.apkup.util.ApkIntegrityException
import com.apkup.util.ApkIntegrityVerifier
import com.apkup.util.ApkSignatureCheckResult
import com.apkup.util.ApkSignatureVerifier
import com.apkup.util.ApkmExtractor
import com.apkup.util.AppJournal
import com.apkup.util.AppLifecycleTracker
import com.apkup.util.BatchProgressCalculator
import com.apkup.util.BatchProgressSummary
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.buildUpdateHistoryEntry
import com.apkup.util.openUrl
import com.aurora.gplayapi.model.AppFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

private class ApkMirrorChallengeFallbackException(val fallbackUrl: String) :
    IOException("APKMirror interactive challenge required")

private class ApkMirrorDirectResolutionException(val fallbackUrl: String, message: String) :
    IOException(message)

/**
 * Application-scoped download & installation coordinator (`F-21`) backing `UpdateDownloadWorker`.
 * Decouples user downloads from `UpdatesViewModel` scan locks, provides live `StateFlow` updates
 * to Compose UI, dynamically computes batch indices (`MED-14`), and enforces SHA-256 / certificate
 * verification via `SessionInstaller` (`F-01`, `F-02`).
 */
class DownloadCoordinator(
    context: Context,
    private val downloader: Downloader,
    private val installer: SessionInstaller,
    private val installLog: InstallLog,
    private val snackBar: SnackBar,
    private val stringer: Stringer,
    private val prefs: Prefs,
    private val apkMirrorResolver: ApkMirrorResolver,
    private val repoMappingManager: RepoMappingManager,
    private val notificationManager: DownloadNotificationManager
) {
    companion object {
        private const val TAG = "DownloadCoordinator"
        const val UNIQUE_WORK_NAME = "ApkUpUserInitiatedDownloadQueue"
        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val enqueueLock = Any()

    private val activeTasks = ConcurrentHashMap<Int, DownloadTaskState>()
    private val _activeTasksFlow = MutableStateFlow<Map<Int, DownloadTaskState>>(emptyMap())
    val activeTasksFlow: StateFlow<Map<Int, DownloadTaskState>> = _activeTasksFlow.asStateFlow()

    private val completedTasks = CopyOnWriteArrayList<DownloadTaskState>()
    private val _completedTasksFlow = MutableStateFlow<List<DownloadTaskState>>(emptyList())
    val completedTasksFlow: StateFlow<List<DownloadTaskState>> = _completedTasksFlow.asStateFlow()

    private val _batchProgressFlow = MutableStateFlow<BatchProgressSummary?>(null)
    val batchProgressFlow: StateFlow<BatchProgressSummary?> = _batchProgressFlow.asStateFlow()

    private val _safFallbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val safFallbackEvents: SharedFlow<String> = _safFallbackEvents.asSharedFlow()

    @Volatile
    private var batchStartedAtMs = 0L
    private val saveOnlyTaskIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private val taskOrder = CopyOnWriteArrayList<Int>()
    private val knownUpdatesById = ConcurrentHashMap<Int, AppUpdate>()
    private val activeDownloadJobs = ConcurrentHashMap<Int, Job>()
    private val foregroundPromoterRef = AtomicReference<((Notification) -> Unit)?>(null)

    @Volatile
    private var lastNotificationRefreshMs = 0L

    init {
        observeInstallEvents()
    }

    fun hasActiveTasks(): Boolean = activeTasks.isNotEmpty()

    fun clearCompletedTasks() {
        completedTasks.clear()
        _completedTasksFlow.value = emptyList()
    }

    private fun expectedInitialTotalBytes(update: AppUpdate): Long = when {
        update.totalSize > 0L -> update.totalSize
        update.total > 0L -> update.total
        update.link is Link.Url && update.link.size > 0L -> update.link.size
        else -> 0L
    }

    fun enqueue(update: AppUpdate, saveOnly: Boolean = false) {
        enqueueBatch(listOf(update), saveOnly = saveOnly)
    }

    fun enqueueBatch(updates: List<AppUpdate>, saveOnly: Boolean = false) {
        if (updates.isEmpty()) return
        val newlyScheduled = mutableListOf<AppUpdate>()

        synchronized(enqueueLock) {
            if (activeTasks.isEmpty()) {
                completedTasks.clear()
                _completedTasksFlow.value = emptyList()
                batchStartedAtMs = System.currentTimeMillis()
            }
            val storageDir = DownloadStorageManager.getDownloadDir(appContext)
            for (update in updates) {
                if (activeDownloadJobs[update.id]?.isActive == true || activeTasks[update.id]?.isInstalling == true) {
                    continue
                }
                if (saveOnly) {
                    saveOnlyTaskIds.add(update.id)
                } else {
                    saveOnlyTaskIds.remove(update.id)
                }
                if (!taskOrder.contains(update.id)) {
                    taskOrder.add(update.id)
                }
                knownUpdatesById[update.id] = update
                val isCachedReady = Downloader.hasReadyCachedApk(storageDir, update.id, update.versionCode)
                activeTasks[update.id] = DownloadTaskState(
                    id = update.id,
                    packageName = update.packageName,
                    appName = update.name.ifBlank { update.packageName },
                    version = update.version,
                    source = update.source,
                    progress = 0L,
                    total = expectedInitialTotalBytes(update),
                    isInstalling = false,
                    isQueued = !isCachedReady,
                    isResolvingLink = false,
                    isSaveOnly = saveOnly
                )
                newlyScheduled += update
            }
            publishActiveTasks()
        }

        if (newlyScheduled.isEmpty()) return

        ensureWorkerScheduled(replaceExisting = false)
        refreshNotification(force = true)

        for (update in newlyScheduled) {
            val job = scope.launch {
                executeDownloadAndInstall(update)
            }
            activeDownloadJobs[update.id] = job
            job.invokeOnCompletion {
                activeDownloadJobs.remove(update.id, job)
            }
        }
    }

    fun cancelTask(id: Int) {
        downloader.cancel("update_$id")
        apkMirrorResolver.cancelResolution("update_$id")
        activeDownloadJobs.remove(id)?.cancel()
        knownUpdatesById.remove(id)
        saveOnlyTaskIds.remove(id)

        val stagingDir = DownloadStorageManager.getStagingDir(appContext, id)
        if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
            runCatching { stagingDir.deleteRecursively() }
        }

        installer.finish(id)
        scope.launch {
            installLog.emitStatus(AppInstallStatus(success = false, id = id, snack = false))
        }
        removeTask(id)
        refreshNotification(force = true)
    }

    fun cancelAll() {
        val ids = synchronized(enqueueLock) { taskOrder.toList() }
        downloader.cancelAll()
        for (id in ids) {
            apkMirrorResolver.cancelResolution("update_$id")
            activeDownloadJobs.remove(id)?.cancel()
            val stagingDir = DownloadStorageManager.getStagingDir(appContext, id)
            if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
                runCatching { stagingDir.deleteRecursively() }
            }
            installer.finish(id)
            scope.launch {
                installLog.emitStatus(AppInstallStatus(success = false, id = id, snack = false))
            }
        }
        synchronized(enqueueLock) {
            saveOnlyTaskIds.clear()
            activeTasks.clear()
            taskOrder.clear()
            knownUpdatesById.clear()
            publishActiveTasks()
        }
        notificationManager.cancel()
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }

    internal fun ensureWorkerScheduled(replaceExisting: Boolean) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(constraints)
                .build()
            val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
        }.onFailure { error ->
            if (com.apkup.BuildConfig.DEBUG) {
                Log.w(TAG, "WorkManager UIDT enqueue warning: ${error.javaClass.simpleName}")
            }
        }
    }

    internal fun buildInitialForegroundNotification(): Notification {
        val firstId = taskOrder.firstOrNull()
        val task = firstId?.let { activeTasks[it] }
        val (batchIndex, batchTotal) = computeDynamicBatchPosition(firstId)
        return if (task != null) {
            notificationManager.buildNotification(
                appName = task.appName,
                currentBytes = task.progress,
                totalBytes = task.total,
                isInstalling = task.isInstalling,
                activeUpdateId = task.id,
                batchIndex = batchIndex,
                batchTotal = batchTotal,
                isResolvingLink = task.isResolvingLink,
                batchSummary = _batchProgressFlow.value,
                isSaveOnly = task.isSaveOnly
            )
        } else {
            notificationManager.buildNotification(
                appName = appContext.getString(R.string.app_name),
                currentBytes = 0L,
                totalBytes = 0L,
                isInstalling = false,
                activeUpdateId = null
            )
        }
    }

    internal suspend fun awaitActiveQueueDrain(onForegroundNotification: (Notification) -> Unit) {
        foregroundPromoterRef.set(onForegroundNotification)
        try {
            refreshNotification(force = true)
            _activeTasksFlow.first { it.isEmpty() }
        } finally {
            foregroundPromoterRef.compareAndSet(onForegroundNotification, null)
            if (activeTasks.isEmpty()) {
                notificationManager.cancel()
            }
        }
    }

    internal suspend fun executeDownloadAndInstall(update: AppUpdate) {
        val storageDir = DownloadStorageManager.getDownloadDir(appContext)
        val stagingDir = DownloadStorageManager.getStagingDir(appContext, update.id)
        installLog.beginInstall(update.id)

        try {
            val (readyFiles, wasCached) = downloadSemaphore.withPermit {
                val cached = downloader.getReadyStagingFiles(storageDir, update.id, update.versionCode)
                if (cached != null && cached.isNotEmpty()) {
                    mutateTask(update.id) { it.copy(isQueued = false, isResolvingLink = false) }
                    cached to true
                } else {
                    if (stagingDir.exists()) {
                        runCatching { stagingDir.deleteRecursively() }
                    }
                    stagingDir.mkdirs()

                    mutateTask(update.id) {
                        it.copy(
                            isQueued = false,
                            isResolvingLink = update.source == ApkMirrorSource
                        )
                    }
                    refreshNotification(force = true)

                    val downloaded = downloadUpdateFiles(update, stagingDir) { current, total ->
                        mutateTask(update.id) { task ->
                            task.copy(
                                isQueued = false,
                                isResolvingLink = false,
                                progress = current,
                                total = total
                            )
                        }
                        refreshNotification(force = false)
                    }
                    if (downloaded.isEmpty()) {
                        throw IOException("No APK files downloaded for ${update.packageName}")
                    }
                    downloaded to false
                }
            }

            val expectedSha256 = if (readyFiles.size == 1) {
                (update.link as? Link.Url)?.expectedSha256
            } else {
                null
            }

            val isSaveOnly = saveOnlyTaskIds.remove(update.id) || activeTasks[update.id]?.isSaveOnly == true

            ApkIntegrityVerifier.verifyStagedApkFiles(
                context = appContext,
                expectedPackageName = update.packageName,
                stagedFiles = readyFiles.map { it to expectedSha256 },
                verifyAgainstInstalled = !isSaveOnly
            )
            if (!wasCached) {
                downloader.markStagingReady(stagingDir, update.versionCode)
            }

            if (isSaveOnly) {
                val totalPayloadSize = readyFiles.sumOf { it.length() }
                mutateTask(update.id) {
                    it.copy(
                        isQueued = false,
                        isResolvingLink = false,
                        isInstalling = true,
                        isSaveOnly = true,
                        progress = totalPayloadSize,
                        total = if (totalPayloadSize > 0L) totalPayloadSize else it.total
                    )
                }
                refreshNotification(force = true)

                val exportResult = DownloadStorageManager.exportApkFiles(
                    context = appContext,
                    files = readyFiles,
                    appName = update.name,
                    packageName = update.packageName,
                    version = update.version,
                    customFolderUri = prefs.customDownloadFolderUri.get()
                )
                AppJournal.record(
                    "SaveAPK",
                    "Exported ${update.packageName} v${update.version} (${update.source.name}, success=${exportResult.success}, safFallback=${exportResult.usedSafFallback}, dest=${exportResult.displayLocation})"
                )
                if (exportResult.success) {
                    activeTasks[update.id]?.let { finishedTask ->
                        if (completedTasks.none { it.id == finishedTask.id }) {
                            completedTasks.add(
                                0,
                                finishedTask.copy(isInstalling = false, isQueued = false, isResolvingLink = false)
                            )
                            _completedTasksFlow.value = completedTasks.toList()
                        }
                    }
                    if (exportResult.usedSafFallback) {
                        _safFallbackEvents.tryEmit(update.name.ifBlank { update.packageName })
                    }
                    val msg = runCatching { stringer.get(R.string.apk_save_success, exportResult.displayLocation) }.getOrDefault("")
                    snackBar.emit(TextSnack(msg, visualType = com.apkup.data.snack.SnackVisualType.SUCCESS))
                } else {
                    val msg = runCatching { stringer.get(R.string.apk_save_error) }.getOrDefault("")
                    snackBar.emit(TextSnack(msg, visualType = com.apkup.data.snack.SnackVisualType.ERROR))
                }
                installLog.emitProgress(AppInstallProgress(update.id, 0L))
                knownUpdatesById.remove(update.id)
                removeTask(update.id)
                refreshNotification(force = true)
                return
            }

            val compat = com.apkup.util.ApkCompatibilityChecker.checkStagedTarget(
                context = appContext,
                target = if (readyFiles.size == 1) readyFiles.first() else stagingDir
            )
            if (!compat.isCompatible) {
                prefs.markVersionIncompatible(update.packageName, update.versionCode)
                AppJournal.record(
                    "Compatibility",
                    "Blocked incompatible APK for ${update.packageName} v${update.version} (${compat.failureReason})"
                )
                runCatching { stagingDir.deleteRecursively() }
                knownUpdatesById.remove(update.id)
                removeTask(update.id)
                installLog.emitProgress(AppInstallProgress(update.id, 0L))
                val errMsg = when (compat.failureReason) {
                    com.apkup.util.ApkCompatibilityResult.FailureReason.MIN_SDK_TOO_HIGH ->
                        runCatching { stringer.get(R.string.install_error_incompatible_sdk, compat.requiredMinSdk) }
                            .getOrDefault("Requires Android API ${compat.requiredMinSdk}+")
                    com.apkup.util.ApkCompatibilityResult.FailureReason.NO_MATCHING_ABI ->
                        runCatching { stringer.get(R.string.install_error_incompatible_abi, compat.apkAbis.joinToString(", ")) }
                            .getOrDefault("Incompatible CPU architecture (${compat.apkAbis.joinToString(", ")})")
                    else -> runCatching { stringer.get(R.string.install_error_integrity_failed) }.getOrDefault("Incompatible APK")
                }
                snackBar.emit(TextSnack(errMsg, visualType = com.apkup.data.snack.SnackVisualType.ERROR))
                refreshNotification(force = true)
                return
            }

            val sigCheck = ApkSignatureVerifier.verifyApkFilesAgainstInstalled(
                context = appContext,
                packageName = update.packageName,
                apkFiles = readyFiles
            )
            if (sigCheck is ApkSignatureCheckResult.Mismatch) {
                AppJournal.record(
                    "Install",
                    "Signature mismatch aborted install for ${update.packageName} v${update.version} (${update.source.name})"
                )
                runCatching { stagingDir.deleteRecursively() }
                recordIncompatibleRepoIfApplicable(update, sigCheck.installedSignersSha256.firstOrNull())
                knownUpdatesById.remove(update.id)
                removeTask(update.id)
                installLog.emitProgress(AppInstallProgress(update.id, 0L))
                installLog.emitStatus(
                    AppInstallStatus(
                        success = false,
                        id = update.id,
                        snack = true,
                        failureReason = InstallFailureReason.SIGNATURE_MISMATCH
                    )
                )
                refreshNotification(force = true)
                return
            }

            val totalPayloadSize = readyFiles.sumOf { it.length() }
            mutateTask(update.id) {
                it.copy(
                    isQueued = false,
                    isResolvingLink = false,
                    isInstalling = true,
                    progress = totalPayloadSize,
                    total = totalPayloadSize
                )
            }
            refreshNotification(force = true)

            val parts = readyFiles.map { file ->
                InstallPart(
                    name = file.name,
                    sizeBytes = file.length(),
                    expectedSha256 = expectedSha256,
                    backingFile = file,
                    openStream = { file.inputStream() }
                )
            }

            if (update.oldVersionCode >= 0L && prefs.enableRollbackBackups.get()) {
                val maxBudgetBytes = com.apkup.util.RollbackBackupManager.budgetMbToBytes(
                    prefs.rollbackMaxBudgetMb.get()
                )
                val maxAgeDays = com.apkup.util.RollbackBackupManager.normalizeMaxAgeDays(
                    prefs.rollbackMaxAgeDays.get()
                )
                com.apkup.util.RollbackBackupManager(appContext)
                    .backupInstalledPackageBeforeUpdate(
                        packageName = update.packageName,
                        maxTotalBytes = maxBudgetBytes,
                        maxAgeDays = maxAgeDays
                    )
            }
            val started = installer.installParts(update.id, update.packageName, parts)
            AppJournal.record(
                "Install",
                "Submitted ${update.packageName} v${update.version} (${update.source.name}, started=$started)"
            )
        } catch (cancelled: CancellationException) {
            if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
                runCatching { stagingDir.deleteRecursively() }
            }
            removeTask(update.id)
            refreshNotification(force = true)
            throw cancelled
        } catch (challenge: ApkMirrorChallengeFallbackException) {
            AppJournal.record("ApkMirror", "Cloudflare challenge fallback to browser for ${update.packageName}")
            if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
                runCatching { stagingDir.deleteRecursively() }
            }
            snackBar.emit(TextSnack(stringer.get(R.string.apkmirror_challenge_fallback), visualType = com.apkup.data.snack.SnackVisualType.WARNING))
            if (AppLifecycleTracker.isAppInForeground) {
                appContext.openUrl(challenge.fallbackUrl)
            }
            knownUpdatesById.remove(update.id)
            removeTask(update.id)
            installLog.emitStatus(AppInstallStatus(success = false, id = update.id, snack = false))
            refreshNotification(force = true)
        } catch (resolutionErr: ApkMirrorDirectResolutionException) {
            AppJournal.record("ApkMirror", "Resolution failed (${resolutionErr.message ?: "error"}) for ${update.packageName}; browser fallback")
            if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
                runCatching { stagingDir.deleteRecursively() }
            }
            snackBar.emit(TextSnack(stringer.get(R.string.apkmirror_resolution_failed_fallback), visualType = com.apkup.data.snack.SnackVisualType.WARNING))
            if (AppLifecycleTracker.isAppInForeground) {
                appContext.openUrl(resolutionErr.fallbackUrl)
            }
            knownUpdatesById.remove(update.id)
            removeTask(update.id)
            installLog.emitStatus(AppInstallStatus(success = false, id = update.id, snack = false))
            refreshNotification(force = true)
        } catch (integrityErr: ApkIntegrityException) {
            if (com.apkup.BuildConfig.DEBUG) {
                Log.e(TAG, "APK integrity check failed for ${update.packageName} (${integrityErr.reason}): ${integrityErr.message}")
            }
            AppJournal.record(
                "Security",
                "APK integrity failed for ${update.packageName} v${update.version} (${update.source.name}, reason=${integrityErr.reason}): ${integrityErr.message.orEmpty()}"
            )
            runCatching { stagingDir.deleteRecursively() }
            val isSigMismatch = integrityErr.reason == ApkIntegrityException.Reason.SIGNATURE_MISMATCH
            if (isSigMismatch) {
                val installedSha = ApkSignatureVerifier.getInstalledPrimarySha256(appContext, update.packageName)
                recordIncompatibleRepoIfApplicable(update, installedSha)
            }
            val wasSaveOnly = saveOnlyTaskIds.remove(update.id) || activeTasks[update.id]?.isSaveOnly == true
            if (wasSaveOnly) {
                val msg = runCatching { stringer.get(R.string.apk_save_error) }.getOrDefault("")
                snackBar.emit(TextSnack(msg, visualType = com.apkup.data.snack.SnackVisualType.ERROR))
            }
            knownUpdatesById.remove(update.id)
            removeTask(update.id)
            installLog.emitProgress(AppInstallProgress(update.id, 0L))
            if (!wasSaveOnly) {
                installLog.emitStatus(
                    AppInstallStatus(
                        success = false,
                        id = update.id,
                        snack = true,
                        failureReason = if (isSigMismatch) InstallFailureReason.SIGNATURE_MISMATCH else InstallFailureReason.GENERIC
                    )
                )
            }
            refreshNotification(force = true)
        } catch (error: Throwable) {
            if (com.apkup.BuildConfig.DEBUG) {
                Log.e(TAG, "Download/install failed for ${update.packageName}: ${error.message}")
            }
            AppJournal.record("Download", "Failed for ${update.packageName} (${update.source.name}): ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
            if (error is SecurityException || !File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
                runCatching { stagingDir.deleteRecursively() }
            }
            knownUpdatesById.remove(update.id)
            removeTask(update.id)
            installLog.emitStatus(AppInstallStatus(success = false, id = update.id, snack = true))
            refreshNotification(force = true)
        }
    }

    private suspend fun downloadUpdateFiles(
        update: AppUpdate,
        stagingDir: File,
        onProgress: (current: Long, total: Long) -> Unit
    ): List<File> {
        val callTag = "update_${update.id}"
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
                var totalBytes = sortedFiles.sumOf { it.size.coerceAtLeast(0L) }
                var downloadedBytes = 0L
                onProgress(0L, totalBytes)

                sortedFiles.mapIndexed { index, playFile ->
                    val partName = if (playFile.type == AppFile.Type.BASE) {
                        "base.apk"
                    } else {
                        ApkIntegrityVerifier.sanitizePartFileName(playFile.name, index.coerceAtLeast(1))
                    }
                    val targetFile = File(stagingDir, partName)
                    val ok = downloader.downloadToFile(
                        url = playFile.url,
                        targetFile = targetFile,
                        callTag = callTag,
                        onContentLength = { cl ->
                            if (totalBytes <= 0L && cl > 0L) {
                                totalBytes = cl
                            }
                        },
                        onBytesRead = { chunk ->
                            downloadedBytes += chunk
                            onProgress(downloadedBytes, totalBytes)
                        }
                    )
                    if (!ok || targetFile.length() <= 0L) {
                        throw IOException("Failed downloading Play split $partName for ${update.packageName}")
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
                            throw ApkMirrorChallengeFallbackException(resolution.fallbackUrl)
                        }
                        is ApkMirrorResolutionResult.Failed -> {
                            throw ApkMirrorDirectResolutionException(
                                resolution.fallbackUrl,
                                resolution.error.message ?: "APKMirror direct resolution failed"
                            )
                        }
                        is ApkMirrorResolutionResult.Success -> {
                            val isApkmHint = resolution.isApkm || resolution.filename.endsWith(".apkm", ignoreCase = true)
                            val tempPayload = File(stagingDir, if (isApkmHint) "bundle.apkm" else "download.bin")
                            var totalBytes = link.size
                            var downloadedBytes = 0L
                            onProgress(0L, totalBytes)

                            val ok = downloader.downloadToFile(
                                url = resolution.directUrl,
                                targetFile = tempPayload,
                                callTag = callTag,
                                onContentLength = { cl ->
                                    if (cl > 0L) totalBytes = cl
                                },
                                onBytesRead = { chunk ->
                                    downloadedBytes += chunk
                                    onProgress(downloadedBytes, totalBytes)
                                }
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
                    var totalBytes = link.size
                    var downloadedBytes = 0L
                    onProgress(0L, totalBytes)
                    val tempFile = File(stagingDir, "base.apk.part")
                    val targetFile = File(stagingDir, "base.apk")
                    val ok = downloader.downloadToFile(
                        url = link.link,
                        targetFile = tempFile,
                        callTag = callTag,
                        onContentLength = { cl ->
                            if (cl > 0L) totalBytes = cl
                        },
                        onBytesRead = { chunk ->
                            downloadedBytes += chunk
                            onProgress(downloadedBytes, totalBytes)
                        }
                    )
                    if (!ok || tempFile.length() <= 0L) {
                        runCatching { tempFile.delete() }
                        throw IOException("Failed downloading URL APK for ${update.packageName}")
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    listOf(targetFile)
                }
            }
        }
    }

    private fun observeInstallEvents() {
        scope.launch {
            installLog.progress().collectLatest { prog ->
                val updated = mutateTask(prog.id) { task ->
                    task.copy(
                        progress = prog.progress ?: task.progress,
                        total = prog.total ?: task.total
                    )
                }
                if (updated != null && updated.isInstalling) {
                    refreshNotification(force = false)
                }
            }
        }

        scope.launch {
            installLog.status().collect { status ->
                if (status.success) {
                    runCatching {
                        DownloadStorageManager.getStagingDir(appContext, status.id).deleteRecursively()
                    }
                    activeTasks[status.id]?.let { completedTask ->
                        if (completedTasks.none { it.id == completedTask.id }) {
                            completedTasks.add(
                                0,
                                completedTask.copy(
                                    isInstalling = false,
                                    isQueued = false,
                                    isResolvingLink = false
                                )
                            )
                            _completedTasksFlow.value = completedTasks.toList()
                        }
                    }
                    if (status.snack) {
                        knownUpdatesById.remove(status.id)?.let { finishedUpdate ->
                            val mode = when {
                                installer.isShizukuReady() -> UpdateInstallMode.MANUAL_SHIZUKU
                                installer.isRootReady() -> UpdateInstallMode.MANUAL_ROOT
                                else -> UpdateInstallMode.MANUAL
                            }
                            val binding = repoMappingManager.getBinding(finishedUpdate.packageName)
                                ?: prefs.readCustomRepoBindingsSafe()[finishedUpdate.packageName]
                            prefs.recordUpdateHistory(
                                buildUpdateHistoryEntry(
                                    update = finishedUpdate,
                                    installMode = mode,
                                    repoBinding = binding
                                )
                            )
                        }
                    }
                } else {
                    val failedUpdate = knownUpdatesById.remove(status.id)
                    if (status.failureReason == InstallFailureReason.SIGNATURE_MISMATCH) {
                        runCatching {
                            DownloadStorageManager.getStagingDir(appContext, status.id).deleteRecursively()
                        }
                        if (failedUpdate != null) {
                            val installedSha = ApkSignatureVerifier.getInstalledPrimarySha256(
                                appContext,
                                failedUpdate.packageName
                            )
                            recordIncompatibleRepoIfApplicable(failedUpdate, installedSha)
                        }
                    }
                    installer.finish(status.id)
                }
                removeTask(status.id)
                refreshNotification(force = true)
            }
        }
    }

    private fun recordIncompatibleRepoIfApplicable(update: AppUpdate, installedSha256: String?) {
        if (update.source != GitHubSource && update.source != GitLabSource && update.source != CodebergSource) return
        val binding = repoMappingManager.getBinding(update.packageName)
            ?: (update.link as? Link.Url)?.link?.let { repoMappingManager.parseRepoUrl(it) }
            ?: return
        repoMappingManager.markSignatureIncompatible(update.packageName, binding, installedSha256)
    }

    private fun computeDynamicBatchPosition(activeId: Int?): Pair<Int, Int> {
        val doneCount = completedTasks.size
        val remainingCount = taskOrder.size
        val total = (doneCount + remainingCount).coerceAtLeast(1)
        val offsetInRemaining = if (activeId != null) {
            taskOrder.indexOf(activeId).takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        val current = (doneCount + offsetInRemaining + 1).coerceIn(1, total)
        return current to total
    }

    private fun publishActiveTasks() {
        val ordered = linkedMapOf<Int, DownloadTaskState>()
        for (id in taskOrder) {
            activeTasks[id]?.let { ordered[id] = it }
        }
        for ((id, task) in activeTasks) {
            if (!ordered.containsKey(id)) {
                ordered[id] = task
            }
        }
        _activeTasksFlow.value = ordered
        _batchProgressFlow.value = if (ordered.isEmpty()) {
            null
        } else {
            BatchProgressCalculator.calculate(
                activeTasks = ordered.values,
                completedTasks = completedTasks,
                batchStartedAtMs = batchStartedAtMs
            )
        }
    }

    private fun mutateTask(id: Int, transform: (DownloadTaskState) -> DownloadTaskState): DownloadTaskState? {
        val updated = activeTasks.computeIfPresent(id) { _, existing -> transform(existing) }
        if (updated != null) {
            publishActiveTasks()
        }
        return updated
    }

    internal fun hasSaveOnlyTask(id: Int): Boolean = id in saveOnlyTaskIds

    private fun removeTask(id: Int) {
        saveOnlyTaskIds.remove(id)
        synchronized(enqueueLock) {
            activeTasks.remove(id)
            taskOrder.remove(id)
            publishActiveTasks()
        }
        if (activeTasks.isEmpty()) {
            runCatching { notificationManager.cancel() }
        }
    }

    private fun refreshNotification(force: Boolean) {
        val currentActiveId = taskOrder.firstOrNull { id ->
            val task = activeTasks[id]
            task != null && !task.isQueued
        } ?: taskOrder.firstOrNull()

        if (currentActiveId == null) {
            runCatching { notificationManager.cancel() }
            return
        }

        val activeTask = activeTasks[currentActiveId] ?: return
        val batchSummary = _batchProgressFlow.value
        val (batchIndex, batchTotal) = computeDynamicBatchPosition(currentActiveId)

        val percent = batchSummary?.overallPercent ?: if (activeTask.total > 0L) {
            ((activeTask.progress * 100) / activeTask.total).toInt().coerceIn(0, 100)
        } else {
            0
        }

        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationRefreshMs < 500L) {
            return
        }
        if (notificationManager.shouldThrottle(percent, force)) {
            return
        }
        lastNotificationRefreshMs = now

        runCatching {
            val notification = notificationManager.buildNotification(
                appName = activeTask.appName,
                currentBytes = activeTask.progress,
                totalBytes = activeTask.total,
                isInstalling = activeTask.isInstalling,
                activeUpdateId = activeTask.id,
                batchIndex = batchIndex,
                batchTotal = batchTotal,
                isResolvingLink = activeTask.isResolvingLink,
                batchSummary = batchSummary,
                isSaveOnly = activeTask.isSaveOnly
            )
            val promoter = foregroundPromoterRef.get()
            if (promoter != null) {
                promoter(notification)
            } else {
                notificationManager.update(notification)
            }
        }
    }
}
