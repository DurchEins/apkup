package com.apkup.util

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.Locale

/**
 * An OkHttp [CookieJar] that synchronizes with Android's system [CookieManager],
 * strictly scoped to HTTPS APKMirror domains (`apkmirror.com` / `apkmirror.net`) so
 * WebView clearance cookies are never leaked across cross-host redirects.
 */
class WebKitCookieJar : CookieJar {

    companion object {
        fun isTrustedApkMirrorHost(host: String?): Boolean {
            val clean = host?.trim()?.lowercase(Locale.ROOT) ?: return false
            return clean == "apkmirror.com" ||
                clean.endsWith(".apkmirror.com") ||
                clean == "apkmirror.net" ||
                clean.endsWith(".apkmirror.net")
        }

        fun isTrustedApkMirrorHttpUrl(url: HttpUrl): Boolean =
            url.isHttps && isTrustedApkMirrorHost(url.host)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!isTrustedApkMirrorHttpUrl(url)) return
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val urlString = url.toString()
        for (cookie in cookies) {
            runCatching { manager.setCookie(urlString, cookie.toString()) }
        }
        runCatching { manager.flush() }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isTrustedApkMirrorHttpUrl(url)) return emptyList()
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyList()
        val cookieString = runCatching { manager.getCookie(url.toString()) }.getOrNull().orEmpty()
        if (cookieString.isBlank()) return emptyList()

        return cookieString.split(";").mapNotNull { part ->
            Cookie.parse(url, part.trim())
        }
    }
}
