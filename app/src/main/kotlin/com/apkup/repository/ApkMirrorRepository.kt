package com.apkup.repository

import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.apkmirror.AppExistsRequest
import com.apkup.data.apkmirror.AppExistsResponseApk
import com.apkup.data.apkmirror.AppExistsResponseData
import com.apkup.data.apkmirror.toAppUpdate
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.Link
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.filterNewerCandidates
import com.apkup.data.ui.getPackageNames
import com.apkup.data.ui.isCompatibleFormFactor
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.data.ui.matchesSignature
import com.apkup.data.ui.supportsArchitecture
import com.apkup.data.ui.supportsMinApi
import com.apkup.service.ApkMirrorService
import com.apkup.util.WebKitCookieJar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

internal const val APKMIRROR_REQUEST_BATCH_SIZE = 25
internal const val APKMIRROR_REQUEST_CONCURRENCY = 6
internal const val APKMIRROR_WORKER_STAGGER_MS = 30L
internal const val APKMIRROR_KEYLESS_FALLBACK_MAX_APPS = 20
internal const val APKMIRROR_KEYLESS_STAGGER_MS = 350L
private const val MAX_SEARCH_HTML_BYTES = 2 * 1024 * 1024 // 2 MB

class ApkMirrorRepository(
    private val service: ApkMirrorService,
    @Suppress("UNUSED_PARAMETER") packageManager: PackageManager? = null,
    private val fallbackHttpClient: OkHttpClient? = null
) {

    @Suppress("SENSELESS_COMPARISON")
    private val arch = (Build.SUPPORTED_ABIS as Array<String>?).orEmpty().let { abis ->
        when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            abis.contains("x86_64") -> "x86_64"
            abis.contains("x86") -> "x86"
            else -> "arm64-v8a"
        }
    }

    private val api = Build.VERSION.SDK_INT

    suspend fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ) = flow<SourceUpdateResult<AppUpdate>> {
        val packageNames = apps.getPackageNames()
        if (packageNames.isEmpty()) {
            emit(SourceUpdateResult(emptyList(), successfulRequests = 0, failedRequests = 0))
            return@flow
        }

        val failureSummaries = ConcurrentLinkedQueue<String>()
        val apiBlockedOrUnauthorized = AtomicBoolean(false)
        val batchCount = (packageNames.size + APKMIRROR_REQUEST_BATCH_SIZE - 1) / APKMIRROR_REQUEST_BATCH_SIZE
        if (BuildConfig.DEBUG) {
            Log.d(
                "ApkMirrorRepository",
                "Checking ${packageNames.size} installed apps in $batchCount request batches " +
                    "(up to $APKMIRROR_REQUEST_CONCURRENCY concurrent)"
            )
        }
        val result = requestConcurrentBatches(
            items = packageNames,
            batchSize = APKMIRROR_REQUEST_BATCH_SIZE,
            concurrency = APKMIRROR_REQUEST_CONCURRENCY,
            workerStaggerMs = APKMIRROR_WORKER_STAGGER_MS,
            request = { batch ->
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val response = service.appExists(AppExistsRequest(batch, buildIgnoreList())).data
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "ApkMirrorRepository",
                            "Batch with ${batch.size} package IDs succeeded in " +
                                "${SystemClock.elapsedRealtime() - startedAt} ms; ${response.size} responses"
                        )
                    }
                    response
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    if (exception is HttpException && exception.code() in listOf(401, 403, 429)) {
                        apiBlockedOrUnauthorized.set(true)
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "ApkMirrorRepository",
                            "Batch with ${batch.size} package IDs failed in " +
                                "${SystemClock.elapsedRealtime() - startedAt} ms " +
                                "(${describeApkMirrorFailure(exception)})"
                        )
                    }
                    throw exception
                }
            },
            stopSchedulingAfterFailure = { exception ->
                exception is java.net.UnknownHostException ||
                    (exception is HttpException && exception.code() in listOf(401, 403, 429))
            },
            onFailure = { exception ->
                val failure = describeApkMirrorFailure(exception)
                failureSummaries.add(failure)
                Log.w("ApkMirrorRepository", "Update batch failed: $failure")
            },
            onBatchSuccess = onBatchSuccess
        )

        if (result.successfulRequests == 0 && apiBlockedOrUnauthorized.get() && fallbackHttpClient != null) {
            val fallbackResult = fetchKeylessWebFallback(apps, fallbackHttpClient, onBatchSuccess)
            if (fallbackResult != null) {
                emit(fallbackResult)
                return@flow
            }
        }

        val processingStartedAt = SystemClock.elapsedRealtime()
        val updates = parseUpdates(result.responses, apps)
        if (BuildConfig.DEBUG) {
            Log.d(
                "ApkMirrorRepository",
                "Processed ${result.responses.size} response(s) into ${updates.size} update(s) in " +
                    "${SystemClock.elapsedRealtime() - processingStartedAt} ms; " +
                    "${result.successfulRequests} batch(es) succeeded, ${result.failedRequests} failed"
            )
        }
        emit(
            SourceUpdateResult(
                updates,
                result.successfulRequests,
                result.failedRequests,
                failureSummaries.peek()
            )
        )
    }

    /**
     * Keyless public web search fallback (F-18b): used when the APKMirror REST endpoint returns
     * HTTP 401/403/429 (e.g. revoked community key or rate limit). Politely checks non-system
     * APKMirror/sideloaded apps via APKMirror's public search HTML.
     */
    private suspend fun fetchKeylessWebFallback(
        apps: List<AppInstalled>,
        client: OkHttpClient,
        onBatchSuccess: suspend (List<String>) -> Unit
    ): SourceUpdateResult<AppUpdate>? = withContext(Dispatchers.IO) {
        val candidates = apps
            .filter { !it.ignored && !it.isSystemApp }
            .sortedByDescending {
                when (it.installOrigin) {
                    InstallOrigin.APKMIRROR -> 2
                    InstallOrigin.SIDELOAD -> 1
                    else -> 0
                }
            }
            .take(APKMIRROR_KEYLESS_FALLBACK_MAX_APPS)

        if (candidates.isEmpty()) return@withContext null

        val foundUpdates = mutableListOf<AppUpdate>()
        var succeeded = 0
        var failed = 0
        var firstError: String? = null

        for ((index, app) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (index > 0) {
                delay(APKMIRROR_KEYLESS_STAGGER_MS)
            }
            try {
                val searchUrl = "https://www.apkmirror.com/".toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.addQueryParameter("post_type", "app_release")
                    ?.addQueryParameter("searchtype", "app")
                    ?.addQueryParameter("s", app.packageName)
                    ?.build() ?: continue

                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", ApkMirrorResolver.BROWSER_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .get()
                    .build()

                val parsedUpdate = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val html = readBoundedUtf8(response.body.byteStream(), MAX_SEARCH_HTML_BYTES)
                    parseKeylessSearchHtml(html, app)
                }
                succeeded++
                if (parsedUpdate != null && parsedUpdate.isMeaningfulVersionUpgrade()) {
                    foundUpdates.add(parsedUpdate)
                }
                onBatchSuccess(listOf(app.packageName))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                failed++
                if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                if (e.message?.contains("403") == true || e.message?.contains("429") == true) {
                    break
                }
            }
        }

        if (succeeded == 0) return@withContext null
        SourceUpdateResult(
            updates = foundUpdates,
            successfulRequests = succeeded,
            failedRequests = failed,
            failureSummary = if (failed > 0) firstError else null
        )
    }

    internal fun parseUpdates(updates: List<AppExistsResponseData>, apps: List<AppInstalled>): List<AppUpdate> {
        val installedByPackage = apps.associateBy { it.packageName }
        val installedVersions = apps.associate { it.packageName to it.versionCode }
        val candidates = updates.asSequence()
            .filter { it.exists == true }
            .flatMap { data -> data.apks.asSequence().map { data to it } }
        val newerCandidates = filterNewerCandidates(
            candidates.toList(),
            installedVersions,
            packageName = { it.first.pname },
            versionCode = { it.second.versionCode }
        )
        val results = newerCandidates
            .asSequence()
            .filter { (data, apk) -> matchesApkSignature(apk, installedByPackage[data.pname]) }
            .filter { (_, apk) -> filterArch(apk) }
            .filter { (_, apk) -> filterMinApi(apk) }
            .filter { (_, apk) -> isCompatibleFormFactor(apk.capabilities, isAndroidTv = false) }
            .groupBy { it.first.pname }
            .mapNotNull { (packageName, packageCandidates) ->
                val installedApp = installedByPackage[packageName] ?: return@mapNotNull null
                packageCandidates
                    .map { (data, apk) -> apk.toAppUpdate(installedApp, data.release) }
                    .filter { it.isMeaningfulVersionUpgrade() }
                    .maxByOrNull { it.versionCode }
            }
        if (BuildConfig.DEBUG) {
            Log.d(
                "ApkMirrorRepository",
                "Received ${updates.size} app responses; ${results.size} updates passed version and compatibility filters"
            )
        }
        return results
    }

    private fun matchesApkSignature(apk: AppExistsResponseApk, installed: AppInstalled?): Boolean {
        if (installed == null) return false
        if (installed.isWatchedOnly || installed.versionCode < 0L) return true
        if (!apk.signaturesSha256.isNullOrEmpty() && installed.signatureSha256.isNotBlank()) {
            return matchesSignature(apk.signaturesSha256, installed.signatureSha256)
        }
        return matchesSignature(apk.signaturesSha1, installed.signature)
    }

    private fun filterArch(app: AppExistsResponseApk) =
        supportsArchitecture(app.arches, Build.SUPPORTED_ABIS.toList(), arch)

    private fun filterMinApi(apk: AppExistsResponseApk) = supportsMinApi(apk.minapi, api)

    private fun buildIgnoreList() = listOf("alpha", "beta")

    companion object {
        private val VERSION_SUFFIX_REGEX = Regex("""\s([0-9][0-9A-Za-z._+-]*)$""")

        internal fun parseKeylessSearchHtml(html: String, app: AppInstalled): AppUpdate? {
            if (html.isBlank()) return null
            val doc = Jsoup.parse(html, "https://www.apkmirror.com")
            val rows = doc.select("div.appRow")
            for (row in rows) {
                val anchor = row.selectFirst("h5.appRowTitle a[href]") ?: continue
                val title = anchor.attr("title").ifBlank { anchor.text() }.trim()
                val lowerTitle = title.lowercase(Locale.ROOT)
                if (lowerTitle.contains("alpha") || lowerTitle.contains("beta")) continue

                val href = anchor.absUrl("href").ifBlank {
                    val rel = anchor.attr("href").trim()
                    if (rel.startsWith("/")) "https://www.apkmirror.com$rel" else ""
                }
                val parsedUrl = href.toHttpUrlOrNull() ?: continue
                if (!WebKitCookieJar.isTrustedApkMirrorHttpUrl(parsedUrl)) continue

                // Ensure the row either matches the app name prefix or only a single app matched the package query
                val appNamePrefix = app.name.trim().lowercase(Locale.ROOT)
                if (appNamePrefix.length >= 3 && !lowerTitle.contains(appNamePrefix) && rows.size > 2) {
                    continue
                }

                val versionMatch = VERSION_SUFFIX_REGEX.find(title)?.groupValues?.getOrNull(1)?.trim() ?: continue
                if (!ReleaseAssetFilter.isNewer(app.version, versionMatch)) continue

                return AppUpdate(
                    name = app.name,
                    packageName = app.packageName,
                    version = versionMatch,
                    oldVersion = app.version,
                    versionCode = maxOf(1L, app.versionCode + 1L),
                    oldVersionCode = app.versionCode,
                    source = ApkMirrorSource,
                    link = Link.Url(parsedUrl.toString()),
                    installOrigin = app.installOrigin,
                    installerPackageName = app.installerPackageName
                )
            }
            return null
        }

        private fun readBoundedUtf8(input: InputStream, maxBytes: Int): String {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            input.use { stream ->
                var read = stream.read(buffer)
                while (read >= 0) {
                    val allowed = (maxBytes - total).coerceAtMost(read)
                    if (allowed > 0) {
                        out.write(buffer, 0, allowed)
                        total += allowed
                    }
                    if (total >= maxBytes) break
                    read = stream.read(buffer)
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}

internal fun describeApkMirrorFailure(exception: Throwable): String = when {
    exception is java.net.SocketTimeoutException -> {
        val message = generateSequence(exception as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        when {
            message.contains("connect", ignoreCase = true) -> "connect timeout"
            message.contains("read", ignoreCase = true) -> "read timeout"
            message.contains("write", ignoreCase = true) -> "write timeout"
            else -> "socket timeout (phase not reported by HTTP client)"
        }
    }
    exception is java.net.UnknownHostException -> "DNS lookup failed"
    exception is HttpException -> "HTTP ${exception.code()}"
    exception is IOException -> "network error (${exception.javaClass.simpleName})"
    else -> "response processing error (${exception.javaClass.simpleName})"
}
