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
import com.apkup.data.ui.InstallOrigin
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
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
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
        val updates = mutableListOf<AppUpdate>()
        var successfulRequests = 0
        var failedRequests = 0
        var firstFailureReason: String? = null

        apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for (app in batch) {
                val binding = repoMappingManager.getCompatibleBinding(app.packageName, app.signatureSha256)
                if (binding != null && binding.isGitLab) {
                    val isManualCustom = repoMappingManager.hasCustomBinding(app.packageName)
                    if (!RepoHostValidator.isAllowedRepoHost(binding.resolvedHost, allowLanHosts = isManualCustom)) {
                        continue
                    }
                    try {
                        val release = fetchLatestRelease(binding, allowLanHost = isManualCustom)
                        successfulRequests++
                        if (release != null) {
                            val candidateTag = release.safeTagName.ifBlank { release.safeName }
                            val cleanCandidate = ReleaseAssetFilter.cleanVersionString(candidateTag)

                            if (ReleaseAssetFilter.isNewer(app.version, cleanCandidate)) {
                                val apkLink = extractBestApkLink(
                                    release = release,
                                    expectedHost = binding.resolvedHost,
                                    requiredSubstring = binding.assetFilter,
                                    assetFilterRegex = binding.assetFilterRegex,
                                    preferFossFlavor = app.installOrigin == InstallOrigin.FDROID
                                )
                                if (apkLink != null) {
                                    val downloadUrl = resolveSafeGitLabAssetUrl(
                                        rawUrl = apkLink.safeDirectAssetUrl.ifBlank { apkLink.safeUrl },
                                        expectedHost = binding.resolvedHost,
                                        allowLanHost = isManualCustom
                                    )
                                    if (downloadUrl != null) {
                                        val update = AppUpdate(
                                            name = app.name,
                                            packageName = app.packageName,
                                            version = cleanCandidate,
                                            oldVersion = app.version,
                                            versionCode = maxOf(1L, app.versionCode + 1L),
                                            oldVersionCode = app.versionCode,
                                            source = GitLabSource,
                                            link = Link.Url(link = downloadUrl, size = 0L),
                                            whatsNew = release.safeDescription,
                                            totalSize = 0L,
                                            updatedOn = (release.released_at as String?).orEmpty(),
                                            installOrigin = app.installOrigin,
                                            installerPackageName = app.installerPackageName
                                        )
                                        if (update.isMeaningfulVersionUpgrade()) {
                                            updates.add(update)
                                        }
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
                        Log.w("GitLabRepository", "GitLab check failed for ${binding.repo}: ${e.javaClass.simpleName}")
                    }
                }
            }
            onBatchSuccess(batch.map { it.packageName })
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                "GitLabRepository",
                "Checked GitLab in ${SystemClock.elapsedRealtime() - startedAt} ms: " +
                    "ok=$successfulRequests, failed=$failedRequests, updates=${updates.size}"
            )
        }

        emit(
            SourceUpdateResult(
                updates = updates,
                successfulRequests = if (successfulRequests == 0 && failedRequests == 0) 1 else successfulRequests,
                failedRequests = failedRequests,
                failureSummary = if (failedRequests > 0) (firstFailureReason ?: "GitLab request failed") else null
            )
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("GitLabRepository", "GitLab update check failed (${error.javaClass.simpleName})")
        emit(
            SourceUpdateResult(
                updates = emptyList(),
                successfulRequests = 0,
                failedRequests = 1,
                failureSummary = error.message ?: "network error"
            )
        )
    }

    /**
     * Resolves a GitLab release asset URL, strictly requiring HTTPS and forbidding cross-domain
     * redirection in Markdown fallback links (`HIGH-05`).
     */
    internal fun resolveSafeGitLabAssetUrl(
        rawUrl: String,
        expectedHost: String,
        allowLanHost: Boolean = false
    ): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null
        val candidate = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") && !trimmed.startsWith("//") -> "https://$expectedHost$trimmed"
            else -> return null // Never silently upgrade arbitrary http:// or scheme-relative links
        }
        val uri = runCatching { URI.create(candidate) }.getOrNull() ?: return null
        val urlHost = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!RepoHostValidator.isAllowedRepoHost(urlHost, allowLanHosts = allowLanHost)) {
            return null
        }
        return candidate
    }

    internal fun extractBestApkLink(
        release: GitLabReleaseDto,
        expectedHost: String = "gitlab.com",
        requiredSubstring: String? = null,
        assetFilterRegex: String? = null,
        preferFossFlavor: Boolean = false
    ): GitLabLinkDto? {
        val linkAssets = release.safeLinks.filter {
            it.safeName.endsWith(".apk", ignoreCase = true) ||
                it.safeUrl.substringBefore('?').endsWith(".apk", ignoreCase = true) ||
                it.safeDirectAssetUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)
        }
        if (linkAssets.isNotEmpty()) {
            return ReleaseAssetFilter.findBestApk(
                assets = linkAssets,
                getFilename = { it.safeName.ifBlank { it.safeUrl.substringBefore('?').substringAfterLast('/') } },
                requiredSubstring = requiredSubstring,
                assetFilterRegex = assetFilterRegex,
                preferFossFlavor = preferFossFlavor
            )
        }

        // Fallback: extract APK URLs from Markdown description ONLY if hosted on the SAME GitLab host (HIGH-05)
        val normalizedExpectedHost = expectedHost.trim().lowercase(Locale.ROOT)
        val apkUrlRegex = Regex("""https://[^\s")<>]+\.apk""", RegexOption.IGNORE_CASE)
        val sameHostMatches = apkUrlRegex.findAll(release.safeDescription)
            .map { it.value }
            .filter { matchedUrl ->
                val matchedHost = runCatching { URI.create(matchedUrl).host?.lowercase(Locale.ROOT) }.getOrNull()
                matchedHost != null && (
                    matchedHost == normalizedExpectedHost ||
                        matchedHost.endsWith(".$normalizedExpectedHost")
                )
            }
            .toList()

        if (sameHostMatches.isNotEmpty()) {
            val virtualLinks = sameHostMatches.map { url ->
                val filename = url.substringBefore('?').substringAfterLast('/')
                GitLabLinkDto(name = filename, url = url, direct_asset_url = url)
            }
            return ReleaseAssetFilter.findBestApk(
                assets = virtualLinks,
                getFilename = { it.safeName },
                requiredSubstring = requiredSubstring,
                assetFilterRegex = assetFilterRegex,
                preferFossFlavor = preferFossFlavor
            )
        }

        return null
    }

    internal suspend fun fetchLatestRelease(
        binding: RepoBinding,
        allowLanHost: Boolean = false
    ): GitLabReleaseDto? = withContext(Dispatchers.IO) {
        val host = binding.resolvedHost.trim().lowercase(Locale.ROOT)
        if (!RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = allowLanHost)) {
            return@withContext null
        }
        val cleanSegments = binding.repo.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (!RepoHostValidator.isValidRepoSlugSegments(cleanSegments)) {
            return@withContext null
        }
        val normalizedRepo = cleanSegments.joinToString("/")
        val encodedRepo = URLEncoder.encode(normalizedRepo, "UTF-8").replace("+", "%20")

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment("api")
            .addPathSegment("v4")
            .addPathSegment("projects")
            .addEncodedPathSegment(encodedRepo)
            .addPathSegment("releases")
            .addPathSegment("permalink")
            .addPathSegment("latest")
            .build()

        val cacheKey = "$host/$normalizedRepo"
        val cached = releaseCache[cacheKey]

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")

        // F-10 Option A: Send standard RFC 6750 Bearer token ONLY to gitlab.com (never third-party hosts)
        val token = prefs.gitlabToken.get().trim().filter { it in '!'..'~' }
        if (token.isNotEmpty() && host.equals("gitlab.com", ignoreCase = true)) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        if (cached?.etag != null) {
            requestBuilder.header("If-None-Match", cached.etag)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && cached != null) {
                return@withContext cached.release
            }

            if (response.code == 401) {
                throw IOException("HTTP 401 (Invalid GitLab Personal Access Token)")
            }

            if (response.code == 404) {
                return@withContext null
            }

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
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
