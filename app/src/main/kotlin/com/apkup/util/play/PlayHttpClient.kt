package com.apkup.util.play

import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.aurora.gplayapi.net.HttpEngine
import com.aurora.gplayapi.net.HttpMethod
import com.aurora.gplayapi.net.HttpResponse
import com.aurora.gplayapi.net.PlayRequest
import com.aurora.gplayapi.net.RequestBody as PlayRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.FormBody
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PlayHttpClient(
    cache: Cache
) : HttpEngine {

    companion object {
        private const val POST = "POST"
        private const val GET = "GET"
        private const val MAX_PLAY_RESPONSE_BYTES = 10 * 1024 * 1024 // 10 MB cap against OOM-DoS
    }

    private val _responseCode = MutableStateFlow(100)
    private val threadResponseCode = ThreadLocal<Int>()
    val responseCode: StateFlow<Int> get() = _responseCode.asStateFlow()

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
    override suspend fun execute(request: PlayRequest): HttpResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(request.url, request.query)
        val okRequestBody = when (val body = request.body) {
            null -> if (request.method == HttpMethod.POST) "".toRequestBody(null) else null
            is PlayRequestBody.Bytes -> body.bytes.toRequestBody(body.contentType.toMediaType())
            is PlayRequestBody.Form -> FormBody.Builder().apply {
                body.fields.forEach { (key, value) -> add(key, value) }
            }.build()
        }
        val okMethod = when (request.method) {
            HttpMethod.GET -> GET
            HttpMethod.POST -> POST
        }
        val okRequest = Request.Builder()
            .url(url)
            .headers(request.headers.toHeaders())
            .method(okMethod, okRequestBody)
            .build()
        processRequest(okRequest)
    }

    private fun processRequest(request: Request): HttpResponse {
        threadResponseCode.set(0)
        _responseCode.value = 0

        if (!request.url.isHttps) {
            throw SecurityException("Rejected non-HTTPS Play request: ${request.url.scheme}://${request.url.host}")
        }

        val call = okHttpClient.newCall(request)
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            buildHttpResponse(call.execute()).also { response ->
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

    private fun buildHttpResponse(response: Response): HttpResponse = response.use {
        val contentLength = it.body.contentLength()
        if (contentLength > MAX_PLAY_RESPONSE_BYTES) {
            throw IOException("Play response exceeds maximum allowed size ($contentLength bytes)")
        }
        val bytes = readBoundedBytes(it.body.byteStream(), MAX_PLAY_RESPONSE_BYTES)
        val headerMap = buildMap {
            for (i in 0 until it.headers.size) {
                put(it.headers.name(i), it.headers.value(i))
            }
        }
        HttpResponse(
            code = it.code,
            body = bytes,
            headers = headerMap,
            message = it.message
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

    fun responseCodeForCurrentThread(): Int = threadResponseCode.get() ?: _responseCode.value
}
