package com.apkup.viewmodel

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.ApkUpBackupConfig
import com.apkup.data.ui.BackgroundCheckInterval
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.prefs.Prefs
import com.apkup.ui.theme.isDarkTheme
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.Themer
import com.apkup.util.UpdatesNotification
import com.apkup.worker.UpdatesWorker
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class SettingsViewModel(
	private val prefs: Prefs,
	private val notification: UpdatesNotification? = null,
	private val workManager: WorkManager? = null,
	private val themer: Themer? = null,
	private val sessionInstaller: SessionInstaller? = null,
	private val snackBar: SnackBar? = null,
	private val stringer: Stringer? = null,
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
) : ViewModel() {

	private val ignoredVersionsFlow = MutableStateFlow(buildIgnoredVersionEntries())
	private val ignoredAppsFlow = MutableStateFlow(prefs.ignoredApps.get())
	private val shizukuSetupStatusFlow = MutableStateFlow(
		sessionInstaller?.getShizukuSetupStatus() ?: ShizukuSetupStatus()
	)
	private val shizukuEnabledFlow = MutableStateFlow(
		prefs.useShizukuInstaller.get() && shizukuSetupStatusFlow.value.isReady
	)
	private val showShizukuSetupSheetFlow = MutableStateFlow(false)
	private var pendingEnableOnReady = false
	private var unregisterShizukuListeners: (() -> Unit)? = null

	init {
		if (!shizukuSetupStatusFlow.value.isReady && prefs.useShizukuInstaller.get()) {
			prefs.useShizukuInstaller.put(false)
		}
		unregisterShizukuListeners = sessionInstaller?.observeShizukuEvents(
			onPermissionResult = { granted ->
				val latest = sessionInstaller.getShizukuSetupStatus()
				shizukuSetupStatusFlow.value = latest
				if (granted && latest.isReady) {
					prefs.useShizukuInstaller.put(true)
					shizukuEnabledFlow.value = true
					pendingEnableOnReady = false
					showShizukuSetupSheetFlow.value = false
				}
			},
			onBinderChanged = {
				refreshShizukuStatus()
			}
		)
	}

	override fun onCleared() {
		unregisterShizukuListeners?.invoke()
		unregisterShizukuListeners = null
		super.onCleared()
	}

	fun ignoredVersions(): StateFlow<List<IgnoredVersionEntry>> = ignoredVersionsFlow
	fun shizukuEnabled(): StateFlow<Boolean> = shizukuEnabledFlow
	fun shizukuSetupStatus(): StateFlow<ShizukuSetupStatus> = shizukuSetupStatusFlow
	fun showShizukuSetupSheet(): StateFlow<Boolean> = showShizukuSetupSheetFlow

	fun refreshShizukuStatus() {
		val status = sessionInstaller?.getShizukuSetupStatus() ?: ShizukuSetupStatus()
		shizukuSetupStatusFlow.value = status
		if (pendingEnableOnReady && status.isReady) {
			prefs.useShizukuInstaller.put(true)
			shizukuEnabledFlow.value = true
			pendingEnableOnReady = false
			showShizukuSetupSheetFlow.value = false
		} else if (!status.isReady) {
			prefs.useShizukuInstaller.put(false)
			shizukuEnabledFlow.value = false
		} else {
			shizukuEnabledFlow.value = prefs.useShizukuInstaller.get()
		}
	}

	fun getUseShizukuInstaller() = shizukuEnabledFlow.value

	fun setUseShizukuInstaller(requested: Boolean) {
		val status = sessionInstaller?.getShizukuSetupStatus() ?: ShizukuSetupStatus()
		shizukuSetupStatusFlow.value = status
		if (!status.isReady) {
			// Whenever Shizuku is not ready, keep switch at false and guarantee the setup sheet opens
			prefs.useShizukuInstaller.put(false)
			shizukuEnabledFlow.value = false
			pendingEnableOnReady = true
			showShizukuSetupSheetFlow.value = true
			return
		}
		if (!requested) {
			pendingEnableOnReady = false
			prefs.useShizukuInstaller.put(false)
			shizukuEnabledFlow.value = false
			return
		}
		prefs.useShizukuInstaller.put(true)
		shizukuEnabledFlow.value = true
		pendingEnableOnReady = false
		showShizukuSetupSheetFlow.value = false
	}

	fun requestShizukuPermissionFromSheet() {
		pendingEnableOnReady = true
		val alreadyGranted = sessionInstaller?.requestShizukuPermission() == true
		if (alreadyGranted) {
			refreshShizukuStatus()
		}
	}

	fun dismissShizukuSetupSheet() {
		showShizukuSetupSheetFlow.value = false
	}

	fun refreshIgnoredVersions() {
		ignoredVersionsFlow.value = buildIgnoredVersionEntries()
	}

	private fun buildIgnoredVersionEntries(): List<IgnoredVersionEntry> {
		val ids = prefs.ignoredVersions.get()
		val storedEntries = prefs.readIgnoredVersionEntriesSafe()
		val byId = storedEntries.associateBy { it.id }
		return ids.map { id ->
			byId[id] ?: IgnoredVersionEntry(
				id = id,
				packageName = "",
				appName = "ID #$id",
				version = "",
				versionCode = 0L,
				sourceName = ""
			)
		}
	}

	fun unignoreVersion(id: Int) {
		val ids = prefs.ignoredVersions.get().toMutableList()
		ids.remove(id)
		prefs.ignoredVersions.put(ids)

		val entries = prefs.readIgnoredVersionEntriesSafe().toMutableList()
		entries.removeAll { it.id == id }
		prefs.ignoredVersionEntries.put(entries)

		refreshIgnoredVersions()
	}

	fun clearAllIgnoredVersions() {
		prefs.ignoredVersions.put(emptyList())
		prefs.ignoredVersionEntries.put(emptyList())
		refreshIgnoredVersions()
	}

	fun ignoredApps(): StateFlow<List<String>> = ignoredAppsFlow

	fun refreshIgnoredApps() {
		ignoredAppsFlow.value = prefs.ignoredApps.get()
	}

	fun unignoreApps(
		packagesToRestore: Set<String>,
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) {
		val current = prefs.ignoredApps.get().toMutableList()
		current.removeAll(packagesToRestore)
		prefs.ignoredApps.put(current)
		refreshIgnoredApps()
		appsViewModel.refresh(false)
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun ignoreApps(
		packagesToIgnore: Set<String>,
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) {
		val current = prefs.ignoredApps.get().toMutableList()
		current.addAll(packagesToIgnore)
		prefs.ignoredApps.put(current.distinct())
		refreshIgnoredApps()
		appsViewModel.refresh(false)
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun canRequestPackageInstalls(): Boolean =
		sessionInstaller?.canRequestPackageInstalls() ?: false

	fun getShowSystemApps() = prefs.showSystemApps.get()
	fun setShowSystemApps(show: Boolean) = prefs.setShowSystemApps(show)

	fun onShowSystemAppsChanged(
		show: Boolean,
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) {
		prefs.setShowSystemApps(show)
		viewModelScope.launch( kotlinx.coroutines.Dispatchers.IO ) {
			val updatedApps = appsViewModel.refreshAndGet(false)
			updatesViewModel.refresh(
				load = true,
				installedAppsSnapshot = updatedApps,
				filterRetainedBySnapshot = true
			)
		}
	}
	fun getUpdatesDisplayMode() = prefs.updatesDisplayMode.get()
	fun setUpdatesDisplayMode(mode: Int) = prefs.updatesDisplayMode.put(mode)
	fun getUseApkMirror() = prefs.useApkMirror.get()
	fun setUseApkMirror(b: Boolean) = prefs.useApkMirror.put(b)
	fun getDirectApkMirrorDownloads(): Boolean = prefs.directApkMirrorDownloads.get()
	fun setDirectApkMirrorDownloads(b: Boolean) = prefs.directApkMirrorDownloads.put(b)
	fun getUsePlay() = prefs.usePlay.get()
	fun setUsePlay(b: Boolean) = prefs.usePlay.put(b)
	fun getUseIzzyOnDroid() = prefs.useIzzyOnDroid.get()
	fun setUseIzzyOnDroid(b: Boolean) = prefs.useIzzyOnDroid.put(b)
	fun getUseGitHub() = prefs.useGitHub.get()
	fun setUseGitHub(b: Boolean) = prefs.useGitHub.put(b)
	fun getUseGitLab() = prefs.useGitLab.get()
	fun setUseGitLab(b: Boolean) = prefs.useGitLab.put(b)
	fun getGitHubToken(): String = prefs.githubToken.get()
	fun setGitHubToken(token: String) = prefs.githubToken.put(token.trim())
	fun getGitLabToken(): String = prefs.gitlabToken.get()
	fun setGitLabToken(token: String) = prefs.gitlabToken.put(token.trim())
	fun getBackgroundChecksEnabled() = prefs.enableBackgroundChecks.get()
	fun getBackgroundCheckIntervalHours(): Int =
		BackgroundCheckInterval.fromHours(prefs.backgroundCheckIntervalHours.get()).hours

	fun setBackgroundCheckIntervalHours(hours: Int) {
		val normalized = BackgroundCheckInterval.fromHours(hours).hours
		prefs.backgroundCheckIntervalHours.put(normalized)
		if (prefs.enableBackgroundChecks.get()) {
			workManager?.let { UpdatesWorker.launch(it) }
		}
	}

	fun getTheme() = prefs.theme.get()

	fun setTheme(theme: Int) {
		prefs.theme.put(theme)
		themer?.setTheme(isDarkTheme(theme))
	}

	fun setBackgroundChecksEnabled(b: Boolean, launcher: ManagedActivityResultLauncher<String, Boolean>) {
		prefs.enableBackgroundChecks.put(b)
		if (b) {
			notification?.checkNotificationPermission(launcher)
			workManager?.let { UpdatesWorker.launch(it) }
		} else {
			workManager?.let { UpdatesWorker.cancel(it) }
		}
	}

	fun exportBackupJson(): String {
		val config = ApkUpBackupConfig(
			schemaVersion = 1,
			exportedAtMillis = System.currentTimeMillis(),
			ignoredApps = prefs.ignoredApps.get(),
			ignoredVersions = prefs.ignoredVersions.get(),
			ignoredVersionEntries = prefs.readIgnoredVersionEntriesSafe(),
			showSystemApps = prefs.showSystemApps.get(),
			updatesDisplayMode = prefs.updatesDisplayMode.get(),
			useApkMirror = prefs.useApkMirror.get(),
			directApkMirrorDownloads = prefs.directApkMirrorDownloads.get(),
			usePlay = prefs.usePlay.get(),
			useIzzyOnDroid = prefs.useIzzyOnDroid.get(),
			useGitHub = prefs.useGitHub.get(),
			useGitLab = prefs.useGitLab.get(),
			customRepoBindings = prefs.readCustomRepoBindingsSafe(),
			enableBackgroundChecks = prefs.enableBackgroundChecks.get(),
			backgroundCheckIntervalHours = getBackgroundCheckIntervalHours(),
			theme = prefs.theme.get(),
			useShizukuInstaller = prefs.useShizukuInstaller.get()
		)
		return gson.toJson(config)
	}

	fun importBackupJson(rawJson: String): Boolean = runCatching {
		val config = gson.fromJson(rawJson, ApkUpBackupConfig::class.java)
			?: throw IllegalArgumentException("Empty backup JSON")
		prefs.ignoredApps.put(config.ignoredApps.distinct())
		prefs.ignoredVersions.put(config.ignoredVersions.distinct())
		prefs.ignoredVersionEntries.put(config.ignoredVersionEntries.distinctBy { it.id })
		prefs.setShowSystemApps(config.showSystemApps)
		prefs.updatesDisplayMode.put(config.updatesDisplayMode.coerceIn(0, 1))
		prefs.useApkMirror.put(config.useApkMirror)
		prefs.directApkMirrorDownloads.put(config.directApkMirrorDownloads)
		prefs.usePlay.put(config.usePlay)
		prefs.useIzzyOnDroid.put(config.useIzzyOnDroid)
		prefs.useGitHub.put(config.useGitHub)
		prefs.useGitLab.put(config.useGitLab)
		if (config.customRepoBindings.isNotEmpty()) {
			prefs.writeCustomRepoBindings(config.customRepoBindings)
		}
		prefs.enableBackgroundChecks.put(config.enableBackgroundChecks)
		prefs.backgroundCheckIntervalHours.put(
			BackgroundCheckInterval.fromHours(config.backgroundCheckIntervalHours).hours
		)
		setTheme(config.theme.coerceIn(0, 2))
		val canEnableShizuku = config.useShizukuInstaller &&
			(sessionInstaller?.getShizukuSetupStatus()?.isReady == true)
		prefs.useShizukuInstaller.put(canEnableShizuku)
		shizukuEnabledFlow.value = canEnableShizuku
		if (config.enableBackgroundChecks) {
			workManager?.let { UpdatesWorker.launch(it) }
		} else {
			workManager?.let { UpdatesWorker.cancel(it) }
		}
		refreshIgnoredVersions()
		refreshIgnoredApps()
		notifySnack(R.string.backup_import_success)
		true
	}.getOrElse {
		notifySnack(R.string.backup_import_error)
		false
	}

	fun notifyBackupExported(success: Boolean) {
		notifySnack(if (success) R.string.backup_export_success else R.string.backup_import_error)
	}

	private fun notifySnack(resId: Int) {
		val snack = snackBar ?: return
		val str = stringer ?: return
		snack.snackBar(viewModelScope, TextSnack(str.get(resId)))
	}

}
