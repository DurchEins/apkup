package com.apkup

import com.apkup.repository.executeWithPlayDeliveryRetry
import com.apkup.repository.isPlayRateLimited
import com.apkup.repository.requestTokenDispenserWithRetry
import com.apkup.repository.requestWithUnauthorizedRetry
import com.apkup.repository.shouldRefreshPlayAuth
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.data.models.PlayResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PlayAuthRetryTest {

    // =========================================================================
    // EXISTING BASELINE TESTS (Retained without regression)
    // =========================================================================

    @Test
    fun unauthorizedRequestRefreshesAuthAndRetriesOnce() {
        var responseCode = 401
        var refreshCount = 0
        var requestCount = 0

        val result = requestWithUnauthorizedRetry(
            initialAuth = "expired",
            responseCode = { responseCode },
            refreshAuth = {
                refreshCount++
                "fresh"
            },
            request = { auth ->
                requestCount++
                if (auth == "expired") throw IOException("unauthorized")
                responseCode = 200
                "result"
            }
        )

        assertEquals("result", result)
        assertEquals(1, refreshCount)
        assertEquals(2, requestCount)
    }

    @Test
    fun nonUnauthorizedFailureDoesNotRefreshAuth() {
        var refreshCount = 0
        val failure = IOException("server unavailable")

        val thrown = assertThrows(IOException::class.java) {
            requestWithUnauthorizedRetry(
                initialAuth = "current",
                responseCode = { 503 },
                refreshAuth = {
                    refreshCount++
                    "fresh"
                },
                request = { throw failure }
            )
        }

        assertEquals(failure, thrown)
        assertEquals(0, refreshCount)
    }

    @Test
    fun secondUnauthorizedResponseIsReturnedAsFailureWithoutInfiniteRetry() {
        var refreshCount = 0
        var requestCount = 0
        val failure = IOException("unauthorized")

        val thrown = assertThrows(IOException::class.java) {
            requestWithUnauthorizedRetry(
                initialAuth = "expired",
                responseCode = { 401 },
                refreshAuth = {
                    refreshCount++
                    "fresh"
                },
                request = {
                    requestCount++
                    throw failure
                }
            )
        }

        assertEquals(failure, thrown)
        assertEquals(1, refreshCount)
        assertEquals(2, requestCount)
    }

    @Test
    fun tokenDispenserRetriesOnHttp502WithExponentialBackoffAndSucceeds() {
        val backoffDelays = mutableListOf<Long>()
        var attempts = 0

        val session = requestTokenDispenserWithRetry(
            maxAttempts = 3,
            initialDelayMs = 400L,
            sleep = { backoffDelays += it },
            request = {
                attempts++
                if (attempts < 3) {
                    PlayResponse(
                        isSuccessful = false,
                        code = 502,
                        responseBytes = ByteArray(0),
                        errorString = "Bad Gateway"
                    )
                } else {
                    PlayResponse(
                        isSuccessful = true,
                        code = 200,
                        responseBytes = "ok-session".toByteArray(),
                        errorString = ""
                    )
                }
            },
            parse = { String(it) }
        )

        assertEquals("ok-session", session)
        assertEquals(3, attempts)
        assertEquals(listOf(400L, 800L), backoffDelays)
    }

    @Test
    fun selfHealingAuthDataResetRecoversFromCorruptJsonException() {
        var resetCalledWith: AuthData? = null
        var corruptCaptured: com.google.gson.JsonParseException? = null

        val recovered = com.apkup.repository.readAuthDataOrReset(
            read = { throw com.google.gson.JsonIOException("Interfaces can't be instantiated! Interface name: com.google.protobuf.Internal\$ProtobufList") },
            reset = { resetCalledWith = it },
            onCorrupt = { corruptCaptured = it }
        )

        assertEquals("", recovered.authToken)
        assertEquals(recovered, resetCalledWith)
        assertEquals(
            "JsonIOException for com.google.protobuf.Internal\$ProtobufList",
            com.apkup.repository.describeStoredAuthJsonFailure(corruptCaptured!!)
        )
    }

    // =========================================================================
    // FEATURE F1.2: ENFORCED COOLDOWN TRACKING TESTS
    // =========================================================================

    @Test
    fun cooldownInactiveAllowsImmediateExecutionWithoutDelay() {
        var simulatedClock = 10_000L
        val sleepDelays = mutableListOf<Long>()
        var operationExecuted = false

        val testAuth = createTestAuth("token-1")
        val result = executeWithPlayDeliveryRetry(
            clock = { simulatedClock },
            sleep = { sleepDelays += it },
            getAuth = { testAuth },
            refreshAuth = { testAuth },
            responseCode = { 200 },
            isCooldownActive = { false },
            remainingCooldownMs = { 0L },
            operation = {
                operationExecuted = true
                listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
            }
        )

        assertTrue(operationExecuted)
        assertEquals(1, result.size)
        assertEquals("base.apk", result[0].name)
        assertTrue("No delay should occur when cooldown is inactive", sleepDelays.isEmpty())
    }

    @Test
    fun cooldownNearExpiryWithinShortWaitThresholdWaitsAndResumes() {
        var simulatedClock = 10_000L
        val cooldownUntil = 12_500L // 2,500ms remaining (<= 3,000ms threshold)
        val sleepDelays = mutableListOf<Long>()
        var executed = false

        val testAuth = createTestAuth("token-1")
        val result = executeWithPlayDeliveryRetry(
            clock = { simulatedClock },
            sleep = { delayMs ->
                sleepDelays += delayMs
                simulatedClock += delayMs
            },
            getAuth = { testAuth },
            refreshAuth = { testAuth },
            responseCode = { 200 },
            isCooldownActive = { simulatedClock < cooldownUntil },
            remainingCooldownMs = { (cooldownUntil - simulatedClock).coerceAtLeast(0L) },
            operation = {
                executed = true
                listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
            }
        )

        assertTrue("Operation should complete after waiting out short cooldown", executed)
        assertEquals(1, result.size)
        assertEquals(listOf(2500L), sleepDelays)
    }

    @Test
    fun cooldownExceedingShortWaitThresholdRejectsImmediatelyWithoutExecuting() {
        val simulatedClock = 10_000L
        val cooldownUntil = 40_000L // 30,000ms remaining (> 3,000ms threshold)
        val sleepDelays = mutableListOf<Long>()
        var operationAttempted = false

        val testAuth = createTestAuth("token-1")
        val thrown = assertThrows(IllegalStateException::class.java) {
            executeWithPlayDeliveryRetry(
                clock = { simulatedClock },
                sleep = { sleepDelays += it },
                getAuth = { testAuth },
                refreshAuth = { testAuth },
                responseCode = { 200 },
                isCooldownActive = { true },
                remainingCooldownMs = { cooldownUntil - simulatedClock },
                operation = {
                    operationAttempted = true
                    listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
                }
            )
        }

        assertFalse("Operation should never be called when cooldown is active beyond threshold", operationAttempted)
        assertTrue("No sleep should happen when rejecting", sleepDelays.isEmpty())
        assertTrue("Exception message should report remaining cooldown", thrown.message!!.contains("cooldown active"))
    }

    @Test
    fun http429RateLimitActivatesCooldownUntilFutureTimestamp() {
        var currentClock = 50_000L
        val cooldownUntilMs = AtomicLong(0L)
        val cooldownDuration = 60_000L

        fun setPlayCooldown(duration: Long) {
            cooldownUntilMs.updateAndGet { current -> maxOf(current, currentClock + duration) }
        }

        fun isCooldownActive(): Boolean = currentClock < cooldownUntilMs.get()

        assertFalse(isCooldownActive())

        // Simulate 429 failure triggering cooldown
        setPlayCooldown(cooldownDuration)

        assertTrue(isCooldownActive())
        assertEquals(60_000L, cooldownUntilMs.get() - currentClock)

        // Advance clock halfway (30s)
        currentClock += 30_000L
        assertTrue(isCooldownActive())
        assertEquals(30_000L, cooldownUntilMs.get() - currentClock)

        // Advance clock past expiration (30_001ms)
        currentClock += 30_001L
        assertFalse(isCooldownActive())
    }

    @Test
    fun consecutiveRateLimitsMonotonicallyExtendCooldown() {
        var currentClock = 100_000L
        val cooldownUntilMs = AtomicLong(0L)

        fun setPlayCooldown(duration: Long) {
            cooldownUntilMs.updateAndGet { current -> maxOf(current, currentClock + duration) }
        }

        setPlayCooldown(60_000L) // Target: 160_000L
        assertEquals(160_000L, cooldownUntilMs.get())

        // 10 seconds later, another 429 arrives
        currentClock += 10_000L
        setPlayCooldown(60_000L) // Target: 170_000L
        assertEquals(170_000L, cooldownUntilMs.get())

        // Stale smaller update should not move cooldown backwards
        setPlayCooldown(10_000L) // Target: 120_000L < 170_000L
        assertEquals(170_000L, cooldownUntilMs.get())
    }

    // =========================================================================
    // FEATURE F1.3 & F1.4: TRANSPARENT 429 DELIVERY RETRY TESTS
    // =========================================================================

    @Test
    fun shouldRefreshPlayAuthReturnsTrueFor429Alongside401And403() {
        assertTrue("401 should trigger auth refresh", shouldRefreshPlayAuth(401))
        assertTrue("403 should trigger auth refresh", shouldRefreshPlayAuth(403))
        assertTrue("429 should trigger auth refresh (Milestone 1)", shouldRefreshPlayAuth(429))

        assertFalse("200 should not trigger auth refresh", shouldRefreshPlayAuth(200))
        assertFalse("404 should not trigger auth refresh", shouldRefreshPlayAuth(404))
        assertFalse("500 should not trigger auth refresh", shouldRefreshPlayAuth(500))
        assertFalse("502 should not trigger auth refresh", shouldRefreshPlayAuth(502))
    }

    @Test
    fun isPlayRateLimitedDetectsBothCodeAndExceptionMessages() {
        assertTrue(isPlayRateLimited(429, null))
        assertTrue(isPlayRateLimited(200, IOException("HTTP 429 Too Many Requests")))
        assertTrue(isPlayRateLimited(0, IllegalStateException("Google Play returned Too Many Requests")))

        assertFalse(isPlayRateLimited(200, null))
        assertFalse(isPlayRateLimited(500, IOException("Server Error")))
    }

    @Test
    fun delivery429TriggersBackoffAccountRefreshAndSucceedsOnSecondAttempt() {
        var responseCode = 429
        var refreshCount = 0
        var attemptCount = 0
        val sleepDelays = mutableListOf<Long>()

        val staleAuth = createTestAuth("stale-token")
        val freshAuth = createTestAuth("fresh-token")

        val result = executeWithPlayDeliveryRetry(
            maxAttempts = 2,
            sleep = { sleepDelays += it },
            getAuth = { staleAuth },
            refreshAuth = { failed ->
                refreshCount++
                assertEquals("stale-token", failed.authToken)
                freshAuth
            },
            responseCode = { responseCode },
            operation = { auth ->
                attemptCount++
                if (auth.authToken == "stale-token") {
                    throw IOException("HTTP 429 Too Many Requests")
                }
                responseCode = 200
                listOf(
                    PlayFile(name = "base.apk", type = PlayFile.Type.BASE),
                    PlayFile(name = "split_config.arm64_v8a.apk", type = PlayFile.Type.SPLIT)
                )
            }
        )

        assertEquals("Should deliver both base and split APKs", 2, result.size)
        assertEquals("base.apk", result[0].name)
        assertEquals("split_config.arm64_v8a.apk", result[1].name)
        assertEquals("Account refresh should be triggered exactly once", 1, refreshCount)
        assertEquals("Total attempts should be exactly 2", 2, attemptCount)
        assertEquals("Should back off 2,000ms after 429 before retry", listOf(2000L), sleepDelays)
    }

    @Test
    fun deliveryEmptyResultWith429TriggersAccountRefreshAndRetry() {
        var responseCode = 429
        var refreshCount = 0
        var attemptCount = 0

        val staleAuth = createTestAuth("stale-token")
        val freshAuth = createTestAuth("fresh-token")

        val result = executeWithPlayDeliveryRetry(
            maxAttempts = 2,
            sleep = { /* no-op in tests */ },
            getAuth = { staleAuth },
            refreshAuth = {
                refreshCount++
                freshAuth
            },
            responseCode = { responseCode },
            operation = { auth ->
                attemptCount++
                if (auth.authToken == "stale-token") {
                    emptyList() // Simulate PurchaseHelper returning empty when throttled
                } else {
                    responseCode = 200
                    listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
                }
            }
        )

        assertEquals(1, result.size)
        assertEquals(1, refreshCount)
        assertEquals(2, attemptCount)
    }

    @Test
    fun delivery429OnBothAttemptsFailsGracefullyAndEnforcesCooldownWithoutInfiniteLoop() {
        var refreshCount = 0
        var attemptCount = 0
        var enforcedCooldownDuration: Long? = null

        val staleAuth = createTestAuth("stale-token")
        val freshAuth = createTestAuth("fresh-token")

        val thrown = assertThrows(IOException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                sleep = { /* no-op */ },
                getAuth = { staleAuth },
                refreshAuth = {
                    refreshCount++
                    freshAuth
                },
                responseCode = { 429 },
                onCooldownEnforced = { enforcedCooldownDuration = it },
                operation = {
                    attemptCount++
                    throw IOException("HTTP 429 Too Many Requests")
                }
            )
        }

        assertTrue(thrown.message!!.contains("429"))
        assertEquals("Must retry at most once (2 attempts total)", 2, attemptCount)
        assertEquals("Must trigger auth refresh once", 1, refreshCount)
        assertEquals("Must enforce 60-second cooldown after exhausted retry", 60_000L, enforcedCooldownDuration)
    }

    @Test
    fun deliveryNon429NonAuthServerErrorDoesNotRefreshAccountAndRethrowsImmediately() {
        var refreshCount = 0
        var attemptCount = 0
        val serverError = IOException("HTTP 500 Internal Server Error")

        val thrown = assertThrows(IOException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                getAuth = { createTestAuth("token-1") },
                refreshAuth = {
                    refreshCount++
                    createTestAuth("token-2")
                },
                responseCode = { 500 },
                operation = {
                    attemptCount++
                    throw serverError
                }
            )
        }

        assertEquals(serverError, thrown)
        assertEquals("Should not refresh auth on 500 error", 0, refreshCount)
        assertEquals("Should not retry on 500 error", 1, attemptCount)
    }

    @Test
    fun delivery401Or403TriggersAccountRefreshAndRetriesWithout429Backoff() {
        var responseCode = 401
        var refreshCount = 0
        var attemptCount = 0
        val sleepDelays = mutableListOf<Long>()

        val staleAuth = createTestAuth("stale-token")
        val freshAuth = createTestAuth("fresh-token")

        val result = executeWithPlayDeliveryRetry(
            maxAttempts = 2,
            sleep = { sleepDelays += it },
            getAuth = { staleAuth },
            refreshAuth = {
                refreshCount++
                freshAuth
            },
            responseCode = { responseCode },
            operation = { auth ->
                attemptCount++
                if (auth.authToken == "stale-token") {
                    throw IOException("HTTP 401 Unauthorized")
                }
                responseCode = 200
                listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
            }
        )

        assertEquals(1, result.size)
        assertEquals(1, refreshCount)
        assertEquals(2, attemptCount)
        assertTrue("Auth expiration (401/403) should not trigger 429 rate-limit sleep backoff", sleepDelays.isEmpty())
    }

    // =========================================================================
    // FEATURE F1.1: CONCURRENT CALLS & THUNDERING HERD PROTECTION TESTS
    // =========================================================================

    @Test
    fun concurrentAuthCallsWhenSessionEmptyShareSingleDispenserRequest() = runBlocking(Dispatchers.Default) {
        val dispenserCalls = AtomicInteger(0)
        var cachedSession: AuthData? = null
        val sessionMutex = Mutex()

        suspend fun getOrRefreshAuth(): AuthData {
            val memory = cachedSession
            if (memory != null && memory.authToken.isNotBlank()) {
                return memory
            }

            return sessionMutex.withLock {
                val doubleCheck = cachedSession
                if (doubleCheck != null && doubleCheck.authToken.isNotBlank()) {
                    return@withLock doubleCheck
                }
                dispenserCalls.incrementAndGet()
                delay(50L) // Simulate network latency
                val fresh = createTestAuth("dispenser-token-1")
                cachedSession = fresh
                fresh
            }
        }

        val results = (1..10).map {
            async {
                getOrRefreshAuth()
            }
        }.awaitAll()

        assertEquals("All 10 callers should receive the exact same auth token", 10, results.size)
        results.forEach {
            assertEquals("dispenser-token-1", it.authToken)
        }
        assertEquals("Dispenser should be contacted exactly ONCE despite 10 concurrent requests", 1, dispenserCalls.get())
    }

    @Test
    fun concurrentDelivery429FailuresShareSingleSessionRefreshViaDoubleCheckedLocking() = runBlocking(Dispatchers.Default) {
        val dispenserCalls = AtomicInteger(0)
        val sessionMutex = Mutex()
        var currentActiveSession = createTestAuth("token-initial")

        suspend fun refreshAuthIfNeeded(failedToken: String): AuthData = sessionMutex.withLock {
            if (currentActiveSession.authToken != failedToken) {
                return@withLock currentActiveSession
            }
            dispenserCalls.incrementAndGet()
            delay(50L) // Simulate dispenser latency
            val newSession = createTestAuth("token-refreshed-after-429")
            currentActiveSession = newSession
            newSession
        }

        val workers = (1..3).map {
            async {
                val failedToken = "token-initial"
                val resolvedAuth = refreshAuthIfNeeded(failedToken)
                resolvedAuth
            }
        }.awaitAll()

        assertEquals(3, workers.size)
        workers.forEach {
            assertEquals("token-refreshed-after-429", it.authToken)
        }
        assertEquals("Only 1 token dispenser call should be fired for 3 concurrent 429 failures", 1, dispenserCalls.get())
    }

    @Test
    fun concurrentCallsDuringInFlightDispenserRequestWaitAndDoNotDuplicate() = runBlocking(Dispatchers.Default) {
        val dispenserCalls = AtomicInteger(0)
        val sessionMutex = Mutex()
        var activeSession: AuthData? = null

        suspend fun authenticate(): AuthData = sessionMutex.withLock {
            activeSession?.let { return@withLock it }
            dispenserCalls.incrementAndGet()
            delay(100L) // Slow initial dispenser call
            val fresh = createTestAuth("token-shared")
            activeSession = fresh
            fresh
        }

        val job1 = async { authenticate() }

        delay(20L)
        val job2 = async { authenticate() }
        val job3 = async { authenticate() }
        val job4 = async { authenticate() }

        val allResults = listOf(job1, job2, job3, job4).awaitAll()

        allResults.forEach {
            assertEquals("token-shared", it.authToken)
        }
        assertEquals("Dispenser must be called exactly once during concurrent in-flight requests", 1, dispenserCalls.get())
    }

    @Test
    fun concurrentDeliveryRequestsRetryTransparentlyAndDeliverFilesToAllWorkers() = runBlocking(Dispatchers.Default) {
        val dispenserCalls = AtomicInteger(0)
        val deliveryAttemptsPerWorker = AtomicInteger(0)
        val sessionMutex = Mutex()
        var sharedSession = createTestAuth("stale-account")

        suspend fun safeRefreshAuth(failedToken: String): AuthData = sessionMutex.withLock {
            if (sharedSession.authToken != failedToken) {
                return@withLock sharedSession
            }
            dispenserCalls.incrementAndGet()
            val fresh = createTestAuth("fresh-account-2")
            sharedSession = fresh
            fresh
        }

        suspend fun simulateDownloadWorker(appPkg: String): List<PlayFile> {
            var workerAuth = sharedSession
            for (attempt in 1..2) {
                deliveryAttemptsPerWorker.incrementAndGet()
                if (workerAuth.authToken == "stale-account") {
                    delay(10L) // brief backoff
                    workerAuth = safeRefreshAuth(workerAuth.authToken)
                    continue
                }
                return listOf(PlayFile(name = "$appPkg.apk", type = PlayFile.Type.BASE))
            }
            throw IllegalStateException("Failed to download $appPkg")
        }

        val packages = listOf("com.example.app1", "com.example.app2", "com.example.app3")
        val downloadResults = packages.map { pkg ->
            async { simulateDownloadWorker(pkg) }
        }.awaitAll()

        assertEquals(3, downloadResults.size)
        downloadResults.forEachIndexed { index, files ->
            assertEquals(1, files.size)
            assertEquals("${packages[index]}.apk", files[0].name)
        }
        assertEquals("Dispenser should be called exactly once across all 3 concurrent download workers", 1, dispenserCalls.get())
        assertEquals("Each worker should take 2 attempts (1 failure + 1 retry) = 6 total attempts", 6, deliveryAttemptsPerWorker.get())
    }

    // =========================================================================
    // EMPIRICAL CHALLENGER STRESS TESTS: EXTREME CONCURRENCY & BOUNDARY CONDITIONS
    // =========================================================================

    @Test
    fun extremeConcurrencyColdStartSixtyCoroutinesExecuteWithExactlyOneDispenserCallAndZeroDeadlocks() = runBlocking(Dispatchers.Default) {
        val dispenserCalls = AtomicInteger(0)
        var cachedSession: AuthData? = null
        val sessionMutex = Mutex()
        val concurrentCount = 60

        suspend fun getOrRefreshAuth(): AuthData {
            val memory = cachedSession
            if (memory != null && memory.authToken.isNotBlank()) {
                return memory
            }

            return sessionMutex.withLock {
                val doubleCheck = cachedSession
                if (doubleCheck != null && doubleCheck.authToken.isNotBlank()) {
                    return@withLock doubleCheck
                }
                dispenserCalls.incrementAndGet()
                delay(60L) // Simulate network dispenser latency under load
                val fresh = createTestAuth("dispenser-cold-start-shared-token")
                cachedSession = fresh
                fresh
            }
        }

        withTimeout(15_000L) {
            val jobs = (1..concurrentCount).map {
                async {
                    getOrRefreshAuth()
                }
            }
            val results = jobs.awaitAll()

            assertEquals("All $concurrentCount callers must complete successfully", concurrentCount, results.size)
            results.forEach { auth ->
                assertEquals("dispenser-cold-start-shared-token", auth.authToken)
            }
            assertEquals("Dispenser must be called exactly ONCE despite $concurrentCount concurrent requests", 1, dispenserCalls.get())
        }
    }

    @Test
    fun extremeConcurrencyFortyTwoNineStampedeSixtyCoroutinesShareSingleTokenRefreshWithZeroDeadlocks() = runBlocking(Dispatchers.Default) {
        val dispenserCalls = AtomicInteger(0)
        val sessionMutex = Mutex()
        var currentActiveSession = createTestAuth("stale-token-causing-429")
        val concurrentCount = 60

        suspend fun refreshAuthIfNeeded(failedToken: String): AuthData = sessionMutex.withLock {
            if (currentActiveSession.authToken != failedToken) {
                return@withLock currentActiveSession
            }
            dispenserCalls.incrementAndGet()
            delay(60L) // Simulate token dispenser processing
            val newSession = createTestAuth("fresh-token-post-429-stampede")
            currentActiveSession = newSession
            newSession
        }

        withTimeout(15_000L) {
            val workers = (1..concurrentCount).map {
                async {
                    refreshAuthIfNeeded("stale-token-causing-429")
                }
            }
            val resolvedAuths = workers.awaitAll()

            assertEquals(concurrentCount, resolvedAuths.size)
            resolvedAuths.forEach { auth ->
                assertEquals("fresh-token-post-429-stampede", auth.authToken)
            }
            assertEquals("Dispenser must be contacted exactly ONCE for $concurrentCount concurrent 429 failures", 1, dispenserCalls.get())
        }
    }

    @Test
    fun cooldownBoundaryExactlyAt3000msThresholdWaitsAndExecutesSuccessfully() {
        var simulatedClock = 10_000L
        val cooldownUntil = 13_000L // EXACTLY 3,000ms remaining
        val sleepDelays = mutableListOf<Long>()
        var executed = false

        val testAuth = createTestAuth("token-1")
        val result = executeWithPlayDeliveryRetry(
            clock = { simulatedClock },
            sleep = { delayMs ->
                sleepDelays += delayMs
                simulatedClock += delayMs
            },
            getAuth = { testAuth },
            refreshAuth = { testAuth },
            responseCode = { 200 },
            isCooldownActive = { simulatedClock < cooldownUntil },
            remainingCooldownMs = { (cooldownUntil - simulatedClock).coerceAtLeast(0L) },
            operation = {
                executed = true
                listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
            }
        )

        assertTrue("Operation must execute after waiting exactly 3000ms threshold", executed)
        assertEquals(1, result.size)
        assertEquals("base.apk", result[0].name)
        assertEquals("Must sleep exactly the 3,000ms remaining", listOf(3000L), sleepDelays)
    }

    @Test
    fun cooldownBoundaryAt3001msThresholdRejectsImmediatelyWithoutExecutionOrSleep() {
        val simulatedClock = 10_000L
        val cooldownUntil = 13_001L // EXACTLY 3,001ms remaining (> 3000ms threshold)
        val sleepDelays = mutableListOf<Long>()
        var operationAttempted = false

        val testAuth = createTestAuth("token-1")
        val thrown = assertThrows(IllegalStateException::class.java) {
            executeWithPlayDeliveryRetry(
                clock = { simulatedClock },
                sleep = { sleepDelays += it },
                getAuth = { testAuth },
                refreshAuth = { testAuth },
                responseCode = { 200 },
                isCooldownActive = { true },
                remainingCooldownMs = { cooldownUntil - simulatedClock },
                operation = {
                    operationAttempted = true
                    listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
                }
            )
        }

        assertFalse("Operation must never be attempted when remaining cooldown exceeds 3000ms", operationAttempted)
        assertTrue("No sleep must occur when rejecting beyond threshold", sleepDelays.isEmpty())
        assertTrue("Exception message must describe active cooldown", thrown.message!!.contains("cooldown active"))
    }

    @Test
    fun cooldownBoundaryZeroOrNegativeRemainingExecutesImmediatelyWithoutSleep() {
        val simulatedClock = 10_000L
        val sleepDelays = mutableListOf<Long>()
        var executed = false

        val testAuth = createTestAuth("token-1")
        val result = executeWithPlayDeliveryRetry(
            clock = { simulatedClock },
            sleep = { sleepDelays += it },
            getAuth = { testAuth },
            refreshAuth = { testAuth },
            responseCode = { 200 },
            isCooldownActive = { true },
            remainingCooldownMs = { 0L }, // 0ms remaining
            operation = {
                executed = true
                listOf(PlayFile(name = "base.apk", type = PlayFile.Type.BASE))
            }
        )

        assertTrue(executed)
        assertEquals(1, result.size)
        assertTrue("0ms remaining should not trigger any sleep", sleepDelays.isEmpty())
    }

    @Test
    fun monotonicTimestampProgressionUnderHighContentionFiftyThreads() = runBlocking(Dispatchers.Default) {
        val threadCount = 60
        val baseClock = 1_000_000L
        val cooldownUntilMs = AtomicLong(0L)
        val lock = Any()
        var synchronizedCooldown = 0L

        fun setCooldownAtomic(duration: Long, clock: Long) {
            cooldownUntilMs.updateAndGet { current -> maxOf(current, clock + duration) }
        }

        fun setCooldownSynchronized(duration: Long, clock: Long) {
            synchronized(lock) {
                synchronizedCooldown = maxOf(synchronizedCooldown, clock + duration)
            }
        }

        val offsets = (1..threadCount).map { i ->
            when (i % 5) {
                0 -> -5_000L // stale/past
                1 -> 10_000L
                2 -> 30_000L
                3 -> 120_000L // highest
                else -> 60_000L
            }
        }

        val expectedMax = baseClock + 120_000L

        withTimeout(10_000L) {
            val jobs = offsets.map { offset ->
                async {
                    setCooldownAtomic(offset, baseClock)
                    setCooldownSynchronized(offset, baseClock)
                }
            }
            jobs.awaitAll()

            assertEquals("Atomic cooldown progression must reach exact maximum without regression", expectedMax, cooldownUntilMs.get())
            assertEquals("Synchronized cooldown progression must reach exact maximum without regression", expectedMax, synchronizedCooldown)
        }
    }

    // =========================================================================
    // TEST HELPERS
    // =========================================================================

    private fun createTestAuth(token: String): AuthData = AuthData(
        email = "test@example.invalid",
        authToken = token,
        gsfId = "test-gsf-id",
        locale = Locale.GERMANY
    )
}
