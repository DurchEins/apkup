package com.apkup

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.RepoMappingManager
import com.apkup.util.AutoUpdateNotifier
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.ShizukuBridge
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.worker.DownloadCoordinator
import com.apkup.worker.ShizukuAutoUpdateRunner
import com.apkup.worker.filterNotifiableUpdates
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreSecurityFixesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `C-01 filterNotifiableUpdates excludes uninstalled watched apps`() {
        val watchedUpdate = AppUpdate(
            name = "Watched App",
            packageName = "com.example.watched",
            version = "2.0.0",
            oldVersion = "",
            versionCode = 200L,
            oldVersionCode = -1L,
            source = GitHubSource,
            link = Link.Url("https://github.com/example/watched/releases/download/v2.0/app.apk")
        )
        val installedUpdate = AppUpdate(
            name = "Installed App",
            packageName = "com.example.installed",
            version = "2.0.0",
            oldVersion = "1.0.0",
            versionCode = 200L,
            oldVersionCode = 100L,
            source = GitHubSource,
            link = Link.Url("https://github.com/example/installed/releases/download/v2.0/app.apk")
        )

        val filtered = filterNotifiableUpdates(
            result = UpdateCheckResult(
                updates = listOf(watchedUpdate, installedUpdate),
                successfulRequests = 1,
                failedRequests = 0,
                isChecking = false
            ),
            ignoredVersionIds = emptyList()
        )

        assertEquals(listOf(installedUpdate), filtered)
    }

    @Test
    fun `C-01 ShizukuAutoUpdateRunner never downloads or installs watched apps`() = runBlocking {
        val storageDir = tempFolder.newFolder("auto_watch_storage")
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            useShizukuInstaller.put(true)
            autoInstallUpdates.put(true)
        }
        val downloadRequests = AtomicInteger(0)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                downloadRequests.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("fake-apk".toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()
        val downloader = Downloader(okHttp, okHttp)
        val installedPackages = mutableListOf<String>()
        val bridge = FakeShizukuBridge(ready = true) { pkg ->
            installedPackages += pkg
            true
        }
        val installer = SessionInstaller(
            context = null,
            installLog = InstallLog(),
            prefs = prefs,
            shizukuBridgeOverride = bridge
        )
        val notifier = RecordingNotifier()
        val runner = ShizukuAutoUpdateRunner(
            storageBaseDir = storageDir,
            prefs = prefs,
            downloader = downloader,
            apkMirrorResolver = ApkMirrorResolver(client = okHttp, minDelayMs = 0L),
            installer = installer,
            notification = notifier
        )

        val watchedOnly = AppUpdate(
            name = "WatchedOnly",
            packageName = "com.example.watched",
            version = "1.0.0",
            oldVersion = "",
            versionCode = 10L,
            oldVersionCode = -1L,
            source = GitHubSource,
            link = Link.Url("https://github.com/example/watched/releases/download/v1.0/app.apk"),
            id = 501
        )
        val summaryWatchedOnly = runner.execute(listOf(watchedOnly))
        assertTrue(summaryWatchedOnly.updatedPackages.isEmpty())
        assertTrue(summaryWatchedOnly.manualRequiredPackages.isEmpty())
        assertEquals(0, downloadRequests.get())
        assertTrue(installedPackages.isEmpty())
        assertNull(notifier.standardCount)
        assertNull(notifier.summaryUpdated)

        val normalInstalled = AppUpdate(
            name = "NormalApp",
            packageName = "com.example.normal",
            version = "2.0.0",
            oldVersion = "1.0.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = GitHubSource,
            link = Link.Url("https://github.com/example/normal/releases/download/v2.0/app.apk"),
            id = 502
        )
        val summaryMixed = runner.execute(listOf(watchedOnly, normalInstalled))
        assertEquals(listOf("com.example.normal"), summaryMixed.updatedPackages)
        assertEquals(listOf("com.example.normal"), installedPackages)
        assertEquals(1, downloadRequests.get())
    }

    @Test
    fun `C-01 ShizukuAutoUpdateRunner verifies integrity before markStagingReady and purges staging on failure`() = runBlocking {
        val storageDir = tempFolder.newFolder("auto_integrity_storage")
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            useShizukuInstaller.put(true)
            autoInstallUpdates.put(true)
        }
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("tampered-bytes".toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()
        val downloader = Downloader(okHttp, okHttp)
        val installedPackages = mutableListOf<String>()
        val bridge = FakeShizukuBridge(ready = true) { pkg ->
            installedPackages += pkg
            true
        }
        val installer = SessionInstaller(
            context = null,
            installLog = InstallLog(),
            prefs = prefs,
            shizukuBridgeOverride = bridge
        )
        val notifier = RecordingNotifier()
        val runner = ShizukuAutoUpdateRunner(
            storageBaseDir = storageDir,
            prefs = prefs,
            downloader = downloader,
            apkMirrorResolver = ApkMirrorResolver(client = okHttp, minDelayMs = 0L),
            installer = installer,
            notification = notifier
        )

        val badShaUpdate = AppUpdate(
            name = "HashTampered",
            packageName = "com.example.tampered",
            version = "2.0.0",
            oldVersion = "1.0.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = GitHubSource,
            link = Link.Url(
                link = "https://github.com/example/tampered/releases/download/v2.0/app.apk",
                expectedSha256 = "f".repeat(64)
            ),
            id = 601
        )
        val summary = runner.execute(listOf(badShaUpdate))
        assertTrue(summary.updatedPackages.isEmpty())
        assertTrue(summary.manualRequiredPackages.isEmpty())
        assertTrue(installedPackages.isEmpty())
        assertFalse(downloader.hasReadyCachedApk(storageDir, 601, 20L))
        assertFalse(File(storageDir, "download_staging_601").exists())

        // Pre-poisoned .ready staging dir with mismatched SHA-256 must also be purged
        val poisonedDir = File(storageDir, "download_staging_602").apply { mkdirs() }
        File(poisonedDir, "base.apk").writeBytes("poisoned".toByteArray())
        downloader.markStagingReady(poisonedDir, 30L)
        val poisonedCachedUpdate = badShaUpdate.copy(
            id = 602,
            packageName = "com.example.poisoned",
            versionCode = 30L
        )
        runner.execute(listOf(poisonedCachedUpdate))
        assertFalse(poisonedDir.exists())
        assertFalse(downloader.hasReadyCachedApk(storageDir, 602, 30L))
    }

    @Test
    fun `Q-07 DownloadCoordinator clears saveOnlyTaskIds on integrity failure and emits safFallbackEvents`() = runBlocking {
        val baseDir = tempFolder.newFolder("coord_sec_base")
        val cacheDir = tempFolder.newFolder("coord_sec_cache")
        val downloadsFallbackRoot = tempFolder.newFolder("coord_sec_downloads")
        @Suppress("DEPRECATION")
        val stubResources = object : Resources(null, null, null) {
            override fun getString(id: Int): String = "stub"
            override fun getString(id: Int, vararg formatArgs: Any?): String = "stub"
        }
        val fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = baseDir
            override fun getNoBackupFilesDir(): File = baseDir
            override fun getCacheDir(): File = cacheDir
            override fun getSystemService(name: String): Any? = null
            override fun getResources(): Resources = stubResources
        }

        val validApkBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write("manifest".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validApkBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()

        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            customDownloadFolderUri.put("content://com.android.externalstorage.documents/tree/primary%3ABadFolder")
        }
        val downloader = Downloader(httpClient, httpClient)
        val installLog = InstallLog()
        val coordinator = DownloadCoordinator(
            context = fakeContext,
            downloader = downloader,
            installer = SessionInstaller(context = null, installLog = installLog, prefs = prefs),
            installLog = installLog,
            snackBar = SnackBar(),
            stringer = Stringer(fakeContext),
            prefs = prefs,
            apkMirrorResolver = ApkMirrorResolver(client = httpClient, minDelayMs = 0L),
            repoMappingManager = RepoMappingManager(context = null, prefs = prefs, gson = Gson()),
            notificationManager = DownloadNotificationManager(fakeContext)
        )

        // 1. Failing verifyStagedApkFiles must still clear saveOnlyTaskIds
        val badHashSaveOnly = AppUpdate(
            id = 801,
            name = "BadHash",
            packageName = "com.example.badhash",
            version = "1.0.0",
            oldVersion = "",
            versionCode = 10L,
            oldVersionCode = -1L,
            source = GitHubSource,
            link = Link.Url(
                link = "https://github.com/example/bad/releases/download/v1.0/app.apk",
                expectedSha256 = "0".repeat(64)
            )
        )
        coordinator.enqueue(badHashSaveOnly, saveOnly = true)
        val deadline = System.currentTimeMillis() + 5_000L
        while (coordinator.activeTasksFlow.value.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertFalse(coordinator.hasSaveOnlyTask(801))

        // 2. SAF fallback emits app label on safFallbackEvents
        DownloadStorageManager.legacyDownloadsDirOverrideForTest = downloadsFallbackRoot
        try {
            val eventDeferred = async {
                withTimeout(5_000L) {
                    coordinator.safFallbackEvents.first()
                }
            }
            val validSaveOnly = AppUpdate(
                id = 802,
                name = "Fallback App",
                packageName = "com.example.fallback",
                version = "1.0.0",
                oldVersion = "",
                versionCode = 10L,
                oldVersionCode = -1L,
                source = GitHubSource,
                link = Link.Url("https://github.com/example/fallback/releases/download/v1.0/app.apk")
            )
            coordinator.enqueue(validSaveOnly, saveOnly = true)
            assertEquals("Fallback App", eventDeferred.await())
            assertFalse(coordinator.hasSaveOnlyTask(802))
        } finally {
            DownloadStorageManager.legacyDownloadsDirOverrideForTest = null
        }
    }

    private class RecordingNotifier : AutoUpdateNotifier {
        var standardCount: Int? = null
        var summaryUpdated: List<String>? = null
        var summaryManual: List<String>? = null

        override fun showUpdateNotification(num: Int) {
            standardCount = num
        }

        override fun showAutoUpdateSummaryNotification(
            updatedAppNames: List<String>,
            manualRequiredAppNames: List<String>
        ) {
            summaryUpdated = updatedAppNames
            summaryManual = manualRequiredAppNames
        }
    }

    private class FakeShizukuBridge(
        private val ready: Boolean,
        private val installHandler: (String) -> Boolean
    ) : ShizukuBridge {
        override fun isPackageInstalled(): Boolean = ready
        override fun pingBinder(): Boolean = ready
        override fun isPermissionGranted(): Boolean = ready
        override fun requestPermission(requestCode: Int) = Unit
        override fun observeEvents(onPermissionResult: (Boolean) -> Unit, onBinderChanged: () -> Unit): () -> Unit = {}
        override suspend fun awaitPermissionResult(timeoutMs: Long): Boolean = ready
        override fun executeShizukuInstall(id: Int, packageName: String, parts: List<InstallPart>): Boolean {
            parts.forEach { it.openStream()?.use { stream -> stream.readBytes() } }
            return installHandler(packageName)
        }
    }
}
