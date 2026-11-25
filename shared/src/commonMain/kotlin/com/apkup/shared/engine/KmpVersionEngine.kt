package com.apkup.shared.engine

import com.apkup.shared.platform.nanoTime

data class KmpParsedSemVer(
    val segments: List<Long>,
    val preRelease: String?
)

data class KmpBatchVersionRequest(
    val id: String,
    val currentVersion: String,
    val candidateTag: String
)

data class KmpBatchVersionResult(
    val id: String,
    val cleanedCandidate: String,
    val isNewer: Boolean,
    val isPreRelease: Boolean,
    val preReleaseChannel: String?
)

object KmpPreReleaseChannel {
    const val BETA = "BETA"
    const val RC = "RC"
    const val ALPHA = "ALPHA"
    const val NIGHTLY = "NIGHTLY"
    val ALL = setOf(BETA, RC, ALPHA, NIGHTLY)
    val DEFAULT = setOf(BETA, RC)
}

object KmpVersionEngine {

    private val PRE_RELEASE_TOKEN_REGEX = Regex(
        "(?:^|[.\\-_+\\s])(alpha|beta|rc|nightly|canary|dev|preview|snapshot|pre|experimental)\\d*(?:$|[.\\-_+\\s])",
        RegexOption.IGNORE_CASE
    )
    private val DIGITS_REGEX = Regex("\\d+")

    fun cleanVersionString(tagOrName: String): String = tagOrName.trim()
        .removePrefix("v")
        .removePrefix("V")
        .removePrefix("release-")
        .removePrefix("Release-")
        .trim()

    fun parseSemVer(versionStr: String): KmpParsedSemVer {
        val withoutBuildMeta = cleanVersionString(versionStr).substringBefore('+').trim()
        val baseAndPre = withoutBuildMeta.split("-", limit = 2)
        val segments = baseAndPre[0].split(".")
            .mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toLongOrNull() }
        val preRelease = baseAndPre.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return KmpParsedSemVer(segments, preRelease)
    }

    fun classifyPreReleaseChannel(versionOrTag: String): String? {
        val match = PRE_RELEASE_TOKEN_REGEX.find(cleanVersionString(versionOrTag)) ?: return null
        val token = match.groupValues.getOrNull(1)?.lowercase().orEmpty()
        return when {
            token == "beta" -> KmpPreReleaseChannel.BETA
            token == "rc" || token == "pre" || token == "preview" -> KmpPreReleaseChannel.RC
            token == "alpha" -> KmpPreReleaseChannel.ALPHA
            token in setOf("nightly", "canary", "dev", "snapshot", "experimental") -> KmpPreReleaseChannel.NIGHTLY
            else -> KmpPreReleaseChannel.BETA
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
        val channel = classifyPreReleaseChannel(versionOrTag) ?: KmpPreReleaseChannel.BETA
        return channel in allowedChannels
    }

    fun isBareBuildNumberTag(parsed: KmpParsedSemVer): Boolean =
        parsed.segments.size == 1 && parsed.segments[0] >= 100_000L

    fun isNewer(currentVersion: String, candidateTag: String): Boolean {
        if (currentVersion.isBlank()) return cleanVersionString(candidateTag).isNotBlank()
        val current = parseSemVer(currentVersion)
        val candidate = parseSemVer(candidateTag)
        if (candidate.segments.isEmpty()) return false

        if (current.segments.size >= 2 && current.segments[0] < 10_000L && isBareBuildNumberTag(candidate)) {
            return false
        }

        val maxLen = maxOf(current.segments.size, candidate.segments.size)
        for (i in 0 until maxLen) {
            val c = current.segments.getOrElse(i) { 0L }
            val n = candidate.segments.getOrElse(i) { 0L }
            if (n != c) return n > c
        }

        if (current.preRelease != null && candidate.preRelease == null) return true
        if (current.preRelease == null && candidate.preRelease != null) return false
        if (current.preRelease != null && candidate.preRelease != null) {
            return comparePreRelease(candidate.preRelease, current.preRelease) > 0
        }
        return false
    }

    fun comparePreRelease(candidate: String, current: String): Int {
        val candTokens = candidate.split('.', '-', '_').filter { it.isNotEmpty() }
        val currTokens = current.split('.', '-', '_').filter { it.isNotEmpty() }
        val maxTokens = maxOf(candTokens.size, currTokens.size)
        for (i in 0 until maxTokens) {
            val cTok = candTokens.getOrNull(i) ?: return -1
            val curTok = currTokens.getOrNull(i) ?: return 1
            val cNum = cTok.toLongOrNull()
            val curNum = curTok.toLongOrNull()
            if (cNum != null && curNum != null) {
                if (cNum != curNum) return cNum.compareTo(curNum)
            } else if (cNum != null && curNum == null) {
                return -1
            } else if (cNum == null && curNum != null) {
                return 1
            } else {
                val cNumEmbedded = DIGITS_REGEX.find(cTok)?.value?.toLongOrNull()
                val curNumEmbedded = DIGITS_REGEX.find(curTok)?.value?.toLongOrNull()
                val cType = cTok.replace(DIGITS_REGEX, "").lowercase()
                val curType = curTok.replace(DIGITS_REGEX, "").lowercase()
                if (cType != curType) {
                    val cmp = cType.compareTo(curType)
                    if (cmp != 0) return cmp
                } else if (cNumEmbedded != null && curNumEmbedded != null && cNumEmbedded != curNumEmbedded) {
                    return cNumEmbedded.compareTo(curNumEmbedded)
                } else if (!cTok.equals(curTok, ignoreCase = true)) {
                    val cmp = cTok.lowercase().compareTo(curTok.lowercase())
                    if (cmp != 0) return cmp
                }
            }
        }
        return 0
    }

    fun evaluateBatch(requests: List<KmpBatchVersionRequest>): List<KmpBatchVersionResult> {
        if (requests.isEmpty()) return emptyList()
        val startedNs = nanoTime()
        val results = requests.map { req ->
            val cleaned = cleanVersionString(req.candidateTag)
            val pre = isPreReleaseVersion(req.candidateTag, false)
            KmpBatchVersionResult(
                id = req.id,
                cleanedCandidate = cleaned,
                isNewer = isNewer(req.currentVersion, cleaned),
                isPreRelease = pre,
                preReleaseChannel = if (pre) classifyPreReleaseChannel(req.candidateTag) else null
            )
        }
        val elapsedMicros = ((nanoTime() - startedNs) / 1000L).coerceAtLeast(1L)
        KmpTelemetryRegistry.recordVersionBatch(elapsedMicros, requests.size)
        return results
    }
}
