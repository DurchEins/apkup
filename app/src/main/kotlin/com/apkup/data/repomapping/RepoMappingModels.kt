package com.apkup.data.repomapping

import android.os.Build

data class RepoBinding(
    val type: String = "github", // "github" or "gitlab"
    val repo: String = "",       // e.g. "TeamNewPipe/NewPipe" or "videolan/vlc-android"
    val host: String? = null     // for custom GitLab instances, e.g. "gitlab.com"
) {
    val isGitHub: Boolean
        get() = type.equals("github", ignoreCase = true)

    val isGitLab: Boolean
        get() = type.equals("gitlab", ignoreCase = true)

    val resolvedHost: String
        get() = host?.takeIf { it.isNotBlank() } ?: if (isGitLab) "gitlab.com" else "github.com"
}

data class GitHubReleaseDto(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    val browser_download_url: String = "",
    val content_type: String = ""
)

data class GitLabReleaseDto(
    val tag_name: String = "",
    val name: String = "",
    val description: String = "",
    val released_at: String = "",
    val assets: GitLabAssetsDto = GitLabAssetsDto()
)

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
)

object ReleaseAssetFilter {

    private val KNOWN_INCOMPATIBLE_ABIS = mapOf(
        "x86" to setOf("x86", "i386", "i686"),
        "x86_64" to setOf("x86_64", "x64", "amd64"),
        "armeabi-v7a" to setOf("armeabi-v7a", "armv7", "armv7a", "armeabi"),
        "arm64-v8a" to setOf("arm64-v8a", "arm64", "aarch64")
    )

    fun <T> findBestApk(
        assets: List<T>,
        getFilename: (T) -> String,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
    ): T? {
        val apkAssets = assets.filter { getFilename(it).endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        val deviceSupportedAbis = supportedAbis.map { it.lowercase() }
        val primaryAbi = deviceSupportedAbis.firstOrNull() ?: "arm64-v8a"

        val scored = apkAssets.mapNotNull { asset ->
            val filename = getFilename(asset).lowercase()

            // Exclude common non-installable APK types (debug, unaligned, sources)
            if (filename.endsWith("-unaligned.apk") || filename.endsWith("-sources.apk")) {
                return@mapNotNull null
            }

            var detectedAbi: String? = null
            for ((canonicalAbi, aliases) in KNOWN_INCOMPATIBLE_ABIS) {
                if (aliases.any { alias ->
                    filename.contains(Regex("[._-]${Regex.escape(alias)}[._-]|^${Regex.escape(alias)}[._-]|[._-]${Regex.escape(alias)}\\.apk$"))
                }) {
                    detectedAbi = canonicalAbi
                    break
                }
            }

            if (detectedAbi != null) {
                // If this APK is for an ABI not supported by this device, discard
                if (detectedAbi !in deviceSupportedAbis) {
                    return@mapNotNull null
                }
                // Determine priority
                val score = if (detectedAbi == primaryAbi) 100 else 80
                Pair(asset, score)
            } else {
                // Universal or un-suffixed APK
                val isExplicitUniversal = filename.contains("universal")
                val score = if (isExplicitUniversal) 60 else 50
                Pair(asset, score)
            }
        }

        return scored.maxByOrNull { it.second }?.first
    }

    data class ParsedSemVer(
        val segments: List<Int>,
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
        val clean = cleanVersionString(versionStr)
        val baseAndPre = clean.split("-", limit = 2)
        val segments = baseAndPre[0].split(".")
            .mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val preRelease = baseAndPre.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedSemVer(segments, preRelease)
    }

    fun isNewer(currentVersion: String, candidateTag: String): Boolean {
        val current = parseSemVer(currentVersion)
        val candidate = parseSemVer(candidateTag)

        val maxLen = maxOf(current.segments.size, candidate.segments.size)
        for (i in 0 until maxLen) {
            val c = current.segments.getOrElse(i) { 0 }
            val n = candidate.segments.getOrElse(i) { 0 }
            if (n != c) return n > c
        }

        // A final release is newer than a pre-release with same numeric segments
        if (current.preRelease != null && candidate.preRelease == null) return true
        if (current.preRelease == null && candidate.preRelease != null) return false

        if (current.preRelease != null && candidate.preRelease != null) {
            return candidate.preRelease > current.preRelease
        }

        return false
    }
}
