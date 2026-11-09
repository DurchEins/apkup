package com.apkup.util

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * An OkHttp [CookieJar] that synchronizes seamlessly with Android's system [CookieManager].
 * This guarantees that Cloudflare challenge clearance cookies (such as `cf_clearance` or `__cf_bm`)
 * acquired inside a headless Chromium WebView are immediately available to OkHttp background requests,
 * and cookies received in OkHttp responses are persisted back to the system cookie store.
 */
class WebKitCookieJar : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val urlString = url.toString()
        for (cookie in cookies) {
            runCatching { manager.setCookie(urlString, cookie.toString()) }
        }
        runCatching { manager.flush() }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyList()
        val cookieString = runCatching { manager.getCookie(url.toString()) }.getOrNull().orEmpty()
        if (cookieString.isBlank()) return emptyList()

        return cookieString.split(";").mapNotNull { part ->
            Cookie.parse(url, part.trim())
        }
    }
}
