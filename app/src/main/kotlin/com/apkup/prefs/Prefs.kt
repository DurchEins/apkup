package com.apkup.prefs

import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.CachedUpdateSnapshot
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.Screen
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
	val updatesDisplayMode = int("updatesDisplayMode", defValue = 0, backed = true)
	val useApkMirror = boolean("useApkMirror", defValue = true, backed = true)
	val usePlay = boolean("usePlay", defValue = true, backed = true)
	val useIzzyOnDroid = boolean("useIzzyOnDroid", defValue = true, backed = true)
	val useGitHub = boolean("useGitHub", defValue = true, backed = true)
	val useGitLab = boolean("useGitLab", defValue = true, backed = true)
	val githubToken = string("githubToken", defValue = "", backed = true)
	val customRepoBindings = json("customRepoBindings", emptyMap<String, RepoBinding>(), true)
	val enableBackgroundChecks = boolean("enableAlarm", defValue = true, backed = true)
	val backgroundCheckIntervalHours = int("backgroundCheckIntervalHours", defValue = 24, backed = true)
	val theme = int("theme", defValue = 0, backed = true)
	val lastTab = string("lastTab", defValue = Screen.Updates.route, backed = true)
	val playAuthData = json("playAuthData", AuthData("", ""), true)
	val lastPlayCheck = long("lastPlayCheck", 0L, true)
	val useShizukuInstaller = boolean("useShizukuInstaller", defValue = false, backed = true)
	val lastSeenVersionCode = long("last_seen_version_code", 0L, true)
	val cachedUpdateSnapshot = json("cachedUpdateSnapshot", CachedUpdateSnapshot(), true)

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
