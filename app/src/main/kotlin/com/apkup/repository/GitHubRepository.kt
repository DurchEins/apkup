package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.repomapping.GitHubAssetDto
import com.apkup.data.repomapping.GitHubReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.Link
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.prefs.Prefs
import com.apkup.util.ApkIntegrityVerifier
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
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
        private const val CACHE_TTL_MS = 5L * 60L * 1_000L
        private const val MAX_SIDECAR_SHA256_BYTES = 4096
        private val ALLOWED_GITHUB_ASSET_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "codeload.github.com"
        )
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
        val updates = mutableListOf<AppUpdate>()
        var successfulRequests = 0
        var failedRequests = 0
        var firstFailureReason: String? = null

        apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for (app in batch) {
                val binding = repoMappingManager.getCompatibleBinding(app.packageName, app.signatureSha256)
                if (binding != null && binding.isGitHub) {
                    try {
                        val release = fetchLatestRelease(binding.repo)
                        successfulRequests++
                        if (release != null && !release.draft) {
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
                                if (bestApk != null && isAllowedGitHubAssetUrl(downloadUrl)) {
                                    val expectedSha256 = resolveGitHubAssetSha256(release, bestApk)
                                    val update = AppUpdate(
                                        name = app.name,
                                        packageName = app.packageName,
                                        version = cleanCandidate,
                                        oldVersion = app.version,
                                        versionCode = maxOf(1L, app.versionCode + 1L),
                                        oldVersionCode = app.versionCode,
                                        source = GitHubSource,
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
                                    }
                                }
                            }
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        failedRequests++
                        if (firstFailureReason == null) {
                            firstFailureReason = e.message ?: e.javaClass.simpleName
                        }
                        Log.w("GitHubRepository", "GitHub check failed for ${binding.repo}: ${e.javaClass.simpleName}")
                    }
                }
            }
            onBatchSuccess(batch.map { it.packageName })
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                "GitHubRepository",
                "Checked GitHub in ${SystemClock.elapsedRealtime() - startedAt} ms: " +
                    "ok=$successfulRequests, failed=$failedRequests, updates=${updates.size}"
            )
        }

        emit(
            SourceUpdateResult(
                updates = updates,
                successfulRequests = if (successfulRequests == 0 && failedRequests == 0) 1 else successfulRequests,
                failedRequests = failedRequests,
                failureSummary = if (failedRequests > 0) (firstFailureReason ?: "GitHub request failed") else null
            )
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("GitHubRepository", "GitHub update check failed (${error.javaClass.simpleName})")
        emit(
            SourceUpdateResult(
                updates = emptyList(),
                successfulRequests = 0,
                failedRequests = 1,
                failureSummary = error.message ?: "network error"
            )
        )
    }

    internal fun isAllowedGitHubAssetUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { URI.create(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return host in ALLOWED_GITHUB_ASSET_HOSTS || host.endsWith(".githubusercontent.com")
    }

    private fun resolveGitHubAssetSha256(release: GitHubReleaseDto, apkAsset: GitHubAssetDto): String? {
        ApkIntegrityVerifier.normalizeSha256Hex(apkAsset.digest)?.let { return it }

        val sidecar = release.safeAssets.firstOrNull { asset ->
            asset.safeName.equals("${apkAsset.safeName}.sha256", ignoreCase = true) ||
                asset.safeName.equals("${apkAsset.safeName}.sha256sum", ignoreCase = true)
        } ?: return null

        if (!isAllowedGitHubAssetUrl(sidecar.safeDownloadUrl)) return null
        return runCatching {
            val req = Request.Builder()
                .url(sidecar.safeDownloadUrl)
                .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val snippet = resp.body.byteStream().use { input ->
                    val buf = ByteArray(MAX_SIDECAR_SHA256_BYTES)
                    val n = input.read(buf)
                    if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
                }
                val token = Regex("\\b([0-9a-fA-F]{64})\\b").find(snippet)?.groupValues?.getOrNull(1)
                ApkIntegrityVerifier.normalizeSha256Hex(token)
            }
        }.getOrNull()
    }

    internal suspend fun fetchLatestRelease(repo: String): GitHubReleaseDto? = withContext(Dispatchers.IO) {
        val parts = repo.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size != 2 || !RepoHostValidator.isValidRepoSlugSegments(parts)) {
            return@withContext null
        }
        val owner = parts[0]
        val repoName = parts[1].removeSuffix(".git")
        val normalizedKey = "$owner/$repoName"

        val cached = releaseCache[normalizedKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 30_000L) {
            return@withContext cached.release
        }

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.github.com")
            .addPathSegment("repos")
            .addPathSegment(owner)
            .addPathSegment(repoName)
            .addPathSegment("releases")
            .addPathSegment("latest")
            .build()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")

        val rawToken = prefs.githubToken.get().trim()
        val token = rawToken.filter { it in '!'..'~' }
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        if (cached?.etag != null && System.currentTimeMillis() - cached.timestamp <= CACHE_TTL_MS * 12) {
            requestBuilder.header("If-None-Match", cached.etag)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && cached != null) {
                releaseCache[normalizedKey] = cached.copy(timestamp = System.currentTimeMillis())
                return@withContext cached.release
            }

            if (response.code == 403 || response.code == 429) {
                val remaining = response.header("x-ratelimit-remaining")
                Log.w("GitHubRepository", "GitHub rate limit / HTTP ${response.code} for $normalizedKey (remaining=$remaining)")
                if (cached != null) return@withContext cached.release
                throw IOException("HTTP ${response.code} (GitHub API rate limit)")
            }

            if (response.code == 401) {
                throw IOException("HTTP 401 (Invalid GitHub Personal Access Token)")
            }

            if (response.code == 404) {
                return@withContext null
            }

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val etag = response.header("ETag")
            val release = gson.fromJson(response.body.charStream(), GitHubReleaseDto::class.java)
            if (release != null) {
                releaseCache[normalizedKey] = CachedRelease(etag, release, System.currentTimeMillis())
            }
            release
        }
    }
}
