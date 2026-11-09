package com.apkup.prefs

import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.CachedUpdateSnapshot
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.Screen
import com.apkup.data.ui.UpdateHistoryEntry
import kotlin.math.abs
import com.aurora.gplayapi.data.models.AuthData
import com.kryptoprefs.context.KryptoContext
import com.kryptoprefs.gson.json
import com.kryptoprefs.preferences.KryptoPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class Prefs(
	prefs: KryptoPrefs
): KryptoContext(prefs) {
	val ignoredApps = json("ignoredApps", emptyList<String>(), true)
	val showSystemApps = boolean("showSystemApps", defValue = false, backed = true)
	private val _showSystemAppsFlow = MutableStateFlow(showSystemApps.get())
	val showSystemAppsFlow: StateFlow<Boolean> = _showSystemAppsFlow.asStateFlow()

	fun setShowSystemApps(show: Boolean) {
		showSystemApps.put(show)
		_showSystemAppsFlow.value = show
	}

	val hasSeenInstallPermissionPrompt = boolean("hasSeenInstallPermissionPrompt", defValue = false, backed = true)
	val ignoredVersions = json("ignoredVersions", emptyList<Int>(), true)
	val ignoredVersionEntries = json("ignoredVersionEntries", emptyList<IgnoredVersionEntry>(), true)
	val ignoreAppsFilterActive = boolean("ignoreAppsFilterActive", defValue = true, backed = true)
	val ignoreVersionsFilterActive = boolean("ignoreVersionsFilterActive", defValue = true, backed = true)
	val updatesDisplayMode = int("updatesDisplayMode", defValue = 0, backed = true)
	val appsViewMode = string("appsViewMode", defValue = "list", backed = true)
	val useApkMirror = boolean("useApkMirror", defValue = true, backed = true)
	val directApkMirrorDownloads = boolean("directApkMirrorDownloads", defValue = true, backed = true)
	val usePlay = boolean("usePlay", defValue = true, backed = true)
	val useIzzyOnDroid = boolean("useIzzyOnDroid", defValue = true, backed = true)
	val useGitHub = boolean("useGitHub", defValue = true, backed = true)
	val useGitLab = boolean("useGitLab", defValue = true, backed = true)
	val githubToken = string("githubToken", defValue = "", backed = true)
	val gitlabToken = string("gitlabToken", defValue = "", backed = true)
	val customRepoBindings = json("customRepoBindings", emptyMap<String, RepoBinding>(), true)
	val enableBackgroundChecks = boolean("enableAlarm", defValue = true, backed = true)
	val backgroundCheckIntervalHours = int("backgroundCheckIntervalHours", defValue = 24, backed = true)
	val theme = int("theme", defValue = 0, backed = true)
	val lastTab = string("lastTab", defValue = Screen.Updates.route, backed = true)
	val playAuthData = json("playAuthData", AuthData("", ""), true)
	val lastPlayCheck = long("lastPlayCheck", 0L, true)
	val useShizukuInstaller = boolean("useShizukuInstaller", defValue = false, backed = true)
	val autoInstallUpdates = boolean("autoInstallUpdates", defValue = false, backed = true)
	val autoInstallWifiOnly = boolean("autoInstallWifiOnly", defValue = true, backed = true)
	val autoInstallChargingOnly = boolean("autoInstallChargingOnly", defValue = true, backed = true)
	val downloadWifiOnly = boolean("downloadWifiOnly", defValue = false, backed = true)
	val downloadChargingOnly = boolean("downloadChargingOnly", defValue = false, backed = true)
	val backgroundCheckWifiOnly = boolean("backgroundCheckWifiOnly", defValue = false, backed = true)
	val lastSeenVersionCode = long("last_seen_version_code", 0L, true)
	val cachedUpdateSnapshot = json("cachedUpdateSnapshot", CachedUpdateSnapshot(), true)
	private val updateHistoryEntries = json("updateHistoryEntries", emptyList<UpdateHistoryEntry>(), true)
	private val _updateHistoryFlow = MutableStateFlow(readUpdateHistorySafe())
	val updateHistoryFlow: StateFlow<List<UpdateHistoryEntry>> = _updateHistoryFlow.asStateFlow()

	companion object {
		const val MAX_UPDATE_HISTORY_ENTRIES = 50
		private const val DEDUP_WINDOW_MILLIS = 5L * 60L * 1_000L
	}

	@Synchronized
	fun readUpdateHistorySafe(): List<UpdateHistoryEntry> = runCatching {
		updateHistoryEntries.get()
	}.getOrElse {
		runCatching { updateHistoryEntries.put(emptyList()) }
		emptyList()
	}

	@Synchronized
	fun recordUpdateHistory(
		entry: UpdateHistoryEntry,
		maxEntries: Int = MAX_UPDATE_HISTORY_ENTRIES
	) {
		if (entry.packageName.isBlank()) return
		val current = readUpdateHistorySafe().toMutableList()
		val duplicateIndex = current.indexOfFirst { existing ->
			existing.packageName == entry.packageName &&
				existing.newVersionCode == entry.newVersionCode &&
				existing.newVersion == entry.newVersion &&
				abs(existing.installedAtMillis - entry.installedAtMillis) <= DEDUP_WINDOW_MILLIS
		}
		if (duplicateIndex != -1) {
			return
		}
		current.add(0, entry)
		val trimmed = current.take(maxEntries.coerceAtLeast(1))
		runCatching { updateHistoryEntries.put(trimmed) }
		_updateHistoryFlow.value = trimmed
	}

	@Synchronized
	fun replaceUpdateHistory(
		entries: List<UpdateHistoryEntry>,
		maxEntries: Int = MAX_UPDATE_HISTORY_ENTRIES
	) {
		val sanitized = entries
			.filter { it.packageName.isNotBlank() }
			.sortedByDescending { it.installedAtMillis }
			.take(maxEntries.coerceAtLeast(1))
		runCatching { updateHistoryEntries.put(sanitized) }
		_updateHistoryFlow.value = sanitized
	}

	@Synchronized
	fun clearUpdateHistory() {
		runCatching { updateHistoryEntries.put(emptyList()) }
		_updateHistoryFlow.value = emptyList()
	}

	fun readCachedSnapshotSafe(): CachedUpdateSnapshot = runCatching {
		cachedUpdateSnapshot.get()
	}.getOrElse {
		val empty = CachedUpdateSnapshot()
		runCatching { cachedUpdateSnapshot.put(empty) }
		empty
	}

	fun readIgnoredVersionEntriesSafe(): List<IgnoredVersionEntry> = runCatching {
		ignoredVersionEntries.get()
	}.getOrElse {
		runCatching { ignoredVersionEntries.put(emptyList()) }
		emptyList()
	}

	fun readCustomRepoBindingsSafe(): Map<String, RepoBinding> = runCatching {
		customRepoBindings.get()
	}.getOrElse {
		val empty = emptyMap<String, RepoBinding>()
		runCatching { customRepoBindings.put(empty) }
		empty
	}

	fun writeCustomRepoBindings(bindings: Map<String, RepoBinding>) {
		runCatching { customRepoBindings.put(bindings) }
	}
}
