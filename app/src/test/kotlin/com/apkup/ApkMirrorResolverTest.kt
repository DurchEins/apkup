package com.apkup

import android.content.SharedPreferences
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolutionResult
import com.apkup.repository.ApkMirrorResolver
import com.apkup.util.ApkmExtractor
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.partitionInstallQueueCandidates
import com.kryptoprefs.preferences.KryptoPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkMirrorResolverTest {

    // =========================================================================
    // 1. SUCCESSFUL 2-STEP RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testTwoStepVariantResolutionSuccessWithStandardApk() = runBlocking {
        val variantUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-android-apk-download/"
        val expectedIntermediateUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-android-apk-download/download/?key=abc123xyz987"
        val expectedDownloadPhpUrl = "https://www.apkmirror.com/wp-content/themes/APKMirror/download.php?id=987654&key=fedcba0987654321&forcebaseapk=true"
        val expectedCdnUrl = "https://download.apkmirror.com/secure/2026/spotify-8.9.12.apk?token=valid123"

        val recordedRequests = mutableListOf<String>()
        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                val url = req.url.toString()
                recordedRequests.add(url)

                return when {
                    url == variantUrl -> {
                        createHtmlResponse(req, 200, ApkMirrorTestFixtures.VARIANT_PAGE_HTML)
                    }
                    url == expectedIntermediateUrl -> {
                        assertEquals("Referer must be set to variantUrl", variantUrl, req.header("Referer"))
                        createHtmlResponse(req, 200, ApkMirrorTestFixtures.INTERMEDIATE_PAGE_HTML)
                    }
                    url.contains("download.php") -> {
                        assertEquals("Referer must be set to intermediateUrl", expectedIntermediateUrl, req.header("Referer"))
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(302)
                            .message("Found")
                            .header("Location", expectedCdnUrl)
                            .header("Content-Disposition", "attachment; filename=\"spotify-8.9.12.apk\"")
                            .body("".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    else -> error("Unexpected request to $url")
                }
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val delays = mutableListOf<Long>()
        val resolver = ApkMirrorResolver(
            client = testClient,
            minDelayMs = 1500L,
            clock = { 10_000L },
            delayFn = { delays.add(it) }
        )

        val result = resolver.resolveDirectDownload(variantUrl)

        assertTrue("Expected Success result but got $result", result is ApkMirrorResolutionResult.Success)
        val success = result as ApkMirrorResolutionResult.Success
        assertEquals(expectedCdnUrl, success.directUrl)
        assertFalse("Should be recognized as standalone APK", success.isApkm)
        assertEquals("spotify-8.9.12.apk", success.filename)
        assertEquals(3, recordedRequests.size)
    }

    @Test
    fun testTwoStepVariantResolutionSuccessWithApkmBundle() = runBlocking {
        val variantUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-bundle-download/"
        val expectedIntermediateUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-bundle-download/download/?key=bundle123456789"
        val expectedCdnUrl = "https://download.apkmirror.com/secure/2026/spotify-8.9.12.apkm?token=valid456"

        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                val url = req.url.toString()
                return when {
                    url == variantUrl -> createHtmlResponse(req, 200, ApkMirrorTestFixtures.VARIANT_PAGE_APKM_HTML)
                    url == expectedIntermediateUrl -> createHtmlResponse(req, 200, ApkMirrorTestFixtures.INTERMEDIATE_PAGE_APKM_HTML)
                    url.contains("download.php") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(302)
                            .message("Found")
                            .header("Location", expectedCdnUrl)
                            .header("Content-Disposition", "attachment; filename=\"spotify-8.9.12.apkm\"")
                            .body("".toResponseBody("text/plain".toMediaType()))
                            .build()
                    }
                    else -> error("Unexpected request to $url")
                }
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val resolver = ApkMirrorResolver(
            client = testClient,
            minDelayMs = 1500L,
            delayFn = { /* no-op in test */ }
        )

        val result = resolver.resolveDirectDownload(variantUrl)

        assertTrue(result is ApkMirrorResolutionResult.Success)
        val success = result as ApkMirrorResolutionResult.Success
        assertEquals(expectedCdnUrl, success.directUrl)
        assertTrue("Bundle download must be flagged as isApkm = true", success.isApkm)
        assertEquals("spotify-8.9.12.apkm", success.filename)
    }

    // =========================================================================
    // 2. APKM VS STANDALONE APK IDENTIFICATION TESTS
    // =========================================================================

    @Test
    fun testIdentifyApkmByExtensionAndContentDisposition() {
        // Filename ending in .apkm
        val apkmDisposition = "attachment; filename=\"com.spotify.music_8.9.12.apkm\""
        val apkDisposition = "attachment; filename=\"com.spotify.music_8.9.12.apk\""

        val apkmFilename = ApkMirrorResolver.extractFilenameFromHeader(apkmDisposition, "https://cdn.example.com/file?k=1")
        val apkFilename = ApkMirrorResolver.extractFilenameFromHeader(apkDisposition, "https://cdn.example.com/file?k=2")

        assertEquals("com.spotify.music_8.9.12.apkm", apkmFilename)
        assertEquals("com.spotify.music_8.9.12.apk", apkFilename)

        assertTrue(ApkMirrorResolver.isApkmFile(apkmFilename, "https://cdn.example.com/file?k=1"))
        assertFalse(ApkMirrorResolver.isApkmFile(apkFilename, "https://cdn.example.com/file?k=2"))

        // Magic byte verification
        val rawApkmBytes = createMockApkmBytes()
        val zipMagicHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        assertTrue(ApkmExtractor.isZipHeader(zipMagicHeader))
        assertTrue(ApkmExtractor.isZipHeader(rawApkmBytes.copyOfRange(0, 4)))

        val nonZipHeader = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00) // GZIP header
        assertFalse(ApkmExtractor.isZipHeader(nonZipHeader))
    }

    // =========================================================================
    // 3. CLOUDFLARE CHALLENGE DETECTION & FALLBACK TESTS
    // =========================================================================

    @Test
    fun testCloudflare403ChallengeEmitsCloudflareBlocked() = runBlocking {
        val variantUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-android-apk-download/"

        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                return Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(403)
                    .message("Forbidden")
                    .header("Server", "cloudflare")
                    .header("cf-mitigated", "challenge")
                    .header("cf-ray", "8df123456789abcd-FRA")
                    .body(ApkMirrorTestFixtures.CLOUDFLARE_403_HTML.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val resolver = ApkMirrorResolver(client = testClient)

        val result = resolver.resolveDirectDownload(variantUrl)

        assertTrue("Expected CloudflareBlocked but got $result", result is ApkMirrorResolutionResult.CloudflareBlocked)
        val blocked = result as ApkMirrorResolutionResult.CloudflareBlocked
        assertEquals(variantUrl, blocked.fallbackUrl)
    }

    @Test
    fun testCloudflare503JustAMomentChallengeEmitsCloudflareBlocked() = runBlocking {
        val variantUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-android-apk-download/"

        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                return Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .header("Server", "cloudflare")
                    .body(ApkMirrorTestFixtures.CLOUDFLARE_JUST_A_MOMENT_HTML.toResponseBody("text/html; charset=utf-8".toMediaType()))
                    .build()
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val resolver = ApkMirrorResolver(client = testClient)

        val result = resolver.resolveDirectDownload(variantUrl)

        assertTrue(result is ApkMirrorResolutionResult.CloudflareBlocked)
        val blocked = result as ApkMirrorResolutionResult.CloudflareBlocked
        assertEquals(variantUrl, blocked.fallbackUrl)
    }

    @Test
    fun testNonCloudflareHttpErrorEmitsFailedResult() = runBlocking {
        val variantUrl = "https://www.apkmirror.com/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-android-apk-download/"

        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                return Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("Internal server crashed".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val resolver = ApkMirrorResolver(client = testClient)

        val result = resolver.resolveDirectDownload(variantUrl)

        assertTrue("Server 500 error must emit Failed, not CloudflareBlocked", result is ApkMirrorResolutionResult.Failed)
        val failed = result as ApkMirrorResolutionResult.Failed
        assertEquals(variantUrl, failed.fallbackUrl)
        assertTrue(failed.error.message!!.contains("500"))
    }

    // =========================================================================
    // 4. POLITE RATE-LIMITING TESTS
    // =========================================================================

    @Test
    fun testPoliteRateLimitingEnforcesDelayBetweenStep1AndStep2() = runBlocking {
        val variantUrl = "https://www.apkmirror.com/apk/test/app/download/"
        val recordedDelays = mutableListOf<Long>()
        var simulatedClock = 10_000L

        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                return when {
                    req.url.toString() == variantUrl -> createHtmlResponse(req, 200, ApkMirrorTestFixtures.VARIANT_PAGE_HTML)
                    req.url.toString().contains("/download/?key=") -> createHtmlResponse(req, 200, ApkMirrorTestFixtures.INTERMEDIATE_PAGE_HTML)
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://download.apkmirror.com/app.apk")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val resolver = ApkMirrorResolver(
            client = testClient,
            minDelayMs = 1500L,
            clock = { simulatedClock },
            delayFn = { delayMs ->
                recordedDelays.add(delayMs)
                simulatedClock += delayMs
            }
        )

        val result = resolver.resolveDirectDownload(variantUrl)

        assertTrue(result is ApkMirrorResolutionResult.Success)
        assertEquals("Must enforce exactly 1500ms delay between Step 1 and Step 2", listOf(1500L), recordedDelays)
    }

    @Test
    fun testConsecutiveResolutionsAreSerializedUnderMutexWithDelay() = runBlocking(Dispatchers.Default) {
        val variantUrl1 = "https://www.apkmirror.com/apk/test/app1/download/"
        val variantUrl2 = "https://www.apkmirror.com/apk/test/app2/download/"
        val recordedDelays = mutableListOf<Long>()
        val simulatedClock = AtomicLong(50_000L)

        val fakeInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val req = chain.request()
                val url = req.url.toString()
                return when {
                    url.endsWith("/download/") -> createHtmlResponse(req, 200, ApkMirrorTestFixtures.VARIANT_PAGE_HTML)
                    url.contains("/download/?key=") -> createHtmlResponse(req, 200, ApkMirrorTestFixtures.INTERMEDIATE_PAGE_HTML)
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://download.apkmirror.com/app.apk")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }
        }

        val testClient = OkHttpClient.Builder().addInterceptor(fakeInterceptor).build()
        val resolver = ApkMirrorResolver(
            client = testClient,
            minDelayMs = 1500L,
            clock = { simulatedClock.get() },
            delayFn = { delayMs ->
                synchronized(recordedDelays) {
                    recordedDelays.add(delayMs)
                }
                simulatedClock.addAndGet(delayMs)
            }
        )

        // Launch two concurrent resolutions
        val job1 = async { resolver.resolveDirectDownload(variantUrl1) }
        val job2 = async { resolver.resolveDirectDownload(variantUrl2) }

        val results = listOf(job1, job2).awaitAll()

        assertEquals(2, results.size)
        assertTrue(results.all { it is ApkMirrorResolutionResult.Success })
        // Delays must include inter-step delays (1500ms each) plus inter-call rate-limiting
        assertTrue("Delays must be recorded for rate-limiting", recordedDelays.isNotEmpty())
        assertTrue("All enforced delays must be at least 1500ms", recordedDelays.all { it >= 1500L })
    }

    // =========================================================================
    // 5. SPLIT-APK (.APKM) UNPACKING TESTS
    // =========================================================================

    @Test
    fun testUnpackApkmZipExtractsBaseAndSplitApksIgnoringMetadata() {
        val baseApkContent = "DUMMY_BASE_APK_DEX_AND_MANIFEST".toByteArray()
        val splitArm64Content = "DUMMY_SPLIT_ARM64_SO_LIBS".toByteArray()
        val splitXxhdpiContent = "DUMMY_SPLIT_XXHDPI_DRAWABLES".toByteArray()

        val rawApkmBytes = createMockApkmBytes(
            baseApkBytes = baseApkContent,
            splits = mapOf(
                "split_config.arm64_v8a.apk" to splitArm64Content,
                "split_config.xxhdpi.apk" to splitXxhdpiContent
            ),
            includeMetadata = true // includes info.json and icon.png
        )

        val tempDir = File.createTempFile("apk_staging_test", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extractedFiles = ApkmExtractor.unpackApkmStream(
                inputStream = ByteArrayInputStream(rawApkmBytes),
                targetDir = tempDir
            )

            // Should extract base.apk, split_config.arm64_v8a.apk, split_config.xxhdpi.apk (3 files)
            // info.json and icon.png must NOT be in the returned list
            assertEquals(3, extractedFiles.size)

            // Base APK must be sorted first for SessionInstaller
            assertEquals("base.apk", extractedFiles[0].name)
            assertEquals("split_config.arm64_v8a.apk", extractedFiles[1].name)
            assertEquals("split_config.xxhdpi.apk", extractedFiles[2].name)

            // Validate byte integrity of extracted files
            assertEquals(baseApkContent.size.toLong(), extractedFiles[0].length())
            assertEquals(splitArm64Content.size.toLong(), extractedFiles[1].length())
            assertEquals(splitXxhdpiContent.size.toLong(), extractedFiles[2].length())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // 6. QUEUE PARTITIONING TESTS
    // =========================================================================

    @Test
    fun testQueuePartitioningWithDirectDownloadsEnabledIncludesApkMirrorInDirectQueue() {
        val playUpdate = AppUpdate(
            name = "PlayApp",
            packageName = "com.example.play",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = PlaySource,
            link = Link.Play { emptyList() }
        )
        val gitHubUpdate = AppUpdate(
            name = "GitHubApp",
            packageName = "com.example.git",
            version = "3.0",
            oldVersion = "2.0",
            versionCode = 30L,
            oldVersionCode = 20L,
            source = GitHubSource,
            link = Link.Url("https://github.com/example/release.apk")
        )
        val apkMirrorUpdate = AppUpdate(
            name = "MirrorApp",
            packageName = "com.example.mirror",
            version = "4.0",
            oldVersion = "3.0",
            versionCode = 40L,
            oldVersionCode = 30L,
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/example/download/")
        )

        val updates = listOf(playUpdate, gitHubUpdate, apkMirrorUpdate)

        // When toggle is ENABLED:
        val (directQueue, browserSkipped) = partitionInstallQueueCandidates(
            updates = updates,
            directApkMirrorEnabled = true
        )

        assertEquals("All 3 updates must be in the direct queue when toggle is enabled", 3, directQueue.size)
        assertTrue(directQueue.contains(playUpdate))
        assertTrue(directQueue.contains(gitHubUpdate))
        assertTrue("APKMirror update must be included in direct queue", directQueue.contains(apkMirrorUpdate))
        assertTrue("Browser-skipped list must be empty", browserSkipped.isEmpty())
    }

    @Test
    fun testQueuePartitioningWithDirectDownloadsDisabledSortsApkMirrorToBrowserOnly() {
        val playUpdate = AppUpdate(
            name = "PlayApp",
            packageName = "com.example.play",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = PlaySource,
            link = Link.Play { emptyList() }
        )
        val apkMirrorUpdate = AppUpdate(
            name = "MirrorApp",
            packageName = "com.example.mirror",
            version = "4.0",
            oldVersion = "3.0",
            versionCode = 40L,
            oldVersionCode = 30L,
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/example/download/")
        )

        val updates = listOf(playUpdate, apkMirrorUpdate)

        // When toggle is DISABLED:
        val (directQueue, browserSkipped) = partitionInstallQueueCandidates(
            updates = updates,
            directApkMirrorEnabled = false
        )

        assertEquals(listOf(playUpdate), directQueue)
        assertEquals("APKMirror update must be routed to browserSkipped when toggle is disabled", listOf(apkMirrorUpdate), browserSkipped)
    }

    @Test
    fun testAppUpdateDirectInstallablePropertyRespondsToToggle() {
        val apkMirrorUpdate = AppUpdate(
            name = "MirrorApp",
            packageName = "com.example.mirror",
            version = "4.0",
            oldVersion = "3.0",
            versionCode = 40L,
            oldVersionCode = 30L,
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/example/download/")
        )

        // Default legacy properties
        assertFalse(apkMirrorUpdate.isDirectInstallable)
        assertTrue(apkMirrorUpdate.isBrowserOnlyUpdate)

        // Parameterized dynamic checks
        assertTrue(apkMirrorUpdate.isDirectInstallable(directApkMirrorEnabled = true))
        assertFalse(apkMirrorUpdate.isBrowserOnlyUpdate(directApkMirrorEnabled = true))

        assertFalse(apkMirrorUpdate.isDirectInstallable(directApkMirrorEnabled = false))
        assertTrue(apkMirrorUpdate.isBrowserOnlyUpdate(directApkMirrorEnabled = false))
    }

    // =========================================================================
    // 7. SETTINGS & PREFERENCES INTEGRATION TESTS
    // =========================================================================

    @Test
    fun testDirectApkMirrorDownloadsPreferenceDefaultsToTrueAndPersists() {
        val storage = TestKryptoPrefs()
        val firstRunPrefs = Prefs(storage)

        // Must default to true (R2)
        assertTrue("Direct APKMirror downloads must default to true", firstRunPrefs.directApkMirrorDownloads.get())

        // Toggle to false
        firstRunPrefs.directApkMirrorDownloads.put(false)
        assertFalse(firstRunPrefs.directApkMirrorDownloads.get())

        // Reopen prefs
        val reopenedPrefs = Prefs(storage)
        assertFalse("Toggled setting must persist across sessions", reopenedPrefs.directApkMirrorDownloads.get())
    }

    @Test
    fun testSettingsViewModelExposesPreferenceGetterAndSetter() {
        val storage = TestKryptoPrefs()
        val prefs = Prefs(storage)
        val viewModel = SettingsViewModel(prefs = prefs)

        assertTrue(viewModel.getDirectApkMirrorDownloads())

        viewModel.setDirectApkMirrorDownloads(false)
        assertFalse(viewModel.getDirectApkMirrorDownloads())
        assertFalse(prefs.directApkMirrorDownloads.get())

        viewModel.setDirectApkMirrorDownloads(true)
        assertTrue(viewModel.getDirectApkMirrorDownloads())
        assertTrue(prefs.directApkMirrorDownloads.get())
    }

    // =========================================================================
    // TEST HELPERS & IN-MEMORY FIXTURES
    // =========================================================================

    private fun createHtmlResponse(request: Request, code: Int, html: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .header("Content-Type", "text/html; charset=utf-8")
            .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()
    }

    private fun createMockApkmBytes(
        baseApkBytes: ByteArray = "MOCK_BASE_APK_BYTES".toByteArray(),
        splits: Map<String, ByteArray> = mapOf(
            "split_config.arm64_v8a.apk" to "MOCK_SPLIT_ARM64".toByteArray(),
            "split_config.xxhdpi.apk" to "MOCK_SPLIT_XXHDPI".toByteArray(),
            "split_config.en.apk" to "MOCK_SPLIT_LANG_EN".toByteArray()
        ),
        includeMetadata: Boolean = true
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            if (includeMetadata) {
                zos.putNextEntry(ZipEntry("info.json"))
                zos.write("""{"pname":"com.spotify.music","version":80912443,"minapi":23}""".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("icon.png"))
                zos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) // PNG magic bytes
                zos.closeEntry()
            }

            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write(baseApkBytes)
            zos.closeEntry()

            splits.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private class TestKryptoPrefs : KryptoPrefs {
        private val values = mutableMapOf<String, Any>()

        override fun putString(key: String, value: String?): KryptoPrefs = put(key, value)
        override fun putBoolean(key: String, value: Boolean?): KryptoPrefs = put(key, value)
        override fun putInt(key: String, value: Int?): KryptoPrefs = put(key, value)
        override fun putLong(key: String, value: Long?): KryptoPrefs = put(key, value)

        override fun getString(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun remove(key: String): KryptoPrefs = apply { values.remove(key) }
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun clear() = values.clear()
        override fun name(): String = "unit-test"
        override fun sharedPreferences(): SharedPreferences = error("Not used by these preference tests")

        private fun put(key: String, value: Any?): KryptoPrefs = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    private object ApkMirrorTestFixtures {

        const val VARIANT_PAGE_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Spotify: Music and Podcasts 8.9.12.443 APK Download by Spotify AB - APKMirror</title>
            </head>
            <body>
                <div id="primary" class="site-main">
                    <div class="row">
                        <div class="header">
                            <h1>Download Spotify 8.9.12.443</h1>
                        </div>
                        <div class="table-row">
                            <span class="infoSlide-name">File size: 75.4 MB</span>
                        </div>
                        <div class="downloadCard">
                            <a class="accent_bg btn btn-flat downloadButton" 
                               href="/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-android-apk-download/download/?key=abc123xyz987">
                                Download APK
                            </a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """

        const val VARIANT_PAGE_APKM_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Spotify: Music and Podcasts 8.9.12.443 (Bundle) APK Download by Spotify AB - APKMirror</title>
            </head>
            <body>
                <div id="primary" class="site-main">
                    <div class="row">
                        <div class="badges">
                            <span class="badge badge-bundle">BUNDLE</span>
                        </div>
                        <div class="downloadCard">
                            <a class="accent_bg btn btn-flat downloadButton" 
                               href="/apk/spotify-ltd/spotify-music/spotify-music-8-9-12-apk/spotify-music-8-9-12-bundle-download/download/?key=bundle123456789">
                                Download APK Bundle
                            </a>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """

        const val INTERMEDIATE_PAGE_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Downloading Spotify APK - APKMirror</title>
            </head>
            <body>
                <div class="wrap">
                    <div class="inner-wrap">
                        <p class="notes">Your download will start immediately. If not, please 
                            <a rel="nofollow" data-google-vignette="false" 
                               href="/wp-content/themes/APKMirror/download.php?id=987654&key=fedcba0987654321&forcebaseapk=true">click here</a>.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """

        const val INTERMEDIATE_PAGE_APKM_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>Downloading Spotify APKM - APKMirror</title>
            </head>
            <body>
                <div class="wrap">
                    <div class="inner-wrap">
                        <p class="notes">Your download will start immediately. If not, please 
                            <a rel="nofollow" 
                               href="/wp-content/themes/APKMirror/download.php?id=987655&key=bundleToken987">click here</a>.
                        </p>
                    </div>
                </div>
            </body>
            </html>
        """

        const val CLOUDFLARE_403_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Access denied | www.apkmirror.com used Cloudflare to restrict access</title>
            </head>
            <body>
                <div id="cf-wrapper">
                    <div id="cf-error-details" class="cf-error-details-wrapper">
                        <h1>Error 1020: Access Denied</h1>
                        <p>This website is using a security service to protect itself from online attacks.</p>
                        <div class="cf-browser-verification">Please complete the security check to proceed.</div>
                    </div>
                </div>
            </body>
            </html>
        """

        const val CLOUDFLARE_JUST_A_MOMENT_HTML = """
            <!DOCTYPE html>
            <html lang="en-US">
            <head>
                <title>Just a moment...</title>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            </head>
            <body>
                <div class="main-wrapper" role="main">
                    <div class="main-content">
                        <h1 class="zone-name-title h1">www.apkmirror.com</h1>
                        <h2 class="h2" id="challenge-running">Verifying that you are not a robot...</h2>
                        <div id="challenge-platform">
                            <div id="turnstile-wrapper"></div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
        """
    }
}
