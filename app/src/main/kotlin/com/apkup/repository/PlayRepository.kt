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
import com.apkup.prefs.StoredPlaySession
import com.apkup.util.play.NativeDeviceInfoProvider
import com.apkup.util.play.PlayHttpClient
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.PlayException
import com.aurora.gplayapi.device.DeviceProfile
import com.aurora.gplayapi.device.DeviceRegistry
import com.aurora.gplayapi.model.App
import com.aurora.gplayapi.model.AppFile
import com.aurora.gplayapi.net.HttpResponse
import com.aurora.gplayapi.session.AnonymousSession
import com.aurora.gplayapi.session.DefaultPlaySessions
import com.aurora.gplayapi.session.PlaySession
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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
        internal const val AUTH_MAX_ATTEMPTS = 3
        internal const val AUTH_INITIAL_BACKOFF_MS = 400L
        internal const val SESSION_CHECK_INTERVAL_MS = 60 * 60 * 1_000L // 1 hour
        internal const val DEFAULT_PLAY_COOLDOWN_MS = 60_000L
        internal const val DEFAULT_DISPENSER_COOLDOWN_MS = 60_000L
        internal const val SHORT_COOLDOWN_WAIT_THRESHOLD_MS = 3_000L
        internal const val DELIVERY_RETRY_BACKOFF_MS = 2_000L
    }

    /**
     * Centralized mutex guarding Google Play anonymous session minting, cache hydration,
     * and persistence to prevent race conditions and thundering herds.
     */
    private val sessionMutex = Mutex()

    @Volatile
    internal var playCooldownUntilMs: Long = 0L

    @Volatile
    internal var dispenserCooldownUntilMs: Long = 0L

    private val deviceProperties by lazy {
        NativeDeviceInfoProvider(context).getNativeDeviceProperties()
    }

    private val deviceProfile: DeviceProfile by lazy {
        DeviceRegistry.parse("native", deviceProperties)
    }

    private val playSessions by lazy {
        DefaultPlaySessions(
            playHttpClient,
            { deviceProfile },
            { resolveActivePlayStoreLocale() }
        )
    }

    @Volatile
    private var lastKnownSession: PlaySession? = null

    private val playClient by lazy {
        PlayClient(
            playHttpClient,
            playSessions
        )
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

    internal suspend fun auth(): PlaySession {
        val activeLocale = resolveActivePlayStoreLocale()
        lastKnownSession?.let { cached ->
            if (cached.locale == activeLocale) {
                return cached
            }
        }

        return sessionMutex.withLock {
            val minted = playSessions.session()
            lastKnownSession = minted
            if (minted is AnonymousSession) {
                savePlaySession(minted)
                prefs.lastPlayCheck.put(System.currentTimeMillis())
            }
            minted
        }
    }

    internal fun authBlocking(): PlaySession = runBlocking { auth() }

    internal suspend fun refreshAuth(
        force: Boolean = false,
        failedToken: String? = null
    ): PlaySession = sessionMutex.withLock {
        val current = lastKnownSession
        if (!force && failedToken != null && current != null && current.gsfId != failedToken) {
            Log.i("PlayRepository", "Session was already refreshed by another concurrent caller; adopting fresh session.")
            return@withLock current
        }

        playSessions.invalidateAnonymous()
        lastKnownSession = null
        prefs.playAuthData.put(StoredPlaySession())
        val refreshed = playSessions.session()
        lastKnownSession = refreshed
        if (refreshed is AnonymousSession) {
            savePlaySession(refreshed)
            prefs.lastPlayCheck.put(System.currentTimeMillis())
        }
        refreshed
    }

    internal fun refreshAuthBlocking(
        force: Boolean = false,
        failedToken: String? = null
    ): PlaySession = runBlocking { refreshAuth(force, failedToken) }

    internal suspend fun refreshAuthIfNeeded(failedSession: PlaySession): PlaySession =
        refreshAuth(force = false, failedToken = failedSession.gsfId)

    internal fun refreshAuthIfNeededBlocking(failedSession: PlaySession): PlaySession =
        runBlocking { refreshAuthIfNeeded(failedSession) }

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
            val initialSession = auth()
            val authDuration = SystemClock.elapsedRealtime() - authStartedAt
            Log.i("PlayRepository", "Authentication stage completed in ${authDuration} ms.")
            phase = "bulk app-details lookup"
            val requestStartedAt = SystemClock.elapsedRealtime()
            val expectedBatchCount = (packageNames.size + PLAY_DETAILS_BATCH_SIZE - 1) / PLAY_DETAILS_BATCH_SIZE
            val failureSummaries = ConcurrentLinkedQueue<String>()
            val consecutive429Count = java.util.concurrent.atomic.AtomicInteger(0)
            Log.i(
                "PlayRepository",
                "Starting bulk app-details lookup for ${packageNames.size} package ID(s) in " +
                    "$expectedBatchCount batch(es), up to $PLAY_DETAILS_REQUEST_CONCURRENCY concurrent."
            )
            val requestResult = requestConcurrentBatches<String, App>(
                items = packageNames,
                batchSize = PLAY_DETAILS_BATCH_SIZE,
                concurrency = PLAY_DETAILS_REQUEST_CONCURRENCY,
                request = { packageBatch ->
                    requestWithUnauthorizedRetrySuspend(
                        initialAuth = initialSession,
                        responseCode = { playHttpClient.responseCodeForCurrentThread() },
                        refreshAuth = { failedSession ->
                            val code = playHttpClient.responseCodeForCurrentThread()
                            if (code == 429) {
                                kotlinx.coroutines.delay(1500L)
                            }
                            refreshAuthIfNeeded(failedSession)
                        },
                        request = {
                            try {
                                val appsResult = playClient.apps.details(packageBatch)
                                consecutive429Count.set(0)
                                appsResult
                            } catch (e: Exception) {
                                val code = playHttpClient.responseCodeForCurrentThread()
                                if (e is PlayException.RateLimited || code == 429) {
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
    ): List<AppFile> = executeWithPlayDeliveryRetry(
        maxAttempts = 2,
        operationName = "delivery($packageName)",
        clock = { SystemClock.elapsedRealtime() },
        sleep = { Thread.sleep(it) },
        getAuth = { authBlocking() },
        refreshAuth = { failedSession -> refreshAuthIfNeededBlocking(failedSession) },
        responseCode = { playHttpClient.responseCodeForCurrentThread() },
        isCooldownActive = { isPlayCooldownActive() },
        remainingCooldownMs = { getRemainingPlayCooldownMs() },
        onCooldownEnforced = { duration -> activatePlayCooldown(duration) }
    ) {
        runBlocking {
            playClient.delivery.resolve(
                packageName = packageName,
                versionCode = versionCode,
                offerType = offerType
            )
        }.filter { it.type == AppFile.Type.BASE || it.type == AppFile.Type.SPLIT }
    }

    internal fun getInstallFilesForPackageBlocking(
        packageName: String,
        versionCode: Long,
        offerType: Int = 1
    ): List<AppFile> = getInstallFilesForPackage(packageName, versionCode, offerType)

    internal suspend fun getInstallFilesForPackageSuspend(
        packageName: String,
        versionCode: Long,
        offerType: Int = 1
    ): List<AppFile> = withContext(Dispatchers.IO) {
        getInstallFilesForPackage(packageName, versionCode, offerType)
    }

    private fun getInstallFiles(app: App) = getInstallFilesForPackage(
        packageName = app.packageName,
        versionCode = app.versionCode,
        offerType = app.offerType
    )

    private fun readStoredPlaySession(): StoredPlaySession = readPlaySessionOrReset(
        read = { prefs.playAuthData.get() },
        reset = { prefs.playAuthData.put(it) },
        onCorrupt = { exception ->
            val summary = describeStoredAuthJsonFailure(exception)
            val message = "Saved Play session JSON is incompatible ($summary); replacing the cached session before re-authentication."
            if (exception is JsonIOException) {
                Log.w("PlayRepository", message, exception)
            } else {
                Log.w("PlayRepository", message)
            }
        }
    )

    private fun savePlaySession(session: AnonymousSession) {
        prefs.playAuthData.put(
            StoredPlaySession(
                gsfId = session.gsfId,
                deviceConfigToken = session.deviceConfigToken,
                checkinConsistencyToken = session.checkinConsistencyToken,
                localeTag = session.locale.toLanguageTag(),
                mintedAtMillis = session.mintedAtMillis
            )
        )
    }
}

internal const val PLAY_DETAILS_BATCH_SIZE = 20
internal const val PLAY_DETAILS_REQUEST_CONCURRENCY = 2

internal fun <Parsed> requestTokenDispenserWithRetry(
    maxAttempts: Int = PlayRepository.AUTH_MAX_ATTEMPTS,
    initialDelayMs: Long = PlayRepository.AUTH_INITIAL_BACKOFF_MS,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    request: () -> HttpResponse,
    parse: (ByteArray) -> Parsed,
    onRetry: (attempt: Int, maxAttempts: Int, reason: String, delayMs: Long) -> Unit = { _, _, _, _ -> }
): Parsed {
    require(maxAttempts > 0) { "maxAttempts must be greater than zero" }
    var delayMs = initialDelayMs.coerceAtLeast(0L)
    var lastException: Exception? = null

    for (attempt in 1..maxAttempts) {
        try {
            val response = request()
            if (response.code !in 200..299) {
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
            return parse(response.body)
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
    if (!shouldRefreshPlayAuth(responseCode(), exception)) throw exception
    request(refreshAuth())
}

internal suspend fun <Auth, Result> requestWithUnauthorizedRetrySuspend(
    initialAuth: Auth,
    responseCode: () -> Int,
    refreshAuth: suspend (failedAuth: Auth) -> Auth,
    request: suspend (Auth) -> Result
): Result = try {
    request(initialAuth)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    if (!shouldRefreshPlayAuth(responseCode(), exception)) throw exception
    request(refreshAuth(initialAuth))
}

internal fun shouldRefreshPlayAuth(responseCode: Int, exception: Throwable? = null): Boolean =
    responseCode == 401 ||
        responseCode == 403 ||
        responseCode == 429 ||
        exception is PlayException.Unauthorized ||
        exception is PlayException.RateLimited

internal fun isPlayRateLimited(responseCode: Int, exception: Throwable? = null): Boolean =
    responseCode == 429 ||
        exception is PlayException.RateLimited ||
        exception?.message?.contains("429") == true ||
        exception?.message?.contains("Too Many Requests", ignoreCase = true) == true

internal fun <Auth, Result> executeWithPlayDeliveryRetry(
    maxAttempts: Int = 2,
    operationName: String = "delivery",
    clock: () -> Long = { SystemClock.elapsedRealtime() },
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    getAuth: () -> Auth,
    refreshAuth: (failedAuth: Auth) -> Auth,
    responseCode: () -> Int,
    isCooldownActive: () -> Boolean = { false },
    remainingCooldownMs: () -> Long = { 0L },
    onCooldownEnforced: (cooldownDurationMs: Long) -> Unit = {},
    operation: (Auth) -> List<Result>
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
            val authExpired = shouldRefreshPlayAuth(code, exception)

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
    exception is PlayException.RateLimited -> "HTTP 429 (rate limited)"
    exception is PlayException.Unauthorized -> "HTTP 401/403 (unauthorized)"
    exception is PlayException.NotFound -> "app not found (404)"
    exception is PlayException.AppRemoved -> "app removed from Play Store"
    exception is PlayException.AppNotSupported -> "app not supported on this device"
    exception is PlayException.NoFilesAvailable -> "no install files available"
    exception is PlayException.AppNotPurchased -> "app not purchased"
    exception is PlayException.RequiresAccount -> "account required"
    exception is PlayException.ServerError -> "Play Store server error (${responseCode.takeIf { it in 500..599 } ?: 500})"
    responseCode in 400..599 -> "HTTP $responseCode"
    exception is IllegalStateException && exception.message?.contains("HTTP ") == true -> {
        Regex("HTTP \\d{3}").find(exception.message.orEmpty())?.value ?: "HTTP error"
    }
    exception.javaClass.simpleName.contains("ProtocolBuffer") ||
        exception.javaClass.simpleName.contains("InvalidProtocolBuffer") ->
        "response parsing failure (${exception.javaClass.simpleName})"
    exception is IOException -> "network error (${exception.javaClass.simpleName})"
    else -> {
        val origin = exception.stackTrace.take(2).joinToString(separator = " <- ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }.takeIf { it.isNotEmpty() }?.let { " at $it" }.orEmpty()
        "response processing error (${exception.javaClass.simpleName})$origin"
    }
}

internal fun StoredPlaySession.hasRequiredPlaySession(): Boolean =
    gsfId.isNotBlank() && deviceConfigToken.isNotBlank()

internal fun StoredPlaySession.isReusableFor(
    locale: Locale,
    nowMillis: Long = System.currentTimeMillis()
): Boolean =
    hasRequiredPlaySession() &&
        localeTag == locale.toLanguageTag() &&
        mintedAtMillis > 0L &&
        (nowMillis - mintedAtMillis) < PlayRepository.SESSION_CHECK_INTERVAL_MS

internal fun StoredPlaySession.toAnonymousSession(
    device: DeviceProfile,
    locale: Locale
): AnonymousSession = AnonymousSession(
    device = device,
    locale = locale,
    additionalLocales = emptyList(),
    gsfId = gsfId,
    deviceConfigToken = deviceConfigToken,
    checkinConsistencyToken = checkinConsistencyToken,
    mintedAtMillis = mintedAtMillis
)

internal fun readPlaySessionOrReset(
    read: () -> StoredPlaySession,
    reset: (StoredPlaySession) -> Unit,
    onCorrupt: (JsonParseException) -> Unit = {}
): StoredPlaySession = try {
    read()
} catch (exception: JsonParseException) {
    onCorrupt(exception)
    StoredPlaySession().also(reset)
} catch (exception: Exception) {
    if (exception is CancellationException) throw exception
    onCorrupt(JsonParseException(exception))
    StoredPlaySession().also(reset)
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

/**
 * Normalizes the Google Play session locale to the active device/app [Locale] (`F-15`),
 * ensuring country fallback when a language-only locale is configured.
 */
internal fun resolveActivePlayStoreLocale(locale: Locale = Locale.getDefault()): Locale = when {
    locale.language.isBlank() -> Locale.US
    locale.country.isNotBlank() -> locale
    else -> Locale.Builder()
        .setLanguage(locale.language)
        .setRegion(defaultCountryForLanguage(locale.language))
        .build()
}

private fun defaultCountryForLanguage(language: String): String = when (language.lowercase(Locale.ROOT)) {
    "de" -> "DE"
    "en" -> "US"
    "es" -> "ES"
    "fr" -> "FR"
    "pt" -> "BR"
    "it" -> "IT"
    "ru" -> "RU"
    "zh" -> "CN"
    "ja" -> "JP"
    else -> "US"
}

fun App.toAppUpdate(
    getInstallFiles: (App) -> List<AppFile>,
    oldVersion: String = "",
    oldVersionCode: Long = 0L,
    installOrigin: InstallOrigin = InstallOrigin.SYSTEM,
    installerPackageName: String = ""
) = AppUpdate(
    name = displayName.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
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
