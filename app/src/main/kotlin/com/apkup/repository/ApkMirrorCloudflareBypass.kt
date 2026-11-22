package com.apkup.repository

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.apkup.util.WebKitCookieJar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for sandboxed APKMirror page DOM capture when OkHttp receives an HTTP error,
 * falling back cleanly to the user's visible browser whenever an interactive gate is present.
 */
interface ApkMirrorCloudflareBypass {
    suspend fun solve(url: String, timeoutMs: Long = 15000L): Boolean
    fun getResolvedHtml(url: String): String? = null
    fun getInterceptedDownloadUrl(): String? = null
    fun getUserAgent(): String
}

class AndroidApkMirrorCloudflareBypass(
    private val context: Context
) : ApkMirrorCloudflareBypass {

    companion object {
        private const val TAG = "ApkMirrorBypass"
        private const val MAX_HTML_CACHE_ENTRIES = 4
        private const val MAX_CAPTURED_HTML_CHARS = 2 * 1024 * 1024
        private const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        internal fun extractCookieValue(rawCookies: String?, cookieName: String): String? {
            if (rawCookies.isNullOrBlank()) return null
            return rawCookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("$cookieName=") }
                ?.substringAfter('=')
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    private val solveMutex = Mutex()
    private val htmlMap = ConcurrentHashMap<String, String>()
    @Volatile private var interceptedUrl: String? = null

    private val defaultUserAgent: String by lazy {
        runCatching {
            WebSettings.getDefaultUserAgent(context)
        }.getOrDefault(FALLBACK_USER_AGENT)
    }

    override fun getUserAgent(): String = defaultUserAgent

    override fun getResolvedHtml(url: String): String? = htmlMap.remove(url)

    override fun getInterceptedDownloadUrl(): String? = interceptedUrl

    private fun storeBoundedHtml(key: String, html: String) {
        if (html.isBlank()) return
        if (htmlMap.size >= MAX_HTML_CACHE_ENTRIES) {
            htmlMap.clear()
        }
        htmlMap[key] = if (html.length > MAX_CAPTURED_HTML_CHARS) {
            html.substring(0, MAX_CAPTURED_HTML_CHARS)
        } else {
            html
        }
    }

    override suspend fun solve(url: String, timeoutMs: Long): Boolean = solveMutex.withLock {
        val parsedInitialUrl = url.toHttpUrlOrNull()
        if (parsedInitialUrl == null || !WebKitCookieJar.isTrustedApkMirrorHttpUrl(parsedInitialUrl)) {
            Log.w(TAG, "Rejected untrusted or non-HTTPS URL in WebView sandbox: $url")
            return@withLock false
        }

        withContext(Dispatchers.Main) {
            val completable = CompletableDeferred<Boolean>()
            var webView: WebView? = null
            interceptedUrl = null
            htmlMap.clear()

            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                val initialClearance = extractCookieValue(cookieManager.getCookie(url), "cf_clearance")

                webView = WebView(context).apply {
                    layout(0, 0, 1080, 1920)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        userAgentString = defaultUserAgent
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    cookieManager.setAcceptThirdPartyCookies(this, false)

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUri = request?.url ?: return true
                            val scheme = reqUri.scheme.orEmpty().lowercase(Locale.ROOT)
                            val host = reqUri.host.orEmpty().lowercase(Locale.ROOT)

                            // Strictly block all non-HTTPS schemes and off-domain navigations (HIGH-07)
                            if (scheme != "https" || !WebKitCookieJar.isTrustedApkMirrorHost(host)) {
                                Log.w(TAG, "Blocked off-domain or non-HTTPS WebView navigation: $scheme://$host")
                                if (!completable.isCompleted && request.isForMainFrame) {
                                    completable.complete(false)
                                }
                                return true
                            }

                            val targetUrl = reqUri.toString()
                            if (
                                host == "download.apkmirror.com" ||
                                host == "s.apkmirror.com" ||
                                targetUrl.contains(".apk", ignoreCase = true) ||
                                targetUrl.contains(".apkm", ignoreCase = true) ||
                                targetUrl.contains("/wp-content/themes/responsive-mobile/download.php", ignoreCase = true)
                            ) {
                                interceptedUrl = targetUrl
                                cookieManager.flush()
                                if (!completable.isCompleted) {
                                    completable.complete(true)
                                }
                                return true
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            val target = finishedUrl ?: url
                            val parsedTarget = target.toHttpUrlOrNull()
                            if (parsedTarget == null || !WebKitCookieJar.isTrustedApkMirrorHttpUrl(parsedTarget)) {
                                if (!completable.isCompleted) completable.complete(false)
                                return
                            }

                            val title = view?.title.orEmpty()
                            val isInteractiveGate = title.contains("Just a moment", ignoreCase = true) ||
                                title.contains("Cloudflare", ignoreCase = true) ||
                                title.contains("Attention Required", ignoreCase = true) ||
                                title.contains("Security Check", ignoreCase = true) ||
                                title.contains("Turnstile", ignoreCase = true)

                            if (isInteractiveGate) {
                                // Never attempt to solve or wait out an interactive anti-bot/CAPTCHA gate;
                                // abort immediately so ApkMirrorResolver hands off to the user's visible browser.
                                if (!completable.isCompleted) {
                                    completable.complete(false)
                                }
                                return
                            }

                            val isApkMirrorContentPage = title.contains("APKMirror", ignoreCase = true)
                            if (isApkMirrorContentPage) {
                                captureDomAndComplete(view, url, target, cookieManager, completable)
                            }
                        }
                    }

                    loadUrl(url, mapOf("Accept-Language" to "en-US,en;q=0.9,de;q=0.8"))
                }

                val result = withTimeoutOrNull(timeoutMs) {
                    val poller = launch {
                        while (isActive && !completable.isCompleted) {
                            delay(400)
                            val currentClearance = extractCookieValue(cookieManager.getCookie(url), "cf_clearance")
                            if (currentClearance != null && currentClearance != initialClearance) {
                                val activeView = webView
                                val activeTitle = activeView.title.orEmpty()
                                if (activeTitle.contains("APKMirror", ignoreCase = true)) {
                                    captureDomAndComplete(activeView, url, url, cookieManager, completable)
                                    break
                                }
                            }
                        }
                    }
                    try {
                        completable.await()
                    } finally {
                        poller.cancel()
                    }
                } ?: false

                result
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Log.w(TAG, "WebView sandbox aborted for $url (${error.javaClass.simpleName})")
                false
            } finally {
                runCatching {
                    webView?.stopLoading()
                    webView?.loadUrl("about:blank")
                    webView?.removeAllViews()
                    webView?.destroy()
                }
            }
        }
    }

    private fun captureDomAndComplete(
        view: WebView?,
        initialUrl: String,
        targetUrl: String,
        cookieManager: CookieManager,
        completable: CompletableDeferred<Boolean>
    ) {
        if (view == null) {
            if (!completable.isCompleted) completable.complete(false)
            return
        }
        view.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { rawHtml ->
            if (!rawHtml.isNullOrBlank() && rawHtml != "null") {
                val unescaped = runCatching {
                    org.json.JSONTokener(rawHtml).nextValue().toString()
                }.getOrDefault(rawHtml)
                storeBoundedHtml(initialUrl, unescaped)
                storeBoundedHtml(targetUrl, unescaped)
            }
            cookieManager.flush()
            if (!completable.isCompleted) {
                completable.complete(true)
            }
        }
    }
}
