package com.apkup

import com.apkup.repository.PlayRepository
import com.apkup.repository.executeWithPlayDeliveryRetry
import com.apkup.repository.isPlayRateLimited
import com.apkup.repository.shouldRefreshPlayAuth
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Empirical stress tests authored by Challenger 2 targeting:
 * 1. Multi-attempt 429 delivery retry loop termination, account refresh, and cooldown activation.
 * 2. Mixed error sequences (429 -> 500, 429 -> DNS drop / UnknownHostException, 429 -> SocketTimeoutException).
 * 3. Network drop during account refresh and preservation of uncorrupted session state.
 * 4. Cancellation exception propagation without swallowing or retry loops.
 */
class PlayAuthRetryTestChallenger2 {

    // =========================================================================
    // STRESS VECTOR 1: MULTI-ATTEMPT 429 DELIVERY RETRY & COOLDOWN TERMINATION
    // =========================================================================

    @Test
    fun multiAttempt429_exhaustsRetries_activates60sCooldownAndRejectsSubsequentCalls() {
        var simulatedClock = 100_000L
        val sleepDelays = mutableListOf<Long>()
        var attemptCount = 0
        var refreshCount = 0
        val activeCooldownUntilMs = AtomicLong(0L)

        val staleAuth = createTestAuth("stale-token-429")
        val refreshedAuth = createTestAuth("refreshed-token-429")
        val currentSessionRef = AtomicReference(staleAuth)

        fun isCooldownActive(): Boolean = simulatedClock < activeCooldownUntilMs.get()
        fun remainingCooldown(): Long = (activeCooldownUntilMs.get() - simulatedClock).coerceAtLeast(0L)
        fun setCooldown(duration: Long) {
            activeCooldownUntilMs.updateAndGet { current -> maxOf(current, simulatedClock + duration) }
        }

        // Call 1: Delivery attempt 1 fails with 429 -> refreshes -> attempt 2 fails with 429
        val thrown = assertThrows(IOException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                operationName = "test-delivery",
                clock = { simulatedClock },
                sleep = { delayMs ->
                    sleepDelays += delayMs
                    simulatedClock += delayMs
                },
                getAuth = { currentSessionRef.get() },
                refreshAuth = { failedAuth ->
                    refreshCount++
                    assertEquals("stale-token-429", failedAuth.authToken)
                    currentSessionRef.set(refreshedAuth)
                    refreshedAuth
                },
                responseCode = { 429 },
                isCooldownActive = ::isCooldownActive,
                remainingCooldownMs = ::remainingCooldown,
                onCooldownEnforced = { duration -> setCooldown(duration) },
                operation = { auth ->
                    attemptCount++
                    throw IOException("HTTP 429 Too Many Requests")
                }
            )
        }

        // Assertions for Call 1
        assertTrue("Exception message must indicate 429", thrown.message!!.contains("429"))
        assertEquals("Must execute exactly 2 attempts before aborting (no infinite loop)", 2, attemptCount)
        assertEquals("Must refresh account exactly once after attempt 1 failure", 1, refreshCount)
        assertEquals("Must sleep 2000ms backoff between attempt 1 and attempt 2", listOf(2000L), sleepDelays)
        assertTrue("Play cooldown must be active after exhausted 429 retries", isCooldownActive())
        assertEquals(
            "Enforced cooldown must be exactly 60,000ms from current clock",
            60_000L,
            remainingCooldown()
        )

        // Call 2: Immediate subsequent call must be rejected upfront without invoking getAuth or operation
        var secondCallAttempted = false
        val rejectedException = assertThrows(IllegalStateException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                operationName = "test-delivery-subsequent",
                clock = { simulatedClock },
                sleep = { delayMs -> simulatedClock += delayMs },
                getAuth = {
                    secondCallAttempted = true
                    currentSessionRef.get()
                },
                refreshAuth = { currentSessionRef.get() },
                responseCode = { 200 },
                isCooldownActive = ::isCooldownActive,
                remainingCooldownMs = ::remainingCooldown,
                onCooldownEnforced = { duration -> setCooldown(duration) },
                operation = {
                    secondCallAttempted = true
                    listOf(PlayFile(name = "test.apk", type = PlayFile.Type.BASE))
                }
            )
        }

        assertFalse("Subsequent call must not execute operation while cooldown is active", secondCallAttempted)
        assertTrue(
            "Rejection exception must detail active cooldown and remaining duration",
            rejectedException.message!!.contains("cooldown active") && rejectedException.message!!.contains("remaining")
        )
    }

    @Test
    fun multiAttempt429_configurableMaxAttempts3_retriesExactCountBeforeFailing() {
        var attempts = 0
        var refreshes = 0
        val sleepDelays = mutableListOf<Long>()
        var cooldownEnforcedDuration: Long? = null

        val authSessions = listOf(
            createTestAuth("token-attempt-1"),
            createTestAuth("token-attempt-2"),
            createTestAuth("token-attempt-3")
        )

        val thrown = assertThrows(IOException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 3,
                sleep = { sleepDelays += it },
                getAuth = { authSessions[0] },
                refreshAuth = {
                    refreshes++
                    authSessions[refreshes]
                },
                responseCode = { 429 },
                onCooldownEnforced = { cooldownEnforcedDuration = it },
                operation = {
                    attempts++
                    throw IOException("HTTP 429 Rate limited on attempt $attempts")
                }
            )
        }

        assertEquals("Should attempt exactly 3 times when maxAttempts is 3", 3, attempts)
        assertEquals("Should trigger account refresh 2 times (after attempt 1 and 2)", 2, refreshes)
        assertEquals("Should execute 2 backoff sleeps of 2,000ms each", listOf(2000L, 2000L), sleepDelays)
        assertEquals("Cooldown must be enforced upon exhausting maxAttempts", 60_000L, cooldownEnforcedDuration)
        assertTrue(thrown.message!!.contains("Rate limited on attempt 3"))
    }

    // =========================================================================
    // STRESS VECTOR 2: MIXED ERROR CONDITIONS & EXCEPTION PROPAGATION
    // =========================================================================

    @Test
    fun mixedErrors_429FollowedByHttp500_refreshesAccountOnceAndRethrows500WithoutCooldown() {
        var attempts = 0
        var refreshes = 0
        var cooldownEnforced = false
        val sleepDelays = mutableListOf<Long>()
        val responseCodeHolder = AtomicInteger(429)

        val initialAuth = createTestAuth("token-v1")
        val refreshedAuth = createTestAuth("token-v2")
        val currentAuthRef = AtomicReference(initialAuth)

        val serverError = IOException("HTTP 500 Internal Server Error")

        val thrown = assertThrows(IOException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                sleep = { sleepDelays += it },
                getAuth = { currentAuthRef.get() },
                refreshAuth = { failed ->
                    refreshes++
                    assertEquals("token-v1", failed.authToken)
                    currentAuthRef.set(refreshedAuth)
                    refreshedAuth
                },
                responseCode = { responseCodeHolder.get() },
                onCooldownEnforced = { cooldownEnforced = true },
                operation = { auth ->
                    attempts++
                    if (auth.authToken == "token-v1") {
                        responseCodeHolder.set(429)
                        throw IOException("HTTP 429 Too Many Requests")
                    } else {
                        responseCodeHolder.set(500)
                        throw serverError
                    }
                }
            )
        }

        assertSame("Must rethrow the exact 500 exception from attempt 2", serverError, thrown)
        assertEquals("Must execute attempt 1 (429) and attempt 2 (500)", 2, attempts)
        assertEquals("Must trigger auth refresh once after attempt 1", 1, refreshes)
        assertEquals("Must sleep 2,000ms backoff before attempt 2", listOf(2000L), sleepDelays)
        assertFalse(
            "HTTP 500 terminal error must NOT enforce Play rate-limit cooldown",
            cooldownEnforced
        )
        assertEquals("Session state must remain updated to refreshed token", "token-v2", currentAuthRef.get().authToken)
    }

    @Test
    fun mixedErrors_429FollowedByUnknownHostException_rethrowsDnsFailureWithoutCooldown() {
        var attempts = 0
        var refreshes = 0
        var cooldownEnforced = false
        val responseCodeHolder = AtomicInteger(429)

        val dnsException = UnknownHostException("Unable to resolve host play.googleapis.com")

        val thrown = assertThrows(UnknownHostException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                sleep = { /* no-op */ },
                getAuth = { createTestAuth("token-stale") },
                refreshAuth = {
                    refreshes++
                    createTestAuth("token-fresh")
                },
                responseCode = { responseCodeHolder.get() },
                onCooldownEnforced = { cooldownEnforced = true },
                operation = { auth ->
                    attempts++
                    if (attempts == 1) {
                        responseCodeHolder.set(429)
                        throw IOException("HTTP 429 Too Many Requests")
                    } else {
                        // Network disconnected: response code resets to 0 and DNS resolution fails
                        responseCodeHolder.set(0)
                        throw dnsException
                    }
                }
            )
        }

        assertSame("Must propagate the UnknownHostException without wrapping", dnsException, thrown)
        assertEquals("Total delivery attempts should be 2", 2, attempts)
        assertEquals("Auth refresh was executed after attempt 1", 1, refreshes)
        assertFalse("DNS failure must not trigger Play rate-limit cooldown", cooldownEnforced)
    }

    @Test
    fun mixedErrors_429FollowedBySocketTimeout_rethrowsTimeoutWithoutCooldown() {
        var attempts = 0
        var refreshes = 0
        var cooldownEnforced = false
        val responseCodeHolder = AtomicInteger(429)

        val timeoutException = SocketTimeoutException("Read timed out waiting for delivery stream")

        val thrown = assertThrows(SocketTimeoutException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                sleep = { /* no-op */ },
                getAuth = { createTestAuth("token-stale") },
                refreshAuth = {
                    refreshes++
                    createTestAuth("token-fresh")
                },
                responseCode = { responseCodeHolder.get() },
                onCooldownEnforced = { cooldownEnforced = true },
                operation = { auth ->
                    attempts++
                    if (attempts == 1) {
                        responseCodeHolder.set(429)
                        throw IOException("HTTP 429 Too Many Requests")
                    } else {
                        responseCodeHolder.set(0)
                        throw timeoutException
                    }
                }
            )
        }

        assertSame("Must propagate SocketTimeoutException directly", timeoutException, thrown)
        assertEquals(2, attempts)
        assertEquals(1, refreshes)
        assertFalse(cooldownEnforced)
    }

    @Test
    fun mixedErrors_networkDropDuringAccountRefresh_abortsImmediatelyAndKeepsOriginalSession() {
        var deliveryAttempts = 0
        var refreshAttempts = 0
        var cooldownEnforced = false

        val originalAuth = createTestAuth("original-valid-session")
        val currentSessionRef = AtomicReference(originalAuth)

        val networkDropException = UnknownHostException("No address associated with hostname auroraoss.com")

        val thrown = assertThrows(UnknownHostException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                sleep = { /* no-op */ },
                getAuth = { currentSessionRef.get() },
                refreshAuth = {
                    refreshAttempts++
                    // Dispenser network fails during refresh
                    throw networkDropException
                },
                responseCode = { 429 },
                onCooldownEnforced = { cooldownEnforced = true },
                operation = {
                    deliveryAttempts++
                    throw IOException("HTTP 429 Too Many Requests")
                }
            )
        }

        assertSame("Network failure during refresh must propagate immediately", networkDropException, thrown)
        assertEquals("Delivery attempted only once before refresh failed", 1, deliveryAttempts)
        assertEquals("Refresh was attempted once", 1, refreshAttempts)
        assertFalse("Delivery cooldown must not be enforced when refresh itself aborted", cooldownEnforced)
        assertEquals(
            "Original session must remain unchanged and uncorrupted",
            "original-valid-session",
            currentSessionRef.get().authToken
        )
    }

    @Test
    fun mixedErrors_cancellationDuringRetry_propagatesImmediatelyWithoutSwallowing() {
        var attempts = 0
        var refreshCount = 0

        val thrown = assertThrows(CancellationException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                sleep = { /* no-op */ },
                getAuth = { createTestAuth("token-1") },
                refreshAuth = {
                    refreshCount++
                    createTestAuth("token-2")
                },
                responseCode = { 429 },
                operation = {
                    attempts++
                    throw CancellationException("Download job was cancelled by user")
                }
            )
        }

        assertTrue("CancellationException must be rethrown directly", thrown.message!!.contains("cancelled"))
        assertEquals("Must abort immediately on attempt 1 without retry", 1, attempts)
        assertEquals("Must not refresh auth when cancelled", 0, refreshCount)
    }

    @Test
    fun mixedErrors_unexpectedRuntimeExceptionOnFirstAttempt_rethrowsImmediatelyWithoutRetry() {
        var attempts = 0
        var refreshes = 0

        val runtimeException = IllegalArgumentException("Invalid package parameters")

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            executeWithPlayDeliveryRetry<PlayFile>(
                maxAttempts = 2,
                getAuth = { createTestAuth("token-1") },
                refreshAuth = {
                    refreshes++
                    createTestAuth("token-2")
                },
                responseCode = { 200 },
                operation = {
                    attempts++
                    throw runtimeException
                }
            )
        }

        assertSame(runtimeException, thrown)
        assertEquals("Should not retry non-retryable runtime exceptions", 1, attempts)
        assertEquals(0, refreshes)
    }

    // =========================================================================
    // HELPER FUNCTIONS
    // =========================================================================

    private fun createTestAuth(token: String): AuthData = AuthData(
        email = "test@example.invalid",
        authToken = token,
        gsfId = "test-gsf-id",
        locale = Locale.GERMANY
    )
}
