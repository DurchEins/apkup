package com.apkup

import com.apkup.prefs.StoredPlaySession
import com.apkup.repository.describePlayFailure
import com.apkup.repository.describeStoredAuthJsonFailure
import com.apkup.repository.hasRequiredPlaySession
import com.apkup.repository.isReusableFor
import com.apkup.repository.readPlaySessionOrReset
import com.apkup.repository.requestWithUnauthorizedRetry
import com.apkup.repository.resolveActivePlayStoreLocale
import com.apkup.repository.toAnonymousSession
import com.apkup.util.play.NativeDeviceInfoProvider
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.PlayException
import com.aurora.gplayapi.device.DeviceRegistry
import com.aurora.gplayapi.net.HttpEngine
import com.aurora.gplayapi.net.HttpResponse
import com.aurora.gplayapi.net.PlayRequest
import com.aurora.gplayapi.session.AnonymousSession
import com.aurora.gplayapi.session.PlaySessions
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.Properties

class PlayStoreLocaleTest {

    @Test
    fun bulkAppDetailsRequestUsesGermanLocaleAndReportsHttpFailure() {
        val client = CapturingPlayHttpClient(HttpResponse(code = 500, body = byteArrayOf(), headers = emptyMap(), message = "test"))
        val locale = resolveActivePlayStoreLocale(Locale.GERMANY)
        val playClient = PlayClient(client, FixedPlaySessions(testAnonymousSession(locale)))

        val result = runCatching {
            runBlocking { playClient.apps.details(listOf("example.app")) }
        }

        assertTrue(result.isFailure)
        assertEquals("DE", locale.country)
        assertEquals("de-DE", client.headerValue("Accept-Language"))
        assertEquals("de-DE", client.headerValue("X-DFE-UserLanguages"))
    }

    @Test
    fun validBulkResponseWithNoEntriesIsAnEmptySuccessfulLookup() {
        val client = CapturingPlayHttpClient(
            HttpResponse(code = 200, body = byteArrayOf(), headers = emptyMap(), message = "OK")
        )
        val playClient = PlayClient(
            client,
            FixedPlaySessions(testAnonymousSession(resolveActivePlayStoreLocale(Locale.GERMANY)))
        )

        val details = runBlocking { playClient.apps.details(listOf("example.app")) }

        assertTrue(details.isEmpty())
        assertEquals(1, client.requestCount)
    }

    @Test
    fun malformedHttp200PayloadIsAProcessingFailureAndDoesNotTriggerAuthRetry() {
        val client = CapturingPlayHttpClient(
            HttpResponse(code = 200, body = byteArrayOf(0x0A, 0xFF.toByte()), headers = emptyMap(), message = "OK")
        )
        val playClient = PlayClient(
            client,
            FixedPlaySessions(testAnonymousSession(resolveActivePlayStoreLocale(Locale.GERMANY)))
        )
        var refreshCount = 0

        val result = runCatching {
            requestWithUnauthorizedRetry(
                initialAuth = testAnonymousSession(Locale.GERMANY),
                responseCode = { 200 },
                refreshAuth = {
                    refreshCount++
                    testAnonymousSession(Locale.GERMANY)
                },
                request = {
                    runBlocking { playClient.apps.details(listOf("example.app")) }
                }
            )
        }

        assertTrue(result.isFailure)
        assertEquals(1, client.requestCount)
        assertEquals(0, refreshCount)
        val failure = result.exceptionOrNull()!!
        assertTrue(
            describePlayFailure(failure, 200).startsWith("response parsing failure") ||
                describePlayFailure(failure, 200).startsWith("response processing error")
        )
    }

    @Test
    fun storedSessionValidationRejectsMissingGsfIdOrDeviceConfigToken() {
        val valid = StoredPlaySession(
            gsfId = "test-device",
            deviceConfigToken = "test-config",
            checkinConsistencyToken = "test-consistency",
            localeTag = Locale.GERMANY.toLanguageTag(),
            mintedAtMillis = System.currentTimeMillis()
        )
        assertTrue(valid.hasRequiredPlaySession())
        assertTrue(valid.isReusableFor(Locale.GERMANY))
        assertFalse(valid.isReusableFor(Locale.US))
        assertFalse(valid.copy(gsfId = "").hasRequiredPlaySession())
        assertFalse(valid.copy(deviceConfigToken = "").hasRequiredPlaySession())
    }

    @Test
    fun corruptStoredSessionIsResetSoAuthenticationCanRefresh() {
        val gson = Gson()
        var storedJson = "{not-valid-json"
        var loggedCause: JsonParseException? = null
        val recovered = readPlaySessionOrReset(
            read = { gson.fromJson(storedJson, StoredPlaySession::class.java) },
            reset = { storedJson = gson.toJson(it) },
            onCorrupt = { loggedCause = it }
        )

        assertTrue(loggedCause != null)
        assertEquals("", recovered.gsfId)
        assertEquals("", recovered.deviceConfigToken)

        val resetSession = gson.fromJson(storedJson, StoredPlaySession::class.java)
        assertEquals("", resetSession.gsfId)
        assertEquals("", resetSession.deviceConfigToken)
    }

    @Test
    fun persistableSessionRoundTripsAndBuildsAnonymousSession() {
        val gson = Gson()
        val now = System.currentTimeMillis()
        val stored = StoredPlaySession(
            gsfId = "test-device",
            deviceConfigToken = "test-config",
            checkinConsistencyToken = "test-consistency",
            localeTag = Locale.GERMANY.toLanguageTag(),
            mintedAtMillis = now
        )
        val restored = gson.fromJson(gson.toJson(stored), StoredPlaySession::class.java)
        val device = DeviceRegistry.parse("native", testDeviceProperties())
        val anonymous = restored.toAnonymousSession(device, Locale.GERMANY)

        assertTrue(restored.hasRequiredPlaySession())
        assertTrue(restored.isReusableFor(Locale.GERMANY, now + 60_000L))
        assertEquals("test-device", anonymous.gsfId)
        assertEquals("test-config", anonymous.deviceConfigToken)
        assertEquals("lynx", device.property("Build.DEVICE"))
    }

    @Test
    fun describePlayFailureFormatsTypedPlayExceptions() {
        assertEquals("HTTP 429 (rate limited)", describePlayFailure(PlayException.RateLimited(429, "rate limited"), 429))
        assertEquals("HTTP 401/403 (unauthorized)", describePlayFailure(PlayException.Unauthorized(401, "unauthorized"), 401))
        assertEquals("app not found (404)", describePlayFailure(PlayException.NotFound(404, "missing"), 404))
        val cause = JsonIOException("Interfaces can't be instantiated! Interface name: com.google.protobuf.Internal\$ProtobufList")
        assertEquals(
            "JsonIOException for com.google.protobuf.Internal\$ProtobufList",
            describeStoredAuthJsonFailure(JsonParseException(cause))
        )
    }

    private fun testDeviceProperties(): Properties = NativeDeviceInfoProvider.buildAnonymousProperties(
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        sdkInt = 35,
        releaseVersion = "15",
        activeLocale = Locale.US
    )

    private fun testAnonymousSession(locale: Locale): AnonymousSession = AnonymousSession(
        device = DeviceRegistry.parse("native", testDeviceProperties()),
        locale = locale,
        additionalLocales = emptyList(),
        gsfId = "3c9c9a9b9f000001",
        deviceConfigToken = "test-config",
        checkinConsistencyToken = "test-consistency",
        mintedAtMillis = System.currentTimeMillis()
    )

    private class FixedPlaySessions(
        private val session: AnonymousSession
    ) : PlaySessions {
        override suspend fun session() = session
    }

    private class CapturingPlayHttpClient(private val response: HttpResponse) : HttpEngine {
        var headers: Map<String, String> = emptyMap()
        var requestCount = 0

        fun headerValue(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override suspend fun execute(request: PlayRequest): HttpResponse {
            requestCount++
            headers = request.headers
            return response
        }
    }
}
