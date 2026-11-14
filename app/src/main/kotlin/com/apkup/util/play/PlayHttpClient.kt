package com.apkup.util.play

import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.network.IHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PlayHttpClient(
    cache: Cache
) : IHttpClient {

    companion object {
        private const val POST = "POST"
        private const val GET = "GET"
        private const val MAX_PLAY_RESPONSE_BYTES = 10 * 1024 * 1024 // 10 MB cap against OOM-DoS
    }

    private val _responseCode = MutableStateFlow(100)
    private val threadResponseCode = ThreadLocal<Int>()
    override val responseCode: StateFlow<Int> get() = _responseCode.asStateFlow()

    private val okHttpClient = OkHttpClient().newBuilder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .cache(cache)
        .build()

    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, requestBody: RequestBody): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .method(POST, requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .method(POST, "".toRequestBody(null))
            .build()
        return processRequest(request)
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        val requestBody = body.toRequestBody("application/json".toMediaType(), 0, body.size)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.aurora.store-4.4.2-56")
            .method(POST, requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
        val requestBody = body.toRequestBody(
            "application/x-protobuf".toMediaType(),
            0,
            body.size
        )
        return post(url, headers, requestBody)
    }

    @Throws(IOException::class)
    override fun get(url: String, headers: Map<String, String>): PlayResponse {
        return get(url, headers, mapOf())
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    override fun getAuth(url: String): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.aurora.store-4.4.2-56")
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        paramString: String
    ): PlayResponse {
        val request = Request.Builder()
            .url(url + paramString)
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    private fun processRequest(request: Request): PlayResponse {
        threadResponseCode.set(0)
        _responseCode.value = 0

        if (!request.url.isHttps) {
            throw SecurityException("Rejected non-HTTPS Play request: ${request.url.scheme}://${request.url.host}")
        }

        val call = okHttpClient.newCall(request)
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            buildPlayResponse(call.execute()).also { response ->
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "PlayHttpClient",
                        "Request to ${request.url.host}${request.url.encodedPath} completed with HTTP " +
                            "${response.code} in ${SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                }
            }
        } catch (exception: Exception) {
            Log.w(
                "PlayHttpClient",
                "Request to ${request.url.host} failed after " +
                    "${SystemClock.elapsedRealtime() - startedAt} ms (${exception.javaClass.simpleName})"
            )
            throw exception
        }
    }

    private fun buildUrl(url: String, params: Map<String, String>): HttpUrl {
        val urlBuilder = url.toHttpUrl().newBuilder()
        params.forEach {
            urlBuilder.addQueryParameter(it.key, it.value)
        }
        return urlBuilder.build()
    }

    private fun buildPlayResponse(response: Response): PlayResponse = response.use {
        val contentLength = it.body.contentLength()
        if (contentLength > MAX_PLAY_RESPONSE_BYTES) {
            throw IOException("Play response exceeds maximum allowed size ($contentLength bytes)")
        }
        val bytes = readBoundedBytes(it.body.byteStream(), MAX_PLAY_RESPONSE_BYTES)
        PlayResponse(
            isSuccessful = it.isSuccessful,
            code = it.code,
            responseBytes = bytes,
            errorString = if (!it.isSuccessful) it.message else ""
        ).also {
            threadResponseCode.set(response.code)
            _responseCode.value = response.code
        }
    }

    private fun readBoundedBytes(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        input.use { stream ->
            var read = stream.read(buffer)
            while (read >= 0) {
                total += read
                if (total > maxBytes) {
                    throw IOException("Play response stream exceeded maximum allowed size ($maxBytes bytes)")
                }
                out.write(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return out.toByteArray()
    }

    fun responseCodeForCurrentThread(): Int = threadResponseCode.get() ?: 0
}
