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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
        internal const val SESSION_CHECK_INTERVAL_MS = 60 * 60 * 1_000L // 1 hour
        internal const val DEFAULT_PLAY_COOLDOWN_MS = 60_000L
        internal const val DEFAULT_DISPENSER_COOLDOWN_MS = 60_000L
        internal const val SHORT_COOLDOWN_WAIT_THRESHOLD_MS = 3_000L
        internal const val DELIVERY_RETRY_BACKOFF_MS = 2_000L
    }

    /**
     * Centralized mutex guarding Google Play anonymous authentication, token dispenser requests,
     * session probing, and session persistence to prevent race conditions and thundering herds.
     */
    private val sessionMutex = Mutex()

    /**
     * In-memory cached session to avoid redundant AES decryption and Gson parsing on hot paths.
     * Guaranteed thread visibility via @Volatile.
     */
    @Volatile
    private var cachedAuthData: AuthData? = null

    /**
     * Cooldown timestamps for rate-limiting protection.
     */
    @Volatile
    internal var playCooldownUntilMs: Long = 0L

    @Volatile
    internal var dispenserCooldownUntilMs: Long = 0L

    private val deviceProperties by lazy {
        NativeDeviceInfoProvider(context).getNativeDeviceProperties()
    }

    internal fun isPlayCooldownActive(now: Long = SystemClock.elapsedRealtime()): Boolean =
        now < playCooldownUntilMs

    internal fun isDispenserCooldownActive(now: Long = SystemClock.elapsedRealtime()): Boolean =
        now < dispenserCooldownUntilMs

    internal fun getRemainingPlayCooldownMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (playCooldownUntilMs - now).coerceAtLeast(0L)

    internal fun getRemainingDispenserCooldownMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (dispenserCooldownUntilMs - now).coerceAtLeast(0L)

    internal fun activatePlayCooldown(
        durationMs: Long = DEFAULT_PLAY_COOLDOWN_MS,
        now: Long = SystemClock.elapsedRealtime()
    ) {
        synchronized(this) {
            playCooldownUntilMs = maxOf(playCooldownUntilMs, now + durationMs)
        }
    }

    internal fun setPlayCooldown(durationMs: Long, now: Long = SystemClock.elapsedRealtime()) {
        activatePlayCooldown(durationMs, now)
    }

    internal fun activateDispenserCooldown(
        durationMs: Long = DEFAULT_DISPENSER_COOLDOWN_MS,
        now: Long = SystemClock.elapsedRealtime()
    ) {
        synchronized(this) {
            dispenserCooldownUntilMs = maxOf(dispenserCooldownUntilMs, now + durationMs)
        }
    }

    internal fun setDispenserCooldown(durationMs: Long, now: Long = SystemClock.elapsedRealtime()) {
        activateDispenserCooldown(durationMs, now)
    }

    internal fun enforcePlayCooldown(
        now: Long = SystemClock.elapsedRealtime(),
        sleep: (Long) -> Unit = { Thread.sleep(it) }
    ) {
        val remaining = playCooldownUntilMs - now
        if (remaining > 0L) {
            if (remaining <= SHORT_COOLDOWN_WAIT_THRESHOLD_MS) {
                sleep(remaining)
            } else {
                val seconds = remaining / 1000L
                throw IllegalStateException("Google Play rate limit cooldown active (${seconds}s remaining).")
            }
        }
    }

    internal fun resetCooldowns() {
        playCooldownUntilMs = 0L
        dispenserCooldownUntilMs = 0L
    }

    private fun isSessionCheckExpired(): Boolean {
        val lastCheck = prefs.lastPlayCheck.get()
        return System.currentTimeMillis() - lastCheck > SESSION_CHECK_INTERVAL_MS
    }

    private fun readAndNormalizeStoredAuthData(): AuthData {
        val properties = deviceProperties
        return readStoredAuthData()
            .withDeviceInfoProvider(properties)
            .withGermanPlayStoreLocale()
    }

    /**
     * Internal token dispenser request and persistence execution.
     * MUST ONLY be called while [sessionMutex] is held by the caller.
     */
    private fun performTokenDispenserAuthLocked(): AuthData {
        val now = SystemClock.elapsedRealtime()
        val remainingDispenserCooldown = (dispenserCooldownUntilMs - now).coerceAtLeast(0L)
        if (remainingDispenserCooldown > 0L) {
            if (remainingDispenserCooldown <= SHORT_COOLDOWN_WAIT_THRESHOLD_MS) {
                Log.i("PlayRepository", "Dispenser cooldown active; waiting ${remainingDispenserCooldown}ms.")
                Thread.sleep(remainingDispenserCooldown)
            } else {
                val seconds = remainingDispenserCooldown / 1000L
                Log.w("PlayRepository", "Dispenser cooldown active; rejecting auth request (${seconds}s remaining).")
                throw IllegalStateException("Aurora token dispenser cooldown active for ${seconds}s.")
            }
        }

        Log.i("PlayRepository", "Starting token-dispenser authentication under sessionMutex.")
        val properties = deviceProperties
        val payloadBytes = gson.toJson(properties).toByteArray()
        val receivedAuthData = try {
            requestTokenDispenserWithRetry(
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
        } catch (exception: Exception) {
            if (playHttpClient.responseCodeForCurrentThread() == 429 ||
                exception.message?.contains("429") == true
            ) {
                activateDispenserCooldown(DEFAULT_DISPENSER_COOLDOWN_MS)
                Log.w("PlayRepository", "Token-dispenser returned HTTP 429; enforced ${DEFAULT_DISPENSER_COOLDOWN_MS / 1000}s cooldown.")
            }
            throw exception
        }

        if (receivedAuthData.deviceInfoProvider == null) {
            Log.w("PlayRepository", "Auth response did not include a device profile; rebuilding it from submitted device properties.")
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

        cachedAuthData = savedAuthData
        prefs.lastPlayCheck.put(System.currentTimeMillis())
        Log.i("PlayRepository", "Auth session saved, cached in memory, and ready.")
        return savedAuthData
    }

    /**
     * Retrieves a valid Google Play AuthData session.
     * Uses double-checked locking with [sessionMutex] to avoid dispenser thundering herds
     * while enabling lock-free in-memory reads on the fast path.
     */
    internal suspend fun auth(): AuthData {
        val memoryAuth = cachedAuthData
        if (memoryAuth != null && memoryAuth.hasRequiredPlaySession() && !isSessionCheckExpired()) {
            return memoryAuth
        }

        return sessionMutex.withLock {
            val doubleCheck = cachedAuthData ?: readAndNormalizeStoredAuthData()
            if (doubleCheck.hasRequiredPlaySession() && !isSessionCheckExpired()) {
                cachedAuthData = doubleCheck
                return@withLock doubleCheck
            }

            if (!doubleCheck.hasRequiredPlaySession()) {
                Log.w("PlayRepository", "Saved Play session is missing or incomplete; requesting fresh session.")
                return@withLock performTokenDispenserAuthLocked()
            }

            if (isSessionCheckExpired()) {
                Log.i("PlayRepository", "Session check interval expired; probing Play session validity.")
                prefs.lastPlayCheck.put(System.currentTimeMillis())

                val probeSuccess = runCatching {
                    Log.i("PlayRepository", "Checking saved Play session with a single app-detail request.")
                    val app = AppDetailsHelper(doubleCheck)
                        .using(playHttpClient)
                        .getAppByPackageName("com.google.android.gm")
                    app.packageName.isNotEmpty()
                }.getOrElse { exception ->
                    val code = playHttpClient.responseCodeForCurrentThread()
                    if (shouldRefreshPlayAuth(code)) {
                        Log.w("PlayRepository", "Saved Play session rejected with HTTP $code; refreshing session.")
                        false
                    } else {
                        throw exception
                    }
                }

                if (!probeSuccess) {
                    return@withLock performTokenDispenserAuthLocked()
                }
                Log.i("PlayRepository", "Play session probe succeeded; token still valid.")
            }

            cachedAuthData = doubleCheck
            doubleCheck
        }
    }

    internal fun authBlocking(): AuthData = runBlocking { auth() }

    /**
     * Refreshes the Google Play anonymous account under [sessionMutex].
     */
    internal suspend fun refreshAuth(
        force: Boolean = false,
        failedToken: String? = null
    ): AuthData = sessionMutex.withLock {
        val current = cachedAuthData ?: readAndNormalizeStoredAuthData()
        if (!force && failedToken != null && current.authToken.isNotBlank() && current.authToken != failedToken) {
            Log.i("PlayRepository", "Session was already refreshed by another concurrent caller; adopting fresh session.")
            cachedAuthData = current
            return@withLock current
        }
        performTokenDispenserAuthLocked()
    }

    internal fun refreshAuthBlocking(
        force: Boolean = false,
        failedToken: String? = null
    ): AuthData = runBlocking { refreshAuth(force, failedToken) }

    internal suspend fun refreshAuthIfNeeded(failedAuth: AuthData): AuthData =
        refreshAuth(force = false, failedToken = failedAuth.authToken)

    internal fun refreshAuthIfNeededBlocking(failedAuth: AuthData): AuthData =
        runBlocking { refreshAuthIfNeeded(failedAuth) }

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

            val remainingCooldown = getRemainingPlayCooldownMs()
            if (remainingCooldown > 0L) {
                if (remainingCooldown <= SHORT_COOLDOWN_WAIT_THRESHOLD_MS) {
                    kotlinx.coroutines.delay(remainingCooldown)
                } else {
                    val failure = "Google Play rate limit cooldown active (${remainingCooldown / 1000}s remaining)"
                    Log.w("PlayRepository", failure)
                    emit(SourceUpdateResult(emptyList(), successfulRequests = 0, failedRequests = 1, failureSummary = failure))
                    return@flow
                }
            }

            phase = "authentication"
            val authStartedAt = SystemClock.elapsedRealtime()
            val initialAuthData = auth()
            val authDuration = SystemClock.elapsedRealtime() - authStartedAt
            Log.i("PlayRepository", "Authentication stage completed in ${authDuration} ms.")
            phase = "bulk app-details lookup"
            val requestStartedAt = SystemClock.elapsedRealtime()
            val expectedBatchCount = (packageNames.size + PLAY_DETAILS_BATCH_SIZE - 1) / PLAY_DETAILS_BATCH_SIZE
            val failureSummaries = ConcurrentLinkedQueue<String>()
            val consecutive429Count = java.util.concurrent.atomic.AtomicInteger(0)
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
                        initialAuth = auth(),
                        responseCode = { playHttpClient.responseCodeForCurrentThread() },
                        refreshAuth = {
                            val code = playHttpClient.responseCodeForCurrentThread()
                            if (code == 429) {
                                Thread.sleep(1500L)
                            }
                            refreshAuthIfNeededBlocking(authBlocking())
                        },
                        request = { currentAuth ->
                            try {
                                val appsResult = AppDetailsHelper(currentAuth)
                                    .using(playHttpClient)
                                    .getAppByPackageName(packageBatch)
                                consecutive429Count.set(0)
                                appsResult
                            } catch (e: Exception) {
                                val code = playHttpClient.responseCodeForCurrentThread()
                                if (code == 429) {
                                    val count = consecutive429Count.incrementAndGet()
                                    if (count >= 2) {
                                        activatePlayCooldown(DEFAULT_PLAY_COOLDOWN_MS)
                                    }
                                }
                                throw e
                            }
                        }
                    )
                },
                stopSchedulingAfterFailure = { exception ->
                    exception is UnknownHostException || isPlayCooldownActive()
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
    ): List<PlayFile> = executeWithPlayDeliveryRetry(
        maxAttempts = 2,
        operationName = "purchase($packageName)",
        clock = { SystemClock.elapsedRealtime() },
        sleep = { Thread.sleep(it) },
        getAuth = { authBlocking() },
        refreshAuth = { failedAuth -> refreshAuthIfNeededBlocking(failedAuth) },
        responseCode = { playHttpClient.responseCodeForCurrentThread() },
        isCooldownActive = { isPlayCooldownActive() },
        remainingCooldownMs = { getRemainingPlayCooldownMs() },
        onCooldownEnforced = { duration -> activatePlayCooldown(duration) }
    ) { currentAuth ->
        PurchaseHelper(currentAuth)
            .using(playHttpClient)
            .purchase(packageName, versionCode, offerType)
            .filter { it.type == PlayFile.Type.BASE || it.type == PlayFile.Type.SPLIT }
    }

    internal fun getInstallFilesForPackageBlocking(
        packageName: String,
        versionCode: Long,
        offerType: Int = 1
    ): List<PlayFile> = getInstallFilesForPackage(packageName, versionCode, offerType)

    internal suspend fun getInstallFilesForPackageSuspend(
        packageName: String,
        versionCode: Long,
        offerType: Int = 1
    ): List<PlayFile> = withContext(Dispatchers.IO) {
        getInstallFilesForPackage(packageName, versionCode, offerType)
    }

    private fun getInstallFiles(app: App) = getInstallFilesForPackage(
        packageName = app.packageName,
        versionCode = app.versionCode,
        offerType = app.offerType
    )

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
    responseCode == 401 || responseCode == 403 || responseCode == 429

internal fun isPlayRateLimited(responseCode: Int, exception: Throwable? = null): Boolean =
    responseCode == 429 ||
        exception?.message?.contains("429") == true ||
        exception?.message?.contains("Too Many Requests", ignoreCase = true) == true

internal fun <Result> executeWithPlayDeliveryRetry(
    maxAttempts: Int = 2,
    operationName: String = "delivery",
    clock: () -> Long = { SystemClock.elapsedRealtime() },
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    getAuth: () -> AuthData,
    refreshAuth: (failedAuth: AuthData) -> AuthData,
    responseCode: () -> Int,
    isCooldownActive: () -> Boolean = { false },
    remainingCooldownMs: () -> Long = { 0L },
    onCooldownEnforced: (cooldownDurationMs: Long) -> Unit = {},
    operation: (AuthData) -> List<Result>
): List<Result> {
    require(maxAttempts > 0) { "maxAttempts must be greater than zero" }

    if (isCooldownActive()) {
        val remaining = remainingCooldownMs()
        if (remaining <= PlayRepository.SHORT_COOLDOWN_WAIT_THRESHOLD_MS) {
            if (remaining > 0L) sleep(remaining)
        } else {
            val seconds = remaining / 1000L
            throw IllegalStateException("Google Play rate limit cooldown active for $operationName (${seconds}s remaining).")
        }
    }

    var currentAuth = getAuth()
    var lastException: Exception? = null

    for (attempt in 1..maxAttempts) {
        try {
            val result = operation(currentAuth)
            if (result.isNotEmpty()) {
                return result
            }
            val code = responseCode()
            if (isPlayRateLimited(code, null) || shouldRefreshPlayAuth(code)) {
                throw IllegalStateException("Google Play returned empty files with HTTP $code.")
            }
            return result
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            lastException = exception
            val code = responseCode()
            val rateLimited = isPlayRateLimited(code, exception)
            val authExpired = shouldRefreshPlayAuth(code)

            if (attempt < maxAttempts && (rateLimited || authExpired)) {
                if (rateLimited) {
                    sleep(PlayRepository.DELIVERY_RETRY_BACKOFF_MS)
                }
                currentAuth = refreshAuth(currentAuth)
                continue
            }

            if (rateLimited) {
                onCooldownEnforced(PlayRepository.DEFAULT_PLAY_COOLDOWN_MS)
            }
            throw exception
        }
    }

    throw lastException ?: IllegalStateException("Delivery failed for $operationName.")
}

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
