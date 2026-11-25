package com.apkup.data.repomapping

import android.os.Build
import com.apkup.repository.RepoMappingManager
import java.util.Locale

data class RepoBinding(
    val type: String = "github", // "github", "gitlab", or "codeberg"
    val repo: String = "",       // e.g. "TeamNewPipe/NewPipe" or "videolan/vlc-android"
    val host: String? = null,    // for custom GitLab/Forgejo instances, e.g. "gitlab.com", "codeberg.org"
    val assetFilter: String? = null,      // optional flavor substring filter, e.g. "k9mail" vs "thunderbird"
    val assetFilterRegex: String? = null  // optional regex to match a specific APK flavor/name
) {
    @Suppress("SENSELESS_COMPARISON")
    val isGitHub: Boolean
        get() = (type as String?)?.equals("github", ignoreCase = true) == true

    @Suppress("SENSELESS_COMPARISON")
    val isGitLab: Boolean
        get() = (type as String?)?.equals("gitlab", ignoreCase = true) == true

    @Suppress("SENSELESS_COMPARISON")
    val isCodeberg: Boolean
        get() = (type as String?)?.let {
            it.equals("codeberg", ignoreCase = true) ||
                it.equals("forgejo", ignoreCase = true) ||
                it.equals("gitea", ignoreCase = true)
        } == true

    val resolvedHost: String
        get() = host?.takeIf { it.isNotBlank() } ?: when {
            isGitLab -> "gitlab.com"
            isCodeberg -> "codeberg.org"
            else -> "github.com"
        }

    @Suppress("SENSELESS_COMPARISON")
    val webUrl: String
        get() = "https://$resolvedHost/${(repo as String?).orEmpty().trim()}"

    @Suppress("SENSELESS_COMPARISON")
    fun canonicalKey(): String =
        "${(type as String?).orEmpty().trim().lowercase(Locale.ROOT)}:${resolvedHost.trim().lowercase(Locale.ROOT)}/${(repo as String?).orEmpty().trim().lowercase(Locale.ROOT)}"
}

object RepoUrlParser {
    fun parseRepoUrl(
        input: String,
        hintSource: String? = null,
        allowLanHosts: Boolean = false
    ): RepoBinding? = RepoMappingManager.parseRepoUrlStatic(
        input = input,
        allowLanHosts = allowLanHosts,
        hintSource = hintSource
    )
}

data class GitHubReleaseDto(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val published_at: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
) {
    @Suppress("SENSELESS_COMPARISON")
    val safeTagName: String get() = (tag_name as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeName: String get() = (name as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeBody: String get() = (body as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeAssets: List<GitHubAssetDto> get() = (assets as List<GitHubAssetDto>?).orEmpty()
}

data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    val browser_download_url: String = "",
    val content_type: String = "",
    val digest: String? = null
) {
    @Suppress("SENSELESS_COMPARISON")
    val safeName: String get() = (name as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeDownloadUrl: String get() = (browser_download_url as String?).orEmpty()
}

data class GitLabReleaseDto(
    val tag_name: String = "",
    val name: String = "",
    val description: String = "",
    val released_at: String = "",
    val assets: GitLabAssetsDto = GitLabAssetsDto()
) {
    @Suppress("SENSELESS_COMPARISON")
    val safeTagName: String get() = (tag_name as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeName: String get() = (name as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeDescription: String get() = (description as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON", "USELESS_CAST")
    val safeLinks: List<GitLabLinkDto>
        get() = ((assets as GitLabAssetsDto?)?.links as List<GitLabLinkDto>?).orEmpty()
}

data class GitLabAssetsDto(
    val count: Int = 0,
    val links: List<GitLabLinkDto> = emptyList()
)

data class GitLabLinkDto(
    val id: Long = 0L,
    val name: String = "",
    val url: String = "",
    val direct_asset_url: String = "",
    val link_type: String = ""
) {
    @Suppress("SENSELESS_COMPARISON")
    val safeName: String get() = (name as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeUrl: String get() = (url as String?).orEmpty()

    @Suppress("SENSELESS_COMPARISON")
    val safeDirectAssetUrl: String get() = (direct_asset_url as String?).orEmpty()
}

object ReleaseAssetFilter {

    const val MAX_REGEX_LENGTH = 256

    private val KNOWN_INCOMPATIBLE_ABIS = mapOf(
        "x86" to setOf("x86", "i386", "i686"),
        "x86_64" to setOf("x86_64", "x64", "amd64"),
        "armeabi-v7a" to setOf("armeabi-v7a", "armv7", "armv7a", "armeabi"),
        "arm64-v8a" to setOf("arm64-v8a", "arm64", "aarch64")
    )

    /**
     * Compiles [pattern] into a case-insensitive [Regex] after enforcing [MAX_REGEX_LENGTH] and
     * rejecting nested unbounded quantifiers (e.g. `(a+)+`, `(.*)*`) that can trigger ReDoS.
     */
    fun compileSafeRegex(pattern: String?): Regex? {
        val trimmed = pattern?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > MAX_REGEX_LENGTH) return null
        if (hasNestedQuantifierRisk(trimmed)) return null
        return runCatching { Regex(trimmed, RegexOption.IGNORE_CASE) }.getOrNull()
    }

    fun isValidRegex(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return true
        return compileSafeRegex(trimmed) != null
    }

    private fun hasNestedQuantifierRisk(pattern: String): Boolean {
        val groupHasQuantifier = ArrayDeque<Boolean>()
        var inCharClass = false
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            if (inCharClass) {
                if (ch == ']') inCharClass = false
                i++
                continue
            }
            when (ch) {
                '[' -> inCharClass = true
                '(' -> groupHasQuantifier.addLast(false)
                ')' -> {
                    val innerQuantified = if (groupHasQuantifier.isEmpty()) false else groupHasQuantifier.removeLast()
                    val (outerQuantified, nextIdx) = consumeQuantifier(pattern, i + 1)
                    if (innerQuantified && outerQuantified) return true
                    if (outerQuantified || innerQuantified) {
                        markParentQuantified(groupHasQuantifier)
                    }
                    i = nextIdx
                    continue
                }
                '*', '+' -> markParentQuantified(groupHasQuantifier)
                '{' -> {
                    val (boundedQuantified, nextIdx) = consumeBraceQuantifier(pattern, i)
                    if (boundedQuantified) {
                        markParentQuantified(groupHasQuantifier)
                        i = nextIdx
                        continue
                    }
                }
            }
            i++
        }
        return false
    }

    private fun markParentQuantified(stack: ArrayDeque<Boolean>) {
        if (stack.isNotEmpty()) {
            stack[stack.lastIndex] = true
        }
    }

    private fun consumeQuantifier(pattern: String, startIndex: Int): Pair<Boolean, Int> {
        if (startIndex >= pattern.length) return false to startIndex
        return when (pattern[startIndex]) {
            '*', '+' -> true to skipQuantifierModifier(pattern, startIndex + 1)
            '{' -> {
                val (isRepeat, nextIdx) = consumeBraceQuantifier(pattern, startIndex)
                if (isRepeat) true to skipQuantifierModifier(pattern, nextIdx) else false to startIndex
            }
            else -> false to startIndex
        }
    }

    private fun consumeBraceQuantifier(pattern: String, startIndex: Int): Pair<Boolean, Int> {
        if (startIndex >= pattern.length || pattern[startIndex] != '{') return false to startIndex
        val close = pattern.indexOf('}', startIndex + 1)
        if (close == -1) return false to startIndex
        val body = pattern.substring(startIndex + 1, close)
        val parts = body.split(',')
        if (parts.size > 2) return false to startIndex
        val min = parts[0].toIntOrNull() ?: return false to startIndex
        val max = if (parts.size == 2) {
            if (parts[1].isEmpty()) Int.MAX_VALUE else parts[1].toIntOrNull() ?: return false to startIndex
        } else {
            min
        }
        return (max > 1) to (close + 1)
    }

    private fun skipQuantifierModifier(pattern: String, index: Int): Int {
        if (index < pattern.length && (pattern[index] == '?' || pattern[index] == '+')) {
            return index + 1
        }
        return index
    }

    fun <T> findBestApk(
        assets: List<T>,
        getFilename: (T) -> String,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray(),
        requiredSubstring: String? = null,
        assetFilterRegex: String? = null,
        preferFossFlavor: Boolean = false
    ): T? {
        val core = com.apkup.core.domain.CoreEngineHolder.engine
        if (core != null && assets.isNotEmpty()) {
            val inputs = assets.mapIndexed { idx, item ->
                com.apkup.core.domain.ApkAssetInput(index = idx, filename = getFilename(item))
            }
            val pickedIdx = runCatching {
                core.selectBestApkAssetIndex(
                    assets = inputs,
                    supportedAbis = supportedAbis.toList(),
                    requiredSubstring = requiredSubstring,
                    assetFilterRegex = assetFilterRegex,
                    preferFossFlavor = preferFossFlavor
                )
            }.getOrNull()
            if (pickedIdx != null && pickedIdx in assets.indices) {
                return assets[pickedIdx]
            }
            if (pickedIdx == null) return null
        }

        val requiredLower = requiredSubstring?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val compiledRegex = if (assetFilterRegex.isNullOrBlank()) null else compileSafeRegex(assetFilterRegex) ?: return null

        val apkAssets = assets.filter { asset ->
            val fname = getFilename(asset)
            fname.endsWith(".apk", ignoreCase = true) &&
                (requiredLower == null || fname.lowercase(Locale.ROOT).contains(requiredLower)) &&
                (compiledRegex == null || compiledRegex.containsMatchIn(fname))
        }
        if (apkAssets.isEmpty()) return null

        val deviceSupportedAbis = supportedAbis.map { it.lowercase(Locale.ROOT) }
        val primaryAbi = deviceSupportedAbis.firstOrNull() ?: "arm64-v8a"

        val scored = apkAssets.mapNotNull { asset ->
            val filename = getFilename(asset).lowercase(Locale.ROOT)

            // Exclude non-installable or debug APK types (F-09)
            if (filename.endsWith("-unaligned.apk") ||
                filename.endsWith("-sources.apk") ||
                filename.endsWith("-debug.apk") ||
                filename.endsWith("_debug.apk") ||
                filename.contains("-debug-")
            ) {
                return@mapNotNull null
            }

            var detectedAbi: String? = null
            for ((canonicalAbi, aliases) in KNOWN_INCOMPATIBLE_ABIS) {
                if (aliases.any { alias ->
                        filename.contains(
                            Regex(
                                "[._-]${Regex.escape(alias)}[._-]|^${Regex.escape(alias)}[._-]|[._-]${Regex.escape(alias)}\\.apk$"
                            )
                        )
                    }
                ) {
                    detectedAbi = canonicalAbi
                    break
                }
            }

            val baseScore = if (detectedAbi != null) {
                if (detectedAbi !in deviceSupportedAbis) {
                    return@mapNotNull null
                }
                if (detectedAbi == primaryAbi) 100 else 80
            } else {
                val isExplicitUniversal = filename.contains("universal")
                if (isExplicitUniversal) 60 else 50
            }

            val flavorAdjustment = computeFlavorAdjustment(filename, preferFossFlavor)
            Pair(asset, baseScore + flavorAdjustment)
        }

        return scored.maxByOrNull { it.second }?.first
    }

    private fun computeFlavorAdjustment(lowercaseFilename: String, preferFossFlavor: Boolean): Int {
        var adjustment = 0
        if (hasFlavorToken(lowercaseFilename, "debug")) adjustment -= 60
        if (hasFlavorToken(lowercaseFilename, "huawei")) adjustment -= 25

        if (preferFossFlavor) {
            if (listOf("foss", "libre", "fdroid", "nogms").any { hasFlavorToken(lowercaseFilename, it) }) {
                adjustment += 15
            }
            if (listOf("gplay", "gms", "playstore", "proprietary").any { hasFlavorToken(lowercaseFilename, it) }) {
                adjustment -= 30
            }
        }
        return adjustment
    }

    private fun hasFlavorToken(lowercaseFilename: String, token: String): Boolean {
        val escaped = Regex.escape(token)
        return lowercaseFilename.contains(Regex("(^|[._-])$escaped([._-]|\\.apk$)"))
    }

    data class ParsedSemVer(
        val segments: List<Long>,
        val preRelease: String?
    )

    fun cleanVersionString(tagOrName: String): String {
        return tagOrName.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("release-")
            .removePrefix("Release-")
            .trim()
    }

    fun parseSemVer(versionStr: String): ParsedSemVer {
        // Strip SemVer 2.0 Section 10 build metadata (+...) before comparing precedence
        val withoutBuildMeta = cleanVersionString(versionStr).substringBefore('+').trim()
        val baseAndPre = withoutBuildMeta.split("-", limit = 2)
        val segments = baseAndPre[0].split(".")
            .mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toLongOrNull() }
        val preRelease = baseAndPre.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedSemVer(segments, preRelease)
    }

    private val PRE_RELEASE_TOKEN_REGEX = Regex(
        "(?:^|[.\\-_+\\s])(alpha|beta|rc|nightly|canary|dev|preview|snapshot|pre|experimental)\\d*(?:$|[.\\-_+\\s])",
        RegexOption.IGNORE_CASE
    )

    object PreReleaseChannel {
        const val BETA = "BETA"
        const val RC = "RC"
        const val ALPHA = "ALPHA"
        const val NIGHTLY = "NIGHTLY"
        val ALL = setOf(BETA, RC, ALPHA, NIGHTLY)
        val DEFAULT = setOf(BETA, RC)
    }

    fun classifyPreReleaseChannel(versionOrTag: String): String? {
        val match = PRE_RELEASE_TOKEN_REGEX.find(cleanVersionString(versionOrTag)) ?: return null
        val token = match.groupValues.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()
        return when {
            token == "beta" -> PreReleaseChannel.BETA
            token == "rc" || token == "pre" || token == "preview" -> PreReleaseChannel.RC
            token == "alpha" -> PreReleaseChannel.ALPHA
            token in setOf("nightly", "canary", "dev", "snapshot", "experimental") -> PreReleaseChannel.NIGHTLY
            else -> PreReleaseChannel.BETA
        }
    }

    fun isPreReleaseVersion(versionOrTag: String, isPrereleaseFlag: Boolean = false): Boolean {
        if (isPrereleaseFlag) return true
        val cleaned = cleanVersionString(versionOrTag)
        if (cleaned.isEmpty()) return false
        return PRE_RELEASE_TOKEN_REGEX.containsMatchIn(cleaned)
    }

    fun isPreReleaseAllowedByChannels(
        versionOrTag: String,
        allowedChannels: Set<String>,
        isPrereleaseFlag: Boolean = false
    ): Boolean {
        if (!isPreReleaseVersion(versionOrTag, isPrereleaseFlag)) return true
        if (allowedChannels.isEmpty()) return false
        val channel = classifyPreReleaseChannel(versionOrTag) ?: PreReleaseChannel.BETA
        return channel in allowedChannels
    }

    fun isBareBuildNumberTag(parsed: ParsedSemVer): Boolean =
        parsed.segments.size == 1 && parsed.segments[0] >= 100_000L

    fun isNewer(currentVersion: String, candidateTag: String): Boolean {
        val core = com.apkup.core.domain.CoreEngineHolder.engine
        if (core != null) {
            return runCatching { core.isNewerVersion(currentVersion, candidateTag) }
                .getOrElse { isNewerKotlin(currentVersion, candidateTag) }
        }
        return isNewerKotlin(currentVersion, candidateTag)
    }

    fun evaluateBatch(
        requests: List<com.apkup.core.domain.BatchVersionEvalInput>
    ): Map<String, com.apkup.core.domain.BatchVersionEvalOutput> {
        if (requests.isEmpty()) return emptyMap()
        val startedNs = System.nanoTime()
        val core = com.apkup.core.domain.CoreEngineHolder.engine
        val results = if (core != null) {
            runCatching { core.evaluateUpdateCandidatesBatch(requests) }
                .getOrElse { evaluateBatchKotlin(requests) }
        } else {
            evaluateBatchKotlin(requests)
        }
        val micros = ((System.nanoTime() - startedNs) / 1000L).coerceAtLeast(1L)
        com.apkup.core.domain.EngineTelemetryRegistry.recordVersionBatch(micros, requests.size)
        return results.associateBy { it.id }
    }

    private fun evaluateBatchKotlin(
        requests: List<com.apkup.core.domain.BatchVersionEvalInput>
    ): List<com.apkup.core.domain.BatchVersionEvalOutput> = requests.map { req ->
        val cleaned = cleanVersionString(req.candidateTag)
        val pre = isPreReleaseVersion(req.candidateTag, false)
        com.apkup.core.domain.BatchVersionEvalOutput(
            id = req.id,
            cleanedCandidate = cleaned,
            isNewer = isNewerKotlin(req.currentVersion, cleaned),
            isPreRelease = pre,
            preReleaseChannel = if (pre) classifyPreReleaseChannel(req.candidateTag) else null
        )
    }

    internal fun isNewerKotlin(currentVersion: String, candidateTag: String): Boolean {
        if (currentVersion.isBlank()) return cleanVersionString(candidateTag).isNotBlank()
        val current = parseSemVer(currentVersion)
        val candidate = parseSemVer(candidateTag)
        if (candidate.segments.isEmpty()) return false

        // Guard against bare date/build-number tags (e.g. "v2026072802") falsely beating a
        // multi-segment semantic version (e.g. "2.4.0-261014").
        if (current.segments.size >= 2 && current.segments[0] < 10_000L && isBareBuildNumberTag(candidate)) {
            return false
        }

        val maxLen = maxOf(current.segments.size, candidate.segments.size)
        for (i in 0 until maxLen) {
            val c = current.segments.getOrElse(i) { 0L }
            val n = candidate.segments.getOrElse(i) { 0L }
            if (n != c) return n > c
        }

        // A final release is newer than a pre-release with same numeric segments (SemVer 2.0 Section 11.3)
        if (current.preRelease != null && candidate.preRelease == null) return true
        if (current.preRelease == null && candidate.preRelease != null) return false

        if (current.preRelease != null && candidate.preRelease != null) {
            return comparePreRelease(candidate.preRelease, current.preRelease) > 0
        }

        return false
    }

    internal fun comparePreRelease(candidate: String, current: String): Int {
        val candTokens = candidate.split('.', '-', '_').filter { it.isNotEmpty() }
        val currTokens = current.split('.', '-', '_').filter { it.isNotEmpty() }
        val maxTokens = maxOf(candTokens.size, currTokens.size)
        for (i in 0 until maxTokens) {
            // SemVer 2.0 Section 11.4.4: larger set of pre-release fields has higher precedence
            val cTok = candTokens.getOrNull(i) ?: return -1
            val curTok = currTokens.getOrNull(i) ?: return 1
            val cNum = cTok.toLongOrNull()
            val curNum = curTok.toLongOrNull()
            if (cNum != null && curNum != null) {
                if (cNum != curNum) return cNum.compareTo(curNum)
            } else if (cNum != null && curNum == null) {
                // SemVer 2.0 Section 11.4.1: Numeric identifiers always have lower precedence than non-numeric
                return -1
            } else if (cNum == null && curNum != null) {
                return 1
            } else {
                val cNumEmbedded = Regex("\\d+").find(cTok)?.value?.toLongOrNull()
                val curNumEmbedded = Regex("\\d+").find(curTok)?.value?.toLongOrNull()
                val cType = cTok.replace(Regex("\\d+"), "").lowercase(Locale.ROOT)
                val curType = curTok.replace(Regex("\\d+"), "").lowercase(Locale.ROOT)
                if (cType != curType) {
                    val cmp = cType.compareTo(curType)
                    if (cmp != 0) return cmp
                } else if (cNumEmbedded != null && curNumEmbedded != null && cNumEmbedded != curNumEmbedded) {
                    return cNumEmbedded.compareTo(curNumEmbedded)
                } else if (cTok != curTok) {
                    val cmp = cTok.compareTo(curTok, ignoreCase = true)
                    if (cmp != 0) return cmp
                }
            }
        }
        return 0
    }
}
