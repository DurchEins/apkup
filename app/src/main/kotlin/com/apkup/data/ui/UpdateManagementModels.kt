package com.apkup.data.ui

import com.apkup.R
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.repomapping.RepoBinding
import com.aurora.gplayapi.model.AppFile
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class UpdateInstallMode {
    AUTO_SHIZUKU,
    MANUAL_SHIZUKU,
    MANUAL,
    AUTO_ROOT,
    MANUAL_ROOT
}

data class UpdateHistoryEntry(
    val packageName: String = "",
    val appName: String = "",
    val oldVersion: String = "",
    val newVersion: String = "",
    val oldVersionCode: Long = 0L,
    val newVersionCode: Long = 0L,
    val sourceName: String = "",
    val installedAtMillis: Long = 0L,
    val installModeName: String = UpdateInstallMode.MANUAL.name,
    val previousVersionUrl: String = ""
) {
    val installMode: UpdateInstallMode
        get() = runCatching { UpdateInstallMode.valueOf(installModeName.orEmpty()) }
            .getOrDefault(UpdateInstallMode.MANUAL)
}

data class IgnoredVersionEntry(
    val id: Int = 0,
    val packageName: String = "",
    val appName: String = "",
    val version: String = "",
    val versionCode: Long = 0L,
    val sourceName: String = "",
    val ignoredAtMillis: Long = 0L
) {
    fun matchesUpdate(
        targetPackageName: String,
        targetVersionCode: Long,
        targetVersion: String,
        targetId: Int = 0
    ): Boolean {
        if (id != 0 && targetId != 0 && id == targetId) return true
        if (packageName.isBlank() || packageName != targetPackageName) return false
        if (versionCode > 0L && targetVersionCode > 0L && versionCode == targetVersionCode) return true
        if (version.isNotBlank() && targetVersion.isNotBlank() && version == targetVersion) return true
        return false
    }
}

@androidx.annotation.Keep
data class SnoozedUpdateEntry(
    @com.google.gson.annotations.SerializedName("packageName")
    val packageName: String = "",
    @com.google.gson.annotations.SerializedName("appName")
    val appName: String = "",
    @com.google.gson.annotations.SerializedName("version")
    val version: String = "",
    @com.google.gson.annotations.SerializedName("versionCode")
    val versionCode: Long = 0L,
    @com.google.gson.annotations.SerializedName("snoozeUntilMillis")
    val snoozeUntilMillis: Long = 0L
) {
    fun isActiveFor(
        targetPackageName: String,
        targetVersionCode: Long,
        targetVersion: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (snoozeUntilMillis <= nowMillis) return false
        if (packageName.isBlank() || !packageName.equals(targetPackageName, ignoreCase = true)) return false
        if (versionCode > 0L && targetVersionCode > 0L) return versionCode == targetVersionCode
        if (version.isNotBlank() && targetVersion.isNotBlank()) return version == targetVersion
        return true
    }
}

data class CachedAppUpdateDto(
    val name: String = "",
    val packageName: String = "",
    val version: String = "",
    val oldVersion: String = "",
    val versionCode: Long = 0L,
    val oldVersionCode: Long = 0L,
    val sourceName: String = "",
    val linkUrl: String = "",
    val linkSize: Long = 0L,
    val expectedSha256: String? = null,
    val isPlayLink: Boolean = false,
    val offerType: Int = 1,
    val whatsNew: String = "",
    val totalSize: Long = 0L,
    val updatedOn: String = "",
    val installerPackageName: String = "",
    val installOriginName: String = InstallOrigin.SYSTEM.name,
    val alternatives: List<CachedAppUpdateDto>? = emptyList()
)

data class CachedUpdateSnapshot(
    val timestampMillis: Long = 0L,
    val updates: List<CachedAppUpdateDto>? = emptyList(),
    val hasSourceErrors: Boolean = false,
    val sourceErrors: Map<String, String>? = emptyMap(),
    val totalApps: Int? = null
)

data class ApkUpBackupConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtMillis: Long = 0L,
    val ignoredApps: List<String>? = emptyList(),
    val ignoredVersions: List<Int>? = emptyList(),
    val ignoredVersionEntries: List<IgnoredVersionEntry>? = emptyList(),
    val showSystemApps: Boolean? = false,
    val updatesDisplayMode: Int? = 0,
    val useApkMirror: Boolean? = true,
    val directApkMirrorDownloads: Boolean? = true,
    val usePlay: Boolean? = true,
    val useIzzyOnDroid: Boolean? = true,
    val useGitHub: Boolean? = true,
    val useGitLab: Boolean? = true,
    val useCodeberg: Boolean? = true,
    val useCustomFDroidRepos: Boolean? = true,
    val customFDroidRepos: List<CustomFDroidRepo>? = emptyList(),
    val watchedApps: List<WatchedAppEntry>? = emptyList(),
    val customRepoBindings: Map<String, RepoBinding>? = emptyMap(),
    val fdroidRepos: List<FDroidRepoConfig>? = null,
    val enableBackgroundChecks: Boolean? = true,
    val backgroundCheckIntervalHours: Int? = 24,
    val theme: Int? = 0,
    val useShizukuInstaller: Boolean? = false,
    val useRootInstaller: Boolean? = false,
    val autoInstallUpdates: Boolean? = false,
    val autoInstallWifiOnly: Boolean? = true,
    val autoInstallChargingOnly: Boolean? = true,
    val downloadWifiOnly: Boolean? = false,
    val downloadChargingOnly: Boolean? = false,
    val backgroundCheckWifiOnly: Boolean? = false,
    val customDownloadFolderUri: String? = "",
    val updateHistory: List<UpdateHistoryEntry>? = null,
    val sourcePriorityOrder: List<String>? = null,
    val pinnedAppSources: Map<String, String>? = null,
    val includePreReleasesGlobal: Boolean? = null,
    val preReleaseChannels: List<String>? = null,
    val appPreReleaseOverrides: Map<String, String>? = null,
    val autoUpdateWindowEnabled: Boolean? = null,
    val autoUpdateWindowMode: String? = null,
    val autoUpdateWindowStartMinutes: Int? = null,
    val autoUpdateWindowEndMinutes: Int? = null,
    val enableRollbackBackups: Boolean? = null,
    val rollbackMaxBudgetMb: Int? = null,
    val rollbackMaxAgeDays: Int? = null,
    val includeSelfUpdatePreReleases: Boolean? = null,
    val a11yThemeSubMode: Int? = null,
    val a11ySunriseMinutes: Int? = null,
    val a11ySunsetMinutes: Int? = null,
    val appsGridColumns: Int? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val MAX_SUPPORTED_SCHEMA_VERSION = 2
    }

    val isSupportedSchemaVersion: Boolean
        get() = schemaVersion in 1..MAX_SUPPORTED_SCHEMA_VERSION
}

enum class BackgroundCheckInterval(val hours: Int) {
    HOURS_6(6),
    HOURS_12(12),
    HOURS_24(24),
    HOURS_48(48),
    DAYS_7(168);

    companion object {
        fun fromHours(rawHours: Int): BackgroundCheckInterval =
            entries.firstOrNull { it.hours == rawHours } ?: HOURS_24
    }
}

data class ShizukuSetupStatus(
    val isInstalled: Boolean = false,
    val isBinderRunning: Boolean = false,
    val isPermissionGranted: Boolean = false
) {
    val isReady: Boolean
        get() = isInstalled && isBinderRunning && isPermissionGranted
}

enum class AppsQuickFilter {
    ALL,
    USER_ONLY,
    SYSTEM_ONLY,
    SIDELOAD_ONLY,
    IGNORED_ONLY,
    RECENTLY_UPDATED,
    WATCHED
}

data class CustomFDroidRepo(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val fingerprint: String = "",
    val enabled: Boolean = true,
    val isTofuPinned: Boolean = false
) {
    @Suppress("SENSELESS_COMPARISON")
    fun normalizedBaseUrl(): String {
        val clean = (baseUrl as String?).orEmpty()
            .trim()
            .substringBefore("?")
            .substringBefore("#")
            .trimEnd('/')
        if (clean.isEmpty()) return ""
        val parsed = clean.toHttpUrlOrNull()
        if (parsed != null) {
            val segments = parsed.pathSegments.filter { it.isNotEmpty() }
            val needsRepoSuffix = segments.isEmpty() ||
                (segments.size == 1 && segments[0].equals("fdroid", ignoreCase = true))
            return if (needsRepoSuffix) "$clean/repo/" else "$clean/"
        }
        return if (clean.endsWith("/fdroid", ignoreCase = true)) "$clean/repo/" else "$clean/"
    }
}

object CustomFDroidPresets {
    val presets: List<CustomFDroidRepo> = listOf(
        CustomFDroidRepo(
            id = "fdroid_official",
            name = "F-Droid Offiziell",
            baseUrl = "https://f-droid.org/repo/",
            fingerprint = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"
        ),
        CustomFDroidRepo(
            id = "newpipe",
            name = "NewPipe Repo",
            baseUrl = "https://archive.newpipe.net/fdroid/repo/",
            fingerprint = "E2402C78F9B97C6C89E97DB914A2751FDA1D02FE2039CC0897A462BDB57E7501"
        ),
        CustomFDroidRepo(
            id = "guardian_project",
            name = "Guardian Project",
            baseUrl = "https://guardianproject.info/fdroid/repo/",
            fingerprint = "B7C2EEFD8DAC7806AF67DFCD92EB18126BC08312A7F2D6F3862E46013C7A6135"
        ),
        CustomFDroidRepo(
            id = "microg",
            name = "microG Project",
            baseUrl = "https://microg.org/fdroid/repo/",
            fingerprint = FDroidRepoConfig.MICROG_FINGERPRINT
        )
    )
}

data class WatchedAppEntry(
    val packageName: String = "",
    val appName: String = "",
    val addedAtMillis: Long = 0L,
    val seenVersion: String = "",
    val seenVersionCode: Long = 0L
) {
    fun isCandidateUnseen(candidateVersion: String, candidateVersionCode: Long): Boolean {
        val cleanSeen = seenVersion.trim()
        val cleanCand = candidateVersion.trim()
        if (seenVersionCode <= 0L && cleanSeen.isEmpty()) return true
        if (seenVersionCode > 0L && candidateVersionCode > 0L && candidateVersionCode != seenVersionCode) {
            return candidateVersionCode > seenVersionCode
        }
        if (cleanSeen.isNotEmpty() && cleanCand.isNotEmpty()) {
            if (cleanSeen.equals(cleanCand, ignoreCase = true)) return false
            return com.apkup.data.repomapping.ReleaseAssetFilter.isNewer(cleanSeen, cleanCand)
        }
        return candidateVersionCode > seenVersionCode
    }
}

object PreReleaseOverride {
    const val DEFAULT = ""
    const val ALLOW = "ALLOW"
    const val BLOCK = "BLOCK"
}

object UpdateWindowMode {
    const val ALLOW_ONLY = "ALLOW_ONLY"
    const val QUIET_PAUSE = "QUIET_PAUSE"
}

object UpdateWindowEvaluator {
    private const val MINUTES_PER_DAY = 24 * 60

    fun normalizeMinutes(minutes: Int): Int =
        ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    fun isMinuteInRange(minuteOfDay: Int, startMinutes: Int, endMinutes: Int): Boolean {
        val m = normalizeMinutes(minuteOfDay)
        val s = normalizeMinutes(startMinutes)
        val e = normalizeMinutes(endMinutes)
        if (s == e) return true
        return if (s < e) {
            m in s until e
        } else {
            m >= s || m < e
        }
    }

    fun isAllowedNow(
        enabled: Boolean,
        mode: String,
        startMinutes: Int,
        endMinutes: Int,
        nowMinuteOfDay: Int
    ): Boolean {
        if (!enabled) return true
        val inRange = isMinuteInRange(nowMinuteOfDay, startMinutes, endMinutes)
        return when (mode) {
            UpdateWindowMode.ALLOW_ONLY -> inRange
            else -> !inRange // QUIET_PAUSE: pause inside range, allow outside
        }
    }

    fun minutesUntilNextAllowedWindow(
        enabled: Boolean,
        mode: String,
        startMinutes: Int,
        endMinutes: Int,
        nowMinuteOfDay: Int
    ): Int {
        if (isAllowedNow(enabled, mode, startMinutes, endMinutes, nowMinuteOfDay)) return 0
        val now = normalizeMinutes(nowMinuteOfDay)
        val targetMinute = when (mode) {
            UpdateWindowMode.ALLOW_ONLY -> normalizeMinutes(startMinutes)
            else -> normalizeMinutes(endMinutes) // QUIET_PAUSE ends at endMinutes
        }
        val diff = (targetMinute - now + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (diff == 0) MINUTES_PER_DAY else diff
    }

    fun formatMinutesOfDay(minutes: Int): String {
        val norm = normalizeMinutes(minutes)
        val h = norm / 60
        val m = norm % 60
        return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)
    }
}

enum class AppsSortOrder {
    NAME_ASC,
    NAME_DESC,
    SOURCE,
    LAST_UPDATED
}

internal const val RECENTLY_UPDATED_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1_000L

internal fun installOriginSortOrder(origin: InstallOrigin): Int = when (origin) {
    InstallOrigin.PLAY_STORE -> 0
    InstallOrigin.FDROID -> 1
    InstallOrigin.APKMIRROR -> 2
    InstallOrigin.REVANCED -> 3
    InstallOrigin.SIDELOAD -> 4
    InstallOrigin.SYSTEM -> 5
}

internal fun filterInstalledApps(
    apps: List<AppInstalled>,
    searchQuery: String,
    quickFilter: AppsQuickFilter,
    nowMillis: Long = System.currentTimeMillis(),
    mappedPackages: Set<String> = emptySet()
): List<AppInstalled> {
    val trimmedQuery = searchQuery.trim()
    val filteredByChip = when (quickFilter) {
        AppsQuickFilter.ALL -> apps
        AppsQuickFilter.USER_ONLY -> apps.filter { !it.isSystemApp }
        AppsQuickFilter.SYSTEM_ONLY -> apps.filter { it.isSystemApp }
        AppsQuickFilter.SIDELOAD_ONLY -> apps.filter { app ->
            !app.isSystemApp && (
                app.packageName in mappedPackages ||
                    app.installOrigin in setOf(
                        InstallOrigin.SIDELOAD,
                        InstallOrigin.FDROID,
                        InstallOrigin.APKMIRROR,
                        InstallOrigin.REVANCED
                    )
            )
        }
        AppsQuickFilter.IGNORED_ONLY -> apps.filter { it.ignored }
        AppsQuickFilter.RECENTLY_UPDATED -> {
            val cutoff = nowMillis - RECENTLY_UPDATED_WINDOW_MILLIS
            apps.filter { it.lastUpdateTime >= cutoff }
                .sortedByDescending { it.lastUpdateTime }
        }
        AppsQuickFilter.WATCHED -> apps.filter { it.isWatchedOnly }
    }
    if (trimmedQuery.isEmpty()) return filteredByChip
    return filteredByChip.filter { app ->
        app.name.contains(trimmedQuery, ignoreCase = true) ||
            app.packageName.contains(trimmedQuery, ignoreCase = true)
    }
}

internal fun filterAndSortInstalledApps(
    apps: List<AppInstalled>,
    searchQuery: String,
    quickFilter: AppsQuickFilter,
    sortOrder: AppsSortOrder = AppsSortOrder.NAME_ASC,
    nowMillis: Long = System.currentTimeMillis(),
    mappedPackages: Set<String> = emptySet()
): List<AppInstalled> {
    val filtered = filterInstalledApps(
        apps = apps,
        searchQuery = searchQuery,
        quickFilter = quickFilter,
        nowMillis = nowMillis,
        mappedPackages = mappedPackages
    )
    return when (sortOrder) {
        AppsSortOrder.NAME_ASC -> filtered.sortedWith(
            compareBy<AppInstalled> { it.name.trim().ifEmpty { it.packageName }.lowercase() }
                .thenBy { it.packageName.lowercase() }
        )
        AppsSortOrder.NAME_DESC -> filtered.sortedWith(
            compareByDescending<AppInstalled> { it.name.trim().ifEmpty { it.packageName }.lowercase() }
                .thenByDescending { it.packageName.lowercase() }
        )
        AppsSortOrder.SOURCE -> filtered.sortedWith(
            compareBy<AppInstalled> { installOriginSortOrder(it.installOrigin) }
                .thenBy { it.name.trim().ifEmpty { it.packageName }.lowercase() }
                .thenBy { it.packageName.lowercase() }
        )
        AppsSortOrder.LAST_UPDATED -> filtered.sortedWith(
            compareByDescending<AppInstalled> { it.lastUpdateTime }
                .thenBy { it.name.trim().ifEmpty { it.packageName }.lowercase() }
        )
    }
}

internal fun AppUpdate.toCachedDto(includeAlternatives: Boolean = true): CachedAppUpdateDto {
    val urlLink = link as? Link.Url
    return CachedAppUpdateDto(
        name = name,
        packageName = packageName,
        version = version,
        oldVersion = oldVersion,
        versionCode = versionCode,
        oldVersionCode = oldVersionCode,
        sourceName = source.name,
        linkUrl = urlLink?.link.orEmpty(),
        linkSize = urlLink?.size ?: 0L,
        expectedSha256 = urlLink?.expectedSha256,
        isPlayLink = link is Link.Play || source == PlaySource,
        offerType = offerType,
        whatsNew = whatsNew,
        totalSize = totalSize,
        updatedOn = updatedOn,
        installerPackageName = installerPackageName,
        installOriginName = installOrigin.name,
        alternatives = if (includeAlternatives) {
            alternativeUpdates.map { it.toCachedDto(includeAlternatives = false) }
        } else {
            emptyList()
        }
    )
}

@Suppress("SENSELESS_COMPARISON")
internal fun CachedAppUpdateDto.toAppUpdate(
    playFilesProvider: (packageName: String, versionCode: Long, offerType: Int) -> List<AppFile>
): AppUpdate {
    val safeSourceName = sourceName.orEmpty()
    val safePackageName = packageName.orEmpty()
    val safeLinkUrl = linkUrl.orEmpty()
    val resolvedSource = when (safeSourceName) {
        PlaySource.name -> PlaySource
        IzzyOnDroidSource.name -> IzzyOnDroidSource
        FDroidSource.name -> FDroidSource
        GitHubSource.name -> GitHubSource
        GitLabSource.name -> GitLabSource
        CodebergSource.name -> CodebergSource
        CustomFDroidSource.name -> CustomFDroidSource
        ApkMirrorSource.name, "" -> ApkMirrorSource
        else -> Source(safeSourceName, R.drawable.ic_izzy)
    }
    val resolvedLink = when {
        isPlayLink || resolvedSource == PlaySource -> Link.Play {
            playFilesProvider(safePackageName, versionCode, offerType)
        }
        safeLinkUrl.isNotBlank() -> Link.Url(safeLinkUrl, linkSize, expectedSha256)
        else -> Link.Empty
    }
    val resolvedOrigin = runCatching { InstallOrigin.valueOf(installOriginName.orEmpty()) }
        .getOrDefault(InstallOrigin.SYSTEM)

    return AppUpdate(
        name = name.orEmpty(),
        packageName = safePackageName,
        version = version.orEmpty(),
        oldVersion = oldVersion.orEmpty(),
        versionCode = versionCode,
        oldVersionCode = oldVersionCode,
        source = resolvedSource,
        link = resolvedLink,
        whatsNew = whatsNew.orEmpty(),
        totalSize = totalSize,
        updatedOn = updatedOn.orEmpty(),
        offerType = offerType,
        installOrigin = resolvedOrigin,
        installerPackageName = installerPackageName.orEmpty(),
        alternativeUpdates = alternatives.orEmpty().map { it.toAppUpdate(playFilesProvider) }
    )
}
