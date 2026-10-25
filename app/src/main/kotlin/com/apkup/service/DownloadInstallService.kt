package com.apkup.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.repository.ApkMirrorResolutionResult
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.Link
import com.apkup.viewmodel.ApkMirrorCloudflareException
import com.apkup.repository.ApkMirrorResolver
import com.apkup.util.ApkmExtractor
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.viewmodel.DownloadedPackage
import com.aurora.gplayapi.data.models.PlayFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

data class DownloadTaskState(
    val id: Int,
    val packageName: String,
    val appName: String,
    val version: String = "",
    val source: com.apkup.data.ui.Source = com.apkup.data.ui.PlaySource,
    var progress: Long = 0L,
    var total: Long = 0L,
    var isInstalling: Boolean = false,
    var isQueued: Boolean = true,
    var isResolvingLink: Boolean = false
)

class DownloadInstallService : Service(), KoinComponent {

    companion object {
        private const val TAG = "DownloadInstallService"

        const val ACTION_START_DOWNLOAD = "com.apkup.service.ACTION_START_DOWNLOAD"
        const val ACTION_ENQUEUE_DOWNLOADS = "com.apkup.service.ACTION_ENQUEUE_DOWNLOADS"
        const val ACTION_CANCEL_DOWNLOAD = "com.apkup.service.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_CANCEL_ALL_DOWNLOADS = "com.apkup.service.ACTION_CANCEL_ALL_DOWNLOADS"
        const val ACTION_STOP_SERVICE = "com.apkup.service.ACTION_STOP_SERVICE"

        const val EXTRA_UPDATE_ID = "extra_update_id"

        private val activeTasks = ConcurrentHashMap<Int, DownloadTaskState>()
        private val _activeTasksFlow = MutableStateFlow<Map<Int, DownloadTaskState>>(emptyMap())
        val activeTasksFlow: StateFlow<Map<Int, DownloadTaskState>> = _activeTasksFlow.asStateFlow()

        private val completedTasks = CopyOnWriteArrayList<DownloadTaskState>()
        private val _completedTasksFlow = MutableStateFlow<List<DownloadTaskState>>(emptyList())
        val completedTasksFlow: StateFlow<List<DownloadTaskState>> = _completedTasksFlow.asStateFlow()

        private val taskOrder = CopyOnWriteArrayList<Int>()
        private val pendingUpdates = ConcurrentLinkedQueue<AppUpdate>()

        fun startDownload(context: Context, update: AppUpdate) {
            if (activeTasks.isEmpty()) {
                completedTasks.clear()
                _completedTasksFlow.value = emptyList()
            }
            if (!taskOrder.contains(update.id)) {
                taskOrder.add(update.id)
            }
            pendingUpdates.offer(update)
            activeTasks[update.id] = DownloadTaskState(
                id = update.id,
                packageName = update.packageName,
                appName = update.name,
                version = update.version,
                source = update.source,
                isInstalling = false,
                isQueued = true,
                isResolvingLink = update.source == ApkMirrorSource
            )
            _activeTasksFlow.value = activeTasks.toMap()

            val intent = Intent(context, DownloadInstallService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_UPDATE_ID, update.id)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startDownloads(context: Context, updates: List<AppUpdate>) {
            if (activeTasks.isEmpty()) {
                completedTasks.clear()
                _completedTasksFlow.value = emptyList()
            }
            updates.forEach { update ->
                if (!taskOrder.contains(update.id)) {
                    taskOrder.add(update.id)
                }
                pendingUpdates.offer(update)
                activeTasks[update.id] = DownloadTaskState(
                    id = update.id,
                    packageName = update.packageName,
                    appName = update.name,
                    version = update.version,
                    source = update.source,
                    isInstalling = false,
                    isQueued = true,
                    isResolvingLink = update.source == ApkMirrorSource
                )
            }
            _activeTasksFlow.value = activeTasks.toMap()

            val intent = Intent(context, DownloadInstallService::class.java).apply {
                action = ACTION_ENQUEUE_DOWNLOADS
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelDownload(context: Context, updateId: Int) {
            val intent = Intent(context, DownloadInstallService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_UPDATE_ID, updateId)
            }
            context.startService(intent)
        }

        fun cancelAllDownloads(context: Context) {
            val intent = Intent(context, DownloadInstallService::class.java).apply {
                action = ACTION_CANCEL_ALL_DOWNLOADS
            }
            context.startService(intent)
        }

        fun clearCompletedTasks() {
            completedTasks.clear()
            _completedTasksFlow.value = emptyList()
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadInstallService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private val downloader: Downloader by inject()
    private val installer: SessionInstaller by inject()
    private val installLog: InstallLog by inject()
    private val snackBar: SnackBar by inject()
    private val stringer: Stringer by inject()
    private val apkMirrorResolver: ApkMirrorResolver by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: DownloadNotificationManager
    private val isForegroundActive = AtomicBoolean(false)

    private val downloadSemaphore = Semaphore(3)
    private val activeDownloadJobs = ConcurrentHashMap<Int, Job>()
    private val installChannel = Channel<DownloadedPackage>(Channel.UNLIMITED)
    private val installQueueMutex = Mutex()
    private var installProcessorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = DownloadNotificationManager(this)
        downloader.cleanupOrphanStagingFiles(cacheDir)
        observeInstallLog()
        startInstallProcessor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundStarted()

        val action = intent?.action
        val updateId = intent?.getIntExtra(EXTRA_UPDATE_ID, 0) ?: 0

        when (action) {
            ACTION_START_DOWNLOAD, ACTION_ENQUEUE_DOWNLOADS -> {
                drainPendingUpdates()
            }
            ACTION_CANCEL_DOWNLOAD -> {
                if (updateId != 0) {
                    handleCancelTask(updateId)
                }
            }
            ACTION_CANCEL_ALL_DOWNLOADS -> {
                pendingUpdates.clear()
                val allIds = taskOrder.toList()
                allIds.forEach { id -> handleCancelTask(id) }
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundAndSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun ensureForegroundStarted() {
        if (isForegroundActive.compareAndSet(false, true)) {
            val currentActiveId = taskOrder.firstOrNull()
            val initialTask = currentActiveId?.let { activeTasks[it] }

            val initialNotification = notificationManager.buildNotification(
                appName = initialTask?.appName ?: getString(R.string.app_name),
                currentBytes = initialTask?.progress ?: 0L,
                totalBytes = initialTask?.total ?: 0L,
                isInstalling = initialTask?.isInstalling ?: false,
                activeUpdateId = initialTask?.id,
                batchIndex = 1,
                batchTotal = taskOrder.size.coerceAtLeast(1),
                isResolvingLink = initialTask?.isResolvingLink ?: false
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    DownloadNotificationManager.NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(DownloadNotificationManager.NOTIFICATION_ID, initialNotification)
            }
        }
    }

    private fun drainPendingUpdates() {
        while (true) {
            val update = pendingUpdates.poll() ?: break
            startDownloadForUpdate(update)
        }
    }

    private fun startDownloadForUpdate(update: AppUpdate) {
        if (activeDownloadJobs.containsKey(update.id)) return

        val job = serviceScope.launch {
            val stagingDir = File(cacheDir, "download_staging_${update.id}").apply { mkdirs() }
            try {
                downloadSemaphore.withPermit {
                    // Update state to active downloading
                    val task = activeTasks[update.id]
                    if (task != null) {
                        task.isQueued = false
                        _activeTasksFlow.value = activeTasks.toMap()
                    }

                    installLog.beginInstall(update.id)
                    installLog.emitProgress(AppInstallProgress(update.id, 0L))
                    refreshNotification(force = true)

                    val files = downloadUpdateFiles(update, stagingDir) { currentBytes, totalBytes ->
                        installLog.emitProgress(AppInstallProgress(update.id, currentBytes, totalBytes))
                        activeTasks[update.id]?.let {
                            it.progress = currentBytes
                            it.total = totalBytes
                            _activeTasksFlow.value = activeTasks.toMap()
                        }
                    }

                    if (files.isEmpty()) {
                        throw IOException("No files downloaded for ${update.packageName}")
                    }

                    // Download complete, transition to install queue
                    activeTasks[update.id]?.let {
                        it.isInstalling = true
                        _activeTasksFlow.value = activeTasks.toMap()
                    }
                    refreshNotification(force = true)

                    installChannel.send(
                        DownloadedPackage(
                            id = update.id,
                            packageName = update.packageName,
                            appName = update.name,
                            stagingDir = stagingDir,
                            files = files
                        )
                    )
                }
            } catch (cancellation: CancellationException) {
                Log.i(TAG, "Download canceled for ${update.packageName} (${update.id})")
                runCatching { stagingDir.deleteRecursively() }
                installLog.emitProgress(AppInstallProgress(update.id, 0L))
                handleCancelTask(update.id)
                throw cancellation
            } catch (cf: ApkMirrorCloudflareException) {
                Log.w(TAG, "Cloudflare challenge encountered for ${update.packageName}")
                runCatching { stagingDir.deleteRecursively() }
                installLog.emitProgress(AppInstallProgress(update.id, 0L))
                handleCancelTask(update.id)
                snackBar.snackBar(serviceScope, TextSnack(stringer.get(R.string.apkmirror_challenge_fallback)))
                val fallbackUrl = cf.fallbackUrl.ifBlank { (update.link as? Link.Url)?.link.orEmpty() }
                if (fallbackUrl.isNotBlank()) {
                    runCatching {
                        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fallbackUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(browserIntent)
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Download failed for ${update.packageName}", error)
                runCatching { stagingDir.deleteRecursively() }
                installLog.emitProgress(AppInstallProgress(update.id, 0L))
                if (!installLog.emitStatus(AppInstallStatus(false, update.id, snack = true))) {
                    handleCancelTask(update.id)
                }
            } finally {
                activeDownloadJobs.remove(update.id)
            }
        }

        activeDownloadJobs[update.id] = job
    }

    private fun startInstallProcessor() {
        if (installProcessorJob?.isActive == true) return
        installProcessorJob = serviceScope.launch {
            for (pkg in installChannel) {
                installQueueMutex.withLock {
                    val task = activeTasks[pkg.id]
                    if (task == null) {
                        runCatching { pkg.stagingDir.deleteRecursively() }
                        continue
                    }

                    task.isInstalling = true
                    _activeTasksFlow.value = activeTasks.toMap()
                    refreshNotification(force = true)

                    try {
                        val parts = pkg.files.map { file ->
                            InstallPart(file.name) { file.inputStream() }
                        }
                        installer.installParts(pkg.id, pkg.packageName, parts)
                    } catch (cancellation: CancellationException) {
                        installLog.emitProgress(AppInstallProgress(pkg.id, 0L))
                        handleCancelTask(pkg.id)
                        throw cancellation
                    } catch (error: Throwable) {
                        Log.e(TAG, "Installation failed for ${pkg.packageName}", error)
                        installLog.emitProgress(AppInstallProgress(pkg.id, 0L))
                        if (!installLog.emitStatus(AppInstallStatus(false, pkg.id, snack = true))) {
                            handleCancelTask(pkg.id)
                        }
                    } finally {
                        runCatching { pkg.stagingDir.deleteRecursively() }
                    }
                }
            }
        }
    }

    private suspend fun downloadUpdateFiles(
        update: AppUpdate,
        stagingDir: File,
        onProgress: (currentBytes: Long, totalBytes: Long) -> Unit
    ): List<File> = when (val link = update.link) {
        Link.Empty -> {
            Log.e(TAG, "downloadUpdateFiles: Empty link for ${update.packageName}")
            throw IOException("Unsupported empty download link for ${update.packageName}")
        }
        is Link.Play -> {
            val playFiles = link.getInstallFiles()
            if (playFiles.isEmpty()) {
                throw IOException("Play Store returned no installable APK files for ${update.packageName}")
            }
            val sortedFiles = playFiles.sortedWith(
                compareByDescending<PlayFile> { it.type == PlayFile.Type.BASE }
                    .thenBy { it.name }
            )
            val totalBytes = sortedFiles.sumOf { it.size }
            var downloadedBytes = 0L
            onProgress(0L, totalBytes)

            val callTag = "update_${update.id}"
            sortedFiles.mapIndexed { index, playFile ->
                val partName = when {
                    playFile.type == PlayFile.Type.BASE -> "base.apk"
                    playFile.name.endsWith(".apk") -> playFile.name
                    playFile.name.isNotBlank() -> "${playFile.name}.apk"
                    else -> "split_$index.apk"
                }
                val targetFile = File(stagingDir, partName)
                val success = downloader.downloadToFile(playFile.url, targetFile, callTag) { chunk ->
                    downloadedBytes += chunk
                    onProgress(downloadedBytes, totalBytes)
                }
                if (!success || targetFile.length() <= 0L) {
                    throw IOException("Failed to download Play split ${playFile.name} for ${update.packageName}")
                }
                targetFile
            }
        }
        is Link.Url -> {
            if (link.link.isBlank()) {
                throw IOException("Blank download URL for ${update.packageName}")
            }

            val callTag = "update_${update.id}"
            if (update.source == ApkMirrorSource) {
                activeTasks[update.id]?.let {
                    it.isResolvingLink = true
                    _activeTasksFlow.value = activeTasks.toMap()
                }
                refreshNotification(force = true)
                when (val resolution = apkMirrorResolver.resolveDirectDownload(link.link)) {
                    is ApkMirrorResolutionResult.CloudflareBlocked -> {
                        throw ApkMirrorCloudflareException(resolution.fallbackUrl)
                    }
                    is ApkMirrorResolutionResult.Failed -> {
                        throw IOException("APKMirror resolution failed: ${resolution.error.message}", resolution.error)
                    }
                    is ApkMirrorResolutionResult.Success -> {
                        activeTasks[update.id]?.let {
                            it.isResolvingLink = false
                            _activeTasksFlow.value = activeTasks.toMap()
                        }
                        val directUrl = resolution.directUrl
                        val isApkmHint = resolution.isApkm || resolution.filename.endsWith(".apkm", ignoreCase = true)
                        val tempPayload = File(stagingDir, if (isApkmHint) "bundle.apkm" else "download.bin")

                        var downloadedBytes = 0L
                        val totalBytes = link.size.takeIf { it > 0 } ?: 0L
                        onProgress(0L, totalBytes)

                        val success = downloader.downloadToFile(directUrl, tempPayload, callTag) { chunk ->
                            downloadedBytes += chunk
                            onProgress(downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes)
                        }

                        if (!success || tempPayload.length() <= 0L) {
                            throw IOException("Failed to download APKMirror payload for ${update.packageName}")
                        }

                        if (isApkmHint || ApkmExtractor.isApkmArchive(tempPayload)) {
                            val extractedFiles = ApkmExtractor.extractApkm(tempPayload, stagingDir)
                            tempPayload.delete()
                            extractedFiles
                        } else {
                            val baseApk = File(stagingDir, "base.apk")
                            if (tempPayload != baseApk) {
                                tempPayload.renameTo(baseApk)
                            }
                            listOf(baseApk)
                        }
                    }
                }
            } else {
                val totalBytes = link.size
                var downloadedBytes = 0L
                onProgress(0L, totalBytes)
                val targetFile = File(stagingDir, "base.apk")
                val success = downloader.downloadToFile(link.link, targetFile, callTag) { chunk ->
                    downloadedBytes += chunk
                    onProgress(downloadedBytes, totalBytes)
                }
                if (!success || targetFile.length() <= 0L) {
                    throw IOException("Failed to download URL APK for ${update.packageName}")
                }
                listOf(targetFile)
            }
        }
    }

    private fun handleCancelTask(id: Int) {
        Log.i(TAG, "Canceling download task for ID: $id")

        // 1. Cooperative socket cancellation via Downloader
        downloader.cancel("update_$id")

        // 2. Cancel active coroutine job
        activeDownloadJobs.remove(id)?.cancel()

        // 3. Wipe partial staging directory
        val stagingDir = File(cacheDir, "download_staging_$id")
        runCatching { stagingDir.deleteRecursively() }

        // 4. Reset install session if in progress
        installer.finish(id)

        // 5. Emit progress and cancellation status to InstallLog
        installLog.emitProgress(AppInstallProgress(id, 0L))
        installLog.emitStatus(AppInstallStatus(false, id, snack = true))

        // 6. Remove from task lists
        removeTask(id)
        refreshNotification(force = true)
    }

    private fun removeTask(id: Int) {
        activeTasks.remove(id)
        taskOrder.remove(id)
        _activeTasksFlow.value = activeTasks.toMap()
        if (taskOrder.isEmpty()) {
            stopForegroundAndSelf()
        }
    }

    private fun observeInstallLog() {
        serviceScope.launch {
            installLog.progress().collectLatest { prog ->
                val task = activeTasks[prog.id]
                if (task != null) {
                    prog.progress?.let { task.progress = it }
                    prog.total?.let { task.total = it }
                    _activeTasksFlow.value = activeTasks.toMap()
                    refreshNotification()
                }
            }
        }

        serviceScope.launch {
            installLog.status().collectLatest { status ->
                if (status.success) {
                    activeTasks[status.id]?.let { completedTask ->
                        if (completedTasks.none { it.id == completedTask.id }) {
                            completedTasks.add(0, completedTask.copy(isInstalling = false, isQueued = false, isResolvingLink = false))
                            _completedTasksFlow.value = completedTasks.toList()
                        }
                    }
                }
                removeTask(status.id)
                refreshNotification(force = true)
            }
        }
    }

    private fun refreshNotification(force: Boolean = false) {
        val currentActiveId = taskOrder.firstOrNull()
        if (currentActiveId == null) {
            stopForegroundAndSelf()
            return
        }

        val activeTask = activeTasks[currentActiveId] ?: return
        val totalCount = taskOrder.size
        val currentIndex = 1

        val percent = if (activeTask.total > 0L) {
            ((activeTask.progress * 100) / activeTask.total).toInt().coerceIn(0, 100)
        } else {
            0
        }

        if (notificationManager.shouldThrottle(percent, force)) {
            return
        }

        val notification = notificationManager.buildNotification(
            appName = activeTask.appName.ifBlank { activeTask.packageName },
            currentBytes = activeTask.progress,
            totalBytes = activeTask.total,
            isInstalling = activeTask.isInstalling,
            activeUpdateId = activeTask.id,
            batchIndex = currentIndex,
            batchTotal = totalCount,
            isResolvingLink = activeTask.isResolvingLink
        )
        notificationManager.update(notification)
    }

    private fun stopForegroundAndSelf() {
        if (isForegroundActive.compareAndSet(true, false)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        notificationManager.cancel()
        isForegroundActive.set(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
