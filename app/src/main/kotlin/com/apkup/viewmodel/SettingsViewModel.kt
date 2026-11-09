package com.apkup.viewmodel

import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.apkup.R
import com.apkup.data.obtainium.ObtainiumManager
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.ApkUpBackupConfig
import com.apkup.data.ui.BackgroundCheckInterval
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.prefs.Prefs
import com.apkup.repository.RepoMappingManager
import com.apkup.ui.theme.isDarkTheme
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.Themer
import com.apkup.util.UpdatesNotification
import com.apkup.worker.ShizukuAutoUpdateWorker
import com.apkup.worker.UpdatesWorker
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request


sealed class TokenValidationResult {
	data class Valid(
		val username: String,
		val rateLimitRemaining: String? = null,
		val rateLimitTotal: String? = null
	) : TokenValidationResult()

	data class Invalid(val httpCode: Int) : TokenValidationResult()
	data class Error(val message: String) : TokenValidationResult()
}

class SettingsViewModel(
	private val prefs: Prefs,
	private val notification: UpdatesNotification? = null,
	private val workManager: WorkManager? = null,
	private val themer: Themer? = null,
	private val sessionInstaller: SessionInstaller? = null,
	private val snackBar: SnackBar? = null,
	private val stringer: Stringer? = null,
	private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
	private val okHttpClient: OkHttpClient = OkHttpClient(),
	private val repoMappingManager: RepoMappingManager? = null
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
			workManager?.let { ShizukuAutoUpdateWorker.cancel(it) }
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

	fun openShizukuSetupSheet() {
		showShizukuSetupSheetFlow.value = true
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

	fun getIgnoreAppsFilterActive(): Boolean = prefs.ignoreAppsFilterActive.get()
	fun setIgnoreAppsFilterActive(active: Boolean, appsViewModel: AppsViewModel, updatesViewModel: UpdatesViewModel) {
		prefs.ignoreAppsFilterActive.put(active)
		refreshIgnoredApps()
		appsViewModel.refresh(false)
		updatesViewModel.syncIgnoredFromPrefs()
	}

	fun getIgnoreVersionsFilterActive(): Boolean = prefs.ignoreVersionsFilterActive.get()
	fun setIgnoreVersionsFilterActive(active: Boolean, updatesViewModel: UpdatesViewModel) {
		prefs.ignoreVersionsFilterActive.put(active)
		refreshIgnoredVersions()
		updatesViewModel.syncIgnoredFromPrefs()
	}

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

	suspend fun validateGitHubToken(rawToken: String): TokenValidationResult = withContext(Dispatchers.IO) {
		val cleanToken = rawToken.trim().filter { it in '!'..'~' }
		if (cleanToken.isBlank()) {
			return@withContext TokenValidationResult.Invalid(401)
		}
		runCatching {
			val request = Request.Builder()
				.url("https://api.github.com/user")
				.cacheControl(CacheControl.FORCE_NETWORK)
				.header("Accept", "application/vnd.github.v3+json")
				.header("User-Agent", "ApkUp")
				.header("Authorization", "Bearer $cleanToken")
				.build()
			okHttpClient.newCall(request).execute().use { response ->
				when {
					response.isSuccessful -> {
						val body = response.body.string()
						val login = runCatching {
							JsonParser.parseString(body).asJsonObject.get("login")?.asString
						}.getOrNull().orEmpty().ifBlank { "GitHub" }
						val remaining = response.header("x-ratelimit-remaining")
						val limit = response.header("x-ratelimit-limit")
						TokenValidationResult.Valid(
							username = login,
							rateLimitRemaining = remaining,
							rateLimitTotal = limit
						)
					}
					response.code == 401 || response.code == 403 -> {
						TokenValidationResult.Invalid(response.code)
					}
					else -> {
						TokenValidationResult.Error("HTTP ${response.code}")
					}
				}
			}
		}.getOrElse { error ->
			TokenValidationResult.Error(error.message ?: "Network error")
		}
	}

	suspend fun validateGitLabToken(rawToken: String): TokenValidationResult = withContext(Dispatchers.IO) {
		val cleanToken = rawToken.trim().filter { it in '!'..'~' }
		if (cleanToken.isBlank()) {
			return@withContext TokenValidationResult.Invalid(401)
		}
		runCatching {
			val request = Request.Builder()
				.url("https://gitlab.com/api/v4/user")
				.cacheControl(CacheControl.FORCE_NETWORK)
				.header("Accept", "application/json")
				.header("User-Agent", "ApkUp")
				.header("PRIVATE-TOKEN", cleanToken)
				.build()
			okHttpClient.newCall(request).execute().use { response ->
				when {
					response.isSuccessful -> {
						val body = response.body.string()
						val username = runCatching {
							JsonParser.parseString(body).asJsonObject.get("username")?.asString
						}.getOrNull().orEmpty().ifBlank { "GitLab" }
						val remaining = response.header("RateLimit-Remaining")
						val limit = response.header("RateLimit-Limit")
						TokenValidationResult.Valid(
							username = username,
							rateLimitRemaining = remaining,
							rateLimitTotal = limit
						)
					}
					response.code == 401 || response.code == 403 -> {
						TokenValidationResult.Invalid(response.code)
					}
					else -> {
						TokenValidationResult.Error("HTTP ${response.code}")
					}
				}
			}
		}.getOrElse { error ->
			TokenValidationResult.Error(error.message ?: "Network error")
		}
	}

	fun notifyTextSnack(message: String) {
		val snack = snackBar ?: return
		snack.snackBar(viewModelScope, TextSnack(message))
	}
	fun getDownloadWifiOnly(): Boolean = prefs.downloadWifiOnly.get()
	fun setDownloadWifiOnly(wifiOnly: Boolean) = prefs.downloadWifiOnly.put(wifiOnly)

	fun getDownloadChargingOnly(): Boolean = prefs.downloadChargingOnly.get()
	fun setDownloadChargingOnly(chargingOnly: Boolean) = prefs.downloadChargingOnly.put(chargingOnly)

	fun getBackgroundCheckWifiOnly(): Boolean = prefs.backgroundCheckWifiOnly.get()
	fun setBackgroundCheckWifiOnly(wifiOnly: Boolean) {
		prefs.backgroundCheckWifiOnly.put(wifiOnly)
		if (getBackgroundChecksEnabled()) {
			workManager?.let { UpdatesWorker.launch(it) }
		}
	}

	fun getAutoInstallUpdates(): Boolean = prefs.autoInstallUpdates.get()

	fun setAutoInstallUpdates(enabled: Boolean) {
		prefs.autoInstallUpdates.put(enabled)
		val wm = workManager ?: return
		if (enabled && getUseShizukuInstaller()) {
			if (prefs.readCachedSnapshotSafe().updates.isNotEmpty()) {
				ShizukuAutoUpdateWorker.enqueue(
					workManager = wm,
					wifiOnly = prefs.autoInstallWifiOnly.get(),
					chargingOnly = prefs.autoInstallChargingOnly.get()
				)
			}
		} else {
			ShizukuAutoUpdateWorker.cancel(wm)
		}
	}

	fun getAutoInstallWifiOnly(): Boolean = prefs.autoInstallWifiOnly.get()

	fun setAutoInstallWifiOnly(wifiOnly: Boolean) {
		prefs.autoInstallWifiOnly.put(wifiOnly)
		if (prefs.autoInstallUpdates.get() && getUseShizukuInstaller() && prefs.readCachedSnapshotSafe().updates.isNotEmpty()) {
			workManager?.let { wm ->
				ShizukuAutoUpdateWorker.enqueue(
					workManager = wm,
					wifiOnly = wifiOnly,
					chargingOnly = prefs.autoInstallChargingOnly.get()
				)
			}
		}
	}

	fun getAutoInstallChargingOnly(): Boolean = prefs.autoInstallChargingOnly.get()

	fun setAutoInstallChargingOnly(chargingOnly: Boolean) {
		prefs.autoInstallChargingOnly.put(chargingOnly)
		if (prefs.autoInstallUpdates.get() && getUseShizukuInstaller() && prefs.readCachedSnapshotSafe().updates.isNotEmpty()) {
			workManager?.let { wm ->
				ShizukuAutoUpdateWorker.enqueue(
					workManager = wm,
					wifiOnly = prefs.autoInstallWifiOnly.get(),
					chargingOnly = chargingOnly
				)
			}
		}
	}
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
			useShizukuInstaller = prefs.useShizukuInstaller.get(),
			autoInstallUpdates = prefs.autoInstallUpdates.get(),
			autoInstallWifiOnly = prefs.autoInstallWifiOnly.get(),
			autoInstallChargingOnly = prefs.autoInstallChargingOnly.get(),
			downloadWifiOnly = prefs.downloadWifiOnly.get(),
			downloadChargingOnly = prefs.downloadChargingOnly.get(),
			backgroundCheckWifiOnly = prefs.backgroundCheckWifiOnly.get(),
			updateHistory = prefs.readUpdateHistorySafe()
		)
		return gson.toJson(config)
	}

	fun importBackupJson(rawJson: String): Boolean {
		if (ObtainiumManager.isObtainiumJson(rawJson)) {
			val result = repoMappingManager?.let {
				ObtainiumManager.importObtainium(rawJson, it)
			} ?: run {
				val apps = ObtainiumManager.parseObtainiumApps(rawJson)
				val newBindings = mutableMapOf<String, com.apkup.data.repomapping.RepoBinding>()
				var skipped = 0
				for (app in apps) {
					val pkg = app.id.trim()
					val url = app.url.trim()
					if (pkg.isBlank() || url.isBlank()) {
						skipped++
						continue
					}
					val binding = ObtainiumManager.convertUrlToBinding(url)
					if (binding != null) {
						newBindings[pkg] = binding
					} else {
						skipped++
					}
				}
				val current = prefs.readCustomRepoBindingsSafe().toMutableMap()
				current.putAll(newBindings)
				prefs.writeCustomRepoBindings(current)
				com.apkup.data.obtainium.ObtainiumImportResult(
					totalEntries = apps.size,
					importedBindingsCount = newBindings.size,
					skippedCount = skipped,
					importedPackages = newBindings.keys.toList()
				)
			}

			return if (result.importedBindingsCount > 0) {
				val str = stringer?.get(R.string.backup_obtainium_import_success)
				val message = if (str != null) {
					runCatching { str.format(result.importedBindingsCount, result.totalEntries) }.getOrDefault(str)
				} else {
					"Imported ${result.importedBindingsCount}/${result.totalEntries} apps from Obtainium"
				}
				notifyTextSnack(message)
				true
			} else {
				notifySnack(R.string.backup_import_error)
				false
			}
		}

		return runCatching {
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
			prefs.autoInstallUpdates.put(config.autoInstallUpdates)
			prefs.autoInstallWifiOnly.put(config.autoInstallWifiOnly ?: true)
			prefs.autoInstallChargingOnly.put(config.autoInstallChargingOnly ?: true)
			if (!canEnableShizuku || !config.autoInstallUpdates) {
				workManager?.let { ShizukuAutoUpdateWorker.cancel(it) }
			}
			prefs.downloadWifiOnly.put(config.downloadWifiOnly)
			prefs.downloadChargingOnly.put(config.downloadChargingOnly ?: false)
			prefs.backgroundCheckWifiOnly.put(config.backgroundCheckWifiOnly)
			config.updateHistory?.let { importedHistory ->
				prefs.replaceUpdateHistory(importedHistory)
			}
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
	}

	fun exportObtainiumJson(appNamesByPackage: Map<String, String> = emptyMap()): String {
		val bindings = prefs.readCustomRepoBindingsSafe()
		return ObtainiumManager.exportObtainium(bindings, appNamesByPackage)
	}

	fun notifyObtainiumExported(success: Boolean) {
		notifySnack(if (success) R.string.backup_obtainium_export_success else R.string.backup_import_error)
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
