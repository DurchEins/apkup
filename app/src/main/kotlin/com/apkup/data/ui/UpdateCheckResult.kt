package com.apkup.data.ui

data class SourceUpdateResult<T>(
	val updates: List<T>,
	val successfulRequests: Int,
	val failedRequests: Int,
	val failureSummary: String? = null,
	val sourceName: String = ""
)

data class UpdateCheckResult<T>(
	val updates: List<T>,
	val successfulRequests: Int,
	val failedRequests: Int,
	val installedAppsFailed: Boolean = false,
	val isChecking: Boolean = false,
	val checkedApps: Int? = null,
	val totalApps: Int? = null,
	val activeSourceName: String? = null,
	val sourceErrors: Map<String, String> = emptyMap()
) {
	val hasFailures: Boolean get() = installedAppsFailed || failedRequests > 0 || sourceErrors.isNotEmpty()
	val isCompleteFailure: Boolean get() = hasFailures && successfulRequests == 0
}

internal fun <T> filterNewerCandidates(
	candidates: Iterable<T>,
	installedVersions: Map<String, Long>,
	packageName: (T) -> String,
	versionCode: (T) -> Long
): List<T> = candidates.filter { candidate ->
	val installedVersion = installedVersions[packageName(candidate)]
	installedVersion != null && versionCode(candidate) > installedVersion
}

internal fun supportsMinApi(minApi: String, deviceApi: Int): Boolean =
	runCatching { minApi.toInt() <= deviceApi }.getOrDefault(false)

internal fun supportsArchitecture(
	appArchitectures: List<String>,
	supportedAbis: List<String>,
	deviceArchitecture: String
): Boolean = when {
	appArchitectures.isEmpty() -> true
	appArchitectures.contains("universal") || appArchitectures.contains("noarch") -> true
	appArchitectures.any { supportedAbis.contains(it) } -> true
	appArchitectures.any { it.contains(deviceArchitecture) } -> true
	else -> false
}

internal fun matchesSignature(appSignatures: List<String>?, installedSignature: String?): Boolean =
	appSignatures.isNullOrEmpty() || appSignatures.contains(installedSignature)

internal fun isCompatibleFormFactor(capabilities: List<String>?, isAndroidTv: Boolean): Boolean {
	if (capabilities?.contains("wear_standalone") == true) return false
	return if (isAndroidTv) {
		capabilities?.contains("leanback_standalone") == true || capabilities?.contains("leanback") == true
	} else {
		capabilities?.contains("leanback_standalone") != true
	}
}

internal fun <T> uniqueUpdatesByPackage(
	updates: List<T>,
	packageName: (T) -> String,
	versionCode: (T) -> Long,
	sourcePriority: (T) -> Int
): List<T> = updates
	.groupBy(packageName)
	.values
	.map { candidates ->
		candidates.maxWithOrNull(compareBy<T> { versionCode(it) }.thenBy { sourcePriority(it) })!!
	}

internal fun <T> combineSourceResults(
	results: List<SourceUpdateResult<T>>,
	packageName: (T) -> String,
	versionCode: (T) -> Long,
	sourcePriority: (T) -> Int
): UpdateCheckResult<T> = UpdateCheckResult(
	updates = uniqueUpdatesByPackage(results.flatMap { it.updates }, packageName, versionCode, sourcePriority),
	successfulRequests = results.sumOf { it.successfulRequests },
	failedRequests = results.sumOf { it.failedRequests },
	sourceErrors = results.mapNotNull { result ->
		if (result.sourceName.isBlank() || (result.failedRequests <= 0 && result.failureSummary.isNullOrBlank())) {
			null
		} else {
			result.sourceName to (result.failureSummary?.takeIf { it.isNotBlank() } ?: "request failed")
		}
	}.toMap()
)
