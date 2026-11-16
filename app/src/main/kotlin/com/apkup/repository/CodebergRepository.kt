package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.repomapping.GitHubReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.Link
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.prefs.Prefs
import com.apkup.util.ApkIntegrityVerifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks Codeberg (`codeberg.org`) and self-hosted Forgejo/Gitea repositories
 * via the v1 Releases REST API (`/api/v1/repos/{owner}/{repo}/releases`).
 */
class CodebergRepository(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs,
    private val repoMappingManager: RepoMappingManager? = null
) {
    companion object {
        private const val TAG = "CodebergRepository"
        private const val BATCH_NOTIFY_SIZE = 10
    }

    private data class CachedRelease(
        val etag: String?,
        val release: GitHubReleaseDto,
        val timestamp: Long
    )

    private val releaseCache = ConcurrentHashMap<String, CachedRelease>()

    fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ): Flow<SourceUpdateResult<AppUpdate>> = flow {
        val startedAt = SystemClock.elapsedRealtime()
        val mappedApps = apps.mapNotNull { app ->
            val binding = repoMappingManager?.getCompatibleBinding(app.packageName, app.signatureSha256)
            if (binding != null && binding.isCodeberg) {
                app to binding
            } else {
                null
            }
        }

        Log.i(TAG, "Checking Codeberg/Forgejo updates for ${mappedApps.size} mapped apps (out of ${apps.size})")
        val updates = mutableListOf<AppUpdate>()
        var successfulRequests = 0
        var failedRequests = 0
        var firstFailureReason: String? = null

        apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for (app in batch) {
                val binding = repoMappingManager?.getCompatibleBinding(app.packageName, app.signatureSha256)
                if (binding != null && binding.isCodeberg) {
                    val isManualCustom = repoMappingManager?.hasCustomBinding(app.packageName) == true
                    if (!RepoHostValidator.isAllowedRepoHost(binding.resolvedHost, allowLanHosts = isManualCustom)) {
                        continue
                    }
                    try {
                        val allowPreReleases = prefs.shouldAllowPreReleases(app.packageName)
                        val release = fetchLatestRelease(
                            binding = binding,
                            allowLanHost = isManualCustom,
                            includePreReleases = allowPreReleases
                        )
                        successfulRequests++
                        if (release != null) {
                            val candidateTag = release.safeTagName.ifBlank { release.safeName }
                            val cleanCandidate = ReleaseAssetFilter.cleanVersionString(candidateTag)

                            if (ReleaseAssetFilter.isNewer(app.version, cleanCandidate)) {
                                val bestApk = ReleaseAssetFilter.findBestApk(
                                    assets = release.safeAssets,
                                    getFilename = { it.safeName },
                                    requiredSubstring = binding.assetFilter,
                                    assetFilterRegex = binding.assetFilterRegex,
                                    preferFossFlavor = app.installOrigin == InstallOrigin.FDROID
                                )
                                val downloadUrl = bestApk?.safeDownloadUrl.orEmpty()
                                val downloadUri = runCatching { URI.create(downloadUrl) }.getOrNull()
                                val downloadHost = downloadUri?.host
                                if (bestApk != null &&
                                    downloadUrl.startsWith("https://", ignoreCase = true) &&
                                    downloadUri != null &&
                                    downloadUri.userInfo == null &&
                                    (downloadUri.port == -1 || downloadUri.port == 443) &&
                                    RepoHostValidator.isAllowedRepoHost(downloadHost, allowLanHosts = isManualCustom)
                                ) {
                                    val expectedSha256 = ApkIntegrityVerifier.normalizeSha256Hex(bestApk.digest)
                                    val update = AppUpdate(
                                        name = app.name,
                                        packageName = app.packageName,
                                        version = cleanCandidate,
                                        oldVersion = app.version,
                                        versionCode = maxOf(1L, app.versionCode + 1),
                                        oldVersionCode = app.versionCode,
                                        source = CodebergSource,
                                        link = Link.Url(
                                            link = downloadUrl,
                                            size = bestApk.size,
                                            expectedSha256 = expectedSha256
                                        ),
                                        whatsNew = release.safeBody,
                                        totalSize = bestApk.size,
                                        updatedOn = (release.published_at as String?).orEmpty(),
                                        installOrigin = app.installOrigin,
                                        installerPackageName = app.installerPackageName
                                    )
                                    if (update.isMeaningfulVersionUpgrade()) {
                                        updates.add(update)
                                        Log.i(TAG, "Valid Codeberg update found for ${app.packageName}: v$cleanCandidate (${bestApk.safeName})")
                                    }
                                }
                            } else if (BuildConfig.DEBUG) {
                                Log.d(TAG, "App ${app.packageName} is up to date against Codeberg release $cleanCandidate")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        failedRequests++
                        if (firstFailureReason == null) {
                            firstFailureReason = e.message ?: e.javaClass.simpleName
                        }
                        Log.w(TAG, "Failed Codeberg check for ${app.packageName} (${binding.webUrl}): ${e.message}")
                    }
                }
            }
            onBatchSuccess(batch.map { it.packageName })
        }

        Log.i(TAG, "Found ${updates.size} Codeberg/Forgejo update(s) in ${SystemClock.elapsedRealtime() - startedAt} ms")
        emit(
            SourceUpdateResult(
                updates = updates,
                successfulRequests = if (successfulRequests == 0 && failedRequests == 0) 1 else successfulRequests,
                failedRequests = failedRequests,
                failureSummary = if (failedRequests > 0) (firstFailureReason ?: "Codeberg request failed") else null
            )
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e(TAG, "Codeberg/Forgejo update check failed", error)
        emit(
            SourceUpdateResult(
                updates = emptyList(),
                successfulRequests = 0,
                failedRequests = 1,
                failureSummary = error.message ?: "network error"
            )
        )
    }

    internal suspend fun fetchLatestRelease(
        binding: RepoBinding,
        allowLanHost: Boolean = false,
        includePreReleases: Boolean = false
    ): GitHubReleaseDto? = withContext(Dispatchers.IO) {
        val host = binding.resolvedHost.trim().lowercase(Locale.ROOT)
        if (!RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = allowLanHost)) {
            return@withContext null
        }
        val rawParts = binding.repo.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (rawParts.size != 2) return@withContext null
        val owner = rawParts[0]
        val repoName = rawParts[1].removeSuffix(".git")
        if (!RepoHostValidator.isValidRepoSlugSegments(listOf(owner, repoName))) {
            return@withContext null
        }
        val repo = "$owner/$repoName"

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(repoName)
            .addPathSegment("releases")
            .addQueryParameter("limit", "5")
            .build()
        val cacheKey = if (includePreReleases) "${binding.canonicalKey()}#pre" else binding.canonicalKey()
        val cached = releaseCache[cacheKey]

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ApkUp")

        val rawToken = prefs.codebergToken.get().trim()
        val token = rawToken.filter { it in '!'..'~' }
        if (token.isNotBlank() && (host == "codeberg.org" || host.endsWith(".codeberg.org"))) {
            requestBuilder.header("Authorization", "token $token")
        }

        if (cached?.etag != null) {
            requestBuilder.header("If-None-Match", cached.etag)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && cached != null) {
                return@withContext cached.release
            }
            if (response.code == 404) {
                Log.w(TAG, "Repository or releases not found for $host/$repo (HTTP 404)")
                return@withContext null
            }
            if (response.code == 401 || response.code == 403) {
                Log.w(TAG, "Codeberg/Forgejo auth or rate limit (${response.code}) for $host/$repo")
                throw IOException("HTTP ${response.code} when fetching Codeberg/Forgejo releases for $repo")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} when fetching Codeberg/Forgejo releases for $repo")
            }

            val etag = response.header("ETag")
            val listType = object : TypeToken<List<GitHubReleaseDto>>() {}.type
            val releases: List<GitHubReleaseDto> = runCatching {
                gson.fromJson<List<GitHubReleaseDto>>(response.body.charStream(), listType)
            }.getOrNull().orEmpty()

            val chosen = if (includePreReleases) {
                releases.firstOrNull { !it.draft && it.safeAssets.any { a -> a.safeName.endsWith(".apk", ignoreCase = true) } }
                    ?: releases.firstOrNull { !it.draft }
            } else {
                releases.firstOrNull { !it.draft && !it.prerelease && it.safeAssets.any { a -> a.safeName.endsWith(".apk", ignoreCase = true) } }
                    ?: releases.firstOrNull { !it.draft && !it.prerelease }
            }

            if (chosen != null) {
                releaseCache[cacheKey] = CachedRelease(etag, chosen, System.currentTimeMillis())
            }
            chosen
        }
    }
}
