package com.apkup.repository

import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.selfupdate.GitHubAsset
import com.apkup.data.selfupdate.GitHubRelease
import com.apkup.data.selfupdate.SelfUpdateInfo
import com.apkup.util.ApkIntegrityVerifier
import com.apkup.util.readUtf8Bounded
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

class SelfUpdateRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val releasesEndpointUrl: String = GITHUB_RELEASES_LIST_URL
) {
    companion object {
        const val GITHUB_RELEASES_LIST_URL =
            "https://api.github.com/repos/DurchEins/apkup/releases?per_page=20"
        private const val MAX_SHA256_SIDECAR_BYTES = 4096
        private const val MAX_RELEASES_JSON_BYTES = 4 * 1024 * 1024 // 4 MB cap
        private val ALLOWED_SELF_UPDATE_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )
    }

    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        includePreReleases: Boolean = false
    ): Result<SelfUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(releasesEndpointUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} when checking GitHub releases")
                }
                val rawJson = response.body.byteStream().use { input ->
                    readUtf8Bounded(input, MAX_RELEASES_JSON_BYTES)
                }
                val releases = parseReleasesPayload(rawJson)
                val bestCandidate = selectNewestEligibleRelease(
                    releases = releases,
                    currentVersionName = currentVersionName,
                    includePreReleases = includePreReleases
                ) ?: return@withContext Result.success(null)

                val (release, candidateTag, apkAsset) = bestCandidate
                val sha256 = resolveAssetSha256(release, apkAsset)
                val isPreRelease = isPreReleaseRelease(release, candidateTag)

                Result.success(
                    SelfUpdateInfo(
                        versionName = candidateTag.trim().removePrefix("v").removePrefix("V"),
                        tagName = candidateTag,
                        releaseNotes = release.safeBody,
                        downloadUrl = apkAsset.safeDownloadUrl,
                        apkSize = apkAsset.size,
                        expectedSha256 = sha256,
                        isPreRelease = isPreRelease
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    internal fun parseReleasesPayload(rawJson: String): List<GitHubRelease> {
        val element: JsonElement = gson.fromJson(rawJson, JsonElement::class.java)
            ?: throw IOException("Could not parse GitHub release JSON")
        return when {
            element.isJsonArray -> {
                val listType = object : TypeToken<List<GitHubRelease>>() {}.type
                gson.fromJson<List<GitHubRelease>>(element, listType).orEmpty()
            }
            element.isJsonObject -> {
                listOfNotNull(gson.fromJson(element, GitHubRelease::class.java))
            }
            else -> emptyList()
        }
    }

    internal fun isPreReleaseRelease(release: GitHubRelease, candidateTag: String): Boolean =
        release.prerelease || parseVersion(candidateTag).preRelease != null

    internal fun selectNewestEligibleRelease(
        releases: List<GitHubRelease>,
        currentVersionName: String,
        includePreReleases: Boolean = false
    ): Triple<GitHubRelease, String, GitHubAsset>? {
        var best: Triple<GitHubRelease, String, GitHubAsset>? = null

        for (release in releases) {
            if (release.draft) continue
            val candidateTag = release.safeTagName.ifBlank { release.safeName }
            if (candidateTag.isBlank()) continue
            val isPreRelease = isPreReleaseRelease(release, candidateTag)
            if (isPreRelease && !includePreReleases) continue
            if (!isNewerVersion(currentVersionName, candidateTag)) continue

            val apkAsset = release.safeAssets.firstOrNull { asset ->
                val lower = asset.safeName.lowercase(Locale.ROOT)
                lower.endsWith(".apk") &&
                    !lower.endsWith("-debug.apk") &&
                    !lower.endsWith("-unaligned.apk") &&
                    isTrustedDownloadUrl(asset.safeDownloadUrl)
            } ?: continue

            val currentBest = best
            if (currentBest == null || isNewerVersion(currentBest.second, candidateTag)) {
                best = Triple(release, candidateTag, apkAsset)
            }
        }
        return best
    }

    internal fun isTrustedDownloadUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { java.net.URI.create(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return host in ALLOWED_SELF_UPDATE_HOSTS
    }

    private fun resolveAssetSha256(release: GitHubRelease, apkAsset: GitHubAsset): String? {
        ApkIntegrityVerifier.normalizeSha256Hex(apkAsset.digest)?.let { return it }

        val sidecar = release.safeAssets.firstOrNull { asset ->
            asset.safeName.equals("${apkAsset.safeName}.sha256", ignoreCase = true) ||
                asset.safeName.equals("${apkAsset.safeName}.sha256sum", ignoreCase = true)
        } ?: release.safeAssets.firstOrNull { asset ->
            asset.safeName.endsWith(".sha256", ignoreCase = true) ||
                asset.safeName.endsWith(".sha256sum", ignoreCase = true)
        } ?: return null

        if (!isTrustedDownloadUrl(sidecar.safeDownloadUrl)) return null
        return runCatching {
            val req = Request.Builder()
                .url(sidecar.safeDownloadUrl)
                .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val snippet = resp.body.byteStream().use { input ->
                    readUtf8Bounded(input, MAX_SHA256_SIDECAR_BYTES)
                }
                val token = Regex("\\b([0-9a-fA-F]{64})\\b").find(snippet)?.groupValues?.getOrNull(1)
                ApkIntegrityVerifier.normalizeSha256Hex(token)
            }
        }.getOrNull()
    }

    internal data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String?,
        val buildNumber: Int = 0
    )

    internal fun parseVersion(versionStr: String): ParsedVersion {
        val buildNum = Regex("\\((\\d+)\\)").find(versionStr)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val clean = versionStr
            .substringBefore("(")
            .substringBefore("+")
            .trim()
            .removePrefix("ApkUp-")
            .removePrefix("apkup-")
            .removePrefix("v")
            .removePrefix("V")
            .trim()
            .trimEnd('-')
        val baseAndPre = clean.split("-", limit = 2)
        val baseParts = baseAndPre[0].trim().split(".")
        val major = baseParts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val minor = baseParts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        val patch = baseParts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
        val preRelease = baseAndPre.getOrNull(1)?.trim()?.trimEnd('-')?.takeIf { it.isNotEmpty() }
        return ParsedVersion(major, minor, patch, preRelease, buildNum)
    }

    internal fun isNewerVersion(currentVersionName: String, candidateTagName: String): Boolean {
        val current = parseVersion(currentVersionName)
        val candidate = parseVersion(candidateTagName)

        if (candidate.major != current.major) return candidate.major > current.major
        if (candidate.minor != current.minor) return candidate.minor > current.minor
        if (candidate.patch != current.patch) return candidate.patch > current.patch

        if (current.preRelease != null && candidate.preRelease == null) return true
        if (current.preRelease == null && candidate.preRelease != null) return false

        if (current.preRelease != null && candidate.preRelease != null) {
            val preCmp = comparePreRelease(candidate.preRelease, current.preRelease)
            if (preCmp != 0) return preCmp > 0
        }

        if (current.buildNumber > 0 && candidate.buildNumber > 0 && candidate.buildNumber != current.buildNumber) {
            return candidate.buildNumber > current.buildNumber
        }

        return false
    }

    private fun preReleaseRank(type: String): Int? = when (type) {
        "dev", "nightly", "canary", "snapshot" -> 0
        "alpha", "a" -> 1
        "beta", "b" -> 2
        "rc" -> 3
        else -> null
    }

    private fun comparePreRelease(candidate: String, current: String): Int {
        val cType = candidate.replace(Regex("\\d+"), "").trim('.', '-', '_').lowercase(Locale.ROOT)
        val curType = current.replace(Regex("\\d+"), "").trim('.', '-', '_').lowercase(Locale.ROOT)
        if (cType != curType) {
            val cRank = preReleaseRank(cType)
            val curRank = preReleaseRank(curType)
            return if (cRank != null && curRank != null && cRank != curRank) {
                cRank.compareTo(curRank)
            } else {
                cType.compareTo(curType)
            }
        }
        val cNums = Regex("\\d+").findAll(candidate).mapNotNull { it.value.toIntOrNull() }.toList()
        val curNums = Regex("\\d+").findAll(current).mapNotNull { it.value.toIntOrNull() }.toList()
        val maxLen = maxOf(cNums.size, curNums.size)
        for (i in 0 until maxLen) {
            val cmp = cNums.getOrElse(i) { 0 }.compareTo(curNums.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}
