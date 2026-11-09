package com.apkup.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

sealed interface ApkMirrorResolutionResult {
    /**
     * Successfully resolved variant page to direct download URL.
     * @property directUrl Authenticated CDN download link or direct stream URL.
     * @property isApkm True if the payload is an APKMirror split bundle (.apkm), false for standalone .apk.
     * @property filename Suggested target filename (e.g. "app.apkm" or "app.apk").
     */
    data class Success(
        val directUrl: String,
        val isApkm: Boolean,
        val filename: String
    ) : ApkMirrorResolutionResult

    /**
     * A Cloudflare or anti-bot challenge was encountered.
     * @property fallbackUrl Web URL to open in external browser so user can solve challenge.
     */
    data class CloudflareBlocked(
        val fallbackUrl: String
    ) : ApkMirrorResolutionResult

    /**
     * General network, HTTP, or HTML scraping failure.
     * @property error Root cause exception.
     * @property fallbackUrl Web URL to open in external browser as fallback.
     */
    data class Failed(
        val error: Throwable,
        val fallbackUrl: String
    ) : ApkMirrorResolutionResult
}

class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, CopyOnWriteArrayList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existing = store.getOrPut(host) { CopyOnWriteArrayList() }
        cookies.forEach { newCookie ->
            existing.removeAll { it.name == newCookie.name }
            existing.add(newCookie)
        }
        runCatching {
            val manager = android.webkit.CookieManager.getInstance()
            cookies.forEach { cookie ->
                manager.setCookie(url.toString(), cookie.toString())
            }
            manager.flush()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val inMemoryCookies = store[url.host]?.filter { it.expiresAt > now } ?: emptyList()
        val systemCookies = runCatching {
            val raw = android.webkit.CookieManager.getInstance().getCookie(url.toString())
            if (!raw.isNullOrBlank()) {
                raw.split(";").mapNotNull { part ->
                    Cookie.parse(url, part.trim())
                }
            } else emptyList()
        }.getOrDefault(emptyList())

        return (inMemoryCookies + systemCookies).distinctBy { it.name }
    }
}

class ApkMirrorResolver(
    private val client: OkHttpClient,
    private val minDelayMs: Long = 1500L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    private val cloudflareBypass: ApkMirrorCloudflareBypass? = null
) {
    companion object {
        private const val TAG = "ApkMirrorResolver"
        const val BASE_URL = "https://www.apkmirror.com"
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

        fun extractDownloadPhpHref(html: String): String? {
            val match = DOWNLOAD_PHP_ANCHOR_REGEX.find(html)
                ?: DOWNLOAD_PHP_HREF_REGEX.find(html)
            return match?.groupValues?.get(1)?.replace("&amp;", "&")
        }

        fun canonicalizeUrl(href: String, baseUrl: String): String {
            val cleaned = href.trim()
            return when {
                cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
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
    }

    private val mutex = Mutex()
    private var lastRequestTimeMs = 0L
    private var lastWasBlocked = false
    private val activeCalls = ConcurrentHashMap<String, CopyOnWriteArrayList<Call>>()

    private val noRedirectClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).build()
    }

    fun isCloudflareBlocked(): Boolean = lastWasBlocked

    fun resetBlockedStatus() {
        lastWasBlocked = false
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

    suspend fun resolveDirectDownload(variantUrl: String, callTag: String? = null): ApkMirrorResolutionResult {
        currentCoroutineContext().ensureActive()
        return mutex.withLock {
            currentCoroutineContext().ensureActive()
            enforceRateLimit()
            currentCoroutineContext().ensureActive()
            try {
                // Step 1: Request variant URL
                var step1Result = executeStep1(variantUrl, callTag)
                currentCoroutineContext().ensureActive()
                if (step1Result is Step1Result.Cloudflare && cloudflareBypass != null) {
                    Log.i(TAG, "Cloudflare challenge detected in Step 1. Attempting WebView bypass for $variantUrl")
                    val solved = cloudflareBypass.solve(variantUrl)
                    if (solved) {
                        val capturedHtml = cloudflareBypass.getResolvedHtml(variantUrl)
                        if (!capturedHtml.isNullOrBlank()) {
                            val intermediateHref = extractDownloadButtonHref(capturedHtml)
                            if (intermediateHref != null) {
                                val intermediateUrl = canonicalizeUrl(intermediateHref, variantUrl)
                                val isApkmCandidate = variantUrl.contains("apk-bundle", ignoreCase = true) ||
                                    variantUrl.contains("bundle-download", ignoreCase = true) ||
                                    intermediateUrl.contains("apk-bundle", ignoreCase = true) ||
                                    intermediateUrl.contains("bundle-download", ignoreCase = true) ||
                                    Regex(
                                        """<a\b[^>]*\bdownloadButton\b[^>]*>[\s\S]*?Download\s+APK\s+Bundle[\s\S]*?</a>""",
                                        RegexOption.IGNORE_CASE
                                    ).containsMatchIn(capturedHtml)
                                Log.i(TAG, "Extracted intermediate URL directly from WebView DOM: $intermediateUrl (isApkm=$isApkmCandidate)")
                                step1Result = Step1Result.Success(intermediateUrl, isApkmCandidate)
                            }
                        }
                        if (step1Result !is Step1Result.Success) {
                            Log.i(TAG, "Retrying Step 1 with OkHttp and acquired clearance.")
                            step1Result = executeStep1(variantUrl, callTag)
                        }
                    }
                }
                if (step1Result !is Step1Result.Success) {
                    val result = when (step1Result) {
                        is Step1Result.Cloudflare -> {
                            lastWasBlocked = true
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
                    Log.i(TAG, "Cloudflare challenge detected in Step 2. Attempting WebView bypass for ${step1Result.intermediateUrl}")
                    val solved = cloudflareBypass.solve(step1Result.intermediateUrl)
                    if (solved) {
                        val capturedHtml = cloudflareBypass.getResolvedHtml(step1Result.intermediateUrl)
                        if (!capturedHtml.isNullOrBlank()) {
                            val downloadPhpHref = extractDownloadPhpHref(capturedHtml)
                            if (downloadPhpHref != null) {
                                val downloadPhpUrl = canonicalizeUrl(downloadPhpHref, step1Result.intermediateUrl)
                                Log.i(TAG, "Extracted download.php URL directly from WebView DOM: $downloadPhpUrl")
                                step2Result = Step2Result.Success(downloadPhpUrl)
                            }
                        }
                        if (step2Result !is Step2Result.Success) {
                            Log.i(TAG, "Retrying Step 2 with OkHttp and acquired cookies.")
                            step2Result = executeStep2(step1Result.intermediateUrl, referer = variantUrl, callTag = callTag)
                        }
                    }
                }
                if (step2Result !is Step2Result.Success) {
                    val result = when (step2Result) {
                        is Step2Result.Cloudflare -> {
                            lastWasBlocked = true
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
                    Log.i(TAG, "Cloudflare challenge detected in Step 3. Attempting WebView bypass for ${step2Result.downloadPhpUrl}")
                    val solved = cloudflareBypass.solve(step2Result.downloadPhpUrl)
                    if (solved) {
                        val interceptedDirect = cloudflareBypass.getInterceptedDownloadUrl()
                        if (!interceptedDirect.isNullOrBlank()) {
                            val filename = extractFilenameFromHeader(null, interceptedDirect)
                            val isApkm = isApkmFile(filename, interceptedDirect) || step1Result.isApkmCandidate
                            Log.i(TAG, "Captured direct CDN URL from WebView intercept: $interceptedDirect")
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
                    lastWasBlocked = true
                } else if (step3Result is ApkMirrorResolutionResult.Success) {
                    lastWasBlocked = false
                }
                step3Result
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (error is IOException && error.message?.contains("Canceled", ignoreCase = true) == true) {
                    throw CancellationException("APKMirror resolution call canceled", error)
                }
                Log.e(TAG, "Resolution failed for $variantUrl: ${error.message}", error)
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

    private fun executeStep1(variantUrl: String, callTag: String? = null): Step1Result {
        val requestBuilder = Request.Builder()
            .url(variantUrl)
            .header("User-Agent", currentBrowserUserAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")

        val cookies = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(variantUrl)
        }.getOrNull()
        if (!cookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookies)
        }

        val call = client.newCall(requestBuilder.build())
        registerCall(callTag, call)
        try {
            call.execute().use { response ->
                val body = response.body.string()
                if (isCloudflareChallenge(response, body)) {
                    return Step1Result.Cloudflare
                }
                if (!response.isSuccessful) {
                    return Step1Result.Failure(IOException("Variant page returned HTTP ${response.code}"))
                }

                val intermediateHref = extractDownloadButtonHref(body)
                    ?: return Step1Result.Failure(IOException("Failed to locate download button on variant page"))

                val intermediateUrl = canonicalizeUrl(intermediateHref, variantUrl)
                val isApkmCandidate = variantUrl.contains("apk-bundle", ignoreCase = true) ||
                    variantUrl.contains("bundle-download", ignoreCase = true) ||
                    intermediateUrl.contains("apk-bundle", ignoreCase = true) ||
                    intermediateUrl.contains("bundle-download", ignoreCase = true) ||
                    Regex(
                        """<a\b[^>]*\bdownloadButton\b[^>]*>[\s\S]*?Download\s+APK\s+Bundle[\s\S]*?</a>""",
                        RegexOption.IGNORE_CASE
                    ).containsMatchIn(body)

                return Step1Result.Success(intermediateUrl, isApkmCandidate)
            }
        } finally {
            unregisterCall(callTag, call)
        }
    }

    private fun executeStep2(intermediateUrl: String, referer: String, callTag: String? = null): Step2Result {
        val requestBuilder = Request.Builder()
            .url(intermediateUrl)
            .header("User-Agent", currentBrowserUserAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
            .header("Referer", referer)

        val cookies = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(intermediateUrl)
        }.getOrNull()
        if (!cookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookies)
        }

        val call = client.newCall(requestBuilder.build())
        registerCall(callTag, call)
        try {
            call.execute().use { response ->
                val body = response.body.string()
                if (isCloudflareChallenge(response, body)) {
                    return Step2Result.Cloudflare
                }
                if (!response.isSuccessful) {
                    return Step2Result.Failure(IOException("Intermediate page returned HTTP ${response.code}"))
                }

                val downloadPhpHref = extractDownloadPhpHref(body)
                    ?: return Step2Result.Failure(IOException("Failed to locate download.php link on intermediate page"))

                val downloadPhpUrl = canonicalizeUrl(downloadPhpHref, intermediateUrl)
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
        val requestBuilder = Request.Builder()
            .url(downloadPhpUrl)
            .header("User-Agent", currentBrowserUserAgent)
            .header("Accept", "*/*")
            .header("Referer", referer)

        val cookies = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(downloadPhpUrl)
        }.getOrNull()
        if (!cookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", cookies)
        }

        val call = noRedirectClient.newCall(requestBuilder.build())
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
