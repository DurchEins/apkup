package com.apkup.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.IgnoredVersionEntry
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
import com.apkup.prefs.Prefs
import com.apkup.repository.UpdatesRepository
import com.apkup.util.Badger
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.UriHandler
import com.apkup.data.ui.ApkMirrorSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue


class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	private val prefs: Prefs,
	private val badger: Badger,
	downloader: Downloader,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	installLog: InstallLog
) : InstallViewModel(downloader, installer, snackBar, stringer, installLog) {

	private val mutex = Mutex()
	private val installQueueMutex = Mutex()
	private val pendingInstallQueue = ConcurrentLinkedQueue<AppUpdate>()
	private var installQueueProcessorJob: Job? = null
	private var updateCheckJob: Job? = null
	private var rawLatestUpdates: List<AppUpdate> = emptyList()
	private var retainedUpdatesDuringCheck: List<AppUpdate> = emptyList()
	private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
	private val _showInstallPermissionDialog = MutableStateFlow(false)
	val showInstallPermissionDialog: StateFlow<Boolean> = _showInstallPermissionDialog.asStateFlow()

	init {
		subscribeToInstallStatus()
		subscribeToInstallProgress { progress ->
			state.value = successState(state.value.mutableUpdates().setProgress(progress))
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

	override fun install(update: AppUpdate, uriHandler: UriHandler) {
		if (update.source != ApkMirrorSource && !canProceedWithInstall()) {
			triggerInstallPermissionPrompt()
			return
		}
		super.install(update, uriHandler)
	}

	fun installAll() = viewModelScope.launch(Dispatchers.IO) {
		val currentUpdates = state.value.updates()
		val (directInstallable, browserOnly) = partitionInstallQueueCandidates(currentUpdates)
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

		if (browserOnly.isNotEmpty()) {
			snackBar.snackBar(
				viewModelScope,
				TextSnack(stringer.get(R.string.install_all_skipped_browser_hint, browserOnly.size))
			)
		}

		enqueueInstallations(directInstallable)
	}

	private suspend fun enqueueInstallations(candidates: List<AppUpdate>) {
		val newlyQueued = mutableListOf<AppUpdate>()
		mutex.withLock {
			val current = state.value.mutableUpdates()
			val alreadyQueuedIds = pendingInstallQueue.map { it.id }.toSet()
			candidates.forEach { candidate ->
				val inState = current.find { it.id == candidate.id }
				if (inState != null && !inState.isInstalling && !inState.isQueued && candidate.id !in alreadyQueuedIds) {
					current.setIsQueued(candidate.id, true)
					pendingInstallQueue.add(candidate)
					newlyQueued.add(candidate)
				}
			}
			if (newlyQueued.isNotEmpty()) {
				state.value = successState(current)
			}
		}
		if (newlyQueued.isNotEmpty()) {
			processInstallQueue()
		}
	}

	private fun processInstallQueue() {
		if (installQueueProcessorJob?.isActive == true) return
		installQueueProcessorJob = viewModelScope.launch(Dispatchers.IO) {
			installQueueMutex.withLock {
				while (true) {
					val next = pendingInstallQueue.poll() ?: break
					val stillPresent = mutex.withLock {
						val current = state.value.mutableUpdates()
						val exists = current.any { it.id == next.id }
						if (exists) {
							state.value = successState(
								current
									.setIsQueued(next.id, false)
									.setIsInstalling(next.id, true)
							)
						}
						exists
					}
					if (stillPresent) {
						downloadAndInstall(
							id = next.id,
							packageName = next.packageName,
							link = next.link,
							appName = next.name
						)
					}
				}
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

	override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		pendingInstallQueue.removeAll { it.id == id }
		state.value = successState(
			state.value.mutableUpdates()
				.setIsQueued(id, false)
				.setIsInstalling(id, false)
		)
		installer.finish()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		pendingInstallQueue.removeAll { it.id == id }
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
		installer.finish()
	}

	override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		if (installer.checkPermission()) {
			val target = mutex.withLock {
				val list = state.value.mutableUpdates()
				val idx = list.indexOfFirst { it.packageName == update.packageName }
				if (idx != -1 && list[idx].id != update.id) {
					list[idx] = update.copy(isQueued = true)
					state.value = successState(list)
				}
				update
			}
			enqueueInstallations(listOf(target))
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
					lastCheckedAtMillis = checkedTimestamp
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
		lastCheckedAtMillis: Long? = state.value.lastCheckedAtMillis()
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
	updates: List<AppUpdate>
): Pair<List<AppUpdate>, List<AppUpdate>> {
	val direct = mutableListOf<AppUpdate>()
	val browserOnly = mutableListOf<AppUpdate>()
	updates.forEach { update ->
		when {
			update.isDirectInstallable -> direct.add(update)
			update.isBrowserOnlyUpdate -> browserOnly.add(update)
			else -> {
				val playAlt = update.alternativeUpdates.firstOrNull { it.isDirectInstallable }
				if (playAlt != null) {
					direct.add(playAlt)
				} else if (update.alternativeUpdates.any { it.isBrowserOnlyUpdate }) {
					browserOnly.add(update)
				}
			}
		}
	}
	return direct to browserOnly
}
