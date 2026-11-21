package com.apkup.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.compose.ui.platform.UriHandler
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.apkup.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Launches a coroutine and executes it inside the mutex
fun CoroutineScope.launchWithMutex(
    mutex: Mutex,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
) = launch(context) {
    mutex.withLock {
        block()
    }
}

fun PackageInfo.name(context: Context): String =
    applicationInfo?.loadLabel(context.packageManager)?.toString()
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()

fun Context.getAppIcon(packageName: String) = runCatching {
    packageManager.getApplicationIcon(packageName)
}.getOrElse {
    ContextCompat.getDrawable(this, R.drawable.ic_shizuku)
}

fun Context.getAppName(packageName: String): String = runCatching {
    packageManager.getPackageInfo(packageName, 0).name(this)
}.getOrDefault("")
    .lineSequence()
    .firstOrNull { it.isNotBlank() }
    ?.trim()
    .orEmpty()

fun ByteArray.toSha1(): String = MessageDigest
    .getInstance("SHA-1")
    .digest(this)
    .joinToString(separator = "", transform = { "%02x".format(it) })

fun ByteArray.toSha256(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "", transform = { "%02x".format(it) })

fun PackageInfo.getSignature(): ByteArray = runCatching {
    val info = signingInfo
    if (info != null) {
        if (info.hasMultipleSigners()) {
            val sorted = info.apkContentsSigners
                ?.map { it.toByteArray() }
                ?.sortedBy { it.toSha256() }
                .orEmpty()
            when {
                sorted.isEmpty() -> ByteArray(0)
                sorted.size == 1 -> sorted.first()
                else -> {
                    val out = java.io.ByteArrayOutputStream()
                    sorted.forEach { out.write(it) }
                    out.toByteArray()
                }
            }
        } else {
            info.apkContentsSigners?.firstOrNull()?.toByteArray()
                ?: info.signingCertificateHistory?.lastOrNull()?.toByteArray()
                ?: ByteArray(0)
        }
    } else {
        @Suppress("DEPRECATION")
        signatures?.firstOrNull()?.toByteArray() ?: ByteArray(0)
    }
}.getOrDefault(ByteArray(0))

fun PackageInfo.getSignatureSha1(): String =
    getSignature().takeIf { it.isNotEmpty() }?.toSha1().orEmpty()

fun PackageInfo.getSignatureSha256(): String =
    getSignature().takeIf { it.isNotEmpty() }?.toSha256().orEmpty()

fun Intent.getIntentExtra(): Intent? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_INTENT)
}

fun isDark() = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun OkHttpClient.Builder.addUserAgentInterceptor(agent: String) = addNetworkInterceptor { chain ->
    val req = chain.request()
    val updated = if (req.header("User-Agent") == null) {
        req.newBuilder().header("User-Agent", agent).build()
    } else {
        req
    }
    chain.proceed(updated)
}

fun Context.openAppInPlayStore(packageName: String) {
    if (!ApkIntegrityVerifier.isValidPackageName(packageName)) return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }.onFailure {
        openUrl("https://play.google.com/store/apps/details?id=$packageName")
    }
}

fun Context.openAppSystemSettings(packageName: String) {
    if (!ApkIntegrityVerifier.isValidPackageName(packageName)) return
    runCatching {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

/**
 * Reads UTF-8 text from [input] up to [maxBytes] in a loop, avoiding single-`read()` short-read
 * truncation on chunked streams and unbounded OOM on oversized responses.
 */
fun readUtf8Bounded(input: InputStream, maxBytes: Int): String {
    require(maxBytes >= 0) { "maxBytes must be non-negative" }
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(minOf(8192, maxOf(256, maxBytes)))
    var total = 0
    while (true) {
        val remaining = maxBytes - total
        if (remaining <= 0) {
            if (input.read() != -1) {
                throw IOException("Stream exceeded maximum allowed size of $maxBytes bytes")
            }
            break
        }
        val toRead = minOf(buffer.size, remaining)
        val n = input.read(buffer, 0, toRead)
        if (n == -1) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toString(Charsets.UTF_8.name())
}

/**
 * Returns true if [url] is a non-blank HTTPS URI with a valid host.
 */
fun isSafeHttpsUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return false
    return runCatching {
        val uri = java.net.URI(trimmed)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

/**
 * Safely opens an external HTTPS URI via Compose [UriHandler], rejecting non-HTTPS schemes.
 */
fun UriHandler.openSafeHttpsUri(url: String): Boolean {
    val trimmed = url.trim()
    if (!isSafeHttpsUrl(trimmed)) {
        runCatching { Log.w("Extensions", "Blocked non-HTTPS external URI launch attempt") }
        return false
    }
    return runCatching {
        openUri(trimmed)
        true
    }.getOrDefault(false)
}

/**
 * Opens an external HTTPS [url] in a Chrome Custom Tab (with optional [toolbarColorArgb]),
 * falling back to a standard `ACTION_VIEW` browser intent if no Custom Tabs provider is available.
 * Strictly rejects non-HTTPS schemes (`intent://`, `javascript:`, `file://`, `content://`, `http://`).
 */
fun openInCustomTab(
    context: Context,
    url: String,
    toolbarColorArgb: Int? = null
) {
    val trimmed = url.trim()
    if (!isSafeHttpsUrl(trimmed)) {
        if (trimmed.isNotEmpty()) {
            runCatching { Log.w("Extensions", "Blocked non-HTTPS external URI launch attempt") }
        }
        return
    }
    val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val nativeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val openedNative = runCatching {
            context.startActivity(nativeIntent)
            true
        }.getOrDefault(false)
        if (openedNative) return
    }
    val launchedCustomTab = runCatching {
        val builder = androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
        if (toolbarColorArgb != null) {
            val colorParams = androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                .setToolbarColor(toolbarColorArgb)
                .build()
            builder.setDefaultColorSchemeParams(colorParams)
        }
        val customTabsIntent = builder.build()
        if (context !is android.app.Activity) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(context, uri)
    }.isSuccess
    if (!launchedCustomTab) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

/**
 * Safely opens an external HTTPS URL in a Chrome Custom Tab (falling back to a browser `ACTION_VIEW`
 * intent), strictly rejecting `intent://`, `javascript:`, `file://`, `content://`, or `http://`.
 */
fun Context.openUrl(url: String) = openInCustomTab(this, url)
