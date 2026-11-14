package com.apkup.data.ui

import java.util.Locale

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
    runCatching { minApi.trim().toInt() <= deviceApi }.getOrDefault(false)

internal fun canonicalizeAbi(raw: String): String {
    val normalized = raw.trim().lowercase(Locale.ROOT)
        .replace('_', '-')
    return when (normalized) {
        "universal", "noarch", "any", "all", "arm64-v8a+armeabi-v7a" -> "universal"
        "arm64-v8a", "arm64", "aarch64", "arm64-v8" -> "arm64-v8a"
        "armeabi-v7a", "armeabi-v7", "armv7", "armv7a", "arm-v7a", "arm" -> "armeabi-v7a"
        "armeabi" -> "armeabi"
        "x86-64", "amd64", "x64" -> "x86_64"
        "x86", "i686", "i386", "ia32" -> "x86"
        else -> raw.trim().lowercase(Locale.ROOT)
    }
}

/**
 * Exact canonical ABI matching (HIGH-09): prevents substring collisions such as `"x86_64".contains("x86")`
 * or `"arm64-v8a".contains("arm")` from selecting 64-bit APKs on 32-bit devices.
 */
internal fun supportsArchitecture(
    appArchitectures: List<String>,
    supportedAbis: List<String>,
    deviceArchitecture: String
): Boolean {
    if (appArchitectures.isEmpty()) return true
    val canonicalCandidateAbis = appArchitectures
        .flatMap { it.split(',', '+', '/') }
        .map { canonicalizeAbi(it) }
        .filter { it.isNotEmpty() }
        .toSet()

    if (canonicalCandidateAbis.isEmpty() || "universal" in canonicalCandidateAbis) {
        return true
    }

    val canonicalSupported = buildSet {
        supportedAbis.mapTo(this) { canonicalizeAbi(it) }
        if (deviceArchitecture.isNotBlank()) {
            add(canonicalizeAbi(deviceArchitecture))
        }
    }
    return canonicalCandidateAbis.any { it in canonicalSupported }
}

internal fun matchesSignature(appSignatures: List<String>?, installedSignature: String?): Boolean {
    if (appSignatures.isNullOrEmpty()) return true
    if (installedSignature.isNullOrBlank()) return false
    return appSignatures.any { it.equals(installedSignature, ignoreCase = true) }
}

internal fun isCompatibleFormFactor(capabilities: List<String>?, isAndroidTv: Boolean = false): Boolean {
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
