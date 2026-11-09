package com.apkup

import android.content.SharedPreferences
import androidx.work.NetworkType
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CachedAppUpdateDto
import com.apkup.data.ui.CachedUpdateSnapshot
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolver
import com.apkup.util.AutoUpdateNotifier
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.ShizukuBridge
import com.apkup.util.formatAutoUpdateSummary
import com.apkup.worker.ShizukuAutoUpdateRunner
import com.apkup.worker.ShizukuAutoUpdateWorker
import com.kryptoprefs.preferences.KryptoPrefs
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ShizukuAutoUpdateTest {

    @Test
    fun `formatAutoUpdateSummary handles full success, partial success, and zero silent updates`() {
        assertNull(
            formatAutoUpdateSummary(
                updatedAppNames = emptyList(),
                manualRequiredAppNames = listOf("Signal"),
                completeTitle = "Complete",
                partialTitle = "Partial",
                completeDescProvider = { "$it updated" },
                partialDescProvider = { u, m -> "$u updated, $m manual" },
                updatedListProvider = { "Updated: $it" },
                manualReadyListProvider = { "Ready: $it" }
            )
        )

        val full = formatAutoUpdateSummary(
            updatedAppNames = listOf("NewPipe", "Bitwarden"),
            manualRequiredAppNames = emptyList(),
            completeTitle = "Updates installed automatically",
            partialTitle = "Background updates completed",
            completeDescProvider = { "$it apps updated silently" },
            partialDescProvider = { u, m -> "$u updated • $m ready" },
            updatedListProvider = { "Updated: $it" },
            manualReadyListProvider = { "Ready to install: $it" }
        )
        assertNotNull(full)
        assertEquals("Updates installed automatically", full!!.title)
        assertEquals("2 apps updated silently", full.contentText)
        assertTrue(full.bigText.contains("Updated: NewPipe, Bitwarden"))

        val partial = formatAutoUpdateSummary(
            updatedAppNames = listOf("NewPipe"),
            manualRequiredAppNames = listOf("Signal"),
            completeTitle = "Updates installed automatically",
            partialTitle = "Background updates completed",
            completeDescProvider = { "$it apps updated silently" },
            partialDescProvider = { u, m -> "$u updated • $m ready" },
            updatedListProvider = { "Updated: $it" },
            manualReadyListProvider = { "Ready to install: $it" }
        )
        assertNotNull(partial)
        assertEquals("Background updates completed", partial!!.title)
        assertEquals("1 updated • 1 ready", partial.contentText)
        assertTrue(partial.bigText.contains("Updated: NewPipe"))
        assertTrue(partial.bigText.contains("Ready to install: Signal"))
    }

    @Test
    fun `buildWorkConstraints maps Wi-Fi and charging guards accurately`() {
        val guarded = ShizukuAutoUpdateWorker.buildWorkConstraints(wifiOnly = true, chargingOnly = true)
        assertEquals(NetworkType.UNMETERED, guarded.requiredNetworkType)
        assertTrue(guarded.requiresCharging())
        assertFalse(guarded.requiresBatteryNotLow())

        val unguarded = ShizukuAutoUpdateWorker.buildWorkConstraints(wifiOnly = false, chargingOnly = false)
        assertEquals(NetworkType.CONNECTED, unguarded.requiredNetworkType)
        assertFalse(unguarded.requiresCharging())
        assertTrue(unguarded.requiresBatteryNotLow())
    }

    @Test
    fun `runner deletes staging on silent success, preserves 24h ready cache on failure, and prunes snapshot`() = runBlocking {
        val tempDir = Files.createTempDirectory("apkup_auto_update_test").toFile()
        try {
            val prefs = Prefs(LocalTestPrefs()).apply {
                useShizukuInstaller.put(true)
                autoInstallUpdates.put(true)
                cachedUpdateSnapshot.put(
                    CachedUpdateSnapshot(
                        timestampMillis = 123_456L,
                        updates = listOf(
                            CachedAppUpdateDto(name = "App One", packageName = "org.one", versionCode = 20L),
                            CachedAppUpdateDto(name = "App Two", packageName = "org.two", versionCode = 30L)
                        )
                    )
                )
            }

            val okHttp = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val payload = "fake-apk-binary-${chain.request().url.encodedPath}"
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                        .build()
                }
                .build()
            val downloader = Downloader(okHttp, okHttp)
            val apkMirrorResolver = ApkMirrorResolver(client = okHttp, minDelayMs = 0L)
            val bridge = TestShizukuBridge(
                ready = true,
                installHandler = { pkg -> pkg == "org.one" } // org.one succeeds, org.two fails silently
            )
            val installer = SessionInstaller(
                context = null,
                installLog = InstallLog(),
                prefs = prefs,
                shizukuBridgeOverride = bridge
            )
            val notification = RecordingAutoUpdateNotifier()

            val runner = ShizukuAutoUpdateRunner(
                storageBaseDir = tempDir,
                prefs = prefs,
                downloader = downloader,
                apkMirrorResolver = apkMirrorResolver,
                installer = installer,
                notification = notification
            )

            val updates = listOf(
                AppUpdate(
                    name = "App One",
                    packageName = "org.one",
                    version = "2.0.0",
                    oldVersion = "1.0.0",
                    versionCode = 20L,
                    oldVersionCode = 10L,
                    source = GitHubSource,
                    link = Link.Url("https://releases.example.org/app-one.apk"),
                    id = 101
                ),
                AppUpdate(
                    name = "App Two",
                    packageName = "org.two",
                    version = "3.0.0",
                    oldVersion = "2.0.0",
                    versionCode = 30L,
                    oldVersionCode = 20L,
                    source = GitHubSource,
                    link = Link.Url("https://releases.example.org/app-two.apk"),
                    id = 102
                )
            )

            val summary = runner.execute(updates)
            assertTrue(summary.shizukuWasReady)
            assertEquals(listOf("org.one"), summary.updatedPackages)
            assertEquals(listOf("org.two"), summary.manualRequiredPackages)

            // App One staging directory was deleted after silent install
            assertFalse(downloader.hasReadyCachedApk(tempDir, 101, 20L))
            // App Two staging directory is preserved with .ready marker for 24h instant manual retry
            assertTrue(downloader.hasReadyCachedApk(tempDir, 102, 30L))

            // Snapshot pruned org.one while keeping org.two
            val remainingPackages = prefs.readCachedSnapshotSafe().updates.map { it.packageName }
            assertEquals(listOf("org.two"), remainingPackages)

            // Summary notification recorded partial outcome
            assertEquals(listOf("App One"), notification.lastSummaryUpdated)
            assertEquals(listOf("App Two"), notification.lastSummaryManual)
            assertNull(notification.lastStandardUpdateCount)

            // Update history recorded only the silently installed app (App One) with AUTO_SHIZUKU mode
            val recordedHistory = prefs.readUpdateHistorySafe()
            assertEquals(1, recordedHistory.size)
            assertEquals("org.one", recordedHistory.first().packageName)
            assertEquals(UpdateInstallMode.AUTO_SHIZUKU, recordedHistory.first().installMode)
            assertTrue(recordedHistory.first().previousVersionUrl.isNotBlank())
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    @Test
    fun `runner falls back to standard notification without downloading when Shizuku is unavailable`() = runBlocking {
        val tempDir = Files.createTempDirectory("apkup_auto_skip_test").toFile()
        try {
            val prefs = Prefs(LocalTestPrefs()).apply {
                useShizukuInstaller.put(true)
                autoInstallUpdates.put(true)
            }
            val okHttp = OkHttpClient.Builder().build()
            val downloader = Downloader(okHttp, okHttp)
            val apkMirrorResolver = ApkMirrorResolver(client = okHttp, minDelayMs = 0L)
            val bridge = TestShizukuBridge(ready = false)
            val installer = SessionInstaller(
                context = null,
                installLog = InstallLog(),
                prefs = prefs,
                shizukuBridgeOverride = bridge
            )
            val notification = RecordingAutoUpdateNotifier()

            val runner = ShizukuAutoUpdateRunner(
                storageBaseDir = tempDir,
                prefs = prefs,
                downloader = downloader,
                apkMirrorResolver = apkMirrorResolver,
                installer = installer,
                notification = notification
            )

            val updates = listOf(
                AppUpdate(
                    name = "Signal",
                    packageName = "org.thoughtcrime.securesms",
                    version = "7.0",
                    oldVersion = "6.9",
                    versionCode = 700L,
                    oldVersionCode = 690L,
                    source = GitHubSource,
                    link = Link.Url("https://invalid.local/signal.apk"),
                    id = 301
                )
            )

            val summary = runner.execute(updates)
            assertFalse(summary.shizukuWasReady)
            assertTrue(summary.updatedPackages.isEmpty())
            assertEquals(1, notification.lastStandardUpdateCount)
            assertNull(notification.lastSummaryUpdated)
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private class RecordingAutoUpdateNotifier : AutoUpdateNotifier {
        var lastStandardUpdateCount: Int? = null
        var lastSummaryUpdated: List<String>? = null
        var lastSummaryManual: List<String>? = null

        override fun showUpdateNotification(num: Int) {
            lastStandardUpdateCount = num
        }

        override fun showAutoUpdateSummaryNotification(
            updatedAppNames: List<String>,
            manualRequiredAppNames: List<String>
        ) {
            lastSummaryUpdated = updatedAppNames
            lastSummaryManual = manualRequiredAppNames
        }
    }

    private class TestShizukuBridge(
        private val ready: Boolean,
        private val installHandler: (String) -> Boolean = { true }
    ) : ShizukuBridge {
        override fun isPackageInstalled(): Boolean = ready
        override fun pingBinder(): Boolean = ready
        override fun isPermissionGranted(): Boolean = ready
        override fun requestPermission(requestCode: Int) = Unit
        override fun observeEvents(onPermissionResult: (Boolean) -> Unit, onBinderChanged: () -> Unit): () -> Unit = {}
        override suspend fun awaitPermissionResult(timeoutMs: Long): Boolean = ready
        override fun executeShizukuInstall(id: Int, packageName: String, parts: List<InstallPart>): Boolean {
            parts.forEach { part -> part.openStream()?.use { it.readBytes() } }
            return installHandler(packageName)
        }
    }

    private class LocalTestPrefs : KryptoPrefs {
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
        override fun name(): String = "auto-update-test"
        override fun sharedPreferences(): SharedPreferences = error("Unused")
        private fun put(key: String, value: Any?): KryptoPrefs = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
