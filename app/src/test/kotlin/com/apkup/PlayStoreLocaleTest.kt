package com.apkup

import com.apkup.repository.withGermanPlayStoreLocale
import com.apkup.repository.withDeviceInfoProvider
import com.apkup.repository.hasRequiredPlaySession
import com.apkup.repository.describePlayFailure
import com.apkup.repository.parseAuthData
import com.apkup.repository.readAuthDataOrReset
import com.apkup.repository.describeStoredAuthJsonFailure
import com.apkup.repository.withoutDeviceInfoProvider
import com.apkup.repository.requestWithUnauthorizedRetry
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayResponse
import com.aurora.gplayapi.data.providers.DeviceInfoProvider
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.network.IHttpClient
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.google.gson.JsonParseException
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Locale
import java.util.Properties

class PlayStoreLocaleTest {

    @Test
    fun bulkAppDetailsRequestUsesGermanLocaleAndReportsHttpFailure() {
        val client = CapturingPlayHttpClient(PlayResponse(isSuccessful = false, code = 500, errorString = "test"))
        val authData = testAuthData().withGermanPlayStoreLocale()

        val result = runCatching {
            AppDetailsHelper(authData).using(client).getAppByPackageName(listOf("example.app"))
        }

        assertTrue(result.isFailure)
        assertEquals("DE", authData.locale.country)
        assertEquals("de-DE", client.headers["Accept-Language"])
        assertEquals("de_DE", client.headers["X-DFE-UserLanguages"])
    }

    @Test
    fun validBulkResponseWithNoEntriesIsAnEmptySuccessfulLookup() {
        val client = CapturingPlayHttpClient(
            PlayResponse(isSuccessful = true, code = 200, responseBytes = byteArrayOf())
        )

        val details = AppDetailsHelper(testAuthData().withGermanPlayStoreLocale())
            .using(client)
            .getAppByPackageName(listOf("example.app"))

        assertTrue(details.isEmpty())
        assertEquals(200, client.responseCode.value)
    }

    @Test
    fun malformedHttp200PayloadIsAProcessingFailureAndDoesNotTriggerAuthRetry() {
        val client = CapturingPlayHttpClient(
            PlayResponse(isSuccessful = true, code = 200, responseBytes = byteArrayOf(0x0A, 0xFF.toByte()))
        )
        var refreshCount = 0

        val failure = assertThrows(Exception::class.java) {
            requestWithUnauthorizedRetry(
                initialAuth = parseAuthData(
                    """{"authToken":"test-token","gsfId":"test-device","unexpected":"ignored"}""",
                    Gson()
                ).withDeviceInfoProvider(testAuthData().deviceInfoProvider!!.properties)
                    .withGermanPlayStoreLocale(),
                responseCode = { client.responseCode.value },
                refreshAuth = {
                    refreshCount++
                    testAuthData()
                },
                request = { auth ->
                    AppDetailsHelper(auth).using(client).getAppByPackageName(listOf("example.app"))
                }
            )
        }

        assertEquals(200, client.responseCode.value)
        assertEquals(0, refreshCount)
        assertTrue(failure.javaClass.simpleName.contains("Protocol"))
        assertTrue(describePlayFailure(failure, 200).startsWith("response parsing failure"))
    }

    @Test
    fun authSessionWithoutDeviceProfileGetsTheSubmittedProfileBeforeDetailsLookup() {
        val completeSession = testAuthData()
        val properties = completeSession.deviceInfoProvider!!.properties
        val sessionWithoutProfile = completeSession.copy(deviceInfoProvider = null)

        val repairedSession = sessionWithoutProfile.withDeviceInfoProvider(properties)

        assertTrue(repairedSession.hasRequiredPlaySession())
        assertEquals("test", repairedSession.deviceInfoProvider!!.properties.getProperty("Build.DEVICE"))
    }

    @Test
    fun authSessionMissingBearerOrDeviceIdIsRejectedBeforeDetailsLookup() {
        assertTrue(testAuthData().hasRequiredPlaySession())
        assertTrue(testAuthData().copy(email = "").hasRequiredPlaySession())
        assertTrue(!testAuthData().copy(authToken = "").hasRequiredPlaySession())
        assertTrue(!testAuthData().copy(gsfId = "").hasRequiredPlaySession())
    }

    @Test
    fun authResponseWithOmittedEmailAndOptionalFieldsUsesKotlinDefaults() {
        val authData = parseAuthData(
            """{"authToken":"test-token","gsfId":"test-device"}""",
            Gson()
        ).withDeviceInfoProvider(testAuthData().deviceInfoProvider!!.properties)
            .withGermanPlayStoreLocale()

        assertEquals("", authData.email)
        assertEquals("test-token", authData.authToken)
        assertEquals("test-device", authData.gsfId)
        assertTrue(authData.hasRequiredPlaySession())
        assertEquals("DE", authData.locale.country)
    }

    @Test
    fun persistedLegacySessionWithMissingEmailCanBeNormalizedSafely() {
        val oldGsonSession = Gson().fromJson(
            """{"authToken":"test-token","gsfId":"test-device"}""",
            AuthData::class.java
        )
        val normalized = oldGsonSession
            .withDeviceInfoProvider(testAuthData().deviceInfoProvider!!.properties)
            .withGermanPlayStoreLocale()

        assertEquals("", normalized.email)
        assertEquals("test-token", normalized.authToken)
        assertTrue(normalized.hasRequiredPlaySession())
    }

    @Test
    fun gsonAuthDataRoundTripReproducesTheProtobufListFailure() {
        val gson = Gson()
        val encoded = gson.toJson(testAuthData())
        val failure = assertThrows(JsonIOException::class.java) {
            gson.fromJson(encoded, AuthData::class.java)
        }
        assertTrue(failure.message.orEmpty().contains("com.google.protobuf.Internal\$ProtobufList"))
        assertEquals(
            "JsonIOException for com.google.protobuf.Internal\$ProtobufList",
            describeStoredAuthJsonFailure(failure)
        )
    }

    @Test
    fun corruptStoredDeviceProfileIsResetSoAuthenticationCanRefresh() {
        val gson = Gson()
        var storedJson = gson.toJson(testAuthData())
        var loggedCause: JsonParseException? = null
        val recovered = readAuthDataOrReset(
            read = { gson.fromJson(storedJson, AuthData::class.java) },
            reset = { storedJson = gson.toJson(it) },
            onCorrupt = { loggedCause = it }
        )

        assertTrue(loggedCause is JsonIOException)
        assertTrue(describeStoredAuthJsonFailure(loggedCause!!).contains("Internal\$ProtobufList"))
        assertEquals("", recovered.authToken)
        assertEquals("", recovered.gsfId)

        // The next read sees the reset value and can proceed to the fresh-auth path.
        val resetSession = gson.fromJson(storedJson, AuthData::class.java)
        assertEquals("", resetSession.authToken)
        assertEquals("", resetSession.gsfId)
    }

    @Test
    fun persistableSessionRoundTripsAndCanFetchAppDetails() {
        val gson = Gson()
        val properties = testAuthData().deviceInfoProvider!!.properties
        val responseSession = parseAuthData(
            """{"authToken":"test-token","gsfId":"test-device","unexpected":"ignored"}""",
            gson
        ).withDeviceInfoProvider(properties).withGermanPlayStoreLocale()
        val persisted = responseSession.withoutDeviceInfoProvider()
        val restored = gson.fromJson(gson.toJson(persisted), AuthData::class.java)
            .withDeviceInfoProvider(properties)
            .withGermanPlayStoreLocale()
        val client = CapturingPlayHttpClient(
            PlayResponse(isSuccessful = true, code = 200, responseBytes = byteArrayOf())
        )

        val details = AppDetailsHelper(restored)
            .using(client)
            .getAppByPackageName(listOf("example.app"))

        assertTrue(restored.hasRequiredPlaySession())
        assertTrue(details.isEmpty())
        assertEquals(1, client.requestCount)
        assertEquals(200, client.responseCode.value)
    }

    @Test
    fun invalidAuthJsonIsNotAcceptedAsAnEmptySuccessfulSession() {
        assertThrows(JsonParseException::class.java) {
            parseAuthData("{not-json", Gson())
        }
    }

    @Test
    fun missingDeviceProfileFailsBeforeHttpAndLocalProfileAllowsTheDetailsRequest() {
        val client = CapturingPlayHttpClient(
            PlayResponse(isSuccessful = true, code = 200, responseBytes = byteArrayOf())
        )
        val authWithoutProfile = testAuthData().copy(deviceInfoProvider = null)

        assertThrows(NullPointerException::class.java) {
            AppDetailsHelper(authWithoutProfile).using(client).getAppByPackageName(listOf("example.app"))
        }
        assertEquals(0, client.requestCount)

        val normalizedAuth = authWithoutProfile
            .withDeviceInfoProvider(testAuthData().deviceInfoProvider!!.properties)
        val details = AppDetailsHelper(normalizedAuth).using(client).getAppByPackageName(listOf("example.app"))

        assertTrue(details.isEmpty())
        assertEquals(1, client.requestCount)
    }

    private fun testAuthData(): AuthData {
        val properties = Properties().apply {
            listOf(
                "TouchScreen",
                "Keyboard",
                "Navigation",
                "ScreenLayout",
                "Screen.Density",
                "Screen.Width",
                "Screen.Height",
                "GL.Version"
            ).forEach { setProperty(it, "0") }
            setProperty("Build.VERSION.SDK_INT", "35")
            setProperty("GSF.version", "1")
            setProperty("SimOperator", "")
            setProperty("Vending.version", "70000000")
            setProperty("Vending.versionString", "7.0.0")
            setProperty("Build.DEVICE", "test")
            setProperty("Build.ID", "test")
            setProperty("Build.VERSION.RELEASE", "15")
        }
        val deviceInfo = DeviceInfoProvider(properties, "en_US")
        return AuthData(
            email = "test@example.invalid",
            authToken = "test-token",
            gsfId = "test-device",
            locale = Locale.US,
            deviceInfoProvider = deviceInfo
        )
    }

    private class CapturingPlayHttpClient(private val response: PlayResponse) : IHttpClient {
        override val responseCode = MutableStateFlow(response.code)
        var headers: Map<String, String> = emptyMap()
        var requestCount = 0

        private fun capture(headers: Map<String, String>): PlayResponse {
            requestCount++
            this.headers = headers
            return response
        }

        override fun get(url: String, headers: Map<String, String>): PlayResponse {
            return capture(headers)
        }

        override fun get(
            url: String,
            headers: Map<String, String>,
            params: Map<String, String>
        ): PlayResponse {
            return capture(headers)
        }

        override fun get(
            url: String,
            headers: Map<String, String>,
            paramString: String
        ): PlayResponse {
            return capture(headers)
        }

        override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
            return capture(headers)
        }

        override fun post(url: String, headers: Map<String, String>, params: Map<String, String>): PlayResponse {
            return capture(headers)
        }

        override fun getAuth(url: String) =
            response

        override fun postAuth(url: String, body: ByteArray) =
            response
    }
}
