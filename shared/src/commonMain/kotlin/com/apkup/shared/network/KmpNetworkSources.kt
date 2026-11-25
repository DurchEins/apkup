package com.apkup.shared.network

import com.apkup.shared.engine.KmpAssetSelector
import com.apkup.shared.engine.KmpBatchVersionRequest
import com.apkup.shared.engine.KmpTelemetryRegistry
import com.apkup.shared.engine.KmpVersionEngine
import com.apkup.shared.model.KmpApkMirrorAppExistsRequest
import com.apkup.shared.model.KmpApkMirrorAppExistsResponse
import com.apkup.shared.model.KmpFDroidV1Index
import com.apkup.shared.model.KmpFDroidV2Entry
import com.apkup.shared.model.KmpFDroidV2Index
import com.apkup.shared.model.KmpGitHubAssetDto
import com.apkup.shared.model.KmpGitHubReleaseDto
import com.apkup.shared.model.KmpGitLabReleaseDto
import com.apkup.shared.model.KmpInstalledApp
import com.apkup.shared.model.KmpRepoBinding
import com.apkup.shared.model.KmpSourceCheckReport
import com.apkup.shared.model.KmpUpdateCandidate
import com.apkup.shared.platform.currentTimeMillis
import com.apkup.shared.platform.nanoTime
import com.apkup.shared.platform.verifyAndExtractFDroidJarEntry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

object KmpJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
}

fun createKmpHttpClient(userAgent: String = "ApkUp-KMP/1.47.0"): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(KmpJson.instance)
    }
    expectSuccess = false
}

fun normalizeSha256Hex(raw: String?): String? {
    val cleaned = raw?.trim()
        ?.removePrefix("sha256:")
        ?.removePrefix("SHA256:")
        ?.replace(":", "")
        ?.replace(" ", "")
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return if (cleaned.length == 64 && cleaned.all { it in '0'..'9' || it in 'a'..'f' }) cleaned else null
}

private fun JsonElement?.preferredLocalizedText(): String {
    if (this == null) return ""
    if (this is JsonPrimitive && isString) return content.trim()
    if (this is JsonObject) {
        this["en-US"]?.let { if (it is JsonPrimitive && it.isString && it.content.isNotBlank()) return it.content.trim() }
        this["en"]?.let { if (it is JsonPrimitive && it.isString && it.content.isNotBlank()) return it.content.trim() }
        this["de-DE"]?.let { if (it is JsonPrimitive && it.isString && it.content.isNotBlank()) return it.content.trim() }
        values.firstOrNull { it is JsonPrimitive && it.isString && it.content.isNotBlank() }?.let {
            return it.jsonPrimitive.content.trim()
        }
    }
    return ""
}

class KmpMultiSourceScanner(
    private val httpClient: HttpClient
) {
    suspend fun checkGitHub(
        appsWithBindings: List<Pair<KmpInstalledApp, KmpRepoBinding>>,
        supportedAbis: List<String>,
        githubToken: String = "",
        includePreReleases: Boolean = false
    ): KmpSourceCheckReport {
        val started = currentTimeMillis()
        val updates = mutableListOf<KmpUpdateCandidate>()
        var ok = 0
        var fail = 0
        var firstError: String? = null

        val fetched = mutableListOf<Triple<KmpInstalledApp, KmpRepoBinding, KmpGitHubReleaseDto>>()
        for ((app, binding) in appsWithBindings) {
            if (!binding.type.equals("github", ignoreCase = true)) continue
            val slug = binding.repo.trim().trim('/')
            if (slug.split('/').size != 2) continue
            try {
                val url = if (includePreReleases) {
                    "https://api.github.com/repos/$slug/releases?per_page=10"
                } else {
                    "https://api.github.com/repos/$slug/releases/latest"
                }
                val resp = httpClient.get(url) {
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    header(HttpHeaders.UserAgent, "ApkUp-KMP/1.47.0")
                    if (githubToken.isNotBlank()) {
                        header(HttpHeaders.Authorization, "Bearer ${githubToken.trim()}")
                    }
                }
                if (resp.status.value == 404) {
                    ok++
                    continue
                }
                if (!resp.status.isSuccess()) {
                    fail++
                    if (firstError == null) firstError = "GitHub HTTP ${resp.status.value}"
                    continue
                }
                ok++
                val body = resp.bodyAsText()
                val release = if (includePreReleases) {
                    KmpJson.instance.decodeFromString<List<KmpGitHubReleaseDto>>(body)
                        .firstOrNull { !it.draft }
                } else {
                    KmpJson.instance.decodeFromString<KmpGitHubReleaseDto>(body)
                        .takeIf { !it.draft && !it.prerelease }
                }
                if (release != null) {
                    fetched += Triple(app, binding, release)
                }
            } catch (t: Throwable) {
                fail++
                if (firstError == null) firstError = t.message ?: t::class.simpleName
            }
        }

        if (fetched.isNotEmpty()) {
            val batchResults = KmpVersionEngine.evaluateBatch(
                fetched.map { (app, _, rel) ->
                    KmpBatchVersionRequest(
                        id = app.packageName,
                        currentVersion = app.version,
                        candidateTag = rel.tagName.ifBlank { rel.name }
                    )
                }
            ).associateBy { it.id }

            for ((app, binding, rel) in fetched) {
                val eval = batchResults[app.packageName] ?: continue
                if (!eval.isNewer) continue
                val bestApk: KmpGitHubAssetDto = KmpAssetSelector.findBestApk(
                    assets = rel.assets,
                    getFilename = { it.name },
                    supportedAbis = supportedAbis,
                    requiredSubstring = binding.assetFilter,
                    assetFilterRegex = binding.assetFilterRegex
                ) ?: continue
                if (!bestApk.browserDownloadUrl.startsWith("https://", ignoreCase = true)) continue
                updates += KmpUpdateCandidate(
                    name = app.name,
                    packageName = app.packageName,
                    currentVersion = app.version,
                    newVersion = eval.cleanedCandidate,
                    currentVersionCode = app.versionCode,
                    newVersionCode = maxOf(1L, app.versionCode + 1L),
                    sourceName = "GitHub",
                    downloadUrl = bestApk.browserDownloadUrl,
                    sizeBytes = bestApk.size,
                    expectedSha256 = normalizeSha256Hex(bestApk.digest),
                    releaseNotes = rel.body,
                    publishedAt = rel.publishedAt,
                    installedShortSha256 = app.shortSha256,
                    isPreRelease = eval.isPreRelease
                )
            }
        }

        return KmpSourceCheckReport(
            sourceName = "GitHub",
            updates = updates,
            successfulRequests = ok,
            failedRequests = fail,
            errorMessage = firstError,
            durationMs = (currentTimeMillis() - started).coerceAtLeast(0L)
        )
    }

    suspend fun checkGitLabAndCodeberg(
        appsWithBindings: List<Pair<KmpInstalledApp, KmpRepoBinding>>,
        supportedAbis: List<String>
    ): KmpSourceCheckReport {
        val started = currentTimeMillis()
        val updates = mutableListOf<KmpUpdateCandidate>()
        var ok = 0
        var fail = 0
        var firstError: String? = null

        for ((app, binding) in appsWithBindings) {
            val type = binding.type.trim().lowercase()
            if (type !in setOf("gitlab", "codeberg", "forgejo", "gitea")) continue
            val host = binding.resolvedHost
            val slug = binding.repo.trim().trim('/')
            try {
                if (type == "gitlab") {
                    val encoded = slug.replace("/", "%2F")
                    val resp = httpClient.get("https://$host/api/v4/projects/$encoded/releases?per_page=1") {
                        header(HttpHeaders.UserAgent, "ApkUp-KMP/1.47.0")
                    }
                    if (!resp.status.isSuccess()) {
                        fail++
                        if (firstError == null) firstError = "GitLab HTTP ${resp.status.value}"
                        continue
                    }
                    ok++
                    val rel = KmpJson.instance.decodeFromString<List<KmpGitLabReleaseDto>>(resp.bodyAsText()).firstOrNull()
                        ?: continue
                    val tag = rel.tagName.ifBlank { rel.name }
                    val clean = KmpVersionEngine.cleanVersionString(tag)
                    if (!KmpVersionEngine.isNewer(app.version, clean)) continue
                    val bestLink = KmpAssetSelector.findBestApk(
                        assets = rel.assets.links,
                        getFilename = { it.name.ifBlank { it.url.substringAfterLast('/') } },
                        supportedAbis = supportedAbis,
                        requiredSubstring = binding.assetFilter,
                        assetFilterRegex = binding.assetFilterRegex
                    ) ?: continue
                    val dl = bestLink.directAssetUrl.ifBlank { bestLink.url }
                    if (!dl.startsWith("https://", ignoreCase = true)) continue
                    updates += KmpUpdateCandidate(
                        name = app.name,
                        packageName = app.packageName,
                        currentVersion = app.version,
                        newVersion = clean,
                        currentVersionCode = app.versionCode,
                        newVersionCode = maxOf(1L, app.versionCode + 1L),
                        sourceName = "GitLab",
                        downloadUrl = dl,
                        releaseNotes = rel.description,
                        publishedAt = rel.releasedAt,
                        installedShortSha256 = app.shortSha256
                    )
                } else {
                    val resp = httpClient.get("https://$host/api/v1/repos/$slug/releases?limit=3") {
                        header(HttpHeaders.UserAgent, "ApkUp-KMP/1.47.0")
                    }
                    if (!resp.status.isSuccess()) {
                        fail++
                        if (firstError == null) firstError = "Codeberg HTTP ${resp.status.value}"
                        continue
                    }
                    ok++
                    val rel = KmpJson.instance.decodeFromString<List<KmpGitHubReleaseDto>>(resp.bodyAsText())
                        .firstOrNull { !it.draft && !it.prerelease } ?: continue
                    val clean = KmpVersionEngine.cleanVersionString(rel.tagName.ifBlank { rel.name })
                    if (!KmpVersionEngine.isNewer(app.version, clean)) continue
                    val bestApk = KmpAssetSelector.findBestApk(
                        assets = rel.assets,
                        getFilename = { it.name },
                        supportedAbis = supportedAbis,
                        requiredSubstring = binding.assetFilter,
                        assetFilterRegex = binding.assetFilterRegex
                    ) ?: continue
                    if (!bestApk.browserDownloadUrl.startsWith("https://", ignoreCase = true)) continue
                    updates += KmpUpdateCandidate(
                        name = app.name,
                        packageName = app.packageName,
                        currentVersion = app.version,
                        newVersion = clean,
                        currentVersionCode = app.versionCode,
                        newVersionCode = maxOf(1L, app.versionCode + 1L),
                        sourceName = "Codeberg",
                        downloadUrl = bestApk.browserDownloadUrl,
                        sizeBytes = bestApk.size,
                        expectedSha256 = normalizeSha256Hex(bestApk.digest),
                        releaseNotes = rel.body,
                        publishedAt = rel.publishedAt,
                        installedShortSha256 = app.shortSha256
                    )
                }
            } catch (t: Throwable) {
                fail++
                if (firstError == null) firstError = t.message ?: t::class.simpleName
            }
        }

        return KmpSourceCheckReport(
            sourceName = "GitLab / Codeberg",
            updates = updates,
            successfulRequests = ok,
            failedRequests = fail,
            errorMessage = firstError,
            durationMs = (currentTimeMillis() - started).coerceAtLeast(0L)
        )
    }

    suspend fun checkFDroidRepo(
        repoName: String,
        repoBaseUrl: String,
        expectedCertSha256: String,
        installedApps: List<KmpInstalledApp>,
        supportedAbis: List<String>
    ): KmpSourceCheckReport {
        val started = currentTimeMillis()
        val base = repoBaseUrl.trimEnd('/')
        val appByPkg = installedApps.associateBy { it.packageName }
        val updates = mutableListOf<KmpUpdateCandidate>()

        return try {
            val entryResp = httpClient.get("$base/entry.jar") {
                header(HttpHeaders.UserAgent, "ApkUp-KMP/1.47.0")
            }
            if (entryResp.status.isSuccess()) {
                val jarBytes = entryResp.bodyAsBytes()
                val verified = verifyAndExtractFDroidJarEntry(
                    jarBytes = jarBytes,
                    targetEntryName = "entry.json",
                    expectedCertSha256 = expectedCertSha256,
                    allowUnpinnedCert = expectedCertSha256.isBlank()
                )
                KmpTelemetryRegistry.recordFDroidJarVerify(verified.elapsedMicros)
                val entry = KmpJson.instance.decodeFromString<KmpFDroidV2Entry>(verified.entryJsonText)
                val indexRel = entry.index?.name?.trimStart('/')?.ifBlank { "index-v2.json" } ?: "index-v2.json"
                val indexResp = httpClient.get("$base/$indexRel") {
                    header(HttpHeaders.UserAgent, "ApkUp-KMP/1.47.0")
                }
                if (!indexResp.status.isSuccess()) {
                    return KmpSourceCheckReport(
                        sourceName = repoName,
                        failedRequests = 1,
                        errorMessage = "HTTP ${indexResp.status.value} loading $indexRel",
                        durationMs = currentTimeMillis() - started
                    )
                }
                val parseStartNs = nanoTime()
                val rawIndex = indexResp.bodyAsText()
                val index = KmpJson.instance.decodeFromString<KmpFDroidV2Index>(rawIndex)
                var matchedCount = 0
                for ((pkgName, pkgEntry) in index.packages) {
                    val installed = appByPkg[pkgName] ?: continue
                    matchedCount++
                    val bestVersion = pkgEntry.versions.values
                        .filter { ver ->
                            val m = ver.manifest ?: return@filter false
                            val abis = m.nativecode.orEmpty()
                            m.versionCode > installed.versionCode &&
                                KmpAssetSelector.supportsArchitecture(abis, supportedAbis)
                        }
                        .maxByOrNull { it.manifest?.versionCode ?: 0L } ?: continue

                    val manifest = bestVersion.manifest ?: continue
                    val file = bestVersion.file ?: continue
                    val relApk = file.name.trimStart('/')
                    updates += KmpUpdateCandidate(
                        name = pkgEntry.metadata?.name.preferredLocalizedText().ifBlank { installed.name },
                        packageName = pkgName,
                        currentVersion = installed.version,
                        newVersion = manifest.versionName,
                        currentVersionCode = installed.versionCode,
                        newVersionCode = manifest.versionCode,
                        sourceName = repoName,
                        downloadUrl = "$base/$relApk",
                        sizeBytes = file.size,
                        expectedSha256 = normalizeSha256Hex(file.sha256),
                        releaseNotes = pkgEntry.metadata?.summary.preferredLocalizedText(),
                        installedShortSha256 = installed.shortSha256
                    )
                }
                val parseMicros = ((nanoTime() - parseStartNs) / 1000L).coerceAtLeast(1L)
                KmpTelemetryRegistry.recordFDroidParse(parseMicros, matchedCount)
                KmpSourceCheckReport(
                    sourceName = repoName,
                    updates = updates,
                    successfulRequests = 1,
                    durationMs = (currentTimeMillis() - started).coerceAtLeast(0L)
                )
            } else {
                // Fallback to index-v1.jar
                val v1Resp = httpClient.get("$base/index-v1.jar") {
                    header(HttpHeaders.UserAgent, "ApkUp-KMP/1.47.0")
                }
                if (!v1Resp.status.isSuccess()) {
                    return KmpSourceCheckReport(
                        sourceName = repoName,
                        failedRequests = 1,
                        errorMessage = "HTTP ${v1Resp.status.value} loading index-v1.jar",
                        durationMs = currentTimeMillis() - started
                    )
                }
                val verified = verifyAndExtractFDroidJarEntry(
                    jarBytes = v1Resp.bodyAsBytes(),
                    targetEntryName = "index-v1.json",
                    expectedCertSha256 = expectedCertSha256,
                    allowUnpinnedCert = expectedCertSha256.isBlank()
                )
                KmpTelemetryRegistry.recordFDroidJarVerify(verified.elapsedMicros)
                val parseStartNs = nanoTime()
                val v1 = KmpJson.instance.decodeFromString<KmpFDroidV1Index>(verified.entryJsonText)
                var matchedCount = 0
                for ((pkgName, pkgList) in v1.packages) {
                    val installed = appByPkg[pkgName] ?: continue
                    matchedCount++
                    val best = pkgList
                        .filter { it.versionCode > installed.versionCode && KmpAssetSelector.supportsArchitecture(it.nativecode.orEmpty(), supportedAbis) }
                        .maxByOrNull { it.versionCode } ?: continue
                    updates += KmpUpdateCandidate(
                        name = installed.name,
                        packageName = pkgName,
                        currentVersion = installed.version,
                        newVersion = best.versionName,
                        currentVersionCode = installed.versionCode,
                        newVersionCode = best.versionCode,
                        sourceName = repoName,
                        downloadUrl = "$base/${best.apkName.trimStart('/')}",
                        sizeBytes = best.size,
                        expectedSha256 = normalizeSha256Hex(best.hash),
                        installedShortSha256 = installed.shortSha256
                    )
                }
                val parseMicros = ((nanoTime() - parseStartNs) / 1000L).coerceAtLeast(1L)
                KmpTelemetryRegistry.recordFDroidParse(parseMicros, matchedCount)
                KmpSourceCheckReport(
                    sourceName = repoName,
                    updates = updates,
                    successfulRequests = 1,
                    durationMs = (currentTimeMillis() - started).coerceAtLeast(0L)
                )
            }
        } catch (t: Throwable) {
            KmpSourceCheckReport(
                sourceName = repoName,
                failedRequests = 1,
                errorMessage = t.message ?: t::class.simpleName,
                durationMs = (currentTimeMillis() - started).coerceAtLeast(0L)
            )
        }
    }

    suspend fun checkApkMirrorDesktop(
        apps: List<KmpInstalledApp>,
        supportedAbis: List<String>
    ): KmpSourceCheckReport {
        val started = currentTimeMillis()
        val updates = mutableListOf<KmpUpdateCandidate>()
        val appByPkg = apps.associateBy { it.packageName }
        var ok = 0
        var fail = 0
        var firstError: String? = null

        for (chunk in apps.map { it.packageName }.chunked(50)) {
            try {
                val resp = httpClient.post("https://www.apkmirror.com/wp-json/apkmirror/v1/app_exists/") {
                    contentType(ContentType.Application.Json)
                    header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ApkUp-KMP/1.47.0")
                    setBody(KmpApkMirrorAppExistsRequest(pname = chunk))
                }
                if (resp.status.value == 403 || resp.status.value == 401) {
                    fail++
                    firstError = "Cloudflare-Schutz aktiv (HTTP ${resp.status.value}) – Browser-Fallback verfügbar"
                    continue
                }
                if (!resp.status.isSuccess()) {
                    fail++
                    if (firstError == null) firstError = "HTTP ${resp.status.value}"
                    continue
                }
                ok++
                val body = resp.body<KmpApkMirrorAppExistsResponse>()
                for (item in body.data) {
                    if (!item.exists) continue
                    val installed = appByPkg[item.pname] ?: continue
                    val rel = item.release ?: continue
                    val bestApk = item.apks
                        .filter { apk ->
                            val vc = apk.versionCode.toLongOrNull() ?: 0L
                            vc > installed.versionCode &&
                                KmpAssetSelector.supportsArchitecture(apk.arches, supportedAbis)
                        }
                        .maxByOrNull { it.versionCode.toLongOrNull() ?: 0L } ?: continue
                    val vc = bestApk.versionCode.toLongOrNull() ?: continue
                    val link = bestApk.link.ifBlank { rel.link }
                    val fullUrl = if (link.startsWith("http")) link else "https://www.apkmirror.com$link"
                    updates += KmpUpdateCandidate(
                        name = item.app?.name?.ifBlank { installed.name } ?: installed.name,
                        packageName = item.pname,
                        currentVersion = installed.version,
                        newVersion = rel.version,
                        currentVersionCode = installed.versionCode,
                        newVersionCode = vc,
                        sourceName = "APKMirror",
                        downloadUrl = fullUrl,
                        releaseNotes = rel.whatsNew.orEmpty(),
                        publishedAt = rel.publishDate,
                        installedShortSha256 = installed.shortSha256,
                        requiresBrowserFallback = true
                    )
                }
            } catch (t: Throwable) {
                fail++
                if (firstError == null) firstError = t.message ?: t::class.simpleName
            }
        }

        return KmpSourceCheckReport(
            sourceName = "APKMirror",
            updates = updates,
            successfulRequests = ok,
            failedRequests = fail,
            errorMessage = firstError,
            durationMs = (currentTimeMillis() - started).coerceAtLeast(0L)
        )
    }
}
