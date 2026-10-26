package com.apkup.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
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
    private val auroraClient: OkHttpClient
) {
    companion object {
        const val READY_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val READY_MARKER_FILE = ".ready"

        fun markStagingReady(stagingDir: File, versionCode: Long) {
            runCatching {
                val marker = File(stagingDir, READY_MARKER_FILE)
                marker.writeText("$versionCode:${System.currentTimeMillis()}")
                Log.i("Downloader", "Marked staging as ready: ${stagingDir.name} (versionCode=$versionCode)")
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

                val content = marker.readText().trim()
                val parts = content.split(":")
                if (parts.isEmpty()) return@runCatching null

                val savedVersionCode = parts[0].toLongOrNull() ?: return@runCatching null
                if (savedVersionCode != versionCode) {
                    Log.i("Downloader", "Ready staging versionCode mismatch ($savedVersionCode != $versionCode) for $updateId; discarding")
                    stagingDir.deleteRecursively()
                    return@runCatching null
                }

                val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: marker.lastModified()
                val now = System.currentTimeMillis()
                if (now - timestamp > READY_CACHE_TTL_MS) {
                    Log.i("Downloader", "Ready staging expired (>24h) for $updateId; discarding")
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

    /**
     * Aborts all active network calls registered under [tag].
     * Closes underlying sockets immediately.
     * @return true if at least one call was cancelled.
     */
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

    /**
     * Aborts all active network calls across the application.
     */
    fun cancelAll() {
        val allCalls = activeCalls.values.flatten()
        activeCalls.clear()
        for (call in allCalls) {
            runCatching { call.cancel() }
        }
    }

    fun isCallActive(tag: String): Boolean = activeCalls.containsKey(tag)

    fun downloadStream(url: String, callTag: String? = null): InputStream? = runCatching {
        val c = when {
            url.contains("aurora") -> auroraClient
            else -> client
        }
        val request = downloadRequest(url, callTag)
        val call = c.newCall(request)
        if (callTag != null) {
            registerCall(callTag, call)
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
            Log.e("Downloader", "Download failed with error code: ${response.code}")
            null
        }
    }.getOrElse {
        Log.e("Downloader", "Download failed (${it.javaClass.simpleName})", it)
        null
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

            val c = when {
                url.contains("aurora") -> auroraClient
                else -> client
            }
            val request = downloadRequest(url, callTag)
            val newCall = c.newCall(request)
            call = newCall
            if (callTag != null) {
                registerCall(callTag, newCall)
            }

            currentCoroutineContext().ensureActive()
            val response = newCall.execute()
            if (!response.isSuccessful) {
                response.close()
                Log.e("Downloader", "Download failed with HTTP ${response.code} for $url")
                return@withContext false
            }

            val body = response.body
            val contentLength = body.contentLength()
            if (contentLength > 0L) {
                onContentLength?.invoke(contentLength)
            }
            val bodyStream = body.byteStream()
            val stream = wrapStreamIfGzip(bodyStream)
            targetFile.parentFile?.mkdirs()

            try {
                stream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            currentCoroutineContext().ensureActive()
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                onBytesRead?.invoke(read)
                            }
                            read = input.read(buffer)
                        }
                        output.flush()
                    }
                }
                downloadCompletedSuccessfully = targetFile.length() > 0L
                downloadCompletedSuccessfully
            } finally {
                runCatching { stream.close() }
                runCatching { body.close() }
                runCatching { response.close() }
            }
        } catch (cancellation: CancellationException) {
            Log.i("Downloader", "Download cancelled for ${targetFile.name} (tag=$callTag)")
            throw cancellation
        } catch (ioe: IOException) {
            if (call?.isCanceled() == true || ioe.message?.contains("Canceled", ignoreCase = true) == true) {
                Log.i("Downloader", "OkHttp call cancelled for ${targetFile.name} (tag=$callTag)")
                throw CancellationException("Download aborted via OkHttp cancellation: ${ioe.message}", ioe)
            }
            Log.e("Downloader", "Failed to download to file ${targetFile.name} (${ioe.javaClass.simpleName})", ioe)
            false
        } catch (e: Exception) {
            Log.e("Downloader", "Unexpected failure downloading ${targetFile.name}", e)
            false
        } finally {
            if (callTag != null && call != null) {
                unregisterCall(callTag, call)
            }
            // Tier 1 cleanup: Immediate Incomplete File Purge
            if (!downloadCompletedSuccessfully) {
                runCatching {
                    if (targetFile.exists()) {
                        val deleted = targetFile.delete()
                        Log.d("Downloader", "Purged incomplete download file: ${targetFile.name} (deleted=$deleted)")
                    }
                }
            }
        }
    }

    /**
     * Tier 3 cleanup: Purges orphaned staging directories leftover from prior crashes or process kills,
     * while preserving valid ready-to-install packages that are younger than 24 hours.
     */
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
                        val content = marker.readText().trim()
                        val timestamp = content.split(":").getOrNull(1)?.toLongOrNull() ?: marker.lastModified()
                        if (now - timestamp <= READY_CACHE_TTL_MS) {
                            Log.d("Downloader", "Preserving valid ready-to-install staging dir: ${dir.name}")
                            continue
                        }
                    }
                }
                Log.i("Downloader", "Purging orphaned/expired staging directory: ${dir.name}")
                dir.deleteRecursively()
            }
        }
    }

    private fun downloadRequest(url: String, tag: String? = null): Request {
        val builder = Request.Builder().url(url)
        if (tag != null) {
            builder.tag(tag)
        }
        return builder.build()
    }
}
