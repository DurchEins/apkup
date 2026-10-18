package com.apkup.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
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

    fun downloadStream(url: String): InputStream? = runCatching {
        val c = when {
            url.contains("aurora") -> auroraClient
            else -> client
        }
        val response = c.newCall(downloadRequest(url)).execute()
        if (response.isSuccessful) {
            return wrapStreamIfGzip(response.body.byteStream())
        } else {
            response.close()
            Log.e("Downloader", "Download failed with error code: ${response.code}")
        }
        return null
    }.getOrElse {
        Log.e("Downloader", "Download failed (${it.javaClass.simpleName})", it)
        null
    }

    fun downloadToFile(
        url: String,
        targetFile: File,
        onBytesRead: ((bytesReadInChunk: Int) -> Unit)? = null
    ): Boolean = runCatching {
        val stream = downloadStream(url) ?: return false
        targetFile.parentFile?.mkdirs()
        stream.use { input ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        onBytesRead?.invoke(read)
                    }
                    read = input.read(buffer)
                }
                output.flush()
            }
        }
        targetFile.length() > 0L
    }.getOrElse {
        Log.e("Downloader", "Failed to download to file ${targetFile.name}", it)
        false
    }

    private fun downloadRequest(url: String) = Request.Builder().url(url).build()

}
