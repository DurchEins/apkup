package com.apkup.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apkup.service.DownloadInstallService
import com.apkup.R
import com.apkup.prefs.Prefs
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.Link
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.UpdatesUiState
import com.apkup.data.ui.mergeRetainedUpdatesDuringCheck
import com.apkup.data.ui.removeId
import com.apkup.data.ui.removePackageName
import com.apkup.data.ui.setIsInstalling
import com.apkup.data.ui.setIsQueued
import com.apkup.data.ui.setProgress
import com.apkup.data.ui.toUpdatesUiState
import com.apkup.data.ui.updateCheckLoadingState
import com.apkup.repository.ApkMirrorResolutionResult
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.UpdatesRepository
import com.apkup.util.ApkmExtractor
import com.apkup.util.Badger
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.UpdatesNotification
import com.apkup.util.launchWithMutex
import com.aurora.gplayapi.data.models.PlayFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.UriHandler
import com.apkup.data.ui.ApkMirrorSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class ApkMirrorCloudflareException(val fallbackUrl: String) : IOException("Cloudflare challenge encountered on APKMirror")
class ApkMirrorResolutionException(val fallbackUrl: String, cause: Throwable? = null) : IOException("Failed to resolve direct APKMirror download link", cause)

data class DownloadedPackage(
	val id: Int,
	val packageName: String,
	val appName: String,
	val stagingDir: File,
	val files: List<File>
)

class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	private val prefs: Prefs,
	private val badger: Badger,
	private val downloader: Downloader,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	private val installLog: InstallLog,
	private val context: Context? = null,
	private val apkMirrorResolver: ApkMirrorResolver? = null,
	private val updatesNotification: UpdatesNotification? = null
) : InstallViewModel(downloader, installer, snackBar, stringer, installLog, prefs) {

	private val mutex = Mutex()
	private val installQueueMutex = Mutex()
	private val downloadSemaphore = Semaphore(3)
	private val activeDownloadJobs = ConcurrentHashMap<Int, Job>()
	private val installChannel = Channel<DownloadedPackage>(Channel.UNLIMITED)
	private var installProcessorJob: Job? = null
	private var updateCheckJob: Job? = null
	private var rawLatestUpdates: List<AppUpdate> = emptyList()
	private var retainedUpdatesDuringCheck: List<AppUpdate> = emptyList()
	private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
	private val _showInstallPermissionDialog = MutableStateFlow(false)
	val showInstallPermissionDialog: StateFlow<Boolean> = _showInstallPermissionDialog.asStateFlow()
	private val _showMobileDataDialog = MutableStateFlow(false)
	val showMobileDataDialog: StateFlow<Boolean> = _showMobileDataDialog.asStateFlow()
	private val _readyUpdateIds = MutableStateFlow<Set<Int>>(emptySet())
	val readyUpdateIds: StateFlow<Set<Int>> = _readyUpdateIds.asStateFlow()
	private var pendingMobileDataInstallAction: (() -> Unit)? = null
	@Volatile
	private var isAppInForeground: Boolean = true

	private fun getCacheDir(): File =
		context?.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_cache")

	init {
		downloader.cleanupOrphanStagingFiles(getCacheDir())
		subscribeToInstallStatus()
		subscribeToInstallProgress { progress ->
			viewModelScope.launch(Dispatchers.IO) {
				mutex.withLock {
					if (context == null || DownloadInstallService.activeTasksFlow.value.containsKey(progress.id)) {
						state.value = successState(state.value.mutableUpdates().setProgress(progress))
					}
				}
			}
		}
		viewModelScope.launch(Dispatchers.IO) {
			DownloadInstallService.activeTasksFlow.collect { activeMap ->
				mutex.withLock {
					val currentList = state.value.mutableUpdates()
					var modified = false
					currentList.forEachIndexed { index, appUpdate ->
						val task = activeMap[appUpdate.id]
							?: activeMap.values.firstOrNull { it.packageName == appUpdate.packageName }
						if (task != null) {
							val updated = appUpdate.copy(
								isInstalling = task.isInstalling || !task.isQueued,
								isQueued = task.isQueued,
								progress = task.progress,
								total = if (task.total > 0) task.total else appUpdate.total
							)
							if (currentList[index] != updated) {
								currentList[index] = updated
								modified = true
							}
						} else if (context != null && (appUpdate.isInstalling || appUpdate.isQueued || appUpdate.progress > 0L)) {
							val reset = appUpdate.copy(
								isInstalling = false,
								isQueued = false,
								progress = 0L
							)
							currentList[index] = reset
							modified = true
						}
					}
					if (modified) {
						state.value = successState(currentList)
					}
				}
			}
		}
		viewModelScope.launch(Dispatchers.IO) {
			prefs.showSystemAppsFlow
				.drop(1)
				.collect {
					val updatedApps = updatesRepository.getInstalledApps()
					refresh(
						load = true,
						installedAppsSnapshot = updatedApps,
						filterRetainedBySnapshot = true
					)
				}
		}
	}

	fun canProceedWithInstall(): Boolean =
		installer.isShizukuReady() || installer.canRequestPackageInstalls()

	fun triggerInstallPermissionPrompt() {
		_showInstallPermissionDialog.value = true
	}

	fun dismissInstallPermissionDialog() {
		_showInstallPermissionDialog.value = false
	}

	fun setAppInForeground(inForeground: Boolean) {
		isAppInForeground = inForeground
		if (inForeground) {
			updatesNotification?.cancelCheckProgressNotification()
		} else {
			val currentState = state.value
			val checking = currentState is UpdatesUiState.Loading || currentState.isChecking()
			if (checking) {
				updatesNotification?.showCheckProgressNotification(
					checkedApps = currentState.checkedApps(),
					totalApps = currentState.totalApps(),
					activeSource = currentState.activeSourceName()
				)
			}
		}
	}

	private fun isUnmeteredWifiConnected(): Boolean {
		val ctx = context ?: return true
		return runCatching {
			val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
			val network = cm.activeNetwork ?: return false
			val caps = cm.getNetworkCapabilities(network) ?: return false
			caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
				caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
				caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		}.getOrDefault(true)
	}

	private fun shouldPromptForMobileData(): Boolean =
		prefs.downloadWifiOnly.get() && !isUnmeteredWifiConnected()

	fun confirmMobileDataDownload() {
		val action = pendingMobileDataInstallAction
		pendingMobileDataInstallAction = null
		_showMobileDataDialog.value = false
		action?.invoke()
	}

	fun dismissMobileDataDialog() {
		pendingMobileDataInstallAction = null
		_showMobileDataDialog.value = false
	}

	fun state(): StateFlow<UpdatesUiState> = state

	@Synchronized
	fun beginRefresh() {
		updateCheckJob?.cancel()
		updateCheckJob = null
		retainedUpdatesDuringCheck = rawLatestUpdates.ifEmpty { state.value.updates() }
		state.value = updateCheckLoadingState(state.value)
	}

	@Synchronized
	fun restoreCacheImmediately(
		installedAppsSnapshot: Result<List<AppInstalled>>? = null
	): Boolean {
		val cached = updatesRepository.restoreCachedResult(installedAppsSnapshot?.getOrNull()) ?: return false
		val (timestampMillis, cachedResult) = cached
		rawLatestUpdates = cachedResult.updates
		retainedUpdatesDuringCheck = cachedResult.updates
		setSuccess(
			updates = cachedResult.updates,
			hasSourceErrors = cachedResult.hasFailures,
			isChecking = state.value.isChecking(),
			checkedApps = cachedResult.checkedApps,
			totalApps = cachedResult.totalApps,
			sourceErrors = cachedResult.sourceErrors,
			lastCheckedAtMillis = timestampMillis
		)
		return true
	}

	@Synchronized
	fun loadCachedOrRefresh(
		installedAppsSnapshot: Result<List<AppInstalled>>? = null
	): Job {
		val cached = updatesRepository.restoreCachedResult(installedAppsSnapshot?.getOrNull())
		if (cached != null) {
			val (timestampMillis, cachedResult) = cached
			return viewModelScope.launch(Dispatchers.IO) {
				mutex.withLock {
					rawLatestUpdates = cachedResult.updates
					retainedUpdatesDuringCheck = cachedResult.updates
					setSuccess(
						updates = cachedResult.updates,
						hasSourceErrors = cachedResult.hasFailures,
						isChecking = false,
						checkedApps = cachedResult.checkedApps,
						totalApps = cachedResult.totalApps,
						sourceErrors = cachedResult.sourceErrors,
						lastCheckedAtMillis = timestampMillis
					)
				}
			}
		}
		return refresh(load = true, installedAppsSnapshot = installedAppsSnapshot)
	}

	@Synchronized
	fun refresh(
		load: Boolean = true,
		installedAppsSnapshot: Result<List<AppInstalled>>? = null,
		filterRetainedBySnapshot: Boolean = false
	): Job {
		if (filterRetainedBySnapshot) {
			val allowedPackages = installedAppsSnapshot?.getOrNull()
				?.asSequence()
				?.filterNot { it.ignored }
				?.map { it.packageName }
				?.toSet()
			if (allowedPackages != null) {
				rawLatestUpdates = rawLatestUpdates.filter { it.packageName in allowedPackages }
				val filteredCurrent = state.value.updates().filter { it.packageName in allowedPackages }
				if (state.value is UpdatesUiState.Success) {
					setSuccess(
						updates = filteredCurrent,
						hasSourceErrors = state.value.hasSourceErrors(),
						isChecking = true,
						checkedApps = 0,
						totalApps = allowedPackages.size,
						sourceErrors = state.value.sourceErrors(),
						lastCheckedAtMillis = state.value.lastCheckedAtMillis()
					)
				}
			}
		}
		if (load) beginRefresh() else {
			updateCheckJob?.cancel()
			retainedUpdatesDuringCheck = rawLatestUpdates.ifEmpty { state.value.updates() }
		}
		return viewModelScope.launch(Dispatchers.IO) {
			mutex.withLock {
				if (retainedUpdatesDuringCheck.isEmpty()) {
					badger.changeUpdatesBadge("")
				}
				updatesRepository.updates(installedAppsSnapshot).collect {
					setSuccess(it)
				}
			}
		}.also { updateCheckJob = it }
	}

	fun refreshReadyUpdateIds() {
		val cache = getCacheDir()
		val currentUpdates = state.value.updates()
		val ready = currentUpdates.filter { update ->
			downloader.hasReadyCachedApk(cache, update.id, update.versionCode)
		}.map { it.id }.toSet()
		_readyUpdateIds.value = ready
	}

	override fun isApkMirrorBlocked(): Boolean =
		apkMirrorResolver?.isCloudflareBlocked() ?: false

	override fun install(update: AppUpdate, uriHandler: UriHandler) {
		val isDirectApkMirror = update.source == ApkMirrorSource && prefs.directApkMirrorDownloads.get() && !isApkMirrorBlocked()
		val isDirectDownload = update.source != ApkMirrorSource || isDirectApkMirror
		val isCachedReady = downloader.hasReadyCachedApk(getCacheDir(), update.id, update.versionCode)
		if (isDirectDownload && !canProceedWithInstall()) {
			triggerInstallPermissionPrompt()
			return
		}
		if (isDirectDownload && !isCachedReady && shouldPromptForMobileData()) {
			pendingMobileDataInstallAction = { super.install(update, uriHandler) }
			_showMobileDataDialog.value = true
			return
		}
		super.install(update, uriHandler)
	}

	fun installAll() = viewModelScope.launch(Dispatchers.IO) {
		val currentUpdates = state.value.updates()
		val directMirror = prefs.directApkMirrorDownloads.get()
		val (directInstallable, browserOnly) = partitionInstallQueueCandidates(currentUpdates, directMirror)
		if (directInstallable.isEmpty()) {
			if (browserOnly.isNotEmpty()) {
				snackBar.snackBar(
					viewModelScope,
					TextSnack(stringer.get(R.string.install_all_browser_only_hint))
				)
			}
			return@launch
		}
		if (!canProceedWithInstall()) {
			triggerInstallPermissionPrompt()
			return@launch
		}
		if (shouldPromptForMobileData()) {
			pendingMobileDataInstallAction = {
				viewModelScope.launch(Dispatchers.IO) {
					if (browserOnly.isNotEmpty()) {
						snackBar.snackBar(
							viewModelScope,
							TextSnack(stringer.get(R.string.install_all_skipped_browser_hint, browserOnly.size))
						)
					}
					enqueueInstallations(directInstallable)
				}
			}
			_showMobileDataDialog.value = true
			return@launch
		}

		if (browserOnly.isNotEmpty()) {
			snackBar.snackBar(
				viewModelScope,
				TextSnack(stringer.get(R.string.install_all_skipped_browser_hint, browserOnly.size))
			)
		}

		enqueueInstallations(directInstallable)
	}

	private suspend fun enqueueInstallations(candidates: List<AppUpdate>) {
		val newlyQueued = mutex.withLock {
			val queued = mutableListOf<AppUpdate>()
			val current = state.value.mutableUpdates()
			val alreadyActive = activeDownloadJobs.keys
			val serviceActiveMap = DownloadInstallService.activeTasksFlow.value
			candidates.forEach { candidate ->
				val idx = current.indexOfFirst { it.id == candidate.id || it.packageName == candidate.packageName }
				if (idx != -1) {
					val inState = current[idx]
					val isSameId = inState.id == candidate.id
					val isCurrentlyActive = if (context != null) {
						serviceActiveMap.containsKey(candidate.id) ||
							(isSameId && serviceActiveMap.containsKey(inState.id))
					} else {
						inState.isInstalling || (inState.isQueued && isSameId) ||
							candidate.id in alreadyActive || inState.id in alreadyActive
					}
					if (!isCurrentlyActive) {
						val queuedCandidate = if (!isSameId) {
							candidate.copy(
								isQueued = true,
								isInstalling = false,
								progress = 0L,
								alternativeUpdates = inState.alternativeUpdates.ifEmpty { candidate.alternativeUpdates }
							)
						} else {
							candidate.copy(
								isQueued = true,
								isInstalling = false,
								progress = 0L
							)
						}
						current[idx] = queuedCandidate
						queued.add(queuedCandidate)
					}
				}
			}
			if (queued.isNotEmpty()) {
				state.value = successState(current)
			}
			queued
		}
		if (newlyQueued.isNotEmpty()) {
			val ctx = context
			if (ctx != null) {
				DownloadInstallService.startDownloads(ctx, newlyQueued)
			} else {
				ensureInstallProcessorStarted()
				newlyQueued.forEach { update ->
					startDownload(update)
				}
			}
		}
	}

	private fun startDownload(update: AppUpdate) {
		val job = viewModelScope.launch(Dispatchers.IO) {
			val stagingDir = File(getCacheDir(), "download_staging_${update.id}").apply { mkdirs() }
			try {
				downloadSemaphore.withPermit {
					val shouldProceed = mutex.withLock {
						val current = state.value.mutableUpdates()
						val exists = current.any { it.id == update.id }
						if (exists) {
							state.value = successState(
								current.setIsQueued(update.id, false).setIsInstalling(update.id, true)
							)
						}
						exists
					}
					if (!shouldProceed) {
						stagingDir.deleteRecursively()
						return@withPermit
					}

					installLog.beginInstall(update.id)
					installLog.emitProgress(AppInstallProgress(update.id, 0L))

					val cachedFiles = downloader.getReadyStagingFiles(getCacheDir(), update.id, update.versionCode)
					val files = if (cachedFiles != null && cachedFiles.isNotEmpty()) {
						cachedFiles
					} else {
						val downloaded = downloadUpdateFiles(update, stagingDir) { currentBytes, totalBytes ->
							installLog.emitProgress(AppInstallProgress(update.id, currentBytes, totalBytes))
						}
						if (downloaded.isEmpty()) {
							throw IOException("No files downloaded for ${update.packageName}")
						}
						downloader.markStagingReady(stagingDir, update.versionCode)
						downloaded
					}

					ensureInstallProcessorStarted()
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
				runCatching { stagingDir.deleteRecursively() }
				installLog.emitProgress(AppInstallProgress(update.id, 0L))
				cancelInstall(update.id)
				throw cancellation
			} catch (cf: ApkMirrorCloudflareException) {
				Log.w("UpdatesViewModel", "Cloudflare security challenge for ${update.packageName}; launching browser fallback.")
				runCatching { stagingDir.deleteRecursively() }
				installLog.emitProgress(AppInstallProgress(update.id, 0L))
				cancelInstall(update.id).join()
				snackBar.snackBar(
					viewModelScope,
					TextSnack(stringer.get(R.string.apkmirror_challenge_fallback))
				)
				val fallbackUrl = cf.fallbackUrl.ifBlank { (update.link as? Link.Url)?.link.orEmpty() }
				if (fallbackUrl.isNotBlank()) {
					context?.let { ctx ->
						runCatching {
							val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackUrl)).apply {
								addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
							}
							ctx.startActivity(intent)
						}
					}
				}
			} catch (failed: ApkMirrorResolutionException) {
				Log.w("UpdatesViewModel", "APKMirror link resolution failed for ${update.packageName}; launching browser fallback.")
				runCatching { stagingDir.deleteRecursively() }
				installLog.emitProgress(AppInstallProgress(update.id, 0L))
				cancelInstall(update.id).join()
				snackBar.snackBar(
					viewModelScope,
					TextSnack(stringer.get(R.string.apkmirror_resolution_failed_fallback))
				)
				val fallbackUrl = failed.fallbackUrl.ifBlank { (update.link as? Link.Url)?.link.orEmpty() }
				if (fallbackUrl.isNotBlank()) {
					context?.let { ctx ->
						runCatching {
							val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fallbackUrl)).apply {
								addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
							}
							ctx.startActivity(intent)
						}
					}
				}
			} catch (error: Throwable) {
				Log.e("UpdatesViewModel", "Download failed for ${update.packageName}", error)
				runCatching { stagingDir.deleteRecursively() }
				installLog.emitProgress(AppInstallProgress(update.id, 0L))
				if (!installLog.emitStatus(AppInstallStatus(false, update.id, snack = true))) {
					cancelInstall(update.id).join()
				}
			} finally {
				activeDownloadJobs.remove(update.id)
			}
		}
		activeDownloadJobs[update.id] = job
	}

	private fun ensureInstallProcessorStarted() {
		if (installProcessorJob?.isActive == true) return
		installProcessorJob = viewModelScope.launch(Dispatchers.IO) {
			for (pkg in installChannel) {
				installQueueMutex.withLock {
					val stillPresent = mutex.withLock {
						state.value.updates().any { it.id == pkg.id }
					}
					if (!stillPresent) {
						runCatching { pkg.stagingDir.deleteRecursively() }
					} else {
						mutex.withLock {
							val current = state.value.mutableUpdates()
							if (current.any { it.id == pkg.id }) {
								state.value = successState(
									current.setIsQueued(pkg.id, false).setIsInstalling(pkg.id, true)
								)
							}
						}

						try {
							val parts = pkg.files.map { file ->
								InstallPart(file.name) { file.inputStream() }
							}
							installer.installParts(pkg.id, pkg.packageName, parts)
						} catch (cancellation: CancellationException) {
							Log.i("UpdatesViewModel", "Installation cancelled for ${pkg.packageName} (${pkg.id})")
							installLog.emitProgress(AppInstallProgress(pkg.id, 0L))
							cancelInstall(pkg.id)
						} catch (error: Throwable) {
							Log.e("UpdatesViewModel", "Installation failed for ${pkg.packageName}", error)
							installLog.emitProgress(AppInstallProgress(pkg.id, 0L))
							if (!installLog.emitStatus(AppInstallStatus(false, pkg.id, snack = true))) {
								cancelInstall(pkg.id).join()
							}
						} finally {
							runCatching { pkg.stagingDir.deleteRecursively() }
						}
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
			Log.e("UpdatesViewModel", "downloadUpdateFiles: Empty link for ${update.packageName}")
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
				val resolver = apkMirrorResolver ?: throw IOException("ApkMirrorResolver not initialized")
				when (val resolution = resolver.resolveDirectDownload(link.link, callTag)) {
					is ApkMirrorResolutionResult.CloudflareBlocked -> {
						throw ApkMirrorCloudflareException(resolution.fallbackUrl)
					}
					is ApkMirrorResolutionResult.Failed -> {
						throw ApkMirrorResolutionException(link.link, resolution.error)
					}
					is ApkMirrorResolutionResult.Success -> {
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

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val targetUpdate = state.value.updates().find { it.id == id }
			?: rawLatestUpdates.find { it.id == id }
		val ignored = prefs.ignoredVersions.get().toMutableList()
		val entries = prefs.readIgnoredVersionEntriesSafe().toMutableList()
		if (ignored.contains(id)) {
			ignored.remove(id)
			entries.removeAll { it.id == id }
		} else {
			ignored.add(id)
			if (targetUpdate != null && entries.none { it.id == id }) {
				entries.add(
					IgnoredVersionEntry(
						id = id,
						packageName = targetUpdate.packageName,
						appName = targetUpdate.name.ifBlank { targetUpdate.packageName },
						version = targetUpdate.version,
						versionCode = targetUpdate.versionCode,
						sourceName = targetUpdate.source.name,
						ignoredAtMillis = System.currentTimeMillis()
					)
				)
			}
		}
		prefs.ignoredVersions.put(ignored)
		prefs.ignoredVersionEntries.put(entries)
		setSuccess(
			updates = rawLatestUpdates.ifEmpty { state.value.mutableUpdates() },
			hasSourceErrors = state.value.hasSourceErrors(),
			sourceErrors = state.value.sourceErrors(),
			lastCheckedAtMillis = state.value.lastCheckedAtMillis()
		)
	}

	fun ignoreAppPermanently(packageName: String) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val ignoredApps = prefs.ignoredApps.get().toMutableList()
		if (!ignoredApps.contains(packageName)) {
			ignoredApps.add(packageName)
			prefs.ignoredApps.put(ignoredApps)
		}
		rawLatestUpdates = rawLatestUpdates.filterNot { it.packageName == packageName }
		val updatedList = state.value.mutableUpdates().removePackageName(packageName)
		setSuccess(
			updates = updatedList,
			hasSourceErrors = state.value.hasSourceErrors(),
			sourceErrors = state.value.sourceErrors(),
			lastCheckedAtMillis = state.value.lastCheckedAtMillis()
		)
		updatesRepository.saveSnapshotToCache(
			result = UpdateCheckResult(
				updates = updatedList,
				successfulRequests = 1,
				failedRequests = if (state.value.hasSourceErrors()) 1 else 0,
				totalApps = state.value.totalApps(),
				sourceErrors = state.value.sourceErrors()
			),
			timestampMillis = state.value.lastCheckedAtMillis() ?: System.currentTimeMillis()
		)
	}

	fun syncIgnoredFromPrefs() = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val ignoredPackages = prefs.ignoredApps.get().toSet()
		val baseUpdates = if (rawLatestUpdates.isNotEmpty()) {
			rawLatestUpdates
		} else {
			updatesRepository.restoreCachedResult()?.second?.updates ?: state.value.updates()
		}
		val afterPackageFilter = baseUpdates.filterNot { it.packageName in ignoredPackages }
		setSuccess(
			updates = afterPackageFilter,
			hasSourceErrors = state.value.hasSourceErrors(),
			sourceErrors = state.value.sourceErrors(),
			lastCheckedAtMillis = state.value.lastCheckedAtMillis()
		)
	}

	public override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		context?.let { ctx -> DownloadInstallService.cancelDownload(ctx, id) }
		apkMirrorResolver?.cancelResolution("update_$id")
		downloader.cancel("update_$id")
		activeDownloadJobs.remove(id)?.cancel()
		val stagingDir = File(getCacheDir(), "download_staging_$id")
		if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
			runCatching { stagingDir.deleteRecursively() }
		}
		state.value = successState(
			state.value.mutableUpdates().map { update ->
				if (update.id == id) {
					update.copy(isQueued = false, isInstalling = false, progress = 0L)
				} else {
					update
				}
			}
		)
		installer.finish(id)
		refreshReadyUpdateIds()
	}

	fun cancelAllInstalls() = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		context?.let { ctx -> DownloadInstallService.cancelAllDownloads(ctx) }
		val current = state.value.mutableUpdates()
		val resetList = current.map { update ->
			if (update.isInstalling || update.isQueued || update.progress > 0L) {
				apkMirrorResolver?.cancelResolution("update_${update.id}")
				downloader.cancel("update_${update.id}")
				activeDownloadJobs.remove(update.id)?.cancel()
				val stagingDir = File(getCacheDir(), "download_staging_${update.id}")
				if (!File(stagingDir, Downloader.READY_MARKER_FILE).exists()) {
					runCatching { stagingDir.deleteRecursively() }
				}
				installer.finish(update.id)
				update.copy(isQueued = false, isInstalling = false, progress = 0L)
			} else {
				update
			}
		}
		state.value = successState(resetList)
		refreshReadyUpdateIds()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		activeDownloadJobs.remove(id)
		runCatching {
			File(getCacheDir(), "download_staging_$id").deleteRecursively()
		}
		rawLatestUpdates = rawLatestUpdates.filterNot { it.id == id }
		val remaining = state.value.mutableUpdates().removeId(id)
		setSuccess(
			updates = remaining,
			hasSourceErrors = state.value.hasSourceErrors(),
			sourceErrors = state.value.sourceErrors(),
			lastCheckedAtMillis = state.value.lastCheckedAtMillis()
		)
		updatesRepository.saveSnapshotToCache(
			result = UpdateCheckResult(
				updates = remaining,
				successfulRequests = 1,
				failedRequests = if (state.value.hasSourceErrors()) 1 else 0,
				totalApps = state.value.totalApps(),
				sourceErrors = state.value.sourceErrors()
			),
			timestampMillis = state.value.lastCheckedAtMillis() ?: System.currentTimeMillis()
		)
		installer.finish(id)
		refreshReadyUpdateIds()
	}

	override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		if (installer.checkPermission()) {
			enqueueInstallations(listOf(update))
		}
	}

	override fun sendInstallSnack(log: AppInstallStatus) {
		if (log.snack) {
			state.value.updates().find { log.id == it.id }?.let { app ->
				val message = if (log.success) R.string.install_success else R.string.install_failure
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name)))
			}
		}
	}

	private fun List<AppUpdate>.filterIgnoredVersions(ignoredVersions: List<Int>): List<AppUpdate> {
		val ignoredSet = ignoredVersions.toSet()
		val ignoredPackages = prefs.ignoredApps.get().toSet()
		return filter { update ->
			update.id !in ignoredSet && update.packageName !in ignoredPackages
		}
	}

	private fun setSuccess(result: UpdateCheckResult<AppUpdate>) {
		when (val mappedState = result.toUpdatesUiState()) {
			UpdatesUiState.Error -> {
				updatesNotification?.cancelCheckProgressNotification()
				if (retainedUpdatesDuringCheck.isNotEmpty()) {
					setSuccess(
						updates = retainedUpdatesDuringCheck,
						hasSourceErrors = true,
						isChecking = false,
						checkedApps = result.checkedApps,
						totalApps = result.totalApps,
						activeSourceName = null,
						sourceErrors = result.sourceErrors,
						lastCheckedAtMillis = state.value.lastCheckedAtMillis()
					)
				} else {
					state.value = UpdatesUiState.Error
					badger.changeUpdatesBadge("")
				}
			}
			is UpdatesUiState.Success -> {
				val wasChecking = state.value is UpdatesUiState.Loading || state.value.isChecking()
				val effectiveUpdates = mergeRetainedUpdatesDuringCheck(
					retainedUpdates = retainedUpdatesDuringCheck,
					incomingUpdates = mappedState.updates,
					isChecking = mappedState.isChecking
				)
				rawLatestUpdates = effectiveUpdates
				if (!mappedState.isChecking) {
					retainedUpdatesDuringCheck = effectiveUpdates
				}
				val checkedTimestamp = if (!mappedState.isChecking) {
					System.currentTimeMillis()
				} else {
					state.value.lastCheckedAtMillis()
				}
				setSuccess(
					updates = effectiveUpdates,
					hasSourceErrors = mappedState.hasSourceErrors,
					isChecking = mappedState.isChecking,
					checkedApps = mappedState.checkedApps,
					totalApps = mappedState.totalApps,
					activeSourceName = mappedState.activeSourceName,
					sourceErrors = mappedState.sourceErrors,
					lastCheckedAtMillis = checkedTimestamp,
					notifyCompletionIfBackground = wasChecking && !mappedState.isChecking
				)
			}
			UpdatesUiState.Loading -> Unit
		}
	}

	private fun setSuccess(
		updates: List<AppUpdate>,
		hasSourceErrors: Boolean = false,
		isChecking: Boolean = state.value.isChecking(),
		checkedApps: Int? = state.value.checkedApps(),
		totalApps: Int? = state.value.totalApps(),
		activeSourceName: String? = state.value.activeSourceName(),
		sourceErrors: Map<String, String> = state.value.sourceErrors(),
		lastCheckedAtMillis: Long? = state.value.lastCheckedAtMillis(),
		notifyCompletionIfBackground: Boolean = false
	) {
		val filtered = updates.filterIgnoredVersions(prefs.ignoredVersions.get())
		state.value = UpdatesUiState.Success(
			updates = filtered,
			hasSourceErrors = hasSourceErrors,
			isChecking = isChecking,
			checkedApps = checkedApps,
			totalApps = totalApps,
			activeSourceName = if (isChecking) activeSourceName else null,
			sourceErrors = sourceErrors,
			lastCheckedAtMillis = lastCheckedAtMillis
		)
		badger.changeUpdatesBadge(if (filtered.isEmpty()) "" else filtered.size.toString())
		refreshReadyUpdateIds()
		if (isChecking) {
			if (!isAppInForeground) {
				updatesNotification?.showCheckProgressNotification(
					checkedApps = checkedApps,
					totalApps = totalApps,
					activeSource = activeSourceName
				)
			}
		} else {
			updatesNotification?.cancelCheckProgressNotification()
			if (notifyCompletionIfBackground && !isAppInForeground && filtered.isNotEmpty()) {
				updatesNotification?.showUpdateNotification(filtered.size)
			}
		}
	}

	private fun successState(updates: List<AppUpdate>) =
		UpdatesUiState.Success(
			updates = updates,
			hasSourceErrors = state.value.hasSourceErrors(),
			isChecking = state.value.isChecking(),
			checkedApps = state.value.checkedApps(),
			totalApps = state.value.totalApps(),
			activeSourceName = state.value.activeSourceName(),
			sourceErrors = state.value.sourceErrors(),
			lastCheckedAtMillis = state.value.lastCheckedAtMillis()
		)

	private fun UpdatesUiState.hasSourceErrors() = this is UpdatesUiState.Success && hasSourceErrors
	private fun UpdatesUiState.isChecking() = this is UpdatesUiState.Success && isChecking
	private fun UpdatesUiState.checkedApps() = (this as? UpdatesUiState.Success)?.checkedApps
	private fun UpdatesUiState.totalApps() = (this as? UpdatesUiState.Success)?.totalApps
	private fun UpdatesUiState.activeSourceName() = (this as? UpdatesUiState.Success)?.activeSourceName
	private fun UpdatesUiState.sourceErrors() = (this as? UpdatesUiState.Success)?.sourceErrors.orEmpty()
	private fun UpdatesUiState.lastCheckedAtMillis() = (this as? UpdatesUiState.Success)?.lastCheckedAtMillis

}

internal fun partitionInstallQueueCandidates(
	updates: List<AppUpdate>,
	directApkMirrorEnabled: Boolean = false
): Pair<List<AppUpdate>, List<AppUpdate>> {
	val direct = mutableListOf<AppUpdate>()
	val browserOnly = mutableListOf<AppUpdate>()
	updates.forEach { update ->
		val isDirect = update.isDirectInstallable(directApkMirrorEnabled)
		val directAlt = if (!isDirect) {
			update.alternativeUpdates.firstOrNull { it.isDirectInstallable(directApkMirrorEnabled) }
		} else null
		val isBrowser = update.isBrowserOnlyUpdate(directApkMirrorEnabled) ||
			update.alternativeUpdates.any { it.isBrowserOnlyUpdate(directApkMirrorEnabled) }

		when {
			isDirect -> direct.add(update)
			directAlt != null -> direct.add(directAlt)
			isBrowser -> browserOnly.add(update)
		}
	}
	return direct to browserOnly
}
