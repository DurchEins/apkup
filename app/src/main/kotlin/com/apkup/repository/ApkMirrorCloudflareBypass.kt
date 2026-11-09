package com.apkup.repository

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface allowing headless/background resolution of Cloudflare challenges for APKMirror.
 * Extracted as interface to ensure 100% testability on JVM without Android runtime stubs.
 */
interface ApkMirrorCloudflareBypass {
    /**
     * Attempts to resolve a Cloudflare Managed Challenge for [url] by executing its JavaScript
     * in an Android Chromium WebView, acquiring and saving the `cf_clearance` cookie and rendered HTML.
     *
     * @param url Target URL currently guarded by Cloudflare.
     * @param timeoutMs Maximum time to wait for challenge resolution.
     * @return True if challenge was solved (or cookie acquired), false on timeout or error.
     */
    suspend fun solve(url: String, timeoutMs: Long = 15000L): Boolean

    /**
     * Returns the fully rendered HTML DOM captured from the WebView for [url], if available.
     * This avoids redundant and blocked re-fetches by OkHttp when the browser has already loaded the page.
     */
    fun getResolvedHtml(url: String): String? = null

    /**
     * Returns any direct CDN download URL intercepted during WebView redirect navigation.
     */
    fun getInterceptedDownloadUrl(): String? = null

    /**
     * Returns the system WebView User-Agent string to ensure OkHttp matches the exact TLS
     * and browser identity that solved the challenge.
     */
    fun getUserAgent(): String
}

class AndroidApkMirrorCloudflareBypass(
    private val context: Context
) : ApkMirrorCloudflareBypass {

    companion object {
        private const val TAG = "ApkMirrorBypass"
        private const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    private val htmlMap = ConcurrentHashMap<String, String>()
    @Volatile private var interceptedUrl: String? = null

    private val defaultUserAgent: String by lazy {
        runCatching {
            WebSettings.getDefaultUserAgent(context)
        }.getOrDefault(FALLBACK_USER_AGENT)
    }

    override fun getUserAgent(): String = defaultUserAgent

    override fun getResolvedHtml(url: String): String? = htmlMap[url]

    override fun getInterceptedDownloadUrl(): String? = interceptedUrl

    override suspend fun solve(url: String, timeoutMs: Long): Boolean = withContext(Dispatchers.Main) {
        val completable = CompletableDeferred<Boolean>()
        var webView: WebView? = null
        interceptedUrl = null

        try {
            Log.i(TAG, "Initializing headless WebView to solve Cloudflare challenge for $url")
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            webView = WebView(context).apply {
                layout(0, 0, 1080, 1920)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    userAgentString = defaultUserAgent
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val targetUrl = request?.url?.toString() ?: return false
                        val host = request.url?.host.orEmpty().lowercase()
                        val isApkMirrorDomain = host == "apkmirror.com" ||
                            host.endsWith(".apkmirror.com") ||
                            host == "apkmirror.net" ||
                            host.endsWith(".apkmirror.net")

                        if (isApkMirrorDomain && (
                            host == "download.apkmirror.com" ||
                            host == "s.apkmirror.com" ||
                            targetUrl.contains(".apk", ignoreCase = true) ||
                            targetUrl.contains(".apkm", ignoreCase = true) ||
                            targetUrl.contains("/wp-content/themes/responsive-mobile/download.php", ignoreCase = true)
                        )) {
                            Log.i(TAG, "Intercepted direct download URL in WebView navigation: $targetUrl")
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
                        val cookies = cookieManager.getCookie(target).orEmpty()
                        val title = view?.title.orEmpty()
                        val hasCfClearance = cookies.contains("cf_clearance")
                        val isKnownChallenge = title.contains("Just a moment", ignoreCase = true) ||
                            title.contains("Cloudflare", ignoreCase = true) ||
                            title.contains("Attention Required", ignoreCase = true) ||
                            title.contains("Nur einen Moment", ignoreCase = true) ||
                            title.contains("Einen Moment", ignoreCase = true) ||
                            title.contains("Un instant", ignoreCase = true) ||
                            title.contains("Momentito", ignoreCase = true) ||
                            title.contains("Un momento", ignoreCase = true) ||
                            title.contains("Security Check", ignoreCase = true) ||
                            title.contains("Turnstile", ignoreCase = true)

                        // Cloudflare challenge pages never include "APKMirror" in their document title.
                        // Real APKMirror content pages always end with "- APKMirror".
                        val isApkMirrorContentPage = title.contains("APKMirror", ignoreCase = true)

                        if (hasCfClearance || (!isKnownChallenge && isApkMirrorContentPage)) {
                            view?.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { rawHtml ->
                                if (!rawHtml.isNullOrBlank() && rawHtml != "null") {
                                    val unescaped = runCatching {
                                        org.json.JSONTokener(rawHtml).nextValue().toString()
                                    }.getOrDefault(rawHtml)
                                    htmlMap[url] = unescaped
                                    htmlMap[target] = unescaped
                                }
                                Log.i(
                                    TAG,
                                    "Cloudflare challenge passed (clearance=$hasCfClearance, title=$title, url=$target, htmlLen=${htmlMap[target]?.length ?: 0})"
                                )
                                cookieManager.flush()
                                if (!completable.isCompleted) {
                                    completable.complete(true)
                                }
                            }
                        }
                    }
                }

                loadUrl(url, mapOf("Accept-Language" to "en-US,en;q=0.9,de;q=0.8"))
            }

            // Periodic cookie poll every 400ms in case the challenge finishes via AJAX without triggering onPageFinished
            val result = withTimeoutOrNull(timeoutMs) {
                val poller = launch {
                    while (isActive) {
                        delay(400)
                        val cookies = cookieManager.getCookie(url).orEmpty()
                        if (cookies.contains("cf_clearance")) {
                            Log.i(TAG, "cf_clearance cookie detected via periodic poll")
                            cookieManager.flush()
                            completable.complete(true)
                            break
                        }
                    }
                }
                try {
                    completable.await()
                } finally {
                    poller.cancel()
                }
            } ?: false

            if (!result) {
                Log.w(TAG, "WebView Cloudflare bypass timed out after ${timeoutMs}ms for $url")
            }
            result
        } catch (error: Throwable) {
            Log.e(TAG, "Error while executing WebView Cloudflare bypass for $url", error)
            false
        } finally {
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }
}
