package com.apkup.repository

import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.selfupdate.GitHubRelease
import com.apkup.data.selfupdate.SelfUpdateInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class SelfUpdateRepository(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/DurchEins/apkup/releases/latest"
    }

    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): Result<SelfUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "ApkUp-SelfUpdater")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} when checking GitHub releases")
                }
                val body = response.body
                val release = gson.fromJson(body.charStream(), GitHubRelease::class.java)
                    ?: throw IOException("Could not parse GitHub release JSON")

                val candidateTag = release.tag_name.ifBlank { release.name }
                if (candidateTag.isBlank()) {
                    return@runCatching null
                }

                if (!isNewerVersion(currentVersionName, candidateTag)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SelfUpdateRepository",
                            "Current version $currentVersionName is up to date against candidate $candidateTag"
                        )
                    }
                    return@runCatching null
                }

                val apkAsset = release.assets.firstOrNull {
                    it.name.endsWith(".apk", ignoreCase = true)
                } ?: run {
                    Log.w("SelfUpdateRepository", "Newer release $candidateTag found, but no APK asset attached.")
                    return@runCatching null
                }

                SelfUpdateInfo(
                    versionName = candidateTag.trim().removePrefix("v").removePrefix("V"),
                    tagName = candidateTag,
                    releaseNotes = release.body,
                    downloadUrl = apkAsset.browser_download_url,
                    apkSize = apkAsset.size
                )
            }
        }
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

    private fun comparePreRelease(candidate: String, current: String): Int {
        val cNum = Regex("\\d+").find(candidate)?.value?.toIntOrNull() ?: 0
        val curNum = Regex("\\d+").find(current)?.value?.toIntOrNull() ?: 0
        val cType = candidate.replace(Regex("\\d+"), "").lowercase()
        val curType = current.replace(Regex("\\d+"), "").lowercase()
        if (cType != curType) {
            return cType.compareTo(curType)
        }
        return cNum.compareTo(curNum)
    }
}
