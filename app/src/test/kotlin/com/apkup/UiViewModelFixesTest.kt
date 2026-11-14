package com.apkup

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.Link
import com.apkup.service.ApkMirrorService
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorRepository
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.AppsRepository
import com.apkup.repository.GitHubRepository
import com.apkup.repository.GitLabRepository
import com.apkup.repository.IzzyOnDroidRepository
import com.apkup.repository.PlayRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.repository.UpdatesRepository
import com.apkup.ui.component.buildBinding
import com.apkup.util.AppJournal
import com.apkup.util.Badger
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.play.PlayHttpClient
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.CustomFDroidAddResult
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import com.apkup.worker.DownloadCoordinator
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class UiViewModelFixesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `importBackupJson always forces useRootInstaller false and flags security reset`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        prefs.useRootInstaller.put(true)
        val vm = SettingsViewModel(prefs = prefs)

        val backupWithRootTrue = """
            {
              "schemaVersion": 1,
              "useRootInstaller": true,
              "useGitHub": true
            }
        """.trimIndent()

        val result = vm.importBackupJsonDetailed(backupWithRootTrue)
        assertTrue(result.success)
        assertTrue(result.rootResetForSecurity)
        assertFalse(prefs.useRootInstaller.get())

        val backupWithRootFalse = """
            {
              "schemaVersion": 1,
              "useRootInstaller": false
            }
        """.trimIndent()

        val resultFalse = vm.importBackupJsonDetailed(backupWithRootFalse)
        assertTrue(resultFalse.success)
        assertFalse(resultFalse.rootResetForSecurity)
        assertFalse(prefs.useRootInstaller.get())
    }

    @Test
    fun `addCustomFDroidRepoDetailed strictly rejects http and validates 64-hex fingerprint separately`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val vm = SettingsViewModel(prefs = prefs)

        assertEquals(
            CustomFDroidAddResult.INVALID_URL,
            vm.addCustomFDroidRepoDetailed("Insecure", "http://example.org/fdroid/repo", "")
        )
        assertFalse(vm.addCustomFDroidRepo("Insecure", "http://example.org/fdroid/repo", ""))

        assertEquals(
            CustomFDroidAddResult.DISALLOWED_HOST,
            vm.addCustomFDroidRepoDetailed(
                name = "Loopback",
                rawUrl = "https://127.0.0.1/fdroid/repo",
                fingerprint = "",
                allowLanHosts = false
            )
        )

        assertEquals(
            CustomFDroidAddResult.INVALID_FINGERPRINT,
            vm.addCustomFDroidRepoDetailed(
                name = "BadFp",
                rawUrl = "https://repo.example.org/fdroid/repo",
                fingerprint = "1234ABCD"
            )
        )
        assertEquals(
            CustomFDroidAddResult.INVALID_FINGERPRINT,
            vm.addCustomFDroidRepoDetailed(
                name = "NonHexFp",
                rawUrl = "https://repo.example.org/fdroid/repo",
                fingerprint = "z".repeat(64)
            )
        )
        assertTrue(prefs.readCustomFDroidReposSafe().isEmpty())

        val validFp = FDroidRepoConfig.MICROG_FINGERPRINT
        assertEquals(
            CustomFDroidAddResult.SUCCESS,
            vm.addCustomFDroidRepoDetailed(
                name = "microG",
                rawUrl = "https://microg.org/fdroid/repo",
                fingerprint = validFp
            )
        )
        assertEquals(
            CustomFDroidAddResult.SUCCESS,
            vm.addCustomFDroidRepoDetailed(
                name = "Unpinned",
                rawUrl = "fdroidrepo://repository.mullvad.net/android/fdroid/repo",
                fingerprint = ""
            )
        )
        val saved = prefs.readCustomFDroidReposSafe()
        assertEquals(2, saved.size)
        assertEquals("https://repository.mullvad.net/android/fdroid/repo", saved[1].baseUrl)
        assertEquals("", saved[1].fingerprint)
    }

    @Test
    fun `importBackupJson handles Gson null fields in watchedApps and customFDroidRepos safely`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val vm = SettingsViewModel(
            prefs = prefs,
            repoMappingManager = RepoMappingManager(context = null, prefs = prefs, gson = Gson())
        )

        val nullableBackup = """
            {
              "schemaVersion": 1,
              "watchedApps": [
                {"packageName": "org.example.valid", "appName": null},
                {"packageName": null, "appName": "MissingPkg"},
                null
              ],
              "customFDroidRepos": [
                {"id": null, "name": null, "baseUrl": "https://repo.example.org/fdroid/repo", "fingerprint": null, "enabled": true},
                {"id": "bad", "name": "MissingUrl", "baseUrl": null, "fingerprint": ""},
                null
              ],
              "customRepoBindings": {
                "org.example.valid": null
              }
            }
        """.trimIndent()

        val result = vm.importBackupJsonDetailed(nullableBackup)
        assertTrue(result.success)
        val watched = prefs.readWatchedAppsSafe()
        assertEquals(1, watched.size)
        assertEquals("org.example.valid", watched.first().packageName)
        assertEquals("org.example.valid", watched.first().appName)

        val repos = prefs.readCustomFDroidReposSafe()
        assertEquals(1, repos.size)
        assertEquals("https://repo.example.org/fdroid/repo", repos.first().baseUrl)
    }

    @Test
    fun `AppsViewModel addWatchedApp returns false when non-blank repoUrlOrSlug is invalid and persists nothing`() = runBlocking {
        val prefs = Prefs(InMemoryPrefsBackend())
        val appsRepo = AppsRepository(context = ContextWrapper(null), prefs = prefs)
        val vm = AppsViewModel(repository = appsRepo, prefs = prefs, badger = Badger())

        assertEquals(
            AppsViewModel.WatchAppAddResult.INVALID_REPO,
            vm.addWatchedAppDetailed("org.example.app", "Example", "not a valid repo url")
        )
        assertFalse(vm.addWatchedApp("org.example.app", "Example", "http://insecure.example.com/owner/repo"))
        assertTrue(prefs.readWatchedAppsSafe().isEmpty())
        assertTrue(prefs.readCustomRepoBindingsSafe().isEmpty())

        assertEquals(
            AppsViewModel.WatchAppAddResult.SUCCESS,
            vm.addWatchedAppDetailed("org.example.app", "Example", "https://codeberg.org/owner/repo")
        )
        assertEquals(1, prefs.readWatchedAppsSafe().size)
        assertEquals("owner/repo", prefs.readCustomRepoBindingsSafe()["org.example.app"]?.repo)
    }

    @Test
    fun `AppsViewModel refreshAndGet survives Gson null appName in watched apps`() = runBlocking {
        val backend = InMemoryPrefsBackend()
        backend.putString("watchedApps", """[{"packageName":"org.example.nullname","appName":null}]""")
        val prefs = Prefs(backend)
        val watchedEntries = prefs.readWatchedAppsSafe()
        assertEquals(1, watchedEntries.size)
        assertEquals("org.example.nullname", watchedEntries.first().packageName)

        val appsRepo = AppsRepository(context = ContextWrapper(null), prefs = prefs)
        val vm = AppsViewModel(repository = appsRepo, prefs = prefs, badger = Badger())

        val result = vm.refreshAndGet()
        assertNotNull(result)
    }

    @Test
    fun `buildDiagnosticLogReport defaults to includeLogcat false and honors opt-in true`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val journal = AppJournal(journalFile = null)
        journal.log("Test", "Diagnostic entry")
        val vm = SettingsViewModel(prefs = prefs, appJournal = journal)

        val defaultReport = vm.buildDiagnosticLogReport()
        assertTrue(defaultReport.contains("Diagnostic entry"))
        assertTrue(defaultReport.contains("(omitted — opt-in disabled)"))

        val optInReport = vm.buildDiagnosticLogReport(includeLogcat = true)
        assertTrue(optInReport.contains("Diagnostic entry"))
        assertFalse(optInReport.contains("(omitted — opt-in disabled)"))
    }

    @Test
    fun `pinCustomFDroidRepoTofu marks repo as TOFU pinned and manual explicit pin clears flag`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val vm = SettingsViewModel(prefs = prefs)

        assertEquals(
            CustomFDroidAddResult.SUCCESS,
            vm.addCustomFDroidRepoDetailed("Example", "https://repo.example.org/fdroid/repo", "")
        )
        val initial = prefs.readCustomFDroidReposSafe().single()
        assertEquals("", initial.fingerprint)
        assertFalse(initial.isTofuPinned)

        val tofuFp = FDroidRepoConfig.MICROG_FINGERPRINT
        assertTrue(prefs.pinCustomFDroidRepoTofu("https://repo.example.org/fdroid/repo", tofuFp))
        val tofuPinned = prefs.readCustomFDroidReposSafe().single()
        assertEquals(FDroidRepoConfig.normalizeFingerprint(tofuFp), tofuPinned.fingerprint)
        assertTrue(tofuPinned.isTofuPinned)

        // Explicit re-pinning by the user clears isTofuPinned
        assertEquals(
            CustomFDroidAddResult.SUCCESS,
            vm.addCustomFDroidRepoDetailed("Example", "https://repo.example.org/fdroid/repo", tofuFp)
        )
        val explicitlyPinned = prefs.readCustomFDroidReposSafe().single()
        assertEquals(FDroidRepoConfig.normalizeFingerprint(tofuFp), explicitlyPinned.fingerprint)
        assertFalse(explicitlyPinned.isTofuPinned)
    }

    @Test
    fun `UpdatesViewModel collects downloadCoordinator safFallbackEvents and emits SAF fallback snackbar`() = runBlocking {
        val baseDir = tempFolder.newFolder("vm_saf_base")
        val cacheDir = tempFolder.newFolder("vm_saf_cache")
        val downloadsFallbackRoot = tempFolder.newFolder("vm_saf_downloads")
        @Suppress("DEPRECATION")
        val stubResources = object : Resources(null, null, null) {
            override fun getString(id: Int): String = when (id) {
                R.string.download_saf_fallback_snack -> "Custom download folder unavailable — saved to default Downloads/ApkUp folder."
                else -> "stub"
            }
            override fun getString(id: Int, vararg formatArgs: Any?): String = when (id) {
                R.string.download_saf_fallback_snack_named -> "Custom download folder unavailable — saved to ${formatArgs.firstOrNull()}."
                R.string.apk_save_success -> "Saved to ${formatArgs.firstOrNull()}"
                else -> "stub"
            }
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

        val gson = Gson()
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            customDownloadFolderUri.put("content://com.android.externalstorage.documents/tree/primary%3ABadFolder")
        }
        val downloader = Downloader(httpClient, httpClient)
        val installLog = InstallLog()
        val snackBar = SnackBar()
        val stringer = Stringer(fakeContext)
        val installer = SessionInstaller(context = null, installLog = installLog, prefs = prefs)
        val mappingManager = RepoMappingManager(context = null, prefs = prefs, gson = gson)
        val resolver = ApkMirrorResolver(client = httpClient, minDelayMs = 0L)
        val coordinator = DownloadCoordinator(
            context = fakeContext,
            downloader = downloader,
            installer = installer,
            installLog = installLog,
            snackBar = snackBar,
            stringer = stringer,
            prefs = prefs,
            apkMirrorResolver = resolver,
            repoMappingManager = mappingManager,
            notificationManager = DownloadNotificationManager(fakeContext)
        )
        val appsRepo = AppsRepository(context = fakeContext, prefs = prefs)
        val apkMirrorService = Retrofit.Builder()
            .client(httpClient)
            .baseUrl("https://www.apkmirror.com")
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApkMirrorService::class.java)
        val updatesRepo = UpdatesRepository(
            appsRepository = appsRepo,
            apkMirrorRepository = ApkMirrorRepository(apkMirrorService, fakeContext.packageManager, httpClient),
            playRepository = PlayRepository(
                fakeContext,
                PlayHttpClient(Cache(tempFolder.newFolder("play_cache"), 1024L * 1024L)),
                gson,
                prefs
            ),
            izzyOnDroidRepository = IzzyOnDroidRepository(fakeContext, httpClient, gson, prefs, mappingManager),
            gitHubRepository = GitHubRepository(fakeContext, httpClient, gson, prefs, mappingManager),
            gitLabRepository = GitLabRepository(fakeContext, httpClient, gson, prefs, mappingManager),
            prefs = prefs,
            repoMappingManager = mappingManager
        )
        val vm = UpdatesViewModel(
            updatesRepository = updatesRepo,
            installer = installer,
            prefs = prefs,
            badger = Badger(),
            downloader = downloader,
            snackBar = snackBar,
            stringer = stringer,
            installLog = installLog,
            context = fakeContext,
            apkMirrorResolver = resolver,
            downloadCoordinator = coordinator
        )

        DownloadStorageManager.legacyDownloadsDirOverrideForTest = downloadsFallbackRoot
        try {
            val fallbackSnackDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000L) {
                    snackBar.flow()
                        .filterIsInstance<TextSnack>()
                        .first { it.message.contains("Custom download folder unavailable") }
                }
            }
            val update = AppUpdate(
                id = 905,
                name = "Fallback App",
                packageName = "com.example.fallback",
                version = "1.0.0",
                oldVersion = "",
                versionCode = 10L,
                oldVersionCode = -1L,
                source = GitHubSource,
                link = Link.Url("https://github.com/example/fallback/releases/download/v1.0/app.apk")
            )
            coordinator.enqueue(update, saveOnly = true)
            val snack = fallbackSnackDeferred.await()
            assertTrue(snack.message.startsWith("Fallback App: "))
            assertTrue(snack.message.contains("Downloads/ApkUp"))
            assertNotNull(vm)
        } finally {
            DownloadStorageManager.legacyDownloadsDirOverrideForTest = null
        }
    }

    @Test
    fun `LinkRepoDialog buildBinding returns null on invalid input so save is blocked`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = Gson())
        assertNull(buildBinding(manager = manager, type = "github", repoInput = "invalid-slug", hostInput = "", assetFilterInput = ""))
        assertNull(buildBinding(manager = manager, type = "github", repoInput = "owner/repo", hostInput = "", assetFilterInput = "[unclosed"))
        val valid = buildBinding(manager = manager, type = "github", repoInput = "owner/repo", hostInput = "", assetFilterInput = ".*\\.apk$")
        assertNotNull(valid)
        assertEquals("owner/repo", valid?.repo)
    }

    @Test
    fun `unified FDroid repo config supports optional TOFU pinning and legacy custom repo migration`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val vm = SettingsViewModel(prefs = prefs)

        // 1. Adding a custom F-Droid repo without SHA-256 succeeds and starts unpinned (TOFU-eligible)
        val addResult = vm.addFDroidRepoConfig(
            rawUrl = "fdroidrepo://repo.example.org/fdroid/repo",
            explicitName = "Example Repo",
            explicitFingerprint = ""
        )
        assertTrue(addResult.isSuccess)
        val added = prefs.getFDroidRepos().first { !it.isBuiltIn }
        assertEquals("https://repo.example.org/fdroid/repo", added.normalizedUrl)
        assertEquals("", added.normalizedFingerprint)
        assertFalse(added.isTofuPinned)

        // 2. First verified signed fetch pins the SHA-256 fingerprint persistently with isTofuPinned = true
        val tofuFp = FDroidRepoConfig.MICROG_FINGERPRINT
        assertTrue(prefs.pinFDroidRepoTofu(added.id, tofuFp))
        val pinned = prefs.getFDroidRepos().first { it.id == added.id }
        assertEquals(FDroidRepoConfig.normalizeFingerprint(tofuFp), pinned.normalizedFingerprint)
        assertTrue(pinned.isTofuPinned)

        // 3. Legacy customFDroidRepos migration moves NewPipe & custom entries into unified fdroidRepos and clears legacy source
        prefs.useCustomFDroidRepos.put(true)
        prefs.writeCustomFDroidRepos(
            listOf(
                com.apkup.data.ui.CustomFDroidRepo(
                    name = "NewPipe Repo",
                    baseUrl = "https://archive.newpipe.net/fdroid/repo",
                    fingerprint = FDroidRepoConfig.NEWPIPE_FINGERPRINT,
                    enabled = true
                ),
                com.apkup.data.ui.CustomFDroidRepo(
                    name = "Legacy Repo",
                    baseUrl = "https://legacy.example.org/fdroid/repo",
                    fingerprint = "",
                    enabled = true
                )
            )
        )
        prefs.migrateLegacyCustomFDroidRepos()
        assertFalse(prefs.useCustomFDroidRepos.get())
        assertTrue(prefs.readCustomFDroidReposSafe().isEmpty())
        val unified = prefs.getFDroidRepos()
        assertTrue(unified.any { it.id == FDroidRepoConfig.ID_NEWPIPE && it.enabled })
        assertTrue(unified.any { it.normalizedUrl == "https://legacy.example.org/fdroid/repo" && it.enabled })
    }
}
