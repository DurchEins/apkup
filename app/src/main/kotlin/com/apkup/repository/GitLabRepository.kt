package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.repomapping.GitLabLinkDto
import com.apkup.data.repomapping.GitLabReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.prefs.Prefs
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class GitLabRepository(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs,
    private val repoMappingManager: RepoMappingManager,
    private val cacheDirectory: File? = null
) {
    companion object {
        private const val BATCH_NOTIFY_SIZE = 10
    }

    private data class CachedRelease(
        val etag: String?,
        val release: GitLabReleaseDto,
        val timestamp: Long
    )

    private val releaseCache = ConcurrentHashMap<String, CachedRelease>()

    fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ): Flow<SourceUpdateResult<AppUpdate>> = flow {
        val startedAt = SystemClock.elapsedRealtime()
        val mappedApps = apps.mapNotNull { app ->
            val binding = repoMappingManager.getBinding(app.packageName)
            if (binding != null && binding.isGitLab) {
                Pair(app, binding)
            } else {
                null
            }
        }

        Log.i("GitLabRepository", "Checking GitLab updates for ${mappedApps.size} mapped apps (out of ${apps.size} installed)")
        val updates = mutableListOf<AppUpdate>()
        var successfulRequests = 0
        var failedRequests = 0

        apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for (app in batch) {
                val binding = repoMappingManager.getBinding(app.packageName)
                if (binding != null && binding.isGitLab) {
                    try {
                        val release = fetchLatestRelease(binding)
                        successfulRequests++
                        if (release != null) {
                            val candidateTag = release.tag_name.ifBlank { release.name }
                            val cleanCandidate = ReleaseAssetFilter.cleanVersionString(candidateTag)

                            if (ReleaseAssetFilter.isNewer(app.version, cleanCandidate)) {
                                val apkLink = extractBestApkLink(release)
                                if (apkLink != null) {
                                    val rawUrl = apkLink.direct_asset_url.ifBlank { apkLink.url }.trim()
                                    val downloadUrl = when {
                                        rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
                                        rawUrl.startsWith("http://", ignoreCase = true) -> rawUrl.replaceFirst("http://", "https://", ignoreCase = true)
                                        rawUrl.startsWith("/") -> "https://${binding.resolvedHost}$rawUrl"
                                        else -> "https://${binding.resolvedHost}/$rawUrl"
                                    }
                                    val update = AppUpdate(
                                        name = app.name,
                                        packageName = app.packageName,
                                        version = cleanCandidate,
                                        oldVersion = app.version,
                                        versionCode = app.versionCode + 1,
                                        oldVersionCode = app.versionCode,
                                        source = GitLabSource,
                                        link = Link.Url(link = downloadUrl, size = 0L),
                                        whatsNew = release.description,
                                        totalSize = 0L,
                                        installOrigin = app.installOrigin,
                                        installerPackageName = app.installerPackageName
                                    )
                                    if (update.isMeaningfulVersionUpgrade()) {
                                        updates.add(update)
                                        Log.i("GitLabRepository", "Valid GitLab update found for ${app.packageName}: v$cleanCandidate ($downloadUrl)")
                                    }
                                } else {
                                    Log.w("GitLabRepository", "Newer release $candidateTag found for ${app.packageName}, but no APK asset found.")
                                }
                            } else {
                                if (BuildConfig.DEBUG) {
                                    Log.d("GitLabRepository", "App ${app.packageName} is up to date against GitLab release $cleanCandidate")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        failedRequests++
                        Log.e("GitLabRepository", "Failed to check GitLab releases for ${app.packageName} (${binding.repo}): ${e.message}")
                    }
                }
            }
            onBatchSuccess(batch.map { it.packageName })
        }

        Log.i(
            "GitLabRepository",
            "Found ${updates.size} GitLab update(s) in ${SystemClock.elapsedRealtime() - startedAt} ms"
        )

        emit(
            SourceUpdateResult(
                updates = updates,
                successfulRequests = apps.size,
                failedRequests = 0,
                failureSummary = if (failedRequests > 0 && updates.isEmpty()) "Some repositories failed" else null
            )
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("GitLabRepository", "GitLab update check failed", error)
        emit(
            SourceUpdateResult(
                updates = emptyList(),
                successfulRequests = 0,
                failedRequests = 1,
                failureSummary = error.message ?: "network error"
            )
        )
    }

    internal fun extractBestApkLink(release: GitLabReleaseDto): GitLabLinkDto? {
        val linkAssets = release.assets.links.filter {
            it.name.endsWith(".apk", ignoreCase = true) || it.url.endsWith(".apk", ignoreCase = true)
        }
        if (linkAssets.isNotEmpty()) {
            return ReleaseAssetFilter.findBestApk(linkAssets, { it.name.ifBlank { it.url } })
        }

        // Fallback: extract APK URLs from description text
        val apkUrlRegex = Regex("""https://[^\s")<>]+\.apk""", RegexOption.IGNORE_CASE)
        val matches = apkUrlRegex.findAll(release.description).map { it.value }.toList()
        if (matches.isNotEmpty()) {
            val virtualLinks = matches.map { url ->
                val filename = url.substringAfterLast('/')
                GitLabLinkDto(name = filename, url = url, direct_asset_url = url)
            }
            return ReleaseAssetFilter.findBestApk(virtualLinks, { it.name })
        }

        return null
    }

    internal suspend fun fetchLatestRelease(binding: RepoBinding): GitLabReleaseDto? = withContext(Dispatchers.IO) {
        val host = binding.resolvedHost
        val encodedRepo = URLEncoder.encode(binding.repo.trim(), "UTF-8")
        val url = "https://$host/api/v4/projects/$encodedRepo/releases/permalink/latest"
        val cacheKey = "$host/${binding.repo}"
        val cached = releaseCache[cacheKey]

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ApkUp")

        val token = prefs.gitlabToken.get().trim().filter { it in '!'..'~' }
        if (token.isNotEmpty()) {
            requestBuilder.header("PRIVATE-TOKEN", token)
        }

        if (cached?.etag != null) {
            requestBuilder.header("If-None-Match", cached.etag)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && cached != null) {
                Log.d("GitLabRepository", "HTTP 304 Not Modified for ${binding.repo}: using cached release")
                return@withContext cached.release
            }

            if (response.code == 404) {
                Log.w("GitLabRepository", "Project or latest release not found for ${binding.repo} (HTTP 404)")
                return@withContext null
            }

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} when fetching GitLab releases for ${binding.repo}")
            }

            val etag = response.header("ETag")
            val release = gson.fromJson(response.body.charStream(), GitLabReleaseDto::class.java)
            if (release != null) {
                releaseCache[cacheKey] = CachedRelease(etag, release, System.currentTimeMillis())
            }
            release
        }
    }
}
