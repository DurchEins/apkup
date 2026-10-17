package com.apkup

import com.apkup.repository.requestWithUnauthorizedRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PlayAuthRetryTest {

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

        val session = com.apkup.repository.requestTokenDispenserWithRetry(
            maxAttempts = 3,
            initialDelayMs = 400L,
            sleep = { backoffDelays += it },
            request = {
                attempts++
                if (attempts < 3) {
                    com.aurora.gplayapi.data.models.PlayResponse(
                        isSuccessful = false,
                        code = 502,
                        responseBytes = ByteArray(0),
                        errorString = "Bad Gateway"
                    )
                } else {
                    com.aurora.gplayapi.data.models.PlayResponse(
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
        var resetCalledWith: com.aurora.gplayapi.data.models.AuthData? = null
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
}
