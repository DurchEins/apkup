package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.cleanChangelogText
import com.apkup.data.ui.filterNewerCandidates
import com.apkup.data.ui.getPackageNames
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.prefs.Prefs
import com.apkup.util.play.NativeDeviceInfoProvider
import com.apkup.util.play.PlayHttpClient
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.data.providers.DeviceInfoProvider
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue


class PlayRepository(
    private val context: Context,
    private val playHttpClient: PlayHttpClient,
    private val gson: Gson,
    private val prefs: Prefs
) {
    companion object {
        const val AUTH_URL = "https://auroraoss.com/api/auth"
        internal const val AUTH_MAX_ATTEMPTS = 3
        internal const val AUTH_INITIAL_BACKOFF_MS = 400L
    }

    private val authRefreshLock = Any()
    private val deviceProperties by lazy {
        NativeDeviceInfoProvider(context).getNativeDeviceProperties()
    }

    private fun refreshAuth(): AuthData {
        Log.i("PlayRepository", "Starting token-dispenser authentication.")
        val properties = deviceProperties
        val payloadBytes = gson.toJson(properties).toByteArray()
        val receivedAuthData = requestTokenDispenserWithRetry(
            maxAttempts = AUTH_MAX_ATTEMPTS,
            initialDelayMs = AUTH_INITIAL_BACKOFF_MS,
            sleep = { delayMs -> Thread.sleep(delayMs) },
            request = { playHttpClient.postAuth(AUTH_URL, payloadBytes) },
            parse = { responseBytes -> parseAuthData(String(responseBytes), gson) },
            onRetry = { attempt, maxAttempts, reason, delayMs ->
                Log.w(
                    "PlayRepository",
                    "Token-dispenser attempt $attempt/$maxAttempts failed ($reason); retrying in ${delayMs} ms."
                )
            }
        )
        if (receivedAuthData.deviceInfoProvider == null) {
            Log.w("PlayRepository", "Auth response did not include a device profile; rebuilding it from the submitted device properties.")
        }
        val authData = receivedAuthData
            .withDeviceInfoProvider(properties)
            .withGermanPlayStoreLocale()
        if (!authData.hasRequiredPlaySession()) {
            Log.e(
                "PlayRepository",
                "Auth response decoded but is missing required fields " +
                    "(bearer=${authData.authToken.isNotBlank()}, deviceId=${authData.gsfId.isNotBlank()}, " +
                    "deviceProfile=${authData.deviceInfoProvider != null})."
            )
            throw IllegalStateException("Auth response is missing required Play session fields.")
        }

        Log.i("PlayRepository", "Auth session decoded; saving session data.")
        saveAuthData(authData)
        val savedAuthData = readStoredAuthData()
            .withDeviceInfoProvider(properties)
            .withGermanPlayStoreLocale()
        if (!savedAuthData.hasRequiredPlaySession()) {
            Log.e("PlayRepository", "Saved Play session is missing required fields.")
            throw IllegalStateException("Saved Play session is incomplete.")
        }
        Log.i("PlayRepository", "Auth session saved and ready for app-detail requests.")
        return savedAuthData
    }

    private fun auth(): AuthData {
        Log.i("PlayRepository", "Reading saved Play session.")
        val savedData = readStoredAuthData()
        val properties = deviceProperties
        val germanStoreData = savedData.withDeviceInfoProvider(properties).withGermanPlayStoreLocale()
        if (!germanStoreData.hasRequiredPlaySession()) {
            Log.w("PlayRepository", "Saved Play session is incomplete; requesting a fresh session.")
            return refreshAuth()
        }
        if (germanStoreData != savedData) {
            saveAuthData(germanStoreData)
        }
        Log.i("PlayRepository", "Using saved Play session; required session fields are present.")
        if (System.currentTimeMillis() - prefs.lastPlayCheck.get() > 60 * 60 * 1_000) {
            // Update check time
            prefs.lastPlayCheck.put(System.currentTimeMillis())
            Log.i("PlayRepository", "Checking token validity.")

            // 1h has passed check if token still works
            val app = try {
                Log.i("PlayRepository", "Checking saved Play session with a single app-detail request.")
                AppDetailsHelper(germanStoreData)
                    .using(playHttpClient)
                    .getAppByPackageName("com.google.android.gm")
            } catch (exception: Exception) {
                val code = playHttpClient.responseCodeForCurrentThread()
                if (shouldRefreshPlayAuth(code)) {
                    Log.w("PlayRepository", "Saved Play session rejected with HTTP $code; refreshing session.")
                    return refreshAuth()
                }
                throw exception
            }

            if (app.packageName.isEmpty()) {
                return refreshAuth()
            }
            Log.i("PlayRepository", "Token still valid.")
        }
        return germanStoreData
    }

    suspend fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ): Flow<SourceUpdateResult<AppUpdate>> {
        var phase = "installed package preparation"
        return flow<SourceUpdateResult<AppUpdate>> {
        val packageNames = apps.getPackageNames()
        if (packageNames.isEmpty()) {
            emit(SourceUpdateResult(emptyList(), successfulRequests = 0, failedRequests = 0))
            return@flow
        }

        phase = "authentication"
        val authStartedAt = SystemClock.elapsedRealtime()
        val authData = auth()
        val authDuration = SystemClock.elapsedRealtime() - authStartedAt
        Log.i("PlayRepository", "Authentication stage completed in ${authDuration} ms.")
        phase = "bulk app-details lookup"
        val requestStartedAt = SystemClock.elapsedRealtime()
        val expectedBatchCount = (packageNames.size + PLAY_DETAILS_BATCH_SIZE - 1) / PLAY_DETAILS_BATCH_SIZE
        val failureSummaries = ConcurrentLinkedQueue<String>()
        Log.i(
            "PlayRepository",
            "Starting bulk app-details lookup for ${packageNames.size} package IDs in " +
                "$expectedBatchCount batch(es), up to $PLAY_DETAILS_REQUEST_CONCURRENCY concurrent."
        )
        val requestResult = requestConcurrentBatches<String, App>(
            items = packageNames,
            batchSize = PLAY_DETAILS_BATCH_SIZE,
            concurrency = PLAY_DETAILS_REQUEST_CONCURRENCY,
            request = { packageBatch ->
                requestWithUnauthorizedRetry(
                    initialAuth = authData,
                    responseCode = { playHttpClient.responseCodeForCurrentThread() },
                    refreshAuth = { refreshAuthIfNeeded(authData) },
                    request = { currentAuth ->
                        AppDetailsHelper(currentAuth)
                            .using(playHttpClient)
                            .getAppByPackageName(packageBatch)
                    }
                )
            },
            stopSchedulingAfterFailure = { exception ->
                exception is UnknownHostException || playHttpClient.responseCodeForCurrentThread() == 429
            },
            onFailure = { exception ->
                val failure = describePlayFailure(exception, playHttpClient.responseCodeForCurrentThread())
                failureSummaries.add(failure)
                Log.e(
                    "PlayRepository",
                    "Google Play $phase failed: $failure"
                )
            },
            onBatchSuccess = onBatchSuccess
        )
        val requestDuration = SystemClock.elapsedRealtime() - requestStartedAt
        val details = requestResult.responses
        Log.i(
            "PlayRepository",
            "Bulk app-details lookup finished: ${requestResult.successfulRequests} succeeded, " +
                "${requestResult.failedRequests} failed, ${details.size} app details in ${requestDuration} ms."
        )
        phase = "update processing"
        val processingStartedAt = SystemClock.elapsedRealtime()
        val installedByPackage = apps.associateBy { it.packageName }
        val installedVersions = apps.associate { it.packageName to it.versionCode }
        val newerApps = filterNewerCandidates(
            details,
            installedVersions,
            packageName = { it.packageName },
            versionCode = { it.versionCode }
        )
        val updates = newerApps
            .map {
                val installed = installedByPackage.getValue(it.packageName)
                it.toAppUpdate(
                    getInstallFiles = ::getInstallFiles,
                    oldVersion = installed.version,
                    oldVersionCode = installed.versionCode,
                    installOrigin = installed.installOrigin,
                    installerPackageName = installed.installerPackageName
                )
            }
            .filter { it.isMeaningfulVersionUpgrade() }
        if (BuildConfig.DEBUG) {
            Log.d(
                "PlayRepository",
                "Checked ${packageNames.size} distinct package ID(s) in " +
                    "${requestResult.successfulRequests + requestResult.failedRequests} batch(es); " +
                    "${details.size} detail(s), ${updates.size} update(s); " +
                    "${requestResult.failedRequests} failed batch(es), auth ${authDuration} ms, request ${requestDuration} ms, " +
                    "processing ${SystemClock.elapsedRealtime() - processingStartedAt} ms"
            )
        }
        emit(
            SourceUpdateResult(
                updates,
                requestResult.successfulRequests,
                requestResult.failedRequests,
                failureSummaries.peek()
            )
        )
    }.catch {
        if (it is CancellationException) throw it
        val failure = "$phase: ${describePlayFailure(it, playHttpClient.responseCodeForCurrentThread())}"
        emit(
            SourceUpdateResult(
                emptyList(),
                successfulRequests = 0,
                failedRequests = 1,
                failureSummary = failure
            )
        )
        Log.e(
            "PlayRepository",
            "Update check failed: $failure"
        )
    }
    }

    internal fun getInstallFilesForPackage(
        packageName: String,
        versionCode: Long,
        offerType: Int = 1
    ): List<PlayFile> = PurchaseHelper(auth())
        .using(playHttpClient)
        .purchase(packageName, versionCode, offerType)
        .filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }

    private fun getInstallFiles(app: App) = getInstallFilesForPackage(
        packageName = app.packageName,
        versionCode = app.versionCode,
        offerType = app.offerType
    )

    private fun refreshAuthIfNeeded(failedAuth: AuthData): AuthData = synchronized(authRefreshLock) {
        val properties = deviceProperties
        val latestAuth = readStoredAuthData()
            .withDeviceInfoProvider(properties)
            .withGermanPlayStoreLocale()
        if (latestAuth.authToken.isNotBlank() && latestAuth.authToken != failedAuth.authToken) {
            latestAuth
        } else {
            refreshAuth()
        }
    }

    private fun readStoredAuthData(): AuthData = readAuthDataOrReset(
        read = { prefs.playAuthData.get() },
        reset = { prefs.playAuthData.put(it) },
        onCorrupt = { exception ->
            val summary = describeStoredAuthJsonFailure(exception)
            val message = "Saved Play session JSON is incompatible ($summary); replacing the cached session before re-authentication."
            if (exception is JsonIOException) {
                // Gson's JsonIOException here contains only the unsupported model type and stack;
                // it does not contain the serialized session or credentials.
                Log.w("PlayRepository", message, exception)
            } else {
                Log.w("PlayRepository", message)
            }
        }
    )

    private fun saveAuthData(authData: AuthData) {
        // DeviceInfoProvider contains generated protobuf internals. Gson cannot deserialize those
        // collections from the persisted AuthData, so reconstruct the provider from local device
        // properties whenever the session is read instead of storing it.
        prefs.playAuthData.put(authData.withoutDeviceInfoProvider())
    }

}

internal const val PLAY_DETAILS_BATCH_SIZE = 20
internal const val PLAY_DETAILS_REQUEST_CONCURRENCY = 2

internal fun <Parsed> requestTokenDispenserWithRetry(
    maxAttempts: Int = PlayRepository.AUTH_MAX_ATTEMPTS,
    initialDelayMs: Long = PlayRepository.AUTH_INITIAL_BACKOFF_MS,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    request: () -> com.aurora.gplayapi.data.models.PlayResponse,
    parse: (ByteArray) -> Parsed,
    onRetry: (attempt: Int, maxAttempts: Int, reason: String, delayMs: Long) -> Unit = { _, _, _, _ -> }
): Parsed {
    require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
    var delayMs = initialDelayMs.coerceAtLeast(0L)
    var lastException: Exception? = null

    for (attempt in 1..maxAttempts) {
        try {
            val response = request()
            if (!response.isSuccessful) {
                val reason = "HTTP ${response.code}"
                val failure = IllegalStateException("Auth not successful ($reason).")
                if (attempt < maxAttempts && isRetryableAuthStatusCode(response.code)) {
                    onRetry(attempt, maxAttempts, reason, delayMs)
                    if (delayMs > 0L) sleep(delayMs)
                    delayMs = (delayMs * 2L).coerceAtMost(3_000L)
                    lastException = failure
                    continue
                }
                throw failure
            }
            return parse(response.responseBytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            lastException = exception
            val retryable = exception !is UnknownHostException &&
                (exception is IOException || exception is JsonParseException)
            if (attempt < maxAttempts && retryable) {
                val reason = describePlayFailure(exception, 0)
                onRetry(attempt, maxAttempts, reason, delayMs)
                if (delayMs > 0L) sleep(delayMs)
                delayMs = (delayMs * 2L).coerceAtMost(3_000L)
                continue
            }
            throw exception
        }
    }

    throw lastException ?: IllegalStateException("Auth not successful.")
}

internal fun isRetryableAuthStatusCode(code: Int): Boolean =
    code == 408 || code == 425 || code == 429 || code in 500..599

internal fun <Auth, Result> requestWithUnauthorizedRetry(
    initialAuth: Auth,
    responseCode: () -> Int,
    refreshAuth: () -> Auth,
    request: (Auth) -> Result
): Result = try {
    request(initialAuth)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    if (!shouldRefreshPlayAuth(responseCode())) throw exception
    request(refreshAuth())
}

internal fun shouldRefreshPlayAuth(responseCode: Int): Boolean =
    responseCode == 401 || responseCode == 403

internal fun describePlayFailure(exception: Throwable, responseCode: Int): String = when {
	exception is SocketTimeoutException -> "timeout"
	exception is UnknownHostException -> "DNS lookup failed"
	responseCode in 400..599 -> "HTTP $responseCode"
	exception is IllegalStateException && exception.message?.contains("HTTP ") == true -> {
        Regex("HTTP \\d{3}").find(exception.message.orEmpty())?.value ?: "HTTP error"
    }
	exception.javaClass.simpleName.contains("ProtocolBuffer") -> "response parsing failure (${exception.javaClass.simpleName})"
	exception is IOException -> "network error (${exception.javaClass.simpleName})"
	else -> {
        val origin = exception.stackTrace.take(2).joinToString(separator = " <- ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }.takeIf { it.isNotEmpty() }?.let { " at $it" }.orEmpty()
        "response processing error (${exception.javaClass.simpleName})$origin"
    }
}

/**
 * Gson does not apply Kotlin constructor defaults when it reflectively creates AuthData. In
 * particular, the token dispenser may omit optional fields such as email, leaving them null;
 * AuthData.copy() then throws before the session can be normalized. Build the model through its
 * supported builder so omitted fields retain their documented defaults.
 */
internal fun parseAuthData(responseJson: String, gson: Gson): AuthData {
    val json = gson.fromJson(responseJson, JsonObject::class.java)
        ?: throw IllegalStateException("Auth response did not contain a session object.")
    fun string(name: String): String = json.get(name)
        ?.takeUnless { it.isJsonNull }
        ?.asString
        .orEmpty()

    val anonymous = json.get("isAnonymous")
        ?.takeUnless { it.isJsonNull }
        ?.asBoolean
        ?: false

    return AuthData.Builder(email = string("email")).apply {
        aasToken = string("aasToken")
        authToken = string("authToken")
        isAnonymous = anonymous
        gsfId = string("gsfId")
        tokenDispenserUrl = string("tokenDispenserUrl")
        ac2dmToken = string("ac2dmToken")
        androidCheckInToken = string("androidCheckInToken")
        deviceCheckInConsistencyToken = string("deviceCheckInConsistencyToken")
        deviceConfigToken = string("deviceConfigToken")
        experimentsConfigToken = string("experimentsConfigToken")
        gcmToken = string("gcmToken")
        oAuthLoginToken = string("oAuthLoginToken")
        dfeCookie = string("dfeCookie")
    }.build()
}

internal fun AuthData.withDeviceInfoProvider(properties: java.util.Properties): AuthData {
    val safeLocale = localeOrDefault(locale)
    return AuthData.Builder(email = nonNullString(email)).apply {
        aasToken = nonNullString(this@withDeviceInfoProvider.aasToken)
        authToken = nonNullString(this@withDeviceInfoProvider.authToken)
        isAnonymous = this@withDeviceInfoProvider.isAnonymous
        gsfId = nonNullString(this@withDeviceInfoProvider.gsfId)
        tokenDispenserUrl = nonNullString(this@withDeviceInfoProvider.tokenDispenserUrl)
        ac2dmToken = nonNullString(this@withDeviceInfoProvider.ac2dmToken)
        androidCheckInToken = nonNullString(this@withDeviceInfoProvider.androidCheckInToken)
        deviceCheckInConsistencyToken = nonNullString(this@withDeviceInfoProvider.deviceCheckInConsistencyToken)
        deviceConfigToken = nonNullString(this@withDeviceInfoProvider.deviceConfigToken)
        experimentsConfigToken = nonNullString(this@withDeviceInfoProvider.experimentsConfigToken)
        gcmToken = nonNullString(this@withDeviceInfoProvider.gcmToken)
        oAuthLoginToken = nonNullString(this@withDeviceInfoProvider.oAuthLoginToken)
        dfeCookie = nonNullString(this@withDeviceInfoProvider.dfeCookie)
        locale = safeLocale
        deviceInfoProvider = this@withDeviceInfoProvider.deviceInfoProvider
            ?: DeviceInfoProvider(properties, safeLocale.toString())
        userProfile = this@withDeviceInfoProvider.userProfile
    }.build()
}

private fun nonNullString(value: String?): String = value.orEmpty()

private fun localeOrDefault(value: Locale?): Locale = value ?: Locale.getDefault()

internal fun AuthData.hasRequiredPlaySession(): Boolean =
    authToken.isNotBlank() && gsfId.isNotBlank() && deviceInfoProvider != null

internal fun AuthData.withoutDeviceInfoProvider(): AuthData =
    copy(deviceInfoProvider = null, userProfile = null)

internal fun readAuthDataOrReset(
    read: () -> AuthData,
    reset: (AuthData) -> Unit,
    onCorrupt: (JsonParseException) -> Unit = {}
): AuthData = try {
    read()
} catch (exception: JsonParseException) {
    onCorrupt(exception)
    AuthData("", "").withoutDeviceInfoProvider().also(reset)
} catch (exception: Exception) {
    if (exception is CancellationException) throw exception
    onCorrupt(JsonParseException(exception))
    AuthData("", "").withoutDeviceInfoProvider().also(reset)
}

internal fun describeStoredAuthJsonFailure(exception: JsonParseException): String {
    val jsonIOException = (exception as? JsonIOException) ?: (exception.cause as? JsonIOException)
    val offendingType = jsonIOException?.message
        ?.let { Regex("(?:Interface name|Class name): ([A-Za-z0-9_$.]+)").find(it)?.groupValues?.get(1) }
    return if (offendingType != null) {
        "${jsonIOException.javaClass.simpleName} for $offendingType"
    } else {
        (exception.cause ?: exception).javaClass.simpleName
    }
}

internal fun AuthData.withGermanPlayStoreLocale(): AuthData =
    copy(locale = Locale.forLanguageTag("de-DE"))

fun App.toAppUpdate(
    getInstallFiles: (App) -> List<PlayFile>,
    oldVersion: String = "",
    oldVersionCode: Long = 0L,
    installOrigin: InstallOrigin = InstallOrigin.SYSTEM,
    installerPackageName: String = ""
) = AppUpdate(
    name = displayName,
    packageName = packageName,
    version = versionName,
    oldVersion = oldVersion,
    versionCode = versionCode,
    oldVersionCode = oldVersionCode,
    source = PlaySource,
    link = Link.Play { getInstallFiles(this) },
    whatsNew = cleanChangelogText(changes),
    totalSize = size,
    updatedOn = updatedOn,
    offerType = offerType,
    installOrigin = installOrigin,
    installerPackageName = installerPackageName
)
