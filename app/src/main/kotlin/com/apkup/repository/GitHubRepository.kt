package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.repomapping.GitHubAssetDto
import com.apkup.data.repomapping.GitHubReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        private const val GRAPHQL_BATCH_SIZE = 20
        private const val CACHE_TTL_MS = 5L * 60L * 1_000L
        private const val WARM_CACHE_WINDOW_MS = 30_000L
        private const val MAX_SIDECAR_SHA256_BYTES = 4096
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
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

        val appBindings = apps.map { app ->
            app to repoMappingManager.getCompatibleBinding(app.packageName, app.signatureSha256)
                ?.takeIf { it.isGitHub }
        }

        // P-18 Part 1: When a GitHub token is configured, batch uncached repository checks into
        // compact GraphQL queries instead of issuing 1 REST call per repository.
        val noReleaseSlugs = HashSet<String>()
        val token = getSanitizedToken()
        var batchAuthFailure: IOException? = null
        if (token.isNotBlank()) {
            val now = System.currentTimeMillis()
            val reposToPrefetch = LinkedHashMap<String, Pair<String, String>>()
            for ((_, binding) in appBindings) {
                val slug = binding?.repo?.let(::parseOwnerAndRepo) ?: continue
                val key = "${slug.first}/${slug.second}"
                val cached = releaseCache[key]
                if (cached == null || now - cached.timestamp >= WARM_CACHE_WINDOW_MS) {
                    reposToPrefetch.putIfAbsent(key, slug)
                }
            }
            for (chunk in reposToPrefetch.entries.chunked(GRAPHQL_BATCH_SIZE)) {
                try {
                    val result = fetchBatchViaGraphQl(chunk.map { it.key to it.value }, token)
                    noReleaseSlugs.addAll(result.noReleaseSlugs)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (authOrErr: IOException) {
                    if (authOrErr.message?.startsWith("HTTP 401") == true) {
                        batchAuthFailure = authOrErr
                        break
                    }
                    Log.w("GitHubRepository", "GraphQL batch fallback to REST: ${authOrErr.message}")
                } catch (t: Throwable) {
                    Log.w("GitHubRepository", "GraphQL batch fallback to REST: ${t.javaClass.simpleName}")
                }
            }
        }

        appBindings.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for ((app, binding) in batch) {
                if (binding != null) {
                    val slugPair = parseOwnerAndRepo(binding.repo)
                    val normalizedKey = slugPair?.let { "${it.first}/${it.second}" }
                    if (batchAuthFailure != null) {
                        failedRequests++
                        if (firstFailureReason == null) {
                            firstFailureReason = batchAuthFailure.message ?: batchAuthFailure.javaClass.simpleName
                        }
                        continue
                    }
                    if (normalizedKey != null && normalizedKey in noReleaseSlugs) {
                        successfulRequests++
                        continue
                    }
                    try {
                        val release = fetchLatestRelease(binding.repo)
                        successfulRequests++
                        if (release != null && !release.draft) {
                            buildAppUpdate(app, binding, release)?.let { update ->
                                updates.add(update)
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
            onBatchSuccess(batch.map { it.first.packageName })
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

    internal data class GraphQlBatchResult(
        val resolvedCount: Int,
        val noReleaseSlugs: Set<String>
    )

    private fun getSanitizedToken(): String {
        val rawToken = prefs.githubToken.get().trim()
        return rawToken.filter { it in '!'..'~' }
    }

    private fun parseOwnerAndRepo(repo: String): Pair<String, String>? {
        val parts = repo.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size != 2 || !RepoHostValidator.isValidRepoSlugSegments(parts)) {
            return null
        }
        return parts[0] to parts[1].removeSuffix(".git")
    }

    private fun buildAppUpdate(
        app: AppInstalled,
        binding: RepoBinding,
        release: GitHubReleaseDto
    ): AppUpdate? {
        val candidateTag = release.safeTagName.ifBlank { release.safeName }
        val cleanCandidate = ReleaseAssetFilter.cleanVersionString(candidateTag)
        if (!ReleaseAssetFilter.isNewer(app.version, cleanCandidate)) return null

        val bestApk = ReleaseAssetFilter.findBestApk(
            assets = release.safeAssets,
            getFilename = { it.safeName },
            requiredSubstring = binding.assetFilter,
            assetFilterRegex = binding.assetFilterRegex,
            preferFossFlavor = app.installOrigin == InstallOrigin.FDROID
        )
        val downloadUrl = bestApk?.safeDownloadUrl.orEmpty()
        if (bestApk == null || !isAllowedGitHubAssetUrl(downloadUrl)) return null

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
        return update.takeIf { it.isMeaningfulVersionUpgrade() }
    }

    internal suspend fun fetchBatchViaGraphQl(
        repos: List<Pair<String, Pair<String, String>>>,
        token: String
    ): GraphQlBatchResult = withContext(Dispatchers.IO) {
        if (repos.isEmpty()) return@withContext GraphQlBatchResult(0, emptySet())

        val varDecls = repos.indices.joinToString(", ") { i -> "\$o$i: String!, \$n$i: String!" }
        val selections = repos.indices.joinToString("\n") { i ->
            """
            r$i: repository(owner: ${'$'}o$i, name: ${'$'}n$i) {
              latestRelease {
                tagName
                name
                description
                isDraft
                isPrerelease
                publishedAt
                releaseAssets(first: 50) {
                  nodes {
                    name
                    downloadUrl
                    size
                  }
                }
              }
            }
            """.trimIndent()
        }
        val queryString = "query($varDecls) {\n$selections\n}"
        val variables = JsonObject().apply {
            repos.forEachIndexed { idx, (_, ownerRepo) ->
                addProperty("o$idx", ownerRepo.first)
                addProperty("n$idx", ownerRepo.second)
            }
        }
        val payloadObj = JsonObject().apply {
            addProperty("query", queryString)
            add("variables", variables)
        }

        val graphqlUrl = HttpUrl.Builder()
            .scheme("https")
            .host("api.github.com")
            .addPathSegment("graphql")
            .build()

        val req = Request.Builder()
            .url(graphqlUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(payloadObj).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            if (response.code == 401) {
                throw IOException("HTTP 401 (Invalid GitHub Personal Access Token)")
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} on GitHub GraphQL batch")
            }
            val root = response.body.charStream().use { JsonParser.parseReader(it) }
            if (root == null || !root.isJsonObject) {
                throw IOException("Malformed GitHub GraphQL response")
            }
            val rootObj = root.asJsonObject
            val dataObj = rootObj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IOException("GitHub GraphQL response missing 'data' object")

            val errorTypesByAlias = HashMap<String, String>()
            rootObj.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { errElem ->
                val errObj = errElem?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val alias = errObj.get("path")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                val type = errObj.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (!alias.isNullOrBlank()) {
                    errorTypesByAlias[alias] = type
                }
            }

            val now = System.currentTimeMillis()
            val noReleaseSlugs = HashSet<String>()
            var resolved = 0
            repos.forEachIndexed { idx, (slugKey, _) ->
                val alias = "r$idx"
                val repoElem = dataObj.get(alias)
                if (repoElem != null && repoElem.isJsonObject) {
                    val repoObj = repoElem.asJsonObject
                    val latestElem = repoObj.get("latestRelease")
                    if (latestElem == null || latestElem.isJsonNull) {
                        noReleaseSlugs.add(slugKey)
                        resolved++
                    } else if (latestElem.isJsonObject) {
                        val relDto = parseGraphQlRelease(latestElem.asJsonObject)
                        if (relDto != null && !relDto.draft) {
                            releaseCache[slugKey] = CachedRelease(etag = null, release = relDto, timestamp = now)
                            resolved++
                        } else if (relDto?.draft == true) {
                            noReleaseSlugs.add(slugKey)
                            resolved++
                        }
                    }
                } else if (errorTypesByAlias[alias] == "NOT_FOUND") {
                    noReleaseSlugs.add(slugKey)
                    resolved++
                }
            }
            GraphQlBatchResult(resolvedCount = resolved, noReleaseSlugs = noReleaseSlugs)
        }
    }

    private fun parseGraphQlRelease(relObj: JsonObject): GitHubReleaseDto? {
        val tagName = relObj.get("tagName")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val name = relObj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val body = relObj.get("description")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val draft = relObj.get("isDraft")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val prerelease = relObj.get("isPrerelease")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val publishedAt = relObj.get("publishedAt")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val assetNodes = relObj.get("releaseAssets")
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray

        val assets = mutableListOf<GitHubAssetDto>()
        assetNodes?.forEach { nodeElem ->
            val node = nodeElem?.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val assetName = node.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val downloadUrl = node.get("downloadUrl")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val size = runCatching {
                node.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            }.getOrDefault(0L)
            if (assetName.isNotBlank() && downloadUrl.isNotBlank()) {
                assets.add(
                    GitHubAssetDto(
                        name = assetName,
                        browser_download_url = downloadUrl,
                        size = size
                    )
                )
            }
        }

        return GitHubReleaseDto(
            tag_name = tagName,
            name = name,
            body = body,
            draft = draft,
            prerelease = prerelease,
            published_at = publishedAt,
            assets = assets
        )
    }

    internal suspend fun fetchLatestRelease(repo: String): GitHubReleaseDto? = withContext(Dispatchers.IO) {
        val (owner, repoName) = parseOwnerAndRepo(repo) ?: return@withContext null
        val normalizedKey = "$owner/$repoName"

        val cached = releaseCache[normalizedKey]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < WARM_CACHE_WINDOW_MS) {
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

        val token = getSanitizedToken()
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
