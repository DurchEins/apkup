package com.apkup.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.viewModelScope
import com.apkup.R
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.InstallFailureReason
import com.apkup.data.ui.Link
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.data.ui.UpdatesUiState
import com.apkup.data.ui.mergeRetainedUpdatesDuringCheck
import com.apkup.data.ui.removeId
import com.apkup.data.ui.removePackageName
import com.apkup.data.ui.setProgress
import com.apkup.data.ui.toUpdatesUiState
import com.apkup.data.ui.updateCheckLoadingState
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.UpdatesRepository
import com.apkup.service.DownloadTaskState
import com.apkup.util.AppJournal
import com.apkup.util.Badger
import com.apkup.util.BatchProgressSummary
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.UpdatesNotification
import com.apkup.util.buildUpdateHistoryEntry
import com.apkup.util.launchWithMutex
import com.apkup.worker.DownloadCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	private val prefs: Prefs,
	private val badger: Badger,
	private val downloader: Downloader,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	private val installLog: InstallLog,
	context: Context? = null,
	private val apkMirrorResolver: ApkMirrorResolver? = null,
	private val updatesNotification: UpdatesNotification? = null,
	private val downloadCoordinator: DownloadCoordinator? = null
) : InstallViewModel(downloader, installer, snackBar, stringer, installLog, prefs) {

	private val appContext: Context? = context?.applicationContext ?: context
	private val scanMutex = Mutex()
	private val uiStateMutex = Mutex()
	private var updateCheckJob: Job? = null

	@Volatile
	private var rawLatestUpdates: List<AppUpdate> = emptyList()

	@Volatile
	private var retainedUpdatesDuringCheck: List<AppUpdate> = emptyList()

	private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)

	private val _showInstallPermissionDialog = MutableStateFlow(false)
	val showInstallPermissionDialog: StateFlow<Boolean> = _showInstallPermissionDialog.asStateFlow()

	private val _showMobileDataDialog = MutableStateFlow(false)
	val showMobileDataDialog: StateFlow<Boolean> = _showMobileDataDialog.asStateFlow()

	private val _showBatteryPromptDialog = MutableStateFlow(false)
	val showBatteryPromptDialog: StateFlow<Boolean> = _showBatteryPromptDialog.asStateFlow()

	private val _readyUpdateIds = MutableStateFlow<Set<Int>>(emptySet())
	val readyUpdateIds: StateFlow<Set<Int>> = _readyUpdateIds.asStateFlow()

	private val emptyActiveTasks = MutableStateFlow<Map<Int, DownloadTaskState>>(emptyMap())
	val activeTasksFlow: StateFlow<Map<Int, DownloadTaskState>> =
		downloadCoordinator?.activeTasksFlow ?: emptyActiveTasks.asStateFlow()

	private val emptyCompletedTasks = MutableStateFlow<List<DownloadTaskState>>(emptyList())
	val completedTasksFlow: StateFlow<List<DownloadTaskState>> =
		downloadCoordinator?.completedTasksFlow ?: emptyCompletedTasks.asStateFlow()

	private val emptyBatchProgress = MutableStateFlow<BatchProgressSummary?>(null)
	val batchProgressFlow: StateFlow<BatchProgressSummary?> =
		downloadCoordinator?.batchProgressFlow ?: emptyBatchProgress.asStateFlow()

	val updateHistoryFlow: StateFlow<List<UpdateHistoryEntry>> = prefs.updateHistoryFlow
	val pinnedAppSourcesFlow: StateFlow<Map<String, String>> = prefs.pinnedAppSourcesFlow

	private var pendingMobileDataInstallAction: (() -> Unit)? = null
	private var pendingBatteryInstallAction: (() -> Unit)? = null

	@Volatile
	private var isAppInForeground: Boolean = true

	private fun getCacheDir(): File =
		appContext?.let { DownloadStorageManager.getDownloadDir(it) }
			?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_cache")

	init {
		downloader.cleanupOrphanStagingFiles(getCacheDir())
		subscribeToInstallStatus()
		subscribeToInstallProgress { progress ->
			viewModelScope.launch(Dispatchers.IO) {
				uiStateMutex.withLock {
					if (downloadCoordinator == null || activeTasksFlow.value.containsKey(progress.id)) {
						state.value = successState(state.value.mutableUpdates().setProgress(progress))
					}
				}
			}
		}
		if (downloadCoordinator != null) {
			viewModelScope.launch(Dispatchers.Main.immediate) {
				downloadCoordinator.safFallbackEvents.collect { labelOrLocation ->
					notifySafFallbackUsed(labelOrLocation)
				}
			}
			viewModelScope.launch(Dispatchers.IO) {
				downloadCoordinator.activeTasksFlow.collect { activeMap ->
					uiStateMutex.withLock {
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
							} else if (appUpdate.isInstalling || appUpdate.isQueued || appUpdate.progress > 0L) {
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
						refreshReadyUpdateIds()
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

	fun clearUpdateHistory() = prefs.clearUpdateHistory()

	fun clearCompletedDownloadTasks() {
		downloadCoordinator?.clearCompletedTasks()
	}

	fun getUpdatesSortOrder(): String = prefs.updatesSortOrder.get()

	fun setUpdatesSortOrder(orderName: String) = prefs.updatesSortOrder.put(orderName)

	fun getCustomRepoBinding(packageName: String): RepoBinding? =
		prefs.readCustomRepoBindingsSafe()[packageName]

	fun pinAppSource(packageName: String, sourceKey: String?) =
		prefs.pinAppSource(packageName, sourceKey)

	val appPreReleaseOverridesFlow: StateFlow<Map<String, String>> = prefs.appPreReleaseOverridesFlow

	fun getAppPreReleaseOverride(packageName: String): String =
		prefs.getAppPreReleaseOverride(packageName)

	fun setAppPreReleaseOverride(packageName: String, mode: String?) =
		prefs.setAppPreReleaseOverride(packageName, mode)

	fun isRootReady(): Boolean = installer.isRootReady()

	suspend fun executeRollback(
		packageName: String,
		backupTarget: File,
		cleanReinstallWithRoot: Boolean = false
	): com.apkup.util.RollbackInstallResult =
		installer.executeRollbackInstall(packageName, backupTarget, cleanReinstallWithRoot)

	fun markWatchedAppSeen(update: AppUpdate) = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		prefs.markWatchedAppSeen(update.packageName, update.version, update.versionCode)
		rawLatestUpdates = rawLatestUpdates.filterNot { it.packageName == update.packageName }
		val updatedList = state.value.mutableUpdates().removePackageName(update.packageName)
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
		val msg = runCatching { stringer.get(R.string.watched_mark_seen_snack, update.version) }
			.getOrDefault("Marked v${update.version} as seen")
		snackBar.snackBar(viewModelScope, TextSnack(msg, visualType = com.apkup.data.snack.SnackVisualType.SUCCESS))
	}

	fun getUpdatesDisplayMode(): Int = prefs.updatesDisplayMode.get()

	fun isDirectApkMirrorEnabled(): Boolean = prefs.directApkMirrorDownloads.get()

	fun isUseApkMirrorEnabled(): Boolean = prefs.useApkMirror.get()

	fun isUsePlayEnabled(): Boolean = prefs.usePlay.get()

	fun getEnabledFDroidRepoNames(): List<String> = buildList {
		if (prefs.useIzzyOnDroid.get()) {
			prefs.getFDroidRepos().filter { it.enabled }.forEach { add(it.name) }
		}
	}

	fun getActiveSourceNames(): List<String> = buildList {
		if (prefs.useApkMirror.get()) add("APKMirror")
		if (prefs.usePlay.get()) add("Google Play")
		if (prefs.useIzzyOnDroid.get()) {
			val enabledFDroidRepos = prefs.getFDroidRepos().filter { it.enabled }
			enabledFDroidRepos.forEach { add(it.name) }
		}
		if (prefs.useGitHub.get()) add("GitHub")
		if (prefs.useGitLab.get()) add("GitLab")
		if (prefs.useCodeberg.get()) add("Codeberg")
		if (prefs.useCustomFDroidRepos.get()) {
			val customRepos = prefs.readCustomFDroidReposSafe().filter { it.enabled }
			customRepos.forEach { add(it.name) }
		}
	}

	fun canProceedWithInstall(): Boolean =
		installer.isSilentInstallerReady() || installer.canRequestPackageInstalls()

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
		val ctx = appContext ?: return true
		return runCatching {
			val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
			val network = cm.activeNetwork ?: return false
			val caps = cm.getNetworkCapabilities(network) ?: return false
			caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
				caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
				caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		}.getOrDefault(true)
	}

	private fun isDeviceCharging(): Boolean {
		val ctx = appContext ?: return true
		return runCatching {
			val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
			val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
			val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
			status == BatteryManager.BATTERY_STATUS_CHARGING ||
				status == BatteryManager.BATTERY_STATUS_FULL ||
				plugged != 0
		}.getOrDefault(true)
	}

	private fun shouldPromptForMobileData(): Boolean =
		prefs.downloadWifiOnly.get() && !isUnmeteredWifiConnected()

	private fun shouldPromptForBattery(): Boolean =
		prefs.downloadChargingOnly.get() && !isDeviceCharging()

	private fun runWithDownloadGuards(isCachedReady: Boolean, onProceed: () -> Unit) {
		if (!isCachedReady && shouldPromptForMobileData()) {
			pendingMobileDataInstallAction = {
				checkBatteryGuardOrProceed(isCachedReady, onProceed)
			}
			_showMobileDataDialog.value = true
			return
		}
		checkBatteryGuardOrProceed(isCachedReady, onProceed)
	}

	private fun checkBatteryGuardOrProceed(isCachedReady: Boolean, onProceed: () -> Unit) {
		if (!isCachedReady && shouldPromptForBattery()) {
			pendingBatteryInstallAction = onProceed
			_showBatteryPromptDialog.value = true
			return
		}
		onProceed()
	}

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

	fun confirmBatteryDownload() {
		val action = pendingBatteryInstallAction
		pendingBatteryInstallAction = null
		_showBatteryPromptDialog.value = false
		action?.invoke()
	}

	fun dismissBatteryPromptDialog() {
		pendingBatteryInstallAction = null
		_showBatteryPromptDialog.value = false
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
				uiStateMutex.withLock {
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
			scanMutex.withLock {
				if (retainedUpdatesDuringCheck.isEmpty()) {
					badger.changeUpdatesBadge("")
				}
				updatesRepository.updates(installedAppsSnapshot).collect { checkResult ->
					uiStateMutex.withLock {
						setSuccess(checkResult)
					}
				}
			}
		}.also { updateCheckJob = it }
	}

	fun refreshReadyUpdateIds() {
		val cache = getCacheDir()
		val currentUpdates = state.value.updates()
		val allCandidates = currentUpdates.flatMap { listOf(it) + it.alternativeUpdates }
		val ready = allCandidates.filter { update ->
			downloader.hasReadyCachedApk(cache, update.id, update.versionCode)
		}.map { it.id }.toSet()
		_readyUpdateIds.value = ready
	}

	override fun isApkMirrorBlocked(): Boolean =
		apkMirrorResolver?.isCloudflareBlocked() ?: false

	override fun isApkMirrorBlocked(update: AppUpdate): Boolean =
		apkMirrorResolver?.isAppInCooldown(
			variantUrl = (update.link as? Link.Url)?.link,
			packageName = update.packageName
		) ?: false

	override fun install(update: AppUpdate, uriHandler: UriHandler) {
		val inCooldown = isApkMirrorBlocked(update)
		val isDirectApkMirror = update.source == ApkMirrorSource && prefs.directApkMirrorDownloads.get() && !inCooldown
		if (update.source == ApkMirrorSource && prefs.directApkMirrorDownloads.get() && inCooldown) {
			AppJournal.record("ApkMirror", "Cooldown active on tap for ${update.packageName}; opening browser fallback")
		}
		val isDirectDownload = update.source != ApkMirrorSource || isDirectApkMirror
		val isCachedReady = downloader.hasReadyCachedApk(getCacheDir(), update.id, update.versionCode)
		if (isDirectDownload && !canProceedWithInstall()) {
			triggerInstallPermissionPrompt()
			return
		}
		if (isDirectDownload) {
			runWithDownloadGuards(isCachedReady = isCachedReady) {
				super.install(update, uriHandler)
			}
			return
		}
		super.install(update, uriHandler)
	}

	fun saveApk(update: AppUpdate, uriHandler: UriHandler) {
		val inCooldown = isApkMirrorBlocked(update)
		val isDirectApkMirror = update.source == ApkMirrorSource && prefs.directApkMirrorDownloads.get() && !inCooldown
		if (update.source == ApkMirrorSource && (!prefs.directApkMirrorDownloads.get() || inCooldown)) {
			val url = (update.link as? Link.Url)?.link.orEmpty()
			if (url.isNotBlank()) {
				if (prefs.directApkMirrorDownloads.get() && inCooldown) {
					snackBar.snackBar(
						viewModelScope,
						TextSnack(stringer.get(R.string.apkmirror_cooldown_browser_fallback, update.name), visualType = com.apkup.data.snack.SnackVisualType.WARNING)
					)
				}
				runCatching { uriHandler.openUri(url) }
			}
			return
		}
		if (update.link is Link.Empty) return
		val isDirectDownload = update.source != ApkMirrorSource || isDirectApkMirror
		val isCachedReady = downloader.hasReadyCachedApk(getCacheDir(), update.id, update.versionCode)
		if (isDirectDownload) {
			runWithDownloadGuards(isCachedReady = isCachedReady) {
				saveApkDirect(update)
			}
			return
		}
		saveApkDirect(update)
	}

	private fun saveApkDirect(update: AppUpdate) {
		downloadCoordinator?.enqueue(update, saveOnly = true)
	}

	/**
	 * Enqueues direct-installable updates (`F-19`: when [targetUpdates] is supplied by a filtered view,
	 * only those filtered updates are queued instead of all unfiltered updates).
	 */
	fun installAll(targetUpdates: List<AppUpdate>? = null) = viewModelScope.launch(Dispatchers.IO) {
		installBatch(targetUpdates ?: state.value.updates())
	}

	fun installSelected(selectedUpdates: List<AppUpdate>) = viewModelScope.launch(Dispatchers.IO) {
		if (selectedUpdates.isEmpty()) return@launch
		installBatch(selectedUpdates)
	}

	private suspend fun installBatch(candidates: List<AppUpdate>) {
		val directMirror = prefs.directApkMirrorDownloads.get()
		val (rawDirect, rawBrowser) = partitionInstallQueueCandidates(candidates, directMirror)
		val cooldownSkipped = rawDirect.filter { it.source == ApkMirrorSource && isApkMirrorBlocked(it) }
		val directInstallable = if (cooldownSkipped.isEmpty()) rawDirect else rawDirect.filterNot { it in cooldownSkipped }
		val browserOnly = rawBrowser + cooldownSkipped
		if (directInstallable.isEmpty()) {
			if (browserOnly.isNotEmpty()) {
				snackBar.snackBar(
					viewModelScope,
					TextSnack(stringer.get(R.string.install_all_browser_only_hint))
				)
			}
			return
		}
		if (!canProceedWithInstall()) {
			triggerInstallPermissionPrompt()
			return
		}

		val allCachedReady = directInstallable.all { candidate ->
			downloader.hasReadyCachedApk(getCacheDir(), candidate.id, candidate.versionCode)
		}

		runWithDownloadGuards(isCachedReady = allCachedReady) {
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
	}

	private suspend fun enqueueInstallations(candidates: List<AppUpdate>) {
		val newlyQueued = uiStateMutex.withLock {
			val queued = mutableListOf<AppUpdate>()
			val current = state.value.mutableUpdates()
			val serviceActiveMap = activeTasksFlow.value
			candidates.forEach { candidate ->
				val idx = current.indexOfFirst { it.id == candidate.id || it.packageName == candidate.packageName }
				if (idx != -1) {
					val inState = current[idx]
					val isSameId = inState.id == candidate.id
					val isCachedReady = candidate.id in readyUpdateIds.value || inState.id in readyUpdateIds.value
					val isCurrentlyActive = if (downloadCoordinator != null) {
						if (isCachedReady && candidates.size == 1) {
							false
						} else {
							serviceActiveMap.containsKey(candidate.id) ||
								(isSameId && serviceActiveMap.containsKey(inState.id))
						}
					} else {
						if (isCachedReady && candidates.size == 1) {
							false
						} else {
							inState.isInstalling || (inState.isQueued && isSameId)
						}
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
			downloadCoordinator?.enqueueBatch(newlyQueued)
		}
	}

	fun ignoreVersion(id: Int) = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		val targetUpdate = state.value.updates().find { it.id == id }
			?: rawLatestUpdates.find { it.id == id }
		val ignored = prefs.ignoredVersions.get().toMutableList()
		val entries = prefs.readIgnoredVersionEntriesSafe().toMutableList()
		if (ignored.contains(id) || (targetUpdate != null && entries.any { it.matchesUpdate(targetUpdate.packageName, targetUpdate.versionCode, targetUpdate.version, id) })) {
			ignored.remove(id)
			entries.removeAll { entry ->
				entry.id == id || (targetUpdate != null && entry.matchesUpdate(targetUpdate.packageName, targetUpdate.versionCode, targetUpdate.version, id))
			}
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

	fun ignoreAppPermanently(packageName: String) = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		prefs.addIgnoredApp(packageName)
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

	fun syncIgnoredFromPrefs() = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
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

	override fun onInstallFailed(status: AppInstallStatus): Job {
		if (status.failureReason == InstallFailureReason.SIGNATURE_MISMATCH) {
			AppJournal.record("Install", "Signature mismatch pruned candidate (updateId=${status.id})")
			return viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
				val id = status.id
				downloadCoordinator?.cancelTask(id)
				runCatching {
					File(getCacheDir(), "download_staging_$id").deleteRecursively()
				}
				rawLatestUpdates = pruneSignatureMismatchedCandidate(rawLatestUpdates, id)
				val updatedList = pruneSignatureMismatchedCandidate(state.value.mutableUpdates(), id)
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
				installer.finish(id)
				refreshReadyUpdateIds()
			}
		}
		return onInstallFailed(status.id)
	}

	private fun hasVerifiedCachedApk(stagingDir: File): Boolean =
		File(stagingDir, Downloader.READY_MARKER_FILE).exists() &&
			stagingDir.listFiles()?.any { it.isFile && it.length() > 0L && it.name.endsWith(".apk", ignoreCase = true) } == true

	/**
	 * Notifies the user when saving to a custom SAF folder failed and ApkUp fell back to `Download/ApkUp/`.
	 * Collected from `downloadCoordinator.safFallbackEvents` (which emits the app label) or called directly with a location.
	 */
	fun notifySafFallbackUsed(displayLocation: String = "") {
		val clean = displayLocation.trim()
		val msg = when {
			clean.isEmpty() -> stringer.get(R.string.download_saf_fallback_snack)
			'/' in clean || '\\' in clean -> stringer.get(R.string.download_saf_fallback_snack_named, clean)
			else -> "$clean: ${stringer.get(R.string.download_saf_fallback_snack)}"
		}
		snackBar.snackBar(viewModelScope, TextSnack(msg, visualType = com.apkup.data.snack.SnackVisualType.WARNING))
	}

	override fun onInstallFailed(id: Int): Job = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		val stagingDir = File(getCacheDir(), "download_staging_$id")
		if (!hasVerifiedCachedApk(stagingDir)) {
			runCatching { stagingDir.deleteRecursively() }
		}
		state.value = successState(
			state.value.mutableUpdates().map { update ->
				if (update.id == id || update.alternativeUpdates.any { alt -> alt.id == id }) {
					update.copy(isQueued = false, isInstalling = false, progress = 0L)
				} else {
					update
				}
			}
		)
		refreshReadyUpdateIds()
	}

	public override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		downloadCoordinator?.cancelTask(id)
		apkMirrorResolver?.cancelResolution("update_$id")
		downloader.cancel("update_$id")
		val stagingDir = File(getCacheDir(), "download_staging_$id")
		if (!hasVerifiedCachedApk(stagingDir)) {
			runCatching { stagingDir.deleteRecursively() }
		}
		state.value = successState(
			state.value.mutableUpdates().map { update ->
				if (update.id == id || update.alternativeUpdates.any { alt -> alt.id == id }) {
					update.copy(isQueued = false, isInstalling = false, progress = 0L)
				} else {
					update
				}
			}
		)
		installer.finish(id)
		refreshReadyUpdateIds()
	}

	fun cancelAllInstalls() = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		downloadCoordinator?.cancelAll()
		val current = state.value.mutableUpdates()
		val resetList = current.map { update ->
			if (update.isInstalling || update.isQueued || update.progress > 0L) {
				apkMirrorResolver?.cancelResolution("update_${update.id}")
				downloader.cancel("update_${update.id}")
				val stagingDir = File(getCacheDir(), "download_staging_${update.id}")
				if (!hasVerifiedCachedApk(stagingDir)) {
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

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(uiStateMutex, Dispatchers.IO) {
		runCatching {
			File(getCacheDir(), "download_staging_$id").deleteRecursively()
		}
		rawLatestUpdates = rawLatestUpdates.filterNot {
			it.id == id || it.alternativeUpdates.any { alt -> alt.id == id }
		}
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
			val matchedUpdate = state.value.updates().firstNotNullOfOrNull { candidate ->
				when {
					candidate.id == log.id -> candidate
					else -> candidate.alternativeUpdates.firstOrNull { alt -> alt.id == log.id }
				}
			}
			matchedUpdate?.let { app ->
				if (log.success && downloadCoordinator == null) {
					val mode = when {
						installer.isShizukuReady() -> UpdateInstallMode.MANUAL_SHIZUKU
						installer.isRootReady() -> UpdateInstallMode.MANUAL_ROOT
						else -> UpdateInstallMode.MANUAL
					}
					val binding = prefs.readCustomRepoBindingsSafe()[app.packageName]
					prefs.recordUpdateHistory(
						buildUpdateHistoryEntry(
							update = app,
							installMode = mode,
							repoBinding = binding
						)
					)
				}
				val message = when {
					log.success -> R.string.install_success
					log.failureReason == InstallFailureReason.SIGNATURE_MISMATCH -> R.string.install_failure_signature_mismatch
					else -> R.string.install_failure
				}
				val visualType = if (log.success) {
					com.apkup.data.snack.SnackVisualType.SUCCESS
				} else {
					com.apkup.data.snack.SnackVisualType.ERROR
				}
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name), visualType = visualType))
			}
		}
	}

	private fun List<AppUpdate>.filterIgnoredVersions(ignoredVersions: List<Int>): List<AppUpdate> {
		val isVersionsFilterActive = prefs.ignoreVersionsFilterActive.get()
		val isAppsFilterActive = prefs.ignoreAppsFilterActive.get()
		val ignoredSet = if (isVersionsFilterActive) ignoredVersions.toSet() else emptySet()
		val ignoredEntries = if (isVersionsFilterActive) prefs.readIgnoredVersionEntriesSafe() else emptyList()
		val ignoredPackages = if (isAppsFilterActive) prefs.ignoredApps.get().toSet() else emptySet()
		return filter { update ->
			val matchesEntry = ignoredEntries.any { entry ->
				entry.matchesUpdate(update.packageName, update.versionCode, update.version, update.id)
			}
			update.id !in ignoredSet && !matchesEntry && update.packageName !in ignoredPackages
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

internal fun pruneSignatureMismatchedCandidate(
	updates: List<AppUpdate>,
	failedId: Int
): List<AppUpdate> = updates.mapNotNull { update ->
	val matchesPrimary = update.id == failedId
	val matchesAlternative = update.alternativeUpdates.any { it.id == failedId }
	if (!matchesPrimary && !matchesAlternative) {
		update
	} else {
		val remainingAlternatives = update.alternativeUpdates.filterNot { it.id == failedId }
		if (!matchesPrimary) {
			update.copy(
				isQueued = false,
				isInstalling = false,
				progress = 0L,
				alternativeUpdates = if (remainingAlternatives.size > 1) remainingAlternatives else emptyList()
			)
		} else {
			val nextPrimary = remainingAlternatives.firstOrNull { it.id != failedId } ?: return@mapNotNull null
			nextPrimary.copy(
				isQueued = false,
				isInstalling = false,
				progress = 0L,
				alternativeUpdates = if (remainingAlternatives.size > 1) remainingAlternatives else emptyList()
			)
		}
	}
}
