package com.apkup.data.ui

import com.aurora.gplayapi.data.models.PlayFile

data class IgnoredVersionEntry(
	val id: Int = 0,
	val packageName: String = "",
	val appName: String = "",
	val version: String = "",
	val versionCode: Long = 0L,
	val sourceName: String = "",
	val ignoredAtMillis: Long = 0L
)

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
	val isPlayLink: Boolean = false,
	val offerType: Int = 1,
	val whatsNew: String = "",
	val totalSize: Long = 0L,
	val updatedOn: String = "",
	val installerPackageName: String = "",
	val installOriginName: String = InstallOrigin.SYSTEM.name,
	val alternatives: List<CachedAppUpdateDto> = emptyList()
)

data class CachedUpdateSnapshot(
	val timestampMillis: Long = 0L,
	val updates: List<CachedAppUpdateDto> = emptyList(),
	val hasSourceErrors: Boolean = false,
	val sourceErrors: Map<String, String> = emptyMap(),
	val totalApps: Int? = null
)

data class ApkUpBackupConfig(
	val schemaVersion: Int = 1,
	val exportedAtMillis: Long = 0L,
	val ignoredApps: List<String> = emptyList(),
	val ignoredVersions: List<Int> = emptyList(),
	val ignoredVersionEntries: List<IgnoredVersionEntry> = emptyList(),
	val showSystemApps: Boolean = false,
	val updatesDisplayMode: Int = 0,
	val useApkMirror: Boolean = true,
	val usePlay: Boolean = true,
	val enableBackgroundChecks: Boolean = true,
	val backgroundCheckIntervalHours: Int = 24,
	val theme: Int = 0,
	val useShizukuInstaller: Boolean = false
)

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
	IGNORED_ONLY,
	RECENTLY_UPDATED
}

internal const val RECENTLY_UPDATED_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1_000L

internal fun filterInstalledApps(
	apps: List<AppInstalled>,
	searchQuery: String,
	quickFilter: AppsQuickFilter,
	nowMillis: Long = System.currentTimeMillis()
): List<AppInstalled> {
	val trimmedQuery = searchQuery.trim()
	val filteredByChip = when (quickFilter) {
		AppsQuickFilter.ALL -> apps
		AppsQuickFilter.USER_ONLY -> apps.filter { !it.isSystemApp }
		AppsQuickFilter.IGNORED_ONLY -> apps.filter { it.ignored }
		AppsQuickFilter.RECENTLY_UPDATED -> {
			val cutoff = nowMillis - RECENTLY_UPDATED_WINDOW_MILLIS
			apps.filter { it.lastUpdateTime >= cutoff }
				.sortedByDescending { it.lastUpdateTime }
		}
	}
	if (trimmedQuery.isEmpty()) return filteredByChip
	return filteredByChip.filter { app ->
		app.name.contains(trimmedQuery, ignoreCase = true) ||
			app.packageName.contains(trimmedQuery, ignoreCase = true)
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

internal fun CachedAppUpdateDto.toAppUpdate(
	playFilesProvider: (packageName: String, versionCode: Long, offerType: Int) -> List<PlayFile>
): AppUpdate {
	val resolvedSource = when (sourceName) {
		PlaySource.name -> PlaySource
		else -> ApkMirrorSource
	}
	val resolvedLink = when {
		isPlayLink || resolvedSource == PlaySource -> Link.Play {
			playFilesProvider(packageName, versionCode, offerType)
		}
		linkUrl.isNotBlank() -> Link.Url(linkUrl, linkSize)
		else -> Link.Empty
	}
	val resolvedOrigin = runCatching { InstallOrigin.valueOf(installOriginName) }
		.getOrDefault(InstallOrigin.SYSTEM)

	return AppUpdate(
		name = name,
		packageName = packageName,
		version = version,
		oldVersion = oldVersion,
		versionCode = versionCode,
		oldVersionCode = oldVersionCode,
		source = resolvedSource,
		link = resolvedLink,
		whatsNew = whatsNew,
		totalSize = totalSize,
		updatedOn = updatedOn,
		offerType = offerType,
		installOrigin = resolvedOrigin,
		installerPackageName = installerPackageName,
		alternativeUpdates = alternatives.map { it.toAppUpdate(playFilesProvider) }
	)
}
