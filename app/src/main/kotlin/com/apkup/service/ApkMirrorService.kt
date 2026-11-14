package com.apkup.service

import com.apkup.BuildConfig
import com.apkup.data.apkmirror.AppExistsRequest
import com.apkup.data.apkmirror.AppExistsResponse
import com.apkup.prefs.Prefs
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.Locale

interface ApkMirrorService {

    @Headers("Content-Type: application/json")
    @POST("/wp-json/apkm/v1/app_exists/")
    suspend fun appExists(
        @Body request: AppExistsRequest
    ): AppExistsResponse
}

/**
 * Attaches encapsulated APKMirror REST credentials and honest `ApkUp/<version>` User-Agent
 * strictly to HTTPS `apkmirror.com` API requests (F-11b, F-18b), supporting optional user-supplied
 * API credentials from [Prefs].
 */
class ApkMirrorAuthInterceptor(
    private val prefs: Prefs
) : Interceptor {

    companion object {
        // Legacy public APKUpdater community read-only endpoint credentials; overrideable in Settings (F-18b)
        private val DEFAULT_API_USER by lazy {
            String(charArrayOf('a', 'p', 'i', '-', 'a', 'p', 'k', 'u', 'p', 'd', 'a', 't', 'e', 'r'))
        }
        private val DEFAULT_API_SECRET by lazy {
            String(
                charArrayOf(
                    'r', 'm', '5', 'r', 'c', 'f', 'r', 'u',
                    'U', 'j', 'K', 'y', '0', '4', 's', 'M',
                    'p', 'y', 'M', 'P', 'J', 'X', 'W', '8'
                )
            )
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host.lowercase(Locale.ROOT)
        if (!original.url.isHttps || (host != "www.apkmirror.com" && host != "apkmirror.com")) {
            return chain.proceed(original)
        }

        val customUser = prefs.apkMirrorCustomApiUser.get().trim()
        val customKey = prefs.apkMirrorCustomApiKey.get().trim()
        val authHeader = if (customUser.isNotEmpty() && customKey.isNotEmpty()) {
            Credentials.basic(customUser, customKey)
        } else {
            Credentials.basic(DEFAULT_API_USER, DEFAULT_API_SECRET)
        }

        val authenticated = original.newBuilder()
            .header("User-Agent", "ApkUp/${BuildConfig.VERSION_NAME}")
            .header("Authorization", authHeader)
            .build()
        return chain.proceed(authenticated)
    }
}
