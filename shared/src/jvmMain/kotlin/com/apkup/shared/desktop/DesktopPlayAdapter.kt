package com.apkup.shared.desktop

import com.apkup.shared.model.KmpInstalledApp
import com.apkup.shared.model.KmpSourceCheckReport
import com.apkup.shared.model.KmpUpdateCandidate
import com.apkup.shared.platform.computeFileSha256Hex
import com.apkup.shared.platform.constantTimeHexEquals
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.device.DeviceProfile
import com.aurora.gplayapi.device.DeviceRegistry
import com.aurora.gplayapi.net.HttpEngine
import com.aurora.gplayapi.net.HttpMethod
import com.aurora.gplayapi.net.HttpResponse
import com.aurora.gplayapi.net.PlayRequest
import com.aurora.gplayapi.net.RequestBody as PlayRequestBody
import com.aurora.gplayapi.session.DefaultPlaySessions
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.Properties
import java.util.concurrent.TimeUnit

private class DesktopPlayHttpEngine : HttpEngine {
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(request: PlayRequest): HttpResponse = withContext(Dispatchers.IO) {
        val urlBuilder = request.url.toHttpUrl().newBuilder()
        request.query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val body = when (val b = request.body) {
            null -> if (request.method == HttpMethod.POST) "".toRequestBody(null) else null
            is PlayRequestBody.Bytes -> b.bytes.toRequestBody(b.contentType.toMediaType())
            is PlayRequestBody.Form -> FormBody.Builder().apply {
                b.fields.forEach { (k, v) -> add(k, v) }
            }.build()
        }
        val req = Request.Builder()
            .url(urlBuilder.build())
            .headers(request.headers.toHeaders())
            .method(if (request.method == HttpMethod.POST) "POST" else "GET", body)
            .build()
        okHttp.newCall(req).execute().use { resp ->
            val bytes = resp.body.bytes()
            val headerMap = buildMap {
                for (i in 0 until resp.headers.size) put(resp.headers.name(i), resp.headers.value(i))
            }
            HttpResponse(
                code = resp.code,
                body = bytes,
                headers = headerMap,
                message = resp.message
            )
        }
    }
}

class DesktopPlayAdapter {
    private val httpEngine = DesktopPlayHttpEngine()
    private val deviceProfile: DeviceProfile by lazy {
        val props = Properties().apply {
            setProperty("UserReadableName", "Google Pixel 9 Pro XL")
            setProperty("Build.HARDWARE", "komodo")
            setProperty("Build.RADIO", "unknown")
            setProperty("Build.BOOTLOADER", "unknown")
            setProperty("Build.FINGERPRINT", "google/komodo/komodo:16/BP2A.250605.031.A2/13578675:user/release-keys")
            setProperty("Build.BRAND", "google")
            setProperty("Build.DEVICE", "komodo")
            setProperty("Build.VERSION.SDK_INT", "36")
            setProperty("Build.MODEL", "Pixel 9 Pro XL")
            setProperty("Build.MANUFACTURER", "Google")
            setProperty("Build.PRODUCT", "komodo")
            setProperty("Build.ID", "BP2A.250605.031.A2")
            setProperty("Build.VERSION.RELEASE", "16")
            setProperty("Build.VERSION.SECURITY_PATCH", "2026-11-05")
            setProperty("Platforms", "arm64-v8a,armeabi-v7a,armeabi")
            setProperty("Screen.Density", "512")
            setProperty("Screen.Width", "1344")
            setProperty("Screen.Height", "2992")
            setProperty("GSF.version", "244738038")
            setProperty("Vending.version", "84412820")
            setProperty("Vending.versionString", "44.1.28-31 [0] [PR] 703978482")
            setProperty("TouchScreen", "3")
            setProperty("Keyboard", "1")
            setProperty("Navigation", "1")
            setProperty("ScreenLayout", "2")
            setProperty("HasHardKeyboard", "false")
            setProperty("HasFiveWayNavigation", "false")
            setProperty("GL.Version", "196610")
            setProperty("Locales", "de_DE,en_US")
            setProperty("Features", "android.hardware.wifi,android.hardware.bluetooth,android.hardware.camera,android.hardware.touchscreen")
            setProperty("SharedLibraries", "android.ext.shared")
            setProperty("GL.Extensions", "")
        }
        DeviceRegistry.parse("desktop_pixel", props)
    }

    private val playSessions by lazy {
        DefaultPlaySessions(httpEngine, { deviceProfile }, { Locale.getDefault() })
    }

    private val playClient by lazy {
        PlayClient(httpEngine, playSessions)
    }

    suspend fun checkUpdates(installedApps: List<KmpInstalledApp>): KmpSourceCheckReport = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (installedApps.isEmpty()) {
            return@withContext KmpSourceCheckReport("Google Play", emptyList(), 0, 0, null, 0L)
        }
        try {
            playSessions.session()
            val appByPkg = installedApps.associateBy { it.packageName }
            val updates = mutableListOf<KmpUpdateCandidate>()
            var ok = 0
            var fail = 0
            var firstErr: String? = null
            for (batch in installedApps.map { it.packageName }.chunked(60)) {
                try {
                    val details = playClient.apps.details(batch)
                    ok++
                    for (app in details) {
                        val inst = appByPkg[app.packageName] ?: continue
                        if (app.versionCode > inst.versionCode && app.versionName.isNotBlank()) {
                            updates += KmpUpdateCandidate(
                                name = app.displayName.ifBlank { inst.name },
                                packageName = app.packageName,
                                currentVersion = inst.version,
                                newVersion = app.versionName,
                                currentVersionCode = inst.versionCode,
                                newVersionCode = app.versionCode,
                                sourceName = "Google Play",
                                downloadUrl = "https://play.google.com/store/apps/details?id=${app.packageName}",
                                sizeBytes = app.size,
                                releaseNotes = app.changes,
                                installedShortSha256 = inst.shortSha256
                            )
                        }
                    }
                } catch (t: Throwable) {
                    fail++
                    if (firstErr == null) firstErr = t.message ?: t::class.simpleName
                }
            }
            KmpSourceCheckReport(
                sourceName = "Google Play",
                updates = updates,
                successfulRequests = ok,
                failedRequests = fail,
                errorMessage = firstErr,
                durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L)
            )
        } catch (t: Throwable) {
            KmpSourceCheckReport(
                sourceName = "Google Play",
                failedRequests = 1,
                errorMessage = t.message ?: t::class.simpleName,
                durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L)
            )
        }
    }

    suspend fun resolvePlayApkUrls(packageName: String, versionCode: Long): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            playSessions.session()
            val files = playClient.delivery.resolve(
                packageName = packageName,
                versionCode = versionCode,
                offerType = 1
            )
            files.filter { it.url.startsWith("https://") }.mapIndexed { idx, file ->
                val name = file.name.ifBlank { if (idx == 0) "base.apk" else "split_$idx.apk" }
                name to file.url
            }
        }
}

object DesktopDownloader {

    suspend fun downloadAndVerifyApk(
        httpClient: HttpClient,
        url: String,
        targetFile: File,
        expectedSha256: String?
    ): File = withContext(Dispatchers.IO) {
        targetFile.parentFile?.mkdirs()
        val temp = File(targetFile.parentFile, "${targetFile.name}.part")
        if (temp.exists()) temp.delete()
        try {
            val resp = httpClient.get(url)
            if (resp.status.value !in 200..299) {
                throw IOException("HTTP ${resp.status.value} downloading $url")
            }
            temp.outputStream().use { out ->
                resp.bodyAsChannel().copyTo(out)
            }
            if (!expectedSha256.isNullOrBlank()) {
                val res = computeFileSha256Hex(temp.absolutePath)
                if (!constantTimeHexEquals(res.sha256Hex, expectedSha256)) {
                    throw SecurityException("SHA-256 mismatch for ${targetFile.name}: expected $expectedSha256, got ${res.sha256Hex}")
                }
            }
            if (targetFile.exists()) targetFile.delete()
            if (!temp.renameTo(targetFile)) {
                temp.copyTo(targetFile, overwrite = true)
                temp.delete()
            }
            targetFile
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
