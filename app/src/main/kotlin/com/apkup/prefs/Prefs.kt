package com.apkup.prefs

import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.CachedUpdateSnapshot
import com.apkup.data.ui.CustomFDroidRepo
import com.apkup.data.ui.IgnoredVersionEntry
import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.WatchedAppEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Type
import kotlin.math.abs

data class StoredPlaySession(
    val gsfId: String = "",
    val deviceConfigToken: String = "",
    val checkinConsistencyToken: String = "",
    val localeTag: String = "",
    val mintedAtMillis: Long = 0L
)

class PrefSlot<T>(
    private val getter: () -> T,
    private val setter: (T) -> Unit
) {
    fun get(): T = getter()
    fun put(value: T) = setter(value)
}

class Prefs(
    private val backend: PrefsBackend = InMemoryPrefsBackend(),
    private val gson: Gson = Gson()
) {
    private val lock = Any()

    private fun booleanSlot(
        key: String,
        defValue: Boolean,
        onChanged: ((Boolean) -> Unit)? = null
    ): PrefSlot<Boolean> = PrefSlot(
        getter = { synchronized(lock) { backend.getBoolean(key, defValue) } },
        setter = { value ->
            synchronized(lock) { backend.putBoolean(key, value) }
            onChanged?.invoke(value)
        }
    )

    private fun intSlot(
        key: String,
        defValue: Int,
        onChanged: ((Int) -> Unit)? = null
    ): PrefSlot<Int> = PrefSlot(
        getter = { synchronized(lock) { backend.getInt(key, defValue) } },
        setter = { value ->
            synchronized(lock) { backend.putInt(key, value) }
            onChanged?.invoke(value)
        }
    )

    private fun longSlot(
        key: String,
        defValue: Long,
        onChanged: ((Long) -> Unit)? = null
    ): PrefSlot<Long> = PrefSlot(
        getter = { synchronized(lock) { backend.getLong(key, defValue) } },
        setter = { value ->
            synchronized(lock) { backend.putLong(key, value) }
            onChanged?.invoke(value)
        }
    )

    private fun stringSlot(
        key: String,
        defValue: String,
        onChanged: ((String) -> Unit)? = null
    ): PrefSlot<String> = PrefSlot(
        getter = { synchronized(lock) { backend.getString(key, defValue) ?: defValue } },
        setter = { value ->
            synchronized(lock) { backend.putString(key, value) }
            onChanged?.invoke(value)
        }
    )

    private fun <T> jsonSlot(
        key: String,
        type: Type,
        defProvider: () -> T,
        onChanged: ((T) -> Unit)? = null
    ): PrefSlot<T> = PrefSlot(
        getter = {
            synchronized(lock) {
                val raw = backend.getString(key, null)
                if (raw.isNullOrBlank()) {
                    defProvider()
                } else {
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        (gson.fromJson<T>(raw, type) ?: defProvider())
                    }.getOrElse {
                        val fallback = defProvider()
                        runCatching { backend.putString(key, gson.toJson(fallback, type)) }
                        fallback
                    }
                }
            }
        },
        setter = { value ->
            synchronized(lock) {
                backend.putString(key, gson.toJson(value, type))
            }
            onChanged?.invoke(value)
        }
    )

    val ignoredApps: PrefSlot<List<String>> = jsonSlot(
        key = "ignoredApps",
        type = object : TypeToken<List<String>>() {}.type,
        defProvider = { emptyList() },
        onChanged = { _ignoredAppsFlow.value = it }
    )
    private val _ignoredAppsFlow = MutableStateFlow(ignoredApps.get())
    val ignoredAppsFlow: StateFlow<List<String>> = _ignoredAppsFlow.asStateFlow()

    val showSystemApps: PrefSlot<Boolean> = booleanSlot(
        key = "showSystemApps",
        defValue = false,
        onChanged = { _showSystemAppsFlow.value = it }
    )
    private val _showSystemAppsFlow = MutableStateFlow(showSystemApps.get())
    val showSystemAppsFlow: StateFlow<Boolean> = _showSystemAppsFlow.asStateFlow()

    fun setShowSystemApps(show: Boolean) {
        showSystemApps.put(show)
    }

    val hasSeenInstallPermissionPrompt: PrefSlot<Boolean> =
        booleanSlot("hasSeenInstallPermissionPrompt", defValue = false)

    val hasCompletedOnboarding: PrefSlot<Boolean> = booleanSlot(
        key = "hasCompletedOnboarding",
        defValue = false,
        onChanged = { _hasCompletedOnboardingFlow.value = it }
    )
    private val _hasCompletedOnboardingFlow = MutableStateFlow(hasCompletedOnboarding.get())
    val hasCompletedOnboardingFlow: StateFlow<Boolean> = _hasCompletedOnboardingFlow.asStateFlow()

    val ignoredVersions: PrefSlot<List<Int>> = jsonSlot(
        key = "ignoredVersions",
        type = object : TypeToken<List<Int>>() {}.type,
        defProvider = { emptyList() }
    )

    val ignoredVersionEntries: PrefSlot<List<IgnoredVersionEntry>> = jsonSlot(
        key = "ignoredVersionEntries",
        type = object : TypeToken<List<IgnoredVersionEntry>>() {}.type,
        defProvider = { emptyList() },
        onChanged = { _ignoredVersionEntriesFlow.value = it }
    )
    private val _ignoredVersionEntriesFlow = MutableStateFlow(ignoredVersionEntries.get())
    val ignoredVersionEntriesFlow: StateFlow<List<IgnoredVersionEntry>> = _ignoredVersionEntriesFlow.asStateFlow()

    val ignoreAppsFilterActive: PrefSlot<Boolean> = booleanSlot("ignoreAppsFilterActive", defValue = true)
    val ignoreVersionsFilterActive: PrefSlot<Boolean> = booleanSlot("ignoreVersionsFilterActive", defValue = true)
    val autoCleanupDownloadsAfterInstall: PrefSlot<Boolean> = booleanSlot("autoCleanupDownloadsAfterInstall", defValue = true)

    val snoozedUpdates: PrefSlot<List<com.apkup.data.ui.SnoozedUpdateEntry>> = jsonSlot(
        key = "snoozedUpdates",
        type = object : TypeToken<List<com.apkup.data.ui.SnoozedUpdateEntry>>() {}.type,
        defProvider = { emptyList() },
        onChanged = { _snoozedUpdatesFlow.value = it }
    )
    private val _snoozedUpdatesFlow = MutableStateFlow(readSnoozedUpdatesSafe())
    val snoozedUpdatesFlow: StateFlow<List<com.apkup.data.ui.SnoozedUpdateEntry>> = _snoozedUpdatesFlow.asStateFlow()
    val updatesDisplayMode: PrefSlot<Int> = intSlot("updatesDisplayMode", defValue = 0)
    val appsViewMode: PrefSlot<String> = stringSlot("appsViewMode", defValue = "list")
    val appsSortOrder: PrefSlot<String> = stringSlot("appsSortOrder", defValue = "NAME_ASC")
    val updatesSortOrder: PrefSlot<String> = stringSlot("updatesSortOrder", defValue = "NAME_ASC")

    val useApkMirror: PrefSlot<Boolean> = booleanSlot("useApkMirror", defValue = true)
    val directApkMirrorDownloads: PrefSlot<Boolean> = booleanSlot("directApkMirrorDownloads", defValue = true)
    val apkMirrorCustomApiUser: PrefSlot<String> = stringSlot("apkMirrorCustomApiUser", defValue = "")
    val apkMirrorCustomApiKey: PrefSlot<String> = stringSlot("apkMirrorCustomApiKey", defValue = "")

    val usePlay: PrefSlot<Boolean> = booleanSlot("usePlay", defValue = true)
    val useIzzyOnDroid: PrefSlot<Boolean> = booleanSlot("useIzzyOnDroid", defValue = true)
    val useGitHub: PrefSlot<Boolean> = booleanSlot("useGitHub", defValue = true)
    val useGitLab: PrefSlot<Boolean> = booleanSlot("useGitLab", defValue = true)
    val useCodeberg: PrefSlot<Boolean> = booleanSlot("useCodeberg", defValue = true)
    val useCustomFDroidRepos: PrefSlot<Boolean> = booleanSlot("useCustomFDroidRepos", defValue = true)

    val customFDroidRepos: PrefSlot<List<CustomFDroidRepo>> = jsonSlot(
        key = "customFDroidRepos",
        type = object : TypeToken<List<CustomFDroidRepo>>() {}.type,
        defProvider = { emptyList() },
        onChanged = { _customFDroidReposFlow.value = it }
    )
    private val _customFDroidReposFlow = MutableStateFlow(readCustomFDroidReposSafe())
    val customFDroidReposFlow: StateFlow<List<CustomFDroidRepo>> = _customFDroidReposFlow.asStateFlow()

    val watchedApps: PrefSlot<List<WatchedAppEntry>> = jsonSlot(
        key = "watchedApps",
        type = object : TypeToken<List<WatchedAppEntry>>() {}.type,
        defProvider = { emptyList() },
        onChanged = { _watchedAppsFlow.value = it }
    )
    private val _watchedAppsFlow = MutableStateFlow(readWatchedAppsSafe())
    val watchedAppsFlow: StateFlow<List<WatchedAppEntry>> = _watchedAppsFlow.asStateFlow()

    val incompatibleRepoSignatures: PrefSlot<Map<String, String>> = jsonSlot(
        key = "incompatibleRepoSignatures",
        type = object : TypeToken<Map<String, String>>() {}.type,
        defProvider = { emptyMap() }
    )

    val fdroidRepos: PrefSlot<List<FDroidRepoConfig>> = jsonSlot(
        key = "fdroidRepos",
        type = object : TypeToken<List<FDroidRepoConfig>>() {}.type,
        defProvider = { FDroidRepoConfig.defaultPresets() },
        onChanged = { _fdroidReposFlow.value = it }
    )
    private val _fdroidReposFlow = MutableStateFlow(readFDroidReposSafe())
    val fdroidReposFlow: StateFlow<List<FDroidRepoConfig>> = _fdroidReposFlow.asStateFlow()

    val fdroidRepoTimestamps: PrefSlot<Map<String, Long>> = jsonSlot(
        key = "fdroidRepoTimestamps",
        type = object : TypeToken<Map<String, Long>>() {}.type,
        defProvider = { emptyMap() }
    )

    val githubToken: PrefSlot<String> = stringSlot("githubToken", defValue = "")
    val gitlabToken: PrefSlot<String> = stringSlot("gitlabToken", defValue = "")
    val codebergToken: PrefSlot<String> = stringSlot("codebergToken", defValue = "")

    val customRepoBindings: PrefSlot<Map<String, RepoBinding>> = jsonSlot(
        key = "customRepoBindings",
        type = object : TypeToken<Map<String, RepoBinding>>() {}.type,
        defProvider = { emptyMap() },
        onChanged = { _customRepoBindingsFlow.value = it }
    )
    private val _customRepoBindingsFlow = MutableStateFlow(customRepoBindings.get())
    val customRepoBindingsFlow: StateFlow<Map<String, RepoBinding>> = _customRepoBindingsFlow.asStateFlow()

    val sourcePriorityOrder: PrefSlot<List<String>> = jsonSlot(
        key = "sourcePriorityOrder",
        type = object : TypeToken<List<String>>() {}.type,
        defProvider = { com.apkup.data.ui.SourcePriorityKeys.DEFAULT_ORDER },
        onChanged = { _sourcePriorityOrderFlow.value = com.apkup.data.ui.SourcePriorityKeys.normalizeOrder(it) }
    )
    private val _sourcePriorityOrderFlow = MutableStateFlow(readSourcePriorityOrderSafe())
    val sourcePriorityOrderFlow: StateFlow<List<String>> = _sourcePriorityOrderFlow.asStateFlow()

    val pinnedAppSources: PrefSlot<Map<String, String>> = jsonSlot(
        key = "pinnedAppSources",
        type = object : TypeToken<Map<String, String>>() {}.type,
        defProvider = { emptyMap() },
        onChanged = { _pinnedAppSourcesFlow.value = it }
    )
    private val _pinnedAppSourcesFlow = MutableStateFlow(readPinnedAppSourcesSafe())
    val pinnedAppSourcesFlow: StateFlow<Map<String, String>> = _pinnedAppSourcesFlow.asStateFlow()

    val includePreReleasesGlobal: PrefSlot<Boolean> = booleanSlot("includePreReleasesGlobal", defValue = false)
    val includeSelfUpdatePreReleases: PrefSlot<Boolean> =
        booleanSlot("includeSelfUpdatePreReleases", defValue = false)

    val preReleaseChannels: PrefSlot<List<String>> = jsonSlot(
        key = "preReleaseChannels",
        type = object : TypeToken<List<String>>() {}.type,
        defProvider = { com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.DEFAULT.toList() }
    )

    val appPreReleaseOverrides: PrefSlot<Map<String, String>> = jsonSlot(
        key = "appPreReleaseOverrides",
        type = object : TypeToken<Map<String, String>>() {}.type,
        defProvider = { emptyMap() },
        onChanged = { _appPreReleaseOverridesFlow.value = it }
    )
    private val _appPreReleaseOverridesFlow = MutableStateFlow(readAppPreReleaseOverridesSafe())
    val appPreReleaseOverridesFlow: StateFlow<Map<String, String>> = _appPreReleaseOverridesFlow.asStateFlow()

    val autoUpdateWindowEnabled: PrefSlot<Boolean> = booleanSlot("autoUpdateWindowEnabled", defValue = false)
    val autoUpdateWindowMode: PrefSlot<String> = stringSlot(
        "autoUpdateWindowMode",
        defValue = com.apkup.data.ui.UpdateWindowMode.QUIET_PAUSE
    )
    val autoUpdateWindowStartMinutes: PrefSlot<Int> = intSlot("autoUpdateWindowStartMinutes", defValue = 22 * 60)
    val autoUpdateWindowEndMinutes: PrefSlot<Int> = intSlot("autoUpdateWindowEndMinutes", defValue = 7 * 60)

    val enableRollbackBackups: PrefSlot<Boolean> = booleanSlot("enableRollbackBackups", defValue = true)
    val rollbackMaxBudgetMb: PrefSlot<Int> = intSlot(
        "rollbackMaxBudgetMb",
        defValue = com.apkup.util.RollbackBackupManager.DEFAULT_MAX_BUDGET_MB
    )
    val rollbackMaxAgeDays: PrefSlot<Int> = intSlot(
        "rollbackMaxAgeDays",
        defValue = com.apkup.util.RollbackBackupManager.DEFAULT_MAX_AGE_DAYS
    )

    val incompatibleVersionKeys: PrefSlot<List<String>> = jsonSlot(
        key = "incompatibleVersionKeys",
        type = object : TypeToken<List<String>>() {}.type,
        defProvider = { emptyList() }
    )

    val enableBackgroundChecks: PrefSlot<Boolean> = booleanSlot("enableAlarm", defValue = true)
    val backgroundCheckIntervalHours: PrefSlot<Int> = intSlot("backgroundCheckIntervalHours", defValue = 24)
    val theme: PrefSlot<Int> = intSlot("theme", defValue = 0)
    val dynamicColor: PrefSlot<Boolean> = booleanSlot("dynamicColor", defValue = false)
    val lastTab: PrefSlot<String> = stringSlot("lastTab", defValue = com.apkup.data.ui.Screen.Updates.route)

    val playAuthData: PrefSlot<StoredPlaySession> = jsonSlot(
        key = "playAuthData",
        type = object : TypeToken<StoredPlaySession>() {}.type,
        defProvider = { StoredPlaySession() }
    )
    val lastPlayCheck: PrefSlot<Long> = longSlot("lastPlayCheck", defValue = 0L)

    val useShizukuInstaller: PrefSlot<Boolean> = booleanSlot("useShizukuInstaller", defValue = false)
    val useRootInstaller: PrefSlot<Boolean> = booleanSlot("useRootInstaller", defValue = false)
    val customDownloadFolderUri: PrefSlot<String> = stringSlot("customDownloadFolderUri", defValue = "")
    val autoInstallUpdates: PrefSlot<Boolean> = booleanSlot("autoInstallUpdates", defValue = false)
    val autoInstallWifiOnly: PrefSlot<Boolean> = booleanSlot("autoInstallWifiOnly", defValue = true)
    val autoInstallChargingOnly: PrefSlot<Boolean> = booleanSlot("autoInstallChargingOnly", defValue = true)
    val downloadWifiOnly: PrefSlot<Boolean> = booleanSlot("downloadWifiOnly", defValue = false)
    val downloadChargingOnly: PrefSlot<Boolean> = booleanSlot("downloadChargingOnly", defValue = false)
    val backgroundCheckWifiOnly: PrefSlot<Boolean> = booleanSlot("backgroundCheckWifiOnly", defValue = false)

    val lastSeenVersionCode: PrefSlot<Long> = longSlot("last_seen_version_code", defValue = 0L)
    val firstLaunchTimestampMillis: PrefSlot<Long> = longSlot("first_launch_timestamp_millis", defValue = 0L)
    val successfulUpdatesCount: PrefSlot<Int> = intSlot("successful_updates_count", defValue = 0)
    val supportPromptDismissedPermanently: PrefSlot<Boolean> = booleanSlot("support_prompt_dismissed_permanently", defValue = false)
    val supportPromptSnoozedUntilMillis: PrefSlot<Long> = longSlot("support_prompt_snoozed_until_millis", defValue = 0L)

    val cachedUpdateSnapshot: PrefSlot<CachedUpdateSnapshot> = jsonSlot(
        key = "cachedUpdateSnapshot",
        type = object : TypeToken<CachedUpdateSnapshot>() {}.type,
        defProvider = { CachedUpdateSnapshot() }
    )

    private val updateHistoryEntries: PrefSlot<List<UpdateHistoryEntry>> = jsonSlot(
        key = "updateHistoryEntries",
        type = object : TypeToken<List<UpdateHistoryEntry>>() {}.type,
        defProvider = { emptyList() }
    )
    private val _updateHistoryFlow = MutableStateFlow(readUpdateHistorySafe())
    val updateHistoryFlow: StateFlow<List<UpdateHistoryEntry>> = _updateHistoryFlow.asStateFlow()

    companion object {
        const val MAX_UPDATE_HISTORY_ENTRIES = 50
        private const val DEDUP_WINDOW_MILLIS = 5L * 60L * 1_000L
    }

    fun addIgnoredApp(packageName: String) = synchronized(lock) {
        if (packageName.isBlank()) return@synchronized
        val updated = (ignoredApps.get() + packageName).distinct()
        ignoredApps.put(updated)
    }

    fun removeIgnoredApp(packageName: String) = synchronized(lock) {
        val updated = ignoredApps.get().filterNot { it == packageName }
        ignoredApps.put(updated)
    }

    fun toggleIgnoredApp(packageName: String): Boolean = synchronized(lock) {
        val current = ignoredApps.get()
        val nowIgnored = if (packageName in current) {
            ignoredApps.put(current.filterNot { it == packageName })
            false
        } else {
            ignoredApps.put((current + packageName).distinct())
            true
        }
        nowIgnored
    }

    fun addIgnoredVersionEntry(entry: IgnoredVersionEntry) = synchronized(lock) {
        val updatedEntries = ignoredVersionEntries.get()
            .filterNot { it.packageName == entry.packageName && it.versionCode == entry.versionCode } + entry
        ignoredVersionEntries.put(updatedEntries)
    }

    fun removeIgnoredVersionEntry(entry: IgnoredVersionEntry, legacyIdToRemove: Int? = null) = synchronized(lock) {
        val updatedEntries = ignoredVersionEntries.get().filterNot {
            it.packageName == entry.packageName &&
                it.versionCode == entry.versionCode &&
                it.version == entry.version
        }
        ignoredVersionEntries.put(updatedEntries)
        if (legacyIdToRemove != null) {
            ignoredVersions.put(ignoredVersions.get().filterNot { it == legacyIdToRemove })
        }
    }

    fun clearAllIgnoredVersions() = synchronized(lock) {
        ignoredVersionEntries.put(emptyList())
        ignoredVersions.put(emptyList())
    }

    fun getFDroidRepos(): List<FDroidRepoConfig> = readFDroidReposSafe()

    fun readFDroidReposSafe(): List<FDroidRepoConfig> = synchronized(lock) {
        val stored = runCatching { fdroidRepos.get() }.getOrDefault(emptyList())
        val presets = FDroidRepoConfig.defaultPresets()
        if (stored.isEmpty()) return@synchronized presets
        val byId = stored.associateBy { it.id }
        val mergedBuiltIns = presets.map { preset ->
            val existing = byId[preset.id]
            if (existing != null) {
                preset.copy(enabled = existing.enabled)
            } else {
                preset
            }
        }
        val customs = stored.filter { !it.isBuiltIn && it.id.isNotBlank() && it.url.isNotBlank() }
        mergedBuiltIns + customs
    }

    fun setFDroidRepoEnabled(repoId: String, enabled: Boolean) = synchronized(lock) {
        val updated = readFDroidReposSafe().map { repo ->
            if (repo.id == repoId) repo.copy(enabled = enabled) else repo
        }
        fdroidRepos.put(updated)
    }

    fun addCustomFDroidRepo(repo: FDroidRepoConfig): Boolean = synchronized(lock) {
        val current = readFDroidReposSafe()
        val matchIdx = current.indexOfFirst { it.normalizedUrl.equals(repo.normalizedUrl, ignoreCase = true) }
        if (matchIdx >= 0) {
            val existing = current[matchIdx]
            if (existing.isBuiltIn) {
                val updated = current.toMutableList()
                updated[matchIdx] = existing.copy(enabled = true)
                fdroidRepos.put(updated)
                return@synchronized true
            }
            return@synchronized false
        }
        fdroidRepos.put(
            current + repo.copy(
                isBuiltIn = false,
                enabled = true,
                isTofuPinned = false
            )
        )
        true
    }

    fun pinFDroidRepoTofu(repoId: String, fingerprint: String): Boolean = synchronized(lock) {
        val cleanFp = FDroidRepoConfig.normalizeFingerprint(fingerprint)
        if (!FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) return@synchronized false
        var changed = false
        val updated = readFDroidReposSafe().map { repo ->
            if (!repo.isBuiltIn && repo.id == repoId && repo.normalizedFingerprint.isEmpty()) {
                changed = true
                repo.copy(fingerprint = cleanFp, isTofuPinned = true)
            } else {
                repo
            }
        }
        if (changed) {
            fdroidRepos.put(updated)
        }
        changed
    }

    @Suppress("SENSELESS_COMPARISON")
    fun migrateLegacyCustomFDroidRepos() = synchronized(lock) {
        val legacy = readCustomFDroidReposSafe()
        val useLegacy = useCustomFDroidRepos.get()
        if (legacy.isEmpty()) {
            if (useLegacy) {
                useCustomFDroidRepos.put(false)
            }
            return@synchronized
        }
        val current = readFDroidReposSafe().toMutableList()
        var enableFDroidSource = false
        for (item in legacy) {
            val normUrl = FDroidRepoConfig.normalizeRepoBaseUrl(item.baseUrl) ?: continue
            val cleanFp = FDroidRepoConfig.normalizeFingerprint((item.fingerprint as String?).orEmpty())
            if (cleanFp.isNotEmpty() && !FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) continue
            val itemActive = item.enabled && useLegacy
            val existingIdx = current.indexOfFirst { it.normalizedUrl.equals(normUrl, ignoreCase = true) }
            if (existingIdx >= 0) {
                val existing = current[existingIdx]
                var updated = if (itemActive && !existing.enabled) existing.copy(enabled = true) else existing
                if (!updated.isBuiltIn && updated.normalizedFingerprint.isEmpty() && cleanFp.isNotEmpty()) {
                    updated = updated.copy(fingerprint = cleanFp, isTofuPinned = item.isTofuPinned)
                }
                current[existingIdx] = updated
            } else {
                val safeId = item.id.ifBlank {
                    "custom_" + normUrl.lowercase(java.util.Locale.ROOT).hashCode().toUInt().toString(16)
                }
                val safeName = item.name.trim().ifBlank { normUrl }
                current.add(
                    FDroidRepoConfig(
                        id = safeId,
                        name = safeName,
                        url = normUrl,
                        fingerprint = cleanFp,
                        enabled = itemActive,
                        isBuiltIn = false,
                        isTofuPinned = item.isTofuPinned && cleanFp.isNotEmpty()
                    )
                )
            }
            if (itemActive) {
                enableFDroidSource = true
            }
        }
        fdroidRepos.put(current)
        if (enableFDroidSource && !useIzzyOnDroid.get()) {
            useIzzyOnDroid.put(true)
        }
        customFDroidRepos.put(emptyList())
        useCustomFDroidRepos.put(false)
    }

    fun removeCustomFDroidRepo(repoId: String) = synchronized(lock) {
        val updated = readFDroidReposSafe().filterNot { !it.isBuiltIn && it.id == repoId }
        fdroidRepos.put(updated)
        val timestamps = fdroidRepoTimestamps.get().toMutableMap()
        if (timestamps.remove(repoId) != null) {
            fdroidRepoTimestamps.put(timestamps)
        }
    }

    fun recordFDroidRepoTimestamp(repoId: String, timestampMillis: Long) = synchronized(lock) {
        val current = fdroidRepoTimestamps.get().toMutableMap()
        val prev = current[repoId] ?: 0L
        if (timestampMillis > prev) {
            current[repoId] = timestampMillis
            fdroidRepoTimestamps.put(current)
        }
    }

    fun getFDroidRepoTimestamp(repoId: String): Long = synchronized(lock) {
        fdroidRepoTimestamps.get()[repoId] ?: 0L
    }

    fun readUpdateHistorySafe(): List<UpdateHistoryEntry> = synchronized(lock) {
        runCatching { updateHistoryEntries.get() }.getOrElse {
            runCatching { updateHistoryEntries.put(emptyList()) }
            emptyList()
        }
    }

    fun recordUpdateHistory(
        entry: UpdateHistoryEntry,
        maxEntries: Int = MAX_UPDATE_HISTORY_ENTRIES
    ) = synchronized(lock) {
        if (entry.packageName.isBlank()) return@synchronized
        val current = readUpdateHistorySafe().toMutableList()
        val duplicateIndex = current.indexOfFirst { existing ->
            existing.packageName == entry.packageName &&
                existing.newVersionCode == entry.newVersionCode &&
                existing.newVersion == entry.newVersion &&
                abs(existing.installedAtMillis - entry.installedAtMillis) <= DEDUP_WINDOW_MILLIS
        }
        if (duplicateIndex != -1) {
            return@synchronized
        }
        current.add(0, entry)
        val trimmed = current.take(maxEntries.coerceAtLeast(1))
        runCatching { updateHistoryEntries.put(trimmed) }
        runCatching { successfulUpdatesCount.put(successfulUpdatesCount.get() + 1) }
        _updateHistoryFlow.value = trimmed
    }

    fun ensureFirstLaunchTimestamp(nowMillis: Long = System.currentTimeMillis()): Long = synchronized(lock) {
        val existing = firstLaunchTimestampMillis.get()
        if (existing > 0L) return@synchronized existing
        firstLaunchTimestampMillis.put(nowMillis)
        nowMillis
    }

    fun replaceUpdateHistory(
        entries: List<UpdateHistoryEntry>,
        maxEntries: Int = MAX_UPDATE_HISTORY_ENTRIES
    ) = synchronized(lock) {
        val sanitized = entries
            .filter { it.packageName.isNotBlank() }
            .sortedByDescending { it.installedAtMillis }
            .take(maxEntries.coerceAtLeast(1))
        runCatching { updateHistoryEntries.put(sanitized) }
        _updateHistoryFlow.value = sanitized
    }

    fun clearUpdateHistory() = synchronized(lock) {
        runCatching { updateHistoryEntries.put(emptyList()) }
        _updateHistoryFlow.value = emptyList()
    }

    fun readCachedSnapshotSafe(): CachedUpdateSnapshot = synchronized(lock) {
        runCatching { cachedUpdateSnapshot.get() }.getOrElse {
            val empty = CachedUpdateSnapshot()
            runCatching { cachedUpdateSnapshot.put(empty) }
            empty
        }
    }

    fun writeCachedSnapshot(snapshot: CachedUpdateSnapshot) = synchronized(lock) {
        runCatching { cachedUpdateSnapshot.put(snapshot) }
    }

    fun pruneCachedSnapshotPackages(packageNames: Set<String>) = synchronized(lock) {
        if (packageNames.isEmpty()) return@synchronized
        val current = readCachedSnapshotSafe()
        val updates = current.updates.orEmpty()
        if (updates.none { it.packageName in packageNames }) return@synchronized
        val pruned = current.copy(updates = updates.filterNot { it.packageName in packageNames })
        runCatching { cachedUpdateSnapshot.put(pruned) }
    }

    fun readIgnoredVersionEntriesSafe(): List<IgnoredVersionEntry> = synchronized(lock) {
        runCatching { ignoredVersionEntries.get() }.getOrElse {
            runCatching { ignoredVersionEntries.put(emptyList()) }
            emptyList()
        }
    }

    fun readCustomRepoBindingsSafe(): Map<String, RepoBinding> = synchronized(lock) {
        runCatching { customRepoBindings.get() }.getOrElse {
            val empty = emptyMap<String, RepoBinding>()
            runCatching { customRepoBindings.put(empty) }
            empty
        }
    }

    fun writeCustomRepoBindings(bindings: Map<String, RepoBinding>) = synchronized(lock) {
        runCatching { customRepoBindings.put(bindings) }
    }

    fun putCustomRepoBinding(packageName: String, binding: RepoBinding) = synchronized(lock) {
        val current = readCustomRepoBindingsSafe().toMutableMap()
        current[packageName] = binding
        writeCustomRepoBindings(current)
    }

    fun removeCustomRepoBinding(packageName: String) = synchronized(lock) {
        val current = readCustomRepoBindingsSafe().toMutableMap()
        if (current.remove(packageName) != null) {
            writeCustomRepoBindings(current)
        }
    }

    fun readIncompatibleRepoSignaturesSafe(): Map<String, String> = synchronized(lock) {
        runCatching { incompatibleRepoSignatures.get() }
            .getOrDefault(emptyMap())
            .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
    }

    fun writeIncompatibleRepoSignatures(entries: Map<String, String>) = synchronized(lock) {
        incompatibleRepoSignatures.put(entries.filter { (k, v) -> k.isNotBlank() && v.isNotBlank() })
    }

    @Suppress("SENSELESS_COMPARISON")
    fun readCustomFDroidReposSafe(): List<CustomFDroidRepo> = synchronized(lock) {
        runCatching { customFDroidRepos.get() }
            .getOrDefault(emptyList())
            .filter { (it.baseUrl as String?)?.isNotBlank() == true }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun writeCustomFDroidRepos(repos: List<CustomFDroidRepo>) = synchronized(lock) {
        val cleaned = repos
            .filter { (it.baseUrl as String?)?.isNotBlank() == true }
            .distinctBy { it.normalizedBaseUrl().lowercase() }
        customFDroidRepos.put(cleaned)
    }

    fun pinCustomFDroidRepoTofu(normalizedUrl: String, fingerprint: String): Boolean = synchronized(lock) {
        val cleanFp = FDroidRepoConfig.normalizeFingerprint(fingerprint)
        if (!FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) return@synchronized false
        val targetNorm = FDroidRepoConfig.normalizeRepoBaseUrl(normalizedUrl) ?: normalizedUrl.trim().trimEnd('/')
        var changed = false
        val updated = readCustomFDroidReposSafe().map { repo ->
            val repoNorm = FDroidRepoConfig.normalizeRepoBaseUrl(repo.normalizedBaseUrl())
                ?: repo.normalizedBaseUrl().trimEnd('/')
            if (repoNorm.equals(targetNorm, ignoreCase = true) &&
                FDroidRepoConfig.normalizeFingerprint((repo.fingerprint as String?).orEmpty()).isEmpty()
            ) {
                changed = true
                repo.copy(fingerprint = cleanFp, isTofuPinned = true)
            } else {
                repo
            }
        }
        if (changed) {
            writeCustomFDroidRepos(updated)
        }
        changed
    }

    fun readWatchedAppsSafe(): List<WatchedAppEntry> = synchronized(lock) {
        runCatching { watchedApps.get() }
            .getOrDefault(emptyList())
            .filter { it.packageName.isNotBlank() }
    }

    fun writeWatchedApps(entries: List<WatchedAppEntry>) = synchronized(lock) {
        val cleaned = entries
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName.trim() }
        watchedApps.put(cleaned)
    }

    fun addWatchedApp(entry: WatchedAppEntry) = synchronized(lock) {
        val pkg = entry.packageName.trim()
        if (pkg.isBlank()) return@synchronized
        val normalizedEntry = entry.copy(
            packageName = pkg,
            appName = entry.appName.trim().ifBlank { pkg }
        )
        val updated = readWatchedAppsSafe()
            .filterNot { it.packageName.equals(pkg, ignoreCase = true) } + normalizedEntry
        writeWatchedApps(updated)
    }

    fun removeWatchedApp(packageName: String) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized
        val updated = readWatchedAppsSafe()
            .filterNot { it.packageName.equals(pkg, ignoreCase = true) }
        writeWatchedApps(updated)
    }

    fun removeInstalledWatchedApps(installedPackages: Set<String>): List<WatchedAppEntry> = synchronized(lock) {
        if (installedPackages.isEmpty()) return@synchronized emptyList()
        val lowerInstalled = installedPackages.map { it.trim().lowercase() }.toSet()
        val current = readWatchedAppsSafe()
        val removed = current.filter { it.packageName.trim().lowercase() in lowerInstalled }
        if (removed.isNotEmpty()) {
            writeWatchedApps(current.filterNot { it.packageName.trim().lowercase() in lowerInstalled })
        }
        removed
    }

    fun readSnoozedUpdatesSafe(nowMillis: Long = System.currentTimeMillis()): List<com.apkup.data.ui.SnoozedUpdateEntry> =
        synchronized(lock) {
            runCatching { snoozedUpdates.get() }
                .getOrDefault(emptyList())
                .filter { it.packageName.isNotBlank() && it.snoozeUntilMillis > nowMillis }
        }

    fun snoozeUpdate(
        packageName: String,
        appName: String,
        version: String,
        versionCode: Long,
        durationMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank() || durationMillis <= 0L) return@synchronized
        val entry = com.apkup.data.ui.SnoozedUpdateEntry(
            packageName = pkg,
            appName = appName.trim().ifBlank { pkg },
            version = version,
            versionCode = versionCode,
            snoozeUntilMillis = nowMillis + durationMillis
        )
        val updated = readSnoozedUpdatesSafe(nowMillis)
            .filterNot { it.packageName.equals(pkg, ignoreCase = true) } + entry
        snoozedUpdates.put(updated)
    }

    fun unsnoozeUpdate(packageName: String) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized
        val updated = readSnoozedUpdatesSafe()
            .filterNot { it.packageName.equals(pkg, ignoreCase = true) }
        snoozedUpdates.put(updated)
    }

    fun isUpdateSnoozed(
        packageName: String,
        versionCode: Long,
        version: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = readSnoozedUpdatesSafe(nowMillis).any {
        it.isActiveFor(packageName, versionCode, version, nowMillis)
    }

    fun readSourcePriorityOrderSafe(): List<String> = synchronized(lock) {
        val raw = runCatching { sourcePriorityOrder.get() }.getOrNull()
        com.apkup.data.ui.SourcePriorityKeys.normalizeOrder(raw)
    }

    fun writeSourcePriorityOrder(order: List<String>) = synchronized(lock) {
        sourcePriorityOrder.put(com.apkup.data.ui.SourcePriorityKeys.normalizeOrder(order))
    }

    fun readPinnedAppSourcesSafe(): Map<String, String> = synchronized(lock) {
        runCatching { pinnedAppSources.get() }
            .getOrDefault(emptyMap())
            .filter { (pkg, key) ->
                pkg.isNotBlank() && key in com.apkup.data.ui.SourcePriorityKeys.DEFAULT_ORDER
            }
    }

    fun getPinnedAppSource(packageName: String): String? = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized null
        readPinnedAppSourcesSafe()[pkg]
    }

    fun pinAppSource(packageName: String, sourceKey: String?) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized
        val current = readPinnedAppSourcesSafe().toMutableMap()
        val normalizedKey = sourceKey?.trim()?.takeIf {
            it in com.apkup.data.ui.SourcePriorityKeys.DEFAULT_ORDER
        }
        if (normalizedKey == null) {
            current.remove(pkg)
        } else {
            current[pkg] = normalizedKey
        }
        pinnedAppSources.put(current)
    }

    fun markWatchedAppSeen(packageName: String, versionName: String, versionCode: Long) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized
        val current = readWatchedAppsSafe()
        val updated = current.map { entry ->
            if (entry.packageName.equals(pkg, ignoreCase = true)) {
                entry.copy(
                    seenVersion = versionName.trim().ifBlank { entry.seenVersion },
                    seenVersionCode = maxOf(entry.seenVersionCode, versionCode.coerceAtLeast(0L))
                )
            } else {
                entry
            }
        }
        writeWatchedApps(updated)
    }

    fun readAppPreReleaseOverridesSafe(): Map<String, String> = synchronized(lock) {
        runCatching { appPreReleaseOverrides.get() }
            .getOrDefault(emptyMap())
            .filter { (pkg, mode) ->
                pkg.isNotBlank() && (
                    mode == com.apkup.data.ui.PreReleaseOverride.ALLOW ||
                        mode == com.apkup.data.ui.PreReleaseOverride.BLOCK
                    )
            }
    }

    fun getAppPreReleaseOverride(packageName: String): String = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized com.apkup.data.ui.PreReleaseOverride.DEFAULT
        readAppPreReleaseOverridesSafe()[pkg] ?: com.apkup.data.ui.PreReleaseOverride.DEFAULT
    }

    fun setAppPreReleaseOverride(packageName: String, overrideMode: String?) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return@synchronized
        val current = readAppPreReleaseOverridesSafe().toMutableMap()
        val normalized = overrideMode?.trim().orEmpty()
        if (normalized == com.apkup.data.ui.PreReleaseOverride.ALLOW ||
            normalized == com.apkup.data.ui.PreReleaseOverride.BLOCK
        ) {
            current[pkg] = normalized
        } else {
            current.remove(pkg)
        }
        appPreReleaseOverrides.put(current)
    }

    fun shouldAllowPreReleases(packageName: String): Boolean = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isNotBlank()) {
            when (readAppPreReleaseOverridesSafe()[pkg]) {
                com.apkup.data.ui.PreReleaseOverride.ALLOW -> return@synchronized true
                com.apkup.data.ui.PreReleaseOverride.BLOCK -> return@synchronized false
            }
        }
        runCatching { includePreReleasesGlobal.get() }.getOrDefault(false) &&
            readAllowedPreReleaseChannelsSafe().isNotEmpty()
    }

    fun readAllowedPreReleaseChannelsSafe(): Set<String> = synchronized(lock) {
        val valid = com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.ALL
        runCatching { preReleaseChannels.get() }
            .getOrDefault(com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.DEFAULT.toList())
            .filter { it in valid }
            .toSet()
    }

    fun setPreReleaseChannelEnabled(channel: String, enabled: Boolean) = synchronized(lock) {
        val valid = com.apkup.data.repomapping.ReleaseAssetFilter.PreReleaseChannel.ALL
        if (channel !in valid) return@synchronized
        val current = readAllowedPreReleaseChannelsSafe().toMutableSet()
        if (enabled) current.add(channel) else current.remove(channel)
        preReleaseChannels.put(current.toList())
    }

    fun isPreReleaseCandidateAllowed(
        packageName: String,
        versionOrTag: String,
        isPrereleaseFlag: Boolean = false
    ): Boolean = synchronized(lock) {
        if (!com.apkup.data.repomapping.ReleaseAssetFilter.isPreReleaseVersion(versionOrTag, isPrereleaseFlag)) {
            return@synchronized true
        }
        val pkg = packageName.trim()
        if (pkg.isNotBlank()) {
            when (readAppPreReleaseOverridesSafe()[pkg]) {
                com.apkup.data.ui.PreReleaseOverride.ALLOW -> return@synchronized true
                com.apkup.data.ui.PreReleaseOverride.BLOCK -> return@synchronized false
            }
        }
        if (!runCatching { includePreReleasesGlobal.get() }.getOrDefault(false)) {
            return@synchronized false
        }
        return@synchronized com.apkup.data.repomapping.ReleaseAssetFilter.isPreReleaseAllowedByChannels(
            versionOrTag = versionOrTag,
            allowedChannels = readAllowedPreReleaseChannelsSafe(),
            isPrereleaseFlag = isPrereleaseFlag
        )
    }

    fun hasAnyPreReleaseAllowed(): Boolean = synchronized(lock) {
        if (runCatching { includePreReleasesGlobal.get() }.getOrDefault(false)) return@synchronized true
        readAppPreReleaseOverridesSafe().values.any { it == com.apkup.data.ui.PreReleaseOverride.ALLOW }
    }

    fun isUpdateWindowAllowedNow(nowMinuteOfDay: Int = currentMinuteOfDay()): Boolean = synchronized(lock) {
        com.apkup.data.ui.UpdateWindowEvaluator.isAllowedNow(
            enabled = runCatching { autoUpdateWindowEnabled.get() }.getOrDefault(false),
            mode = runCatching { autoUpdateWindowMode.get() }.getOrDefault(com.apkup.data.ui.UpdateWindowMode.QUIET_PAUSE),
            startMinutes = runCatching { autoUpdateWindowStartMinutes.get() }.getOrDefault(22 * 60),
            endMinutes = runCatching { autoUpdateWindowEndMinutes.get() }.getOrDefault(7 * 60),
            nowMinuteOfDay = nowMinuteOfDay
        )
    }

    fun minutesUntilNextUpdateWindow(nowMinuteOfDay: Int = currentMinuteOfDay()): Int = synchronized(lock) {
        com.apkup.data.ui.UpdateWindowEvaluator.minutesUntilNextAllowedWindow(
            enabled = runCatching { autoUpdateWindowEnabled.get() }.getOrDefault(false),
            mode = runCatching { autoUpdateWindowMode.get() }.getOrDefault(com.apkup.data.ui.UpdateWindowMode.QUIET_PAUSE),
            startMinutes = runCatching { autoUpdateWindowStartMinutes.get() }.getOrDefault(22 * 60),
            endMinutes = runCatching { autoUpdateWindowEndMinutes.get() }.getOrDefault(7 * 60),
            nowMinuteOfDay = nowMinuteOfDay
        )
    }

    private fun currentMinuteOfDay(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    fun markVersionIncompatible(packageName: String, versionCode: Long) = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank() || versionCode <= 0L) return@synchronized
        val key = "$pkg:$versionCode"
        val current = runCatching { incompatibleVersionKeys.get() }.getOrDefault(emptyList())
        if (key !in current) {
            incompatibleVersionKeys.put((current + key).takeLast(200))
        }
    }

    fun isVersionMarkedIncompatible(packageName: String, versionCode: Long): Boolean = synchronized(lock) {
        val pkg = packageName.trim()
        if (pkg.isBlank() || versionCode <= 0L) return@synchronized false
        val key = "$pkg:$versionCode"
        runCatching { incompatibleVersionKeys.get() }.getOrDefault(emptyList()).contains(key)
    }
}
