package com.apkup.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream


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
        if (response.isSuccessful && response.body != null) {
            return response.body!!.byteStream()
        } else {
            response.close()
            Log.e("Downloader", "Download failed with error code: ${response.code}")
        }
        return null
    }.getOrElse {
        Log.e("Downloader", "Download failed (${it.javaClass.simpleName})")
        null
    }

    private fun downloadRequest(url: String) = Request.Builder().url(url).build()

}
