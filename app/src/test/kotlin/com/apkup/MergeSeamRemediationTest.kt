package com.apkup

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.repomapping.GitHubReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.repomapping.RepoUrlParser
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CustomFDroidPresets
import com.apkup.data.ui.CustomFDroidRepo
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.Link
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.AppsRepository
import com.apkup.repository.CodebergRepository
import com.apkup.repository.CustomFDroidReposRepository
import com.apkup.repository.IzzyOnDroidRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.ui.component.buildBinding
import com.apkup.ui.component.normalizePastedRepoUrlOrNull
import com.apkup.util.Badger
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.DownloadStorageManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.worker.DownloadCoordinator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MergeSeamRemediationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // Area 1: LinkRepoDialog + RepoMappingManager (GitLab subgroups, hintSource, paste gate, traversal)
    // =========================================================================

    @Test
    fun `LinkRepoDialog paste normalizer ignores bare slugs while typing and normalizes URLs and domain pastes`() {
        assertNull(normalizePastedRepoUrlOrNull("fdroid/f"))
        assertNull(normalizePastedRepoUrlOrNull("group/subgroup/"))
        assertNull(normalizePastedRepoUrlOrNull("group/subgroup/project"))
        assertEquals(
            "https://gitlab.com/fdroid/fdroidclient",
            normalizePastedRepoUrlOrNull("gitlab.com/fdroid/fdroidclient")
        )
        assertEquals(
            "https://codeberg.org/Freeyourgadget/Gadgetbridge",
            normalizePastedRepoUrlOrNull("codeberg.org/Freeyourgadget/Gadgetbridge")
        )
        assertEquals(
            "https://invent.kde.org/network/tokodon",
            normalizePastedRepoUrlOrNull("https://invent.kde.org/network/tokodon")
        )
    }

    @Test
    fun `RepoUrlParser and buildBinding support GitLab multi-segment subgroups and block slug traversal`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = gson)

        // Bare 3-segment GitLab subgroup slug succeeds when GitLab is selected
        val glSubgroup = RepoUrlParser.parseRepoUrl("group/subgroup/project.git", hintSource = "gitlab")
        assertNotNull(glSubgroup)
        assertEquals("gitlab", glSubgroup!!.type)
        assertEquals("group/subgroup/project", glSubgroup.repo)

        val dialogSubgroup = buildBinding(
            manager = manager,
            type = "gitlab",
            repoInput = "group/sub1/sub2/app",
            hostInput = "gitlab.example.org"
        )
        assertNotNull(dialogSubgroup)
        assertEquals("gitlab", dialogSubgroup!!.type)
        assertEquals("group/sub1/sub2/app", dialogSubgroup.repo)
        assertEquals("gitlab.example.org", dialogSubgroup.resolvedHost)

        // Codeberg bare slug keeps type = codeberg
        val cbSlug = buildBinding(
            manager = manager,
            type = "codeberg",
            repoInput = "Freeyourgadget/Gadgetbridge",
            hostInput = ""
        )
        assertNotNull(cbSlug)
        assertEquals("codeberg", cbSlug!!.type)
        assertEquals("codeberg.org", cbSlug.resolvedHost)

        // 3-segment slug rejected for GitHub and Codeberg
        assertNull(buildBinding(manager, type = "github", repoInput = "a/b/c", hostInput = ""))
        assertNull(buildBinding(manager, type = "codeberg", repoInput = "a/b/c", hostInput = ""))

        // Traversal & oversized regex rejected in buildBinding
        assertNull(buildBinding(manager, type = "gitlab", repoInput = "owner/../../api", hostInput = ""))
        assertNull(
            buildBinding(
                manager = manager,
                type = "github",
                repoInput = "owner/repo",
                hostInput = "",
                assetFilterInput = "a".repeat(ReleaseAssetFilter.MAX_REGEX_LENGTH + 1)
            )
        )
    }

    // =========================================================================
    // Area 2: IzzyOnDroidRepository + CustomFDroidReposRepository + microG preset
    // =========================================================================

    @Test
    fun `CustomFDroidPresets microG matches official FDroidRepoConfig MICROG_FINGERPRINT`() {
        val microg = CustomFDroidPresets.presets.first { it.id == "microg" }
        assertEquals(FDroidRepoConfig.MICROG_FINGERPRINT, microg.fingerprint)
        assertTrue(FDroidRepoConfig.isValidSha256Fingerprint(microg.fingerprint))
    }

    @Test
    fun `IzzyOnDroidRepository resolveActiveRepos fails closed on non-blank invalid or 16-hex fingerprints and cleartext HTTP`() {
        val truncated16HexRepo = IzzyOnDroidRepository(
            okHttpClient = OkHttpClient(),
            gson = gson,
            repoBaseUrl = "https://cdn.mullvad.net/android/fdroid/repo",
            repoFingerprint = "626c860f384a98b5",
            targetSource = CustomFDroidSource,
            repoDisplayName = "Truncated FP Repo"
        )
        try {
            truncated16HexRepo.resolveActiveRepos()
            fail("Expected SecurityException for truncated 16-hex custom F-Droid fingerprint")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("fingerprint", ignoreCase = true))
        }

        val cleartextRepo = IzzyOnDroidRepository(
            okHttpClient = OkHttpClient(),
            gson = gson,
            repoBaseUrl = "http://evil.example.org/fdroid/repo",
            repoFingerprint = "",
            targetSource = CustomFDroidSource,
            repoDisplayName = "HTTP Repo"
        )
        try {
            cleartextRepo.resolveActiveRepos()
            fail("Expected SecurityException for cleartext HTTP custom F-Droid repo")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("HTTPS", ignoreCase = true))
        }

        // Blank fingerprint (e.g. Mullvad preset pending Q-02) remains explicitly unpinned
        val unpinnedMullvad = IzzyOnDroidRepository(
            okHttpClient = OkHttpClient(),
            gson = gson,
            repoBaseUrl = "https://repository.mullvad.net/android/fdroid/repo/",
            repoFingerprint = "",
            targetSource = CustomFDroidSource,
            repoDisplayName = "Mullvad Repo"
        ).resolveActiveRepos()
        assertEquals(1, unpinnedMullvad.size)
        assertEquals("", unpinnedMullvad.single().normalizedFingerprint)
    }

    @Test
    fun `CustomFDroidReposRepository does not mask failedRequests when another custom repo returns updates`() = runBlocking {
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            useCustomFDroidRepos.put(true)
            writeCustomFDroidRepos(
                listOf(
                    CustomFDroidRepo(
                        id = "bad_fp",
                        name = "BadFpRepo",
                        baseUrl = "https://bad.example.org/fdroid/repo",
                        fingerprint = "BAD_FINGERPRINT",
                        enabled = true
                    ),
                    CustomFDroidRepo(
                        id = "unpinned_ok",
                        name = "GoodCustomRepo",
                        baseUrl = "https://good.example.org/fdroid/repo",
                        fingerprint = "",
                        enabled = true
                    )
                )
            )
        }

        val cacheRoot = tempFolder.newFolder("custom_fdroid_cache")
        val normalizedGoodUrl = "https://good.example.org/fdroid/repo/"
        val goodDirHash = normalizedGoodUrl.lowercase(Locale.ROOT).hashCode().toUInt().toString(16)
        val goodRepoCacheDir = File(cacheRoot, "repo_$goodDirHash").apply { mkdirs() }
        File(goodRepoCacheDir, "index-v1.json").writeText(
            """
            {
              "repo": {"timestamp": 1700000000000},
              "packages": {
                "org.example.watched": [
                  {
                    "apkName": "watched-10.apk",
                    "versionName": "1.0.0",
                    "versionCode": 10,
                    "size": 2048,
                    "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "hashType": "sha256",
                    "signer": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "minSdkVersion": 0
                  }
                ]
              }
            }
            """.trimIndent()
        )
        File(goodRepoCacheDir, "index.meta").writeText("\"etag-v1\"\n\n\nv1")

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val path = chain.request().url.encodedPath
                val statusCode = if (path.endsWith("/index-v1.jar")) 304 else 404
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(if (statusCode == 304) "Not Modified" else "Not Found")
                    .body("".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()

        val customRepos = CustomFDroidReposRepository(
            context = null,
            okHttpClient = client,
            gson = gson,
            prefs = prefs,
            baseCacheDirectory = cacheRoot
        )

        val apps = listOf(
            AppInstalled(
                name = "Watched App",
                packageName = "org.example.watched",
                version = "",
                versionCode = -1L,
                isWatchedOnly = true
            )
        )
        val result = customRepos.updates(apps).first()
        assertEquals("GoodCustomRepo must still emit its update", 1, result.updates.size)
        assertTrue("Failed custom repos must NOT be masked when another repo returns updates", result.failedRequests >= 1)
        assertNotNull("failureSummary must report BadFpRepo failure", result.failureSummary)
        assertTrue(result.failureSummary!!.contains("BadFpRepo"))
    }

    // =========================================================================
    // Area 3: DownloadCoordinator Save-APK integrity & staging cache hygiene
    // =========================================================================

    @Test
    fun `DownloadCoordinator Save-APK verifies SHA256 and archive integrity BEFORE writing ready marker and purges poisoned stagingDir`() = runBlocking {
        val baseDir = tempFolder.newFolder("coord_base")
        val cacheDir = tempFolder.newFolder("coord_cache")
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

        val validApkZipBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write("manifest-placeholder".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val bodyBytes = if (chain.request().url.encodedPath.contains("corrupt")) {
                    "not-a-zip-payload".toByteArray()
                } else {
                    validApkZipBytes
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            })
            .build()

        val prefs = Prefs(InMemoryPrefsBackend())
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
            repoMappingManager = RepoMappingManager(context = null, prefs = prefs, gson = gson),
            notificationManager = DownloadNotificationManager(fakeContext)
        )

        // 1. Wrong expectedSha256 on Save-APK must fail BEFORE .ready is written and purge stagingDir
        val wrongHashUpdate = AppUpdate(
            id = 701,
            name = "Watched Gadget",
            packageName = "org.example.gadget",
            version = "1.0.0",
            oldVersion = "",
            versionCode = 100L,
            oldVersionCode = -1L,
            source = GitHubSource,
            link = Link.Url(
                link = "https://github.com/example/gadget/releases/download/v1.0.0/app.apk",
                expectedSha256 = "0".repeat(64)
            )
        )
        coordinator.enqueue(wrongHashUpdate, saveOnly = true)
        val deadline = System.currentTimeMillis() + 5_000L
        while (coordinator.activeTasksFlow.value.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        val downloadRoot = DownloadStorageManager.getDownloadDir(fakeContext)
        val staging701 = File(downloadRoot, "download_staging_701")
        assertFalse(
            "Tampered SHA-256 download must NOT leave .ready cache",
            downloader.hasReadyCachedApk(downloadRoot, 701, 100L)
        )
        assertFalse("Tampered staging directory must be deleted recursively", staging701.exists())

        // 2. Pre-poisoned .ready staging directory with non-APK bytes must be purged on execution!
        val staging702 = DownloadStorageManager.getStagingDir(fakeContext, 702)
        File(staging702, "base.apk").writeBytes("corrupted-cached-bytes".toByteArray())
        downloader.markStagingReady(staging702, 200L)
        assertTrue(File(staging702, Downloader.READY_MARKER_FILE).exists())
        assertTrue(downloader.hasReadyCachedApk(downloadRoot, 702, 200L))

        val poisonedCachedUpdate = AppUpdate(
            id = 702,
            name = "Poisoned App",
            packageName = "org.example.poisoned",
            version = "2.0.0",
            oldVersion = "1.0.0",
            versionCode = 200L,
            oldVersionCode = 100L,
            source = GitHubSource,
            link = Link.Url("https://github.com/example/poisoned/releases/download/v2.0.0/app.apk")
        )
        coordinator.executeDownloadAndInstall(poisonedCachedUpdate)
        assertFalse("Poisoned .ready staging directory must be purged on ApkIntegrityException", staging702.exists())
    }

    // =========================================================================
    // Area 4: CodebergRepository null-assets resilience, SSRF guard & failedRequests
    // =========================================================================

    @Test
    fun `CodebergRepository handles null assets without NPE, blocks SSRF hosts, and reports failedRequests`() = runBlocking {
        val prefs = Prefs(InMemoryPrefsBackend())
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = gson)

        // 1. Release JSON with missing/null assets and null name/tag_name must NOT throw NPE
        val sparseJson = """[{"draft":false,"prerelease":false,"assets":null}]"""
        val sparseClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sparseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val sparseRepo = CodebergRepository(okHttpClient = sparseClient, gson = gson, prefs = prefs, repoMappingManager = manager)
        val parsedSparse: GitHubReleaseDto? = sparseRepo.fetchLatestRelease(
            RepoBinding(type = "codeberg", repo = "org/app", host = "codeberg.org")
        )
        assertNotNull(parsedSparse)
        assertTrue(parsedSparse!!.safeAssets.isEmpty())

        // 2. SSRF / loopback / traversal bindings rejected before network call
        assertNull(sparseRepo.fetchLatestRelease(RepoBinding(type = "codeberg", repo = "org/app", host = "127.0.0.1")))
        assertNull(sparseRepo.fetchLatestRelease(RepoBinding(type = "codeberg", repo = "org/../../admin", host = "codeberg.org")))

        // 3. HTTP 500 increments failedRequests instead of reporting failedRequests = 0
        manager.setCustomBinding("org.example.cb", RepoBinding(type = "codeberg", repo = "org/app", host = "codeberg.org"))
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("error".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()
        val failingRepo = CodebergRepository(okHttpClient = failingClient, gson = gson, prefs = prefs, repoMappingManager = manager)
        val res = failingRepo.updates(
            listOf(AppInstalled(name = "CB App", packageName = "org.example.cb", version = "1.0.0", versionCode = 10L))
        ).first()
        assertEquals(1, res.failedRequests)
        assertNotNull(res.failureSummary)
    }

    // =========================================================================
    // Area 5: ReleaseAssetFilter MAX_REGEX_LENGTH = 256
    // =========================================================================

    @Test
    fun `ReleaseAssetFilter enforces MAX_REGEX_LENGTH 256 in isValidRegex and findBestApk`() {
        assertTrue(ReleaseAssetFilter.isValidRegex("arm64.*\\.apk$"))
        assertTrue(ReleaseAssetFilter.isValidRegex("a".repeat(ReleaseAssetFilter.MAX_REGEX_LENGTH)))
        assertFalse(ReleaseAssetFilter.isValidRegex("a".repeat(ReleaseAssetFilter.MAX_REGEX_LENGTH + 1)))

        val assets = listOf("app-arm64-release.apk")
        assertNull(
            ReleaseAssetFilter.findBestApk(
                assets = assets,
                getFilename = { it },
                supportedAbis = arrayOf("arm64-v8a"),
                assetFilterRegex = ".".repeat(ReleaseAssetFilter.MAX_REGEX_LENGTH + 1)
            )
        )
    }

    // =========================================================================
    // Area 6: SettingsViewModel.importBackupJson + AppsViewModel.addWatchedApp
    // =========================================================================

    @Test
    fun `SettingsViewModel importBackupJson strips SSRF HTTP and invalid customFDroidRepos and traversal customRepoBindings`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = gson)
        val vm = SettingsViewModel(prefs = prefs, repoMappingManager = manager)
        val validFp = FDroidRepoConfig.MICROG_FINGERPRINT
        val overlongRegex = "a".repeat(300)

        val maliciousBackup = """
            {
              "schemaVersion": 1,
              "customFDroidRepos": [
                {"id": "http_bad", "name": "Cleartext", "baseUrl": "http://example.org/fdroid/repo", "fingerprint": "$validFp", "enabled": true},
                {"id": "ssrf_bad", "name": "Loopback", "baseUrl": "https://127.0.0.1/fdroid/repo", "fingerprint": "$validFp", "enabled": true},
                {"id": "fp_bad", "name": "TruncatedFp", "baseUrl": "https://repo.example.org/fdroid/repo", "fingerprint": "1234ABCD", "enabled": true},
                {"id": "valid_pinned", "name": "ValidPinned", "baseUrl": "https://microg.org/fdroid/repo/", "fingerprint": "$validFp", "enabled": true},
                {"id": "valid_unpinned", "name": "Mullvad", "baseUrl": "https://repository.mullvad.net/android/fdroid/repo/", "fingerprint": "", "enabled": true}
              ],
              "customRepoBindings": {
                "org.valid.gitlab": {"type": "gitlab", "repo": "group/subgroup/project", "host": "gitlab.com"},
                "org.bad.traversal": {"type": "github", "repo": "owner/../../etc/passwd"},
                "org.bad.regex": {"type": "codeberg", "repo": "org/app", "assetFilterRegex": "$overlongRegex"},
                "org.bad.type": {"type": "evil_type", "repo": "org/app"}
              }
            }
        """.trimIndent()

        assertTrue(vm.importBackupJson(maliciousBackup))

        val importedCustomRepos = prefs.readCustomFDroidReposSafe()
        assertEquals(2, importedCustomRepos.size)
        assertEquals(setOf("valid_pinned", "valid_unpinned"), importedCustomRepos.map { it.id }.toSet())

        val importedBindings = prefs.readCustomRepoBindingsSafe()
        assertEquals(setOf("org.valid.gitlab"), importedBindings.keys)
        assertEquals("group/subgroup/project", importedBindings["org.valid.gitlab"]?.repo)
    }

    @Test
    fun `AppsViewModel addWatchedApp enforces ApkIntegrityVerifier isValidPackageName`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val appsRepo = AppsRepository(context = ContextWrapper(null), prefs = prefs)
        val vm = AppsViewModel(repository = appsRepo, prefs = prefs, badger = Badger())

        assertFalse(vm.addWatchedApp("../traversal.pkg", "Bad"))
        assertFalse(vm.addWatchedApp("123.invalid.start", "Bad"))
        assertTrue(vm.addWatchedApp("org.videolan.vlc", "VLC", "https://gitlab.com/videolan/vlc-android"))
    }

    // =========================================================================
    // Area 7: Custom F-Droid TOFU Cert Pinning, No-Harvesting & Gson Null-Safety
    // =========================================================================

    @Test
    fun `CustomFDroidReposRepository performs TOFU cert pinning, skips RepoMappingManager harvesting, and tolerates null name`() = runBlocking {
        val workDir = tempFolder.newFolder("tofu_jars")
        val javaHomeBin = File(System.getProperty("java.home"), "bin")
        val isWindows = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")
        val keytool = File(javaHomeBin, if (isWindows) "keytool.exe" else "keytool")
        val jarsigner = File(javaHomeBin, if (isWindows) "jarsigner.exe" else "jarsigner")

        fun buildSignedV1Jar(alias: String, ksFile: File, indexJson: String): Pair<ByteArray, String> {
            val genProc = ProcessBuilder(
                keytool.absolutePath,
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-dname", "CN=$alias",
                "-keystore", ksFile.absolutePath,
                "-storepass", "changeit",
                "-keypass", "changeit"
            ).redirectErrorStream(true).start()
            val genOut = genProc.inputStream.bufferedReader().readText()
            assertEquals("keytool genkeypair failed: $genOut", 0, genProc.waitFor())

            val ks = java.security.KeyStore.getInstance(ksFile, "changeit".toCharArray())
            val cert = ks.getCertificate(alias)
            val fp = java.security.MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded)
                .joinToString("") { "%02X".format(it) }

            val jarFile = File(workDir, "$alias-index-v1.jar")
            java.util.jar.JarOutputStream(jarFile.outputStream()).use { jos ->
                jos.putNextEntry(java.util.jar.JarEntry("index-v1.json"))
                jos.write(indexJson.toByteArray(Charsets.UTF_8))
                jos.closeEntry()
            }
            val signProc = ProcessBuilder(
                jarsigner.absolutePath,
                "-keystore", ksFile.absolutePath,
                "-storepass", "changeit",
                "-keypass", "changeit",
                jarFile.absolutePath,
                alias
            ).redirectErrorStream(true).start()
            val signOut = signProc.inputStream.bufferedReader().readText()
            assertEquals("jarsigner failed: $signOut", 0, signProc.waitFor())
            return jarFile.readBytes() to fp
        }

        val signerHex = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        val apkSha = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val indexJson = """
            {
              "repo": {"timestamp": 1700000000000},
              "apps": [
                {
                  "packageName": "org.example.tofu",
                  "name": "TOFU App",
                  "sourceCode": "https://github.com/evil/hijack-attempt"
                }
              ],
              "packages": {
                "org.example.tofu": [
                  {
                    "apkName": "tofu-20.apk",
                    "versionName": "2.0.0",
                    "versionCode": 20,
                    "size": 4096,
                    "hash": "$apkSha",
                    "hashType": "sha256",
                    "signer": "$signerHex",
                    "minSdkVersion": 0
                  }
                ]
              }
            }
        """.trimIndent()

        val (jarA, fpA) = buildSignedV1Jar("certa", File(workDir, "ks_a.p12"), indexJson)
        val (jarB, _) = buildSignedV1Jar("certb", File(workDir, "ks_b.p12"), indexJson)

        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            useCustomFDroidRepos.put(true)
        }
        // Deserialize a CustomFDroidRepo with null name via Gson to verify Gson null-safety
        val nullNameRepo = gson.fromJson(
            """{"id":"tofu_repo","name":null,"baseUrl":"https://tofu.example.org/fdroid/repo","fingerprint":"","enabled":true}""",
            CustomFDroidRepo::class.java
        )
        prefs.writeCustomFDroidRepos(listOf(nullNameRepo))

        val manager = RepoMappingManager(context = null, prefs = prefs, gson = gson)
        var serveJarB = false
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val path = chain.request().url.encodedPath
                if (path.endsWith("/index-v1.jar")) {
                    val bytes = if (serveJarB) jarB else jarA
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(bytes.toResponseBody("application/java-archive".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            })
            .build()

        val cacheRoot = tempFolder.newFolder("tofu_repo_cache")
        val customRepos = CustomFDroidReposRepository(
            context = null,
            okHttpClient = client,
            gson = gson,
            prefs = prefs,
            repoMappingManager = manager,
            baseCacheDirectory = cacheRoot
        )

        val installedApp = AppInstalled(
            name = "TOFU App",
            packageName = "org.example.tofu",
            version = "1.0.0",
            versionCode = 10L,
            signatureSha256 = signerHex
        )

        // 1. First fetch succeeds, pins Cert A via TOFU, and does NOT harvest sourceCode into RepoMappingManager
        val firstResult = customRepos.updates(listOf(installedApp)).first()
        assertEquals(1, firstResult.updates.size)
        assertEquals(0, firstResult.failedRequests)

        val storedRepo = prefs.readCustomFDroidReposSafe().single()
        assertEquals(fpA, storedRepo.fingerprint)
        assertTrue(storedRepo.isTofuPinned)
        assertNull("Custom F-Droid repos must NOT harvest sourceCode into RepoMappingManager", manager.getBinding("org.example.tofu"))

        // 2. Second fetch with attacker Cert B fails closed even on a fresh CustomFDroidReposRepository
        serveJarB = true
        val freshCustomRepos = CustomFDroidReposRepository(
            context = null,
            okHttpClient = client,
            gson = gson,
            prefs = prefs,
            repoMappingManager = manager,
            baseCacheDirectory = tempFolder.newFolder("tofu_repo_cache_2")
        )
        val attackResult = freshCustomRepos.updates(listOf(installedApp)).first()
        assertTrue("Swapped cert B must fail closed after TOFU pin", attackResult.updates.isEmpty())
        assertTrue(attackResult.failedRequests >= 1)
    }
}
