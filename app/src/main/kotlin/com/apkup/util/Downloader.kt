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
    private val activeCalls = ConcurrentHashMap<String, CopyOnWriteArrayList<Call>>()

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
     * Tier 3 cleanup: Purges orphaned staging directories leftover from prior crashes or process kills.
     */
    fun cleanupOrphanStagingFiles(cacheDir: File) {
        runCatching {
            val stagingDirs = cacheDir.listFiles { file ->
                file.isDirectory && (
                    file.name.startsWith("download_staging_") ||
                    file.name.startsWith("shizuku_staging_") ||
                    file.name == "self_update_staging"
                )
            } ?: emptyArray()

            for (dir in stagingDirs) {
                Log.i("Downloader", "Purging orphaned staging directory from previous session: ${dir.name}")
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
