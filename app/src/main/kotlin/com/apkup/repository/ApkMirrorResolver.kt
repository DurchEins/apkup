package com.apkup.repository

import android.os.Build
import android.util.Log
import com.apkup.util.AppJournal
import com.apkup.util.WebKitCookieJar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ApkMirrorResolutionResult {
    data class Success(
        val directUrl: String,
        val isApkm: Boolean,
        val filename: String
    ) : ApkMirrorResolutionResult

    data class CloudflareBlocked(
        val fallbackUrl: String
    ) : ApkMirrorResolutionResult

    data class Failed(
        val error: Throwable,
        val fallbackUrl: String
    ) : ApkMirrorResolutionResult
}

class ApkMirrorResolver(
    private val client: OkHttpClient,
    private val minDelayMs: Long = 1500L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val cloudflareBypass: ApkMirrorCloudflareBypass? = null,
    private val cloudflareCooldownMs: Long = DEFAULT_CLOUDFLARE_COOLDOWN_MS
) {
    companion object {
        private const val TAG = "ApkMirrorResolver"
        const val DEFAULT_CLOUDFLARE_COOLDOWN_MS = 10 * 60 * 1000L
        const val BASE_URL = "https://www.apkmirror.com"
        const val MAX_HTML_BYTES = 2 * 1024 * 1024 // 2 MB cap against OOM-DoS (HIGH-08)
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        private val DOWNLOAD_BUTTON_CLASS_FIRST_REGEX = Regex(
            """<a\s+[^>]*class=["'][^"']*\bdownloadButton\b[^"']*["'][^>]*href=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val DOWNLOAD_BUTTON_HREF_FIRST_REGEX = Regex(
            """<a\s+[^>]*href=["']([^"']+)["'][^>]*class=["'][^"']*\bdownloadButton\b[^"']*["']""",
            RegexOption.IGNORE_CASE
        )
        private val DOWNLOAD_PATH_FALLBACK_REGEX = Regex(
            """<a\s+[^>]*href=["'](/apk/[^"']+/download/(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        )
        private val DOWNLOAD_QUERY_FALLBACK_REGEX = Regex(
            """href=["']([^"']*/download/\?key=[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )

        private val VARIANT_DOWNLOAD_PAGE_REGEX = Regex(
            """<a\s+[^>]*href=["'](/apk/[^"']+-(?:android-apk-download|bundle-download)/)["']""",
            RegexOption.IGNORE_CASE
        )

        private val DOWNLOAD_PHP_ANCHOR_REGEX = Regex(
            """<a\s+[^>]*href=["']([^"']*download\.php[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        private val DOWNLOAD_PHP_HREF_REGEX = Regex(
            """href=["']([^"']*download\.php\?[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )

        private val CONTENT_DISPOSITION_FILENAME_REGEX = Regex(
            """filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""",
            RegexOption.IGNORE_CASE
        )

        fun isTrustedApkMirrorUrl(rawUrl: String): Boolean {
            val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
            return WebKitCookieJar.isTrustedApkMirrorHttpUrl(parsed)
        }

        fun extractFilenameFromHeader(disposition: String?, url: String?): String {
            val fromHeader = disposition?.let {
                CONTENT_DISPOSITION_FILENAME_REGEX.find(it)?.groupValues?.get(1)?.trim('"', '\'')
            }
            if (!fromHeader.isNullOrBlank()) return fromHeader

            val fromUrl = url?.let {
                runCatching { URI(it).path.substringAfterLast('/') }.getOrNull()
            }
            if (!fromUrl.isNullOrBlank()) return fromUrl

            return "update.apk"
        }

        fun isApkmFile(filename: String?, url: String?): Boolean {
            val cleanFilename = filename?.substringBefore('?')?.trim().orEmpty()
            val cleanUrl = url?.substringBefore('?')?.trim().orEmpty()
            return cleanFilename.endsWith(".apkm", ignoreCase = true) ||
                cleanFilename.endsWith(".apks", ignoreCase = true) ||
                cleanUrl.endsWith(".apkm", ignoreCase = true) ||
                cleanUrl.endsWith(".apks", ignoreCase = true) ||
                cleanUrl.contains(".apkm?", ignoreCase = true)
        }

        fun extractDownloadButtonHref(html: String): String? {
            val match = DOWNLOAD_BUTTON_CLASS_FIRST_REGEX.find(html)
                ?: DOWNLOAD_BUTTON_HREF_FIRST_REGEX.find(html)
                ?: DOWNLOAD_PATH_FALLBACK_REGEX.find(html)
                ?: DOWNLOAD_QUERY_FALLBACK_REGEX.find(html)
            return match?.groupValues?.get(1)?.replace("&amp;", "&")
        }

        fun extractVariantPageHref(html: String): String? {
            val matches = VARIANT_DOWNLOAD_PAGE_REGEX.findAll(html).map { it.groupValues[1] }.toList()
            if (matches.isEmpty()) return null
            // Prefer standalone APK variant over bundle when both are offered on a release page
            return matches.firstOrNull { it.contains("android-apk-download", ignoreCase = true) }
                ?: matches.first()
        }

        fun extractDownloadPhpHref(html: String): String? {
            val match = DOWNLOAD_PHP_ANCHOR_REGEX.find(html)
                ?: DOWNLOAD_PHP_HREF_REGEX.find(html)
            return match?.groupValues?.get(1)?.replace("&amp;", "&")
        }

        fun canonicalizeUrl(href: String, baseUrl: String): String {
            val cleaned = href.trim()
            return when {
                cleaned.startsWith("https://", ignoreCase = true) -> cleaned
                cleaned.startsWith("http://", ignoreCase = true) -> {
                    "https://" + cleaned.substring("http://".length)
                }
                cleaned.startsWith("//") -> "https:$cleaned"
                cleaned.startsWith("/") -> "$BASE_URL$cleaned"
                else -> runCatching {
                    URI(baseUrl).resolve(cleaned).toString()
                }.getOrDefault("$BASE_URL/$cleaned")
            }
        }

        fun isCloudflareChallenge(response: Response, bodySnippet: String?): Boolean {
            val code = response.code
            val server = response.header("Server").orEmpty()
            val mitigated = response.header("cf-mitigated").orEmpty()
            val hasCfHeaders = server.contains("cloudflare", ignoreCase = true) ||
                mitigated.contains("challenge", ignoreCase = true) ||
                response.header("cf-ray") != null

            if (code in listOf(403, 429, 503) && hasCfHeaders) {
                return true
            }
            if (mitigated.contains("challenge", ignoreCase = true)) {
                return true
            }
            if (!bodySnippet.isNullOrBlank()) {
                val markers = listOf(
                    "cf-browser-verification",
                    "Just a moment...",
                    "challenge-platform",
                    "turnstile",
                    "cf-please-wait",
                    "Attention Required! | Cloudflare",
                    "challenge-form"
                )
                if (markers.any { bodySnippet.contains(it, ignoreCase = true) }) {
                    return true
                }
            }
            if (code in listOf(403, 429)) {
                return true
            }
            return false
        }

        internal fun readBoundedString(stream: InputStream, maxBytes: Int = MAX_HTML_BYTES): String {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            var total = 0
            stream.use { input ->
                var read = input.read(buffer)
                while (read >= 0) {
                    val toWrite = (maxBytes - total).coerceAtMost(read)
                    if (toWrite > 0) {
                        output.write(buffer, 0, toWrite)
                        total += toWrite
                    }
                    if (total >= maxBytes) break
                    read = input.read(buffer)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private val mutex = Mutex()
    private var lastRequestTimeMs = 0L
    @Volatile private var lastWasBlocked = false
    private val appCooldowns = ConcurrentHashMap<String, Long>()
    private val activeCalls = ConcurrentHashMap<String, CopyOnWriteArrayList<Call>>()

    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).build()
    }

    fun isCloudflareBlocked(): Boolean = lastWasBlocked

    fun resetBlockedStatus() {
        lastWasBlocked = false
        appCooldowns.clear()
    }

    fun isAppInCooldown(variantUrl: String? = null, packageName: String? = null): Boolean {
        val now = clock()
        val keys = buildCooldownKeys(variantUrl, packageName)
        if (keys.isEmpty()) return false
        var active = false
        for (key in keys) {
            val blockedUntil = appCooldowns[key] ?: continue
            if (now < blockedUntil) {
                active = true
            } else {
                appCooldowns.remove(key, blockedUntil)
            }
        }
        return active
    }

    fun recordCloudflareBlocked(variantUrl: String? = null, packageName: String? = null) {
        lastWasBlocked = true
        val blockedUntil = clock() + cloudflareCooldownMs
        val keys = buildCooldownKeys(variantUrl, packageName)
        for (key in keys) {
            appCooldowns[key] = blockedUntil
        }
        val targetLabel = packageName?.takeIf { it.isNotBlank() } ?: variantUrl?.takeIf { it.isNotBlank() } ?: "unknown"
        AppJournal.record("ApkMirror", "Cloudflare cooldown (${cloudflareCooldownMs / 60_000L}m) active for $targetLabel")
        com.apkup.util.SourceHealthTracker.recordCooldown(
            sourceName = "APKMirror",
            cooldownUntilMillis = blockedUntil,
            reason = "Cloudflare cooldown ($targetLabel)"
        )
    }

    fun clearCooldown(variantUrl: String? = null, packageName: String? = null) {
        lastWasBlocked = false
        for (key in buildCooldownKeys(variantUrl, packageName)) {
            appCooldowns.remove(key)
        }
    }

    private fun buildCooldownKeys(variantUrl: String?, packageName: String?): List<String> = buildList {
        packageName?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { add("pkg:$it") }
        variantUrl?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { add("url:$it") }
    }

    fun cancelResolution(callTag: String) {
        val calls = activeCalls.remove(callTag) ?: return
        for (call in calls) {
            if (!call.isCanceled()) {
                runCatching { call.cancel() }
            }
        }
    }

    private fun registerCall(tag: String?, call: Call) {
        if (tag == null) return
        activeCalls.computeIfAbsent(tag) { CopyOnWriteArrayList() }.add(call)
    }

    private fun unregisterCall(tag: String?, call: Call) {
        if (tag == null) return
        activeCalls[tag]?.let { list ->
            list.remove(call)
            if (list.isEmpty()) {
                activeCalls.remove(tag, list)
            }
        }
    }

    val currentBrowserUserAgent: String
        get() = cloudflareBypass?.getUserAgent()?.takeIf { it.isNotBlank() } ?: BROWSER_USER_AGENT

    suspend fun resolveDirectDownload(
        variantUrl: String,
        callTag: String? = null,
        packageName: String? = null
    ): ApkMirrorResolutionResult {
        if (!isTrustedApkMirrorUrl(variantUrl)) {
            return ApkMirrorResolutionResult.Failed(
                SecurityException("Rejected untrusted APKMirror URL: $variantUrl"),
                variantUrl
            )
        }

        currentCoroutineContext().ensureActive()
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            enforceRateLimit()
            currentCoroutineContext().ensureActive()
            try {
                // Step 1: Request variant (or release) URL
                var step1Result = executeStep1(variantUrl, callTag)
                currentCoroutineContext().ensureActive()
                if (step1Result is Step1Result.Cloudflare && cloudflareBypass != null) {
                    val solved = cloudflareBypass.solve(variantUrl)
                    currentCoroutineContext().ensureActive()
                    if (solved) {
                        val capturedHtml = cloudflareBypass.getResolvedHtml(variantUrl)
                        if (!capturedHtml.isNullOrBlank()) {
                            val intermediateHref = extractDownloadButtonHref(capturedHtml)
                            if (intermediateHref != null) {
                                val intermediateUrl = canonicalizeUrl(intermediateHref, variantUrl)
                                if (isTrustedApkMirrorUrl(intermediateUrl)) {
                                    val isApkmCandidate = isApkmBundleHtml(variantUrl, intermediateUrl, capturedHtml)
                                    step1Result = Step1Result.Success(intermediateUrl, isApkmCandidate)
                                }
                            }
                        }
                        if (step1Result !is Step1Result.Success) {
                            step1Result = executeStep1(variantUrl, callTag)
                        }
                    }
                }
                if (step1Result !is Step1Result.Success) {
                    val result = when (step1Result) {
                        is Step1Result.Cloudflare -> {
                            recordCloudflareBlocked(variantUrl, packageName)
                            ApkMirrorResolutionResult.CloudflareBlocked(variantUrl)
                        }
                        is Step1Result.Failure -> ApkMirrorResolutionResult.Failed(step1Result.error, variantUrl)
                    }
                    return@withLock result
                }

                // Polite delay between Step 1 and Step 2
                delayFn(minDelayMs)
                currentCoroutineContext().ensureActive()
                lastRequestTimeMs = clock()

                // Step 2: Request intermediate landing page
                var step2Result = executeStep2(step1Result.intermediateUrl, referer = variantUrl, callTag = callTag)
                currentCoroutineContext().ensureActive()
                if (step2Result is Step2Result.Cloudflare && cloudflareBypass != null) {
                    val solved = cloudflareBypass.solve(step1Result.intermediateUrl)
                    currentCoroutineContext().ensureActive()
                    if (solved) {
                        val capturedHtml = cloudflareBypass.getResolvedHtml(step1Result.intermediateUrl)
                        if (!capturedHtml.isNullOrBlank()) {
                            val downloadPhpHref = extractDownloadPhpHref(capturedHtml)
                            if (downloadPhpHref != null) {
                                val downloadPhpUrl = canonicalizeUrl(downloadPhpHref, step1Result.intermediateUrl)
                                if (isTrustedApkMirrorUrl(downloadPhpUrl)) {
                                    step2Result = Step2Result.Success(downloadPhpUrl)
                                }
                            }
                        }
                        if (step2Result !is Step2Result.Success) {
                            step2Result = executeStep2(step1Result.intermediateUrl, referer = variantUrl, callTag = callTag)
                        }
                    }
                }
                if (step2Result !is Step2Result.Success) {
                    val result = when (step2Result) {
                        is Step2Result.Cloudflare -> {
                            recordCloudflareBlocked(variantUrl, packageName)
                            ApkMirrorResolutionResult.CloudflareBlocked(variantUrl)
                        }
                        is Step2Result.Failure -> ApkMirrorResolutionResult.Failed(step2Result.error, variantUrl)
                    }
                    return@withLock result
                }

                // Step 3: Resolve download.php direct redirect
                var step3Result = executeStep3(
                    downloadPhpUrl = step2Result.downloadPhpUrl,
                    referer = step1Result.intermediateUrl,
                    fallbackUrl = variantUrl,
                    isApkmCandidate = step1Result.isApkmCandidate,
                    callTag = callTag
                )
                currentCoroutineContext().ensureActive()
                if (step3Result is ApkMirrorResolutionResult.CloudflareBlocked && cloudflareBypass != null) {
                    val solved = cloudflareBypass.solve(step2Result.downloadPhpUrl)
                    currentCoroutineContext().ensureActive()
                    if (solved) {
                        val interceptedDirect = cloudflareBypass.getInterceptedDownloadUrl()
                        if (!interceptedDirect.isNullOrBlank() && isTrustedApkMirrorUrl(interceptedDirect)) {
                            val filename = extractFilenameFromHeader(null, interceptedDirect)
                            val isApkm = isApkmFile(filename, interceptedDirect) || step1Result.isApkmCandidate
                            step3Result = ApkMirrorResolutionResult.Success(
                                directUrl = interceptedDirect,
                                isApkm = isApkm,
                                filename = filename
                            )
                        } else {
                            step3Result = executeStep3(
                                downloadPhpUrl = step2Result.downloadPhpUrl,
                                referer = step1Result.intermediateUrl,
                                fallbackUrl = variantUrl,
                                isApkmCandidate = step1Result.isApkmCandidate,
                                callTag = callTag
                            )
                        }
                    }
                }
                if (step3Result is ApkMirrorResolutionResult.CloudflareBlocked) {
                    recordCloudflareBlocked(variantUrl, packageName)
                } else if (step3Result is ApkMirrorResolutionResult.Success) {
                    clearCooldown(variantUrl, packageName)
                }
                step3Result
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (error is IOException && error.message?.contains("Canceled", ignoreCase = true) == true) {
                    throw CancellationException("APKMirror resolution call canceled", error)
                }
                Log.w(TAG, "Resolution failed for $variantUrl (${error.javaClass.simpleName})")
                ApkMirrorResolutionResult.Failed(error, variantUrl)
            } finally {
                lastRequestTimeMs = clock()
            }
        }
    }

    private suspend fun enforceRateLimit() {
        val now = clock()
        val elapsed = now - lastRequestTimeMs
        if (elapsed < minDelayMs && lastRequestTimeMs > 0L) {
            val waitTime = minDelayMs - elapsed
            delayFn(waitTime)
        }
    }

    private fun isApkmBundleHtml(variantUrl: String, intermediateUrl: String, body: String): Boolean {
        return variantUrl.contains("apk-bundle", ignoreCase = true) ||
            variantUrl.contains("bundle-download", ignoreCase = true) ||
            intermediateUrl.contains("apk-bundle", ignoreCase = true) ||
            intermediateUrl.contains("bundle-download", ignoreCase = true) ||
            Regex(
                """<a\b[^>]*\bdownloadButton\b[^>]*>[\s\S]*?Download\s+APK\s+Bundle[\s\S]*?</a>""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(body)
    }

    private suspend fun executeStep1(
        variantUrl: String,
        callTag: String? = null,
        allowReleaseHop: Boolean = true
    ): Step1Result {
        if (!isTrustedApkMirrorUrl(variantUrl)) {
            return Step1Result.Failure(SecurityException("Untrusted variant URL: $variantUrl"))
        }

        val request = Request.Builder()
            .url(variantUrl)
            .header("User-Agent", currentBrowserUserAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
            .build()

        val call = client.newCall(request)
        registerCall(callTag, call)
        val hopVariantUrl: String?
        try {
            call.execute().use { response ->
                val body = readBoundedString(response.body.byteStream())
                if (isCloudflareChallenge(response, body)) {
                    return Step1Result.Cloudflare
                }
                if (!response.isSuccessful) {
                    return Step1Result.Failure(IOException("Variant page returned HTTP ${response.code}"))
                }

                val intermediateHref = extractDownloadButtonHref(body)
                if (intermediateHref != null) {
                    val intermediateUrl = canonicalizeUrl(intermediateHref, variantUrl)
                    if (!isTrustedApkMirrorUrl(intermediateUrl)) {
                        return Step1Result.Failure(SecurityException("Untrusted intermediate URL: $intermediateUrl"))
                    }
                    val isApkmCandidate = isApkmBundleHtml(variantUrl, intermediateUrl, body)
                    return Step1Result.Success(intermediateUrl, isApkmCandidate)
                }

                // Keyless fallback support: if variantUrl was a release overview page, hop once to its variant page
                val variantHref = if (allowReleaseHop) extractVariantPageHref(body) else null
                hopVariantUrl = variantHref?.let { canonicalizeUrl(it, variantUrl) }
                if (hopVariantUrl == null) {
                    return Step1Result.Failure(IOException("Failed to locate download button on variant page"))
                }
            }
        } finally {
            unregisterCall(callTag, call)
        }

        delayFn(minDelayMs)
        currentCoroutineContext().ensureActive()
        val resolvedHopUrl = hopVariantUrl
            ?: return Step1Result.Failure(IOException("Failed to locate download button on variant page"))
        return executeStep1(resolvedHopUrl, callTag, allowReleaseHop = false)
    }

    private fun executeStep2(intermediateUrl: String, referer: String, callTag: String? = null): Step2Result {
        if (!isTrustedApkMirrorUrl(intermediateUrl)) {
            return Step2Result.Failure(SecurityException("Untrusted intermediate URL: $intermediateUrl"))
        }

        val request = Request.Builder()
            .url(intermediateUrl)
            .header("User-Agent", currentBrowserUserAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
            .header("Referer", referer)
            .build()

        val call = client.newCall(request)
        registerCall(callTag, call)
        try {
            call.execute().use { response ->
                val body = readBoundedString(response.body.byteStream())
                if (isCloudflareChallenge(response, body)) {
                    return Step2Result.Cloudflare
                }
                if (!response.isSuccessful) {
                    return Step2Result.Failure(IOException("Intermediate page returned HTTP ${response.code}"))
                }

                val downloadPhpHref = extractDownloadPhpHref(body)
                    ?: return Step2Result.Failure(IOException("Failed to locate download.php link on intermediate page"))

                val downloadPhpUrl = canonicalizeUrl(downloadPhpHref, intermediateUrl)
                if (!isTrustedApkMirrorUrl(downloadPhpUrl)) {
                    return Step2Result.Failure(SecurityException("Untrusted download.php URL: $downloadPhpUrl"))
                }
                return Step2Result.Success(downloadPhpUrl)
            }
        } finally {
            unregisterCall(callTag, call)
        }
    }

    private fun executeStep3(
        downloadPhpUrl: String,
        referer: String,
        fallbackUrl: String,
        isApkmCandidate: Boolean,
        callTag: String? = null
    ): ApkMirrorResolutionResult {
        if (!isTrustedApkMirrorUrl(downloadPhpUrl)) {
            return ApkMirrorResolutionResult.Failed(
                SecurityException("Untrusted download.php URL: $downloadPhpUrl"),
                fallbackUrl
            )
        }

        val request = Request.Builder()
            .url(downloadPhpUrl)
            .header("User-Agent", currentBrowserUserAgent)
            .header("Accept", "*/*")
            .header("Referer", referer)
            .build()

        val call = noRedirectClient.newCall(request)
        registerCall(callTag, call)
        try {
            call.execute().use { response ->
                if (isCloudflareChallenge(response, null)) {
                    return ApkMirrorResolutionResult.CloudflareBlocked(fallbackUrl)
                }

                val location = response.header("Location")
                val directUrl = when {
                    response.code in listOf(301, 302, 303, 307, 308) && !location.isNullOrBlank() -> {
                        canonicalizeUrl(location, downloadPhpUrl)
                    }
                    response.isSuccessful -> downloadPhpUrl
                    else -> {
                        return ApkMirrorResolutionResult.Failed(
                            IOException("download.php returned unexpected HTTP ${response.code}"),
                            fallbackUrl
                        )
                    }
                }

                val parsedDirect = directUrl.toHttpUrlOrNull()
                if (parsedDirect == null || !parsedDirect.isHttps) {
                    return ApkMirrorResolutionResult.Failed(
                        SecurityException("Rejected non-HTTPS CDN download URL: $directUrl"),
                        fallbackUrl
                    )
                }

                val filename = extractFilenameFromHeader(response.header("Content-Disposition"), directUrl)
                val isApkm = isApkmFile(filename, directUrl) || isApkmCandidate

                return ApkMirrorResolutionResult.Success(
                    directUrl = directUrl,
                    isApkm = isApkm,
                    filename = filename
                )
            }
        } finally {
            unregisterCall(callTag, call)
        }
    }

    private sealed interface Step1Result {
        data class Success(val intermediateUrl: String, val isApkmCandidate: Boolean) : Step1Result
        data object Cloudflare : Step1Result
        data class Failure(val error: Throwable) : Step1Result
    }

    private sealed interface Step2Result {
        data class Success(val downloadPhpUrl: String) : Step2Result
        data object Cloudflare : Step2Result
        data class Failure(val error: Throwable) : Step2Result
    }
}
