package com.apkup.util

import android.util.Log
import com.apkup.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

internal fun wrapStreamIfGzip(raw: InputStream): InputStream {
    val pushback = PushbackInputStream(raw, 2)
    val header = ByteArray(2)
    val read = pushback.read(header)
    if (read == 2 && (header[0].toInt() and 0xFF == 0x1F) && (header[1].toInt() and 0xFF == 0x8B)) {
        pushback.unread(header)
        return GZIPInputStream(pushback)
    } else if (read > 0) {
        pushback.unread(header, 0, read)
    }
    return pushback
}

class Downloader(
    private val client: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val apkMirrorClient: OkHttpClient = client
) {
    companion object {
        const val READY_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val READY_MARKER_FILE = ".ready"
        const val MAX_APK_DOWNLOAD_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB cap (HIGH-06)
        private const val MIN_FREE_DISK_HEADROOM_BYTES = 32L * 1024L * 1024L // 32 MB

        fun markStagingReady(stagingDir: File, versionCode: Long) {
            runCatching {
                val apkFiles = stagingDir.listFiles { file ->
                    file.isFile && file.extension.equals("apk", ignoreCase = true) && file.length() > 0L
                }?.sortedBy { it.name }.orEmpty()
                if (apkFiles.isEmpty()) return@runCatching

                val manifestLines = buildList {
                    add("$versionCode:${System.currentTimeMillis()}")
                    for (apk in apkFiles) {
                        val sha256 = ApkIntegrityVerifier.computeFileSha256(apk)
                        add("${apk.name}|${apk.length()}|$sha256")
                    }
                }
                val marker = File(stagingDir, READY_MARKER_FILE)
                marker.writeText(manifestLines.joinToString("\n"))
                if (BuildConfig.DEBUG) {
                    Log.d("Downloader", "Marked staging as ready: ${stagingDir.name} (versionCode=$versionCode, files=${apkFiles.size})")
                }
            }
        }

        fun getReadyStagingFiles(baseDir: File, updateId: Int, versionCode: Long): List<File>? {
            return runCatching {
                val directStaging = File(baseDir, "download_staging_$updateId")
                val persistentStaging = File(File(baseDir, "downloads"), "download_staging_$updateId")
                val stagingDir = when {
                    directStaging.exists() -> directStaging
                    persistentStaging.exists() -> persistentStaging
                    else -> directStaging
                }
                if (!stagingDir.exists() || !stagingDir.isDirectory) return@runCatching null

                val marker = File(stagingDir, READY_MARKER_FILE)
                if (!marker.exists() || !marker.isFile) return@runCatching null

                val lines = marker.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return@runCatching null

                val headerParts = lines[0].split(":")
                val savedVersionCode = headerParts.getOrNull(0)?.toLongOrNull() ?: return@runCatching null
                if (savedVersionCode != versionCode) {
                    stagingDir.deleteRecursively()
                    return@runCatching null
                }

                val timestamp = headerParts.getOrNull(1)?.toLongOrNull() ?: marker.lastModified()
                val now = System.currentTimeMillis()
                if (now - timestamp > READY_CACHE_TTL_MS) {
                    stagingDir.deleteRecursively()
                    return@runCatching null
                }

                val apkFiles = stagingDir.listFiles { file ->
                    file.isFile && file.extension.equals("apk", ignoreCase = true) && file.length() > 0L
                }?.sortedWith(
                    compareByDescending<File> { it.name.equals("base.apk", ignoreCase = true) }
                        .thenBy { it.name }
                ).orEmpty()

                if (apkFiles.isEmpty()) {
                    stagingDir.deleteRecursively()
                    return@runCatching null
                }

                // Verify recorded file hashes and lengths if present in .ready manifest (CRIT-01 / HIGH-06)
                if (lines.size > 1) {
                    val recorded = lines.drop(1).mapNotNull { line ->
                        val cols = line.split("|")
                        if (cols.size == 3) {
                            val name = cols[0]
                            val len = cols[1].toLongOrNull() ?: return@mapNotNull null
                            val sha = cols[2].lowercase(Locale.ROOT)
                            name to (len to sha)
                        } else null
                    }.toMap()

                    if (recorded.size != apkFiles.size) {
                        Log.w("Downloader", "Ready staging file count mismatch for $updateId; purging")
                        stagingDir.deleteRecursively()
                        return@runCatching null
                    }

                    for (apk in apkFiles) {
                        val expected = recorded[apk.name]
                        if (expected == null || apk.length() != expected.first) {
                            Log.w("Downloader", "Ready staging size mismatch for ${apk.name}; purging")
                            stagingDir.deleteRecursively()
                            return@runCatching null
                        }
                        val actualSha = ApkIntegrityVerifier.computeFileSha256(apk)
                        if (!actualSha.equals(expected.second, ignoreCase = true)) {
                            Log.e("Downloader", "Ready staging SHA-256 mismatch for ${apk.name}; purging tampered file")
                            stagingDir.deleteRecursively()
                            return@runCatching null
                        }
                    }
                }

                apkFiles
            }.getOrNull()
        }

        fun hasReadyCachedApk(cacheDir: File, updateId: Int, versionCode: Long): Boolean =
            getReadyStagingFiles(cacheDir, updateId, versionCode) != null
    }

    private val activeCalls = ConcurrentHashMap<String, CopyOnWriteArrayList<Call>>()

    fun markStagingReady(stagingDir: File, versionCode: Long) =
        Companion.markStagingReady(stagingDir, versionCode)

    fun getReadyStagingFiles(cacheDir: File, updateId: Int, versionCode: Long): List<File>? =
        Companion.getReadyStagingFiles(cacheDir, updateId, versionCode)

    fun hasReadyCachedApk(cacheDir: File, updateId: Int, versionCode: Long): Boolean =
        Companion.hasReadyCachedApk(cacheDir, updateId, versionCode)

    fun registerCall(tag: String, call: Call) {
        activeCalls.computeIfAbsent(tag) { CopyOnWriteArrayList() }.add(call)
    }

    fun unregisterCall(tag: String, call: Call) {
        activeCalls[tag]?.let { list ->
            list.remove(call)
            if (list.isEmpty()) {
                activeCalls.remove(tag, list)
            }
        }
    }

    fun cancel(tag: String): Boolean {
        val calls = activeCalls.remove(tag) ?: return false
        var anyCancelled = false
        for (call in calls) {
            if (!call.isCanceled()) {
                call.cancel()
                anyCancelled = true
            }
        }
        return anyCancelled
    }

    fun cancelAll() {
        val allCalls = activeCalls.values.flatten()
        activeCalls.clear()
        for (call in allCalls) {
            runCatching { call.cancel() }
        }
    }

    fun isCallActive(tag: String): Boolean = activeCalls.containsKey(tag)

    private fun selectClientForUrl(url: String): OkHttpClient {
        val host = url.toHttpUrlOrNull()?.host?.lowercase(Locale.ROOT).orEmpty()
        return when {
            WebKitCookieJar.isTrustedApkMirrorHost(host) -> apkMirrorClient
            url.contains("aurora", ignoreCase = true) -> auroraClient
            else -> client
        }
    }

    fun downloadStream(url: String, callTag: String? = null): InputStream? {
        var registeredCall: okhttp3.Call? = null
        return runCatching {
            val request = downloadRequest(url, callTag)
            val c = selectClientForUrl(url)
            val call = c.newCall(request)
            if (callTag != null) {
                registerCall(callTag, call)
                registeredCall = call
            }
            val response = call.execute()
            if (response.isSuccessful) {
                val wrapped = wrapStreamIfGzip(response.body.byteStream())
                object : java.io.FilterInputStream(wrapped) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            if (callTag != null) {
                                unregisterCall(callTag, call)
                            }
                            response.close()
                        }
                    }
                }
            } else {
                if (callTag != null) {
                    unregisterCall(callTag, call)
                }
                response.close()
                Log.w("Downloader", "Download failed with HTTP ${response.code}")
                null
            }
        }.getOrElse {
            if (callTag != null) {
                registeredCall?.let { call -> unregisterCall(callTag, call) }
            }
            Log.w("Downloader", "Download stream failed (${it.javaClass.simpleName})")
            null
        }
    }

    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        callTag: String? = null,
        onContentLength: ((contentLength: Long) -> Unit)? = null,
        onBytesRead: ((bytesReadInChunk: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var call: Call? = null
        var downloadCompletedSuccessfully = false

        try {
            currentCoroutineContext().ensureActive()

            val request = downloadRequest(url, callTag)
            val c = selectClientForUrl(url)
            val newCall = c.newCall(request)
            call = newCall
            if (callTag != null) {
                registerCall(callTag, newCall)
            }

            currentCoroutineContext().ensureActive()
            val response = newCall.execute()
            if (!response.isSuccessful) {
                response.close()
                Log.w("Downloader", "Download failed with HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > MAX_APK_DOWNLOAD_BYTES) {
                response.close()
                throw SecurityException("Rejected oversized download ($contentLength bytes > $MAX_APK_DOWNLOAD_BYTES)")
            }

            val parentDir = targetFile.parentFile
            parentDir?.mkdirs()
            if (parentDir != null && contentLength > 0L) {
                val usableSpace = parentDir.usableSpace
                if (usableSpace > 0L && usableSpace < contentLength + MIN_FREE_DISK_HEADROOM_BYTES) {
                    response.close()
                    throw IOException("Insufficient storage space: need $contentLength bytes, available $usableSpace bytes")
                }
            }

            if (contentLength > 0L) {
                onContentLength?.invoke(contentLength)
            }

            val contentEncoding = response.header("Content-Encoding").orEmpty()
            val bodyStream = body.byteStream()
            val stream = wrapStreamIfGzip(bodyStream)
            val isGzipStream = stream is GZIPInputStream || contentEncoding.contains("gzip", ignoreCase = true)

            try {
                var totalBytesWritten = 0L
                stream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            currentCoroutineContext().ensureActive()
                            if (read > 0) {
                                totalBytesWritten += read
                                if (totalBytesWritten > MAX_APK_DOWNLOAD_BYTES) {
                                    throw SecurityException("Download exceeded maximum allowed size ($MAX_APK_DOWNLOAD_BYTES bytes)")
                                }
                                output.write(buffer, 0, read)
                                onBytesRead?.invoke(read)
                            }
                            read = input.read(buffer)
                        }
                        output.flush()
                    }
                }
                if (!isGzipStream && contentLength > 0L && totalBytesWritten != contentLength) {
                    throw IOException("Truncated download: expected $contentLength bytes, received $totalBytesWritten bytes")
                }
                downloadCompletedSuccessfully = targetFile.length() > 0L
                downloadCompletedSuccessfully
            } finally {
                runCatching { stream.close() }
                runCatching { body.close() }
                runCatching { response.close() }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ioe: IOException) {
            if (call?.isCanceled() == true || ioe.message?.contains("Canceled", ignoreCase = true) == true) {
                throw CancellationException("Download aborted via OkHttp cancellation: ${ioe.message}", ioe)
            }
            Log.w("Downloader", "Failed to download ${targetFile.name} (${ioe.javaClass.simpleName})")
            false
        } catch (e: Exception) {
            Log.w("Downloader", "Unexpected failure downloading ${targetFile.name} (${e.javaClass.simpleName})")
            false
        } finally {
            if (callTag != null && call != null) {
                unregisterCall(callTag, call)
            }
            if (!downloadCompletedSuccessfully) {
                runCatching {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                }
            }
        }
    }

    fun cleanupOrphanStagingFiles(cacheDir: File) {
        runCatching {
            val now = System.currentTimeMillis()
            val stagingDirs = cacheDir.listFiles { file ->
                file.isDirectory && (
                    file.name.startsWith("download_staging_") ||
                        file.name.startsWith("shizuku_staging_") ||
                        file.name == "self_update_staging"
                )
            } ?: emptyArray()

            for (dir in stagingDirs) {
                if (dir.name.startsWith("download_staging_")) {
                    val marker = File(dir, READY_MARKER_FILE)
                    if (marker.exists()) {
                        val firstLine = marker.useLines { it.firstOrNull()?.trim().orEmpty() }
                        val timestamp = firstLine.split(":").getOrNull(1)?.toLongOrNull() ?: marker.lastModified()
                        if (now - timestamp <= READY_CACHE_TTL_MS) {
                            continue
                        }
                    }
                }
                dir.deleteRecursively()
            }
        }
    }

    internal fun downloadRequest(url: String, tag: String? = null): Request {
        val parsed = url.toHttpUrlOrNull()
            ?: throw IOException("Invalid download URL")
        if (!parsed.isHttps) {
            throw SecurityException("Rejected non-HTTPS download URL: ${parsed.scheme}://${parsed.host}")
        }

        val isApkMirror = WebKitCookieJar.isTrustedApkMirrorHost(parsed.host)
        val builder = Request.Builder().url(parsed)
        builder.header(
            "User-Agent",
            if (isApkMirror) {
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            } else {
                "ApkUp/${BuildConfig.VERSION_NAME}"
            }
        )
        builder.header("Accept", "*/*")
        builder.header("Accept-Language", "en-US,en;q=0.9,de;q=0.8")
        if (isApkMirror) {
            builder.header("Referer", "https://www.apkmirror.com/")
        }
        if (tag != null) {
            builder.tag(tag)
        }
        return builder.build()
    }
}
