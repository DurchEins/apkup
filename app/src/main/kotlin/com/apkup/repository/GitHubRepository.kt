package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.repomapping.GitHubReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
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
import java.util.concurrent.ConcurrentHashMap

class GitHubRepository(
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
            val binding = repoMappingManager.getBinding(app.packageName)
            if (binding != null && binding.isGitHub) {
                Pair(app, binding)
            } else {
                null
            }
        }

        Log.i("GitHubRepository", "Checking GitHub updates for ${mappedApps.size} mapped apps (out of ${apps.size} installed)")
        val updates = mutableListOf<AppUpdate>()
        var successfulRequests = 0
        var failedRequests = 0

        apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for (app in batch) {
                val binding = repoMappingManager.getBinding(app.packageName)
                if (binding != null && binding.isGitHub) {
                    try {
                        val release = fetchLatestRelease(binding.repo)
                        successfulRequests++
                        if (release != null) {
                            val candidateTag = release.tag_name.ifBlank { release.name }
                            val cleanCandidate = ReleaseAssetFilter.cleanVersionString(candidateTag)

                            if (ReleaseAssetFilter.isNewer(app.version, cleanCandidate)) {
                                val bestApk = ReleaseAssetFilter.findBestApk(release.assets, { it.name })
                                if (bestApk != null) {
                                    val update = AppUpdate(
                                        name = app.name,
                                        packageName = app.packageName,
                                        version = cleanCandidate,
                                        oldVersion = app.version,
                                        versionCode = app.versionCode + 1,
                                        oldVersionCode = app.versionCode,
                                        source = GitHubSource,
                                        link = Link.Url(link = bestApk.browser_download_url, size = bestApk.size),
                                        whatsNew = release.body,
                                        totalSize = bestApk.size,
                                        installOrigin = app.installOrigin,
                                        installerPackageName = app.installerPackageName
                                    )
                                    if (update.isMeaningfulVersionUpgrade()) {
                                        updates.add(update)
                                        Log.i("GitHubRepository", "Valid GitHub update found for ${app.packageName}: v$cleanCandidate (${bestApk.name})")
                                    }
                                } else {
                                    Log.w("GitHubRepository", "Newer release $candidateTag found for ${app.packageName}, but no compatible APK asset found.")
                                }
                            } else {
                                if (BuildConfig.DEBUG) {
                                    Log.d("GitHubRepository", "App ${app.packageName} is up to date against GitHub release $cleanCandidate")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        failedRequests++
                        Log.e("GitHubRepository", "Failed to check GitHub releases for ${app.packageName} (${binding.repo}): ${e.message}")
                    }
                }
            }
            onBatchSuccess(batch.map { it.packageName })
        }

        Log.i(
            "GitHubRepository",
            "Found ${updates.size} GitHub update(s) in ${SystemClock.elapsedRealtime() - startedAt} ms"
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
        Log.e("GitHubRepository", "GitHub update check failed", error)
        emit(
            SourceUpdateResult(
                updates = emptyList(),
                successfulRequests = 0,
                failedRequests = 1,
                failureSummary = error.message ?: "network error"
            )
        )
    }

    internal suspend fun fetchLatestRelease(repo: String): GitHubReleaseDto? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${repo.trim()}/releases/latest"
        val cached = releaseCache[repo]

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "ApkUp")

        val rawToken = prefs.githubToken.get().trim()
        val token = rawToken.filter { it in '!'..'~' }
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        if (cached?.etag != null) {
            requestBuilder.header("If-None-Match", cached.etag)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && cached != null) {
                Log.d("GitHubRepository", "HTTP 304 Not Modified for $repo: using cached release")
                return@withContext cached.release
            }

            if (response.code == 403) {
                val remaining = response.header("x-ratelimit-remaining")
                Log.w("GitHubRepository", "GitHub rate limit exceeded for $repo (remaining: $remaining). Consider adding a PAT in Settings.")
                return@withContext cached?.release
            }

            if (response.code == 404) {
                Log.w("GitHubRepository", "Repository or latest release not found for $repo (HTTP 404)")
                return@withContext null
            }

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} when fetching GitHub releases for $repo")
            }

            val etag = response.header("ETag")
            val release = gson.fromJson(response.body.charStream(), GitHubReleaseDto::class.java)
            if (release != null) {
                releaseCache[repo] = CachedRelease(etag, release, System.currentTimeMillis())
            }
            release
        }
    }
}
