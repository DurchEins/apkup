package com.apkup

import com.apkup.data.fdroid.FDroidIndexEngine
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.obtainium.ObtainiumManager
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.selfupdate.GitHubAsset
import com.apkup.data.selfupdate.GitHubRelease
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorRepository
import com.apkup.repository.KnownRepoEntry
import com.apkup.repository.RepoHostValidator
import com.apkup.repository.RepoMappingManager
import com.apkup.repository.SelfUpdateRepository
import com.apkup.repository.computeUpdateSourcePriority
import com.apkup.repository.defaultSourcePriority
import com.apkup.repository.resolveActivePlayStoreLocale
import com.apkup.service.ApkMirrorAuthInterceptor
import com.apkup.util.ApkIntegrityException
import com.apkup.util.ApkIntegrityVerifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class SecurityHardeningAuditTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()

    // =========================================================================
    // 1. APK INTEGRITY VERIFIER (F-01, F-02, CRIT-01)
    // =========================================================================

    @Test
    fun `ApkIntegrityVerifier validates package names and sanitizes split filenames against injection`() {
        assertTrue(ApkIntegrityVerifier.isValidPackageName("com.apkup"))
        assertTrue(ApkIntegrityVerifier.isValidPackageName("org.schabi.newpipe_nightly"))
        assertFalse(ApkIntegrityVerifier.isValidPackageName(""))
        assertFalse(ApkIntegrityVerifier.isValidPackageName("singleword"))
        assertFalse(ApkIntegrityVerifier.isValidPackageName("com.evil;id"))
        assertFalse(ApkIntegrityVerifier.isValidPackageName("../com.evil.app"))
        assertFalse(ApkIntegrityVerifier.isValidPackageName("-d com.evil.app"))

        assertEquals("passwd.apk", ApkIntegrityVerifier.sanitizePartFileName("../../etc/passwd", fallbackIndex = 0))
        assertEquals("base.apk", ApkIntegrityVerifier.sanitizePartFileName("../../", fallbackIndex = 0))
        assertEquals("S_evil.apk", ApkIntegrityVerifier.sanitizePartFileName("-S evil.apk", fallbackIndex = 1))
        assertEquals("split_2.apk", ApkIntegrityVerifier.sanitizePartFileName("---.apk", fallbackIndex = 2))
        assertEquals("split_config.arm64_v8a.apk", ApkIntegrityVerifier.sanitizePartFileName("splits/split_config.arm64_v8a.apk", fallbackIndex = 1))
    }

    @Test
    fun `ApkIntegrityVerifier enforces constant-time SHA-256 verification and rejects mismatches`() {
        val apkFile = tempFolder.newFile("sample.apk").apply {
            writeBytes("deterministic-apk-payload".toByteArray(Charsets.UTF_8))
        }
        val actualHash = ApkIntegrityVerifier.computeFileSha256(apkFile)
        assertEquals(64, actualHash.length)

        // Accepts lowercase, uppercase, and GitHub "sha256:" prefixed digests
        ApkIntegrityVerifier.verifyFileSha256(apkFile, actualHash)
        ApkIntegrityVerifier.verifyFileSha256(apkFile, "sha256:${actualHash.uppercase(Locale.ROOT)}")

        // Rejects wrong hash with HASH_MISMATCH
        val wrongHash = "0".repeat(64)
        val mismatchEx = assertThrows(ApkIntegrityException::class.java) {
            ApkIntegrityVerifier.verifyFileSha256(apkFile, wrongHash)
        }
        assertEquals(ApkIntegrityException.Reason.HASH_MISMATCH, mismatchEx.reason)

        // Rejects malformed digest
        val malformedEx = assertThrows(ApkIntegrityException::class.java) {
            ApkIntegrityVerifier.verifyFileSha256(apkFile, "not-a-sha256")
        }
        assertEquals(ApkIntegrityException.Reason.HASH_MISMATCH, malformedEx.reason)
    }

    @Test
    fun `ApkIntegrityVerifier verifies single-signer, multi-signer set equality, and v3 key rotation lineage`() {
        val certA = "a".repeat(64)
        val certB = "b".repeat(64)
        val certC = "c".repeat(64)

        // Exact single-signer match (case-insensitive)
        assertTrue(
            ApkIntegrityVerifier.verifySignerCompatibility(
                installedSigners = setOf(certA),
                candidateSigners = setOf(certA.uppercase(Locale.ROOT)),
                candidateHasMultipleSigners = false,
                candidateRotationLineage = emptySet()
            )
        )

        // Single-signer mismatch without lineage must fail closed
        assertFalse(
            ApkIntegrityVerifier.verifySignerCompatibility(
                installedSigners = setOf(certA),
                candidateSigners = setOf(certB),
                candidateHasMultipleSigners = false,
                candidateRotationLineage = emptySet()
            )
        )

        // Android APK Signature Scheme v3 rotation: old certA -> new certB present in verified lineage
        assertTrue(
            ApkIntegrityVerifier.verifySignerCompatibility(
                installedSigners = setOf(certA),
                candidateSigners = setOf(certB),
                candidateHasMultipleSigners = false,
                candidateRotationLineage = setOf(certA, certB)
            )
        )

        // Multi-signer APK: exact set match succeeds, partial subset fails closed
        assertTrue(
            ApkIntegrityVerifier.verifySignerCompatibility(
                installedSigners = setOf(certA, certB),
                candidateSigners = setOf(certB, certA),
                candidateHasMultipleSigners = true,
                candidateRotationLineage = emptySet()
            )
        )
        assertFalse(
            ApkIntegrityVerifier.verifySignerCompatibility(
                installedSigners = setOf(certA, certB),
                candidateSigners = setOf(certA, certC),
                candidateHasMultipleSigners = true,
                candidateRotationLineage = setOf(certA, certB, certC)
            )
        )
    }

    // =========================================================================
    // 2. PREFS, ONBOARDING CONSENT & F-DROID REPO ANTI-ROLLBACK (F-07, F-08, F-23b)
    // =========================================================================

    @Test
    fun `Prefs onboarding state, F-Droid custom repo CRUD, and monotonic anti-rollback timestamps work cleanly`() {
        val prefs = Prefs(InMemoryPrefsBackend())

        assertFalse(prefs.hasCompletedOnboarding.get())
        assertFalse(prefs.hasCompletedOnboardingFlow.value)
        prefs.hasCompletedOnboarding.put(true)
        assertTrue(prefs.hasCompletedOnboardingFlow.value)

        // Built-in presets default to IzzyOnDroid enabled, official F-Droid & NewPipe disabled
        val expectedBuiltinCount = FDroidRepoConfig.defaultPresets().size
        assertEquals(5, expectedBuiltinCount)
        val initialRepos = prefs.readFDroidReposSafe()
        assertEquals(expectedBuiltinCount, initialRepos.size)
        assertTrue(initialRepos.first { it.id == FDroidRepoConfig.ID_IZZYONDROID }.enabled)
        assertFalse(initialRepos.first { it.id == FDroidRepoConfig.ID_FDROID_OFFICIAL }.enabled)
        assertFalse(initialRepos.first { it.id == FDroidRepoConfig.ID_NEWPIPE }.enabled)

        // Add custom repo and reject duplicate normalized URL
        val customRepo = FDroidRepoConfig.parseShareInput(
            rawUrl = "https://calyxos.gitlab.io/calyx-fdroid-repo/fdroid/repo?fingerprint=${"D".repeat(64)}",
            explicitName = "CalyxOS"
        ).getOrThrow()
        assertTrue(prefs.addCustomFDroidRepo(customRepo))
        assertFalse("Duplicate repo URL must be rejected", prefs.addCustomFDroidRepo(customRepo))
        assertEquals(expectedBuiltinCount + 1, prefs.readFDroidReposSafe().size)

        // Anti-rollback timestamp must only advance monotonically
        prefs.recordFDroidRepoTimestamp(customRepo.id, 1_700_000_000_000L)
        prefs.recordFDroidRepoTimestamp(customRepo.id, 1_600_000_000_000L) // Stale replay attempt
        assertEquals(1_700_000_000_000L, prefs.getFDroidRepoTimestamp(customRepo.id))

        // Removing custom repo cleans up both repo list and stored timestamp
        prefs.removeCustomFDroidRepo(customRepo.id)
        assertEquals(expectedBuiltinCount, prefs.readFDroidReposSafe().size)
        assertEquals(0L, prefs.getFDroidRepoTimestamp(customRepo.id))
    }

    // =========================================================================
    // 3. SSRF & REPO HOST VALIDATION (F-04a, F-04b, HIGH-03, HIGH-04)
    // =========================================================================

    @Test
    fun `RepoHostValidator blocks loopback, cloud metadata, and obfuscated IPs while gating LAN by flag`() {
        // Always forbidden regardless of allowLanHosts
        val alwaysBlocked = listOf(
            "localhost",
            "api.localhost",
            "127.0.0.1",
            "127.255.255.254",
            "0.0.0.0",
            "169.254.169.254", // AWS/GCP/Azure metadata endpoint
            "100.64.0.1",
            "224.0.0.1",
            "::1",
            "[::1]",
            "::ffff:127.0.0.1",
            "fe80::1",
            "0x7f000001",
            "2130706433"
        )
        for (host in alwaysBlocked) {
            assertFalse("Host '$host' must be blocked (allowLan=false)", RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = false))
            assertFalse("Host '$host' must be blocked (allowLan=true)", RepoHostValidator.isAllowedRepoHost(host, allowLanHosts = true))
        }

        // RFC1918 / .local LAN hosts blocked for automated imports/harvesting, allowed for manual user dialog
        val lanHosts = listOf("192.168.1.25", "10.0.0.5", "172.20.10.2", "gitlab.local", "fd00::1234")
        for (lan in lanHosts) {
            assertFalse("LAN host '$lan' must be blocked when allowLanHosts=false", RepoHostValidator.isAllowedRepoHost(lan, allowLanHosts = false))
            assertTrue("LAN host '$lan' must be allowed when allowLanHosts=true", RepoHostValidator.isAllowedRepoHost(lan, allowLanHosts = true))
        }

        // Public forge hosts allowed
        assertTrue(RepoHostValidator.isAllowedRepoHost("github.com", allowLanHosts = false))
        assertTrue(RepoHostValidator.isAllowedRepoHost("invent.kde.org", allowLanHosts = false))

        // Passive harvesting restricted strictly to HTTPS trusted forges
        val prefs = Prefs(InMemoryPrefsBackend())
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = gson)
        manager.registerHarvestedRepo("org.example.good", "https://github.com/example/good")
        manager.registerHarvestedRepo("org.example.http", "http://github.com/example/http")
        manager.registerHarvestedRepo("org.example.untrusted", "https://evil-forge.example.com/example/bad")

        assertNotNull(manager.getBinding("org.example.good"))
        assertNull(manager.getBinding("org.example.http"))
        assertNull(manager.getBinding("org.example.untrusted"))
    }

    // =========================================================================
    // 4. OBTAINIUM IMPORT HARDENING & F-DROID/IZZY AUTO-ACTIVATION (F-03, F-04b)
    // =========================================================================

    @Test
    fun `ObtainiumManager activates F-Droid and IzzyOnDroid repos and skips unsupported domains without hijacking`() {
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            useIzzyOnDroid.put(false)
            setFDroidRepoEnabled(FDroidRepoConfig.ID_IZZYONDROID, false)
            setFDroidRepoEnabled(FDroidRepoConfig.ID_FDROID_OFFICIAL, false)
        }
        val manager = RepoMappingManager(context = null, prefs = prefs, gson = gson)

        val payload = """
            [
              {"id": "org.schabi.newpipe", "url": "https://github.com/TeamNewPipe/NewPipe"},
              {"id": "org.fdroid.fdroid", "url": "https://f-droid.org/packages/org.fdroid.fdroid/"},
              {"id": "de.tutao.tutanota", "url": "https://apt.izzysoft.de/fdroid/index/apk/de.tutao.tutanota"},
              {"id": "com.unsupported.store", "url": "https://apkpure.com/some-app/com.unsupported.store"},
              {"id": "com.ssrf.attempt", "url": "https://192.168.1.10/root/internal/-/releases"}
            ]
        """.trimIndent()

        val result = ObtainiumManager.importObtainium(payload, repoMappingManager = manager, prefs = prefs)

        assertEquals(5, result.totalEntries)
        assertEquals(3, result.importedBindingsCount)
        assertEquals(2, result.skippedCount)
        assertEquals(1, result.detectedFDroidCount)
        assertEquals(1, result.detectedIzzyCount)
        assertTrue(result.unsupportedDomains.contains("apkpure.com"))
        assertTrue(result.unsupportedDomains.contains("192.168.1.10"))

        // Unsupported domains must NEVER be hijacked into GitHub bindings
        assertNull(manager.getCustomBinding("com.unsupported.store"))
        assertNull(manager.getCustomBinding("com.ssrf.attempt"))
        assertNotNull(manager.getCustomBinding("org.schabi.newpipe"))

        // F-Droid and IzzyOnDroid were automatically enabled
        assertTrue(prefs.useIzzyOnDroid.get())
        assertTrue(prefs.readFDroidReposSafe().first { it.id == FDroidRepoConfig.ID_FDROID_OFFICIAL }.enabled)
        assertTrue(prefs.readFDroidReposSafe().first { it.id == FDroidRepoConfig.ID_IZZYONDROID }.enabled)
    }

    // =========================================================================
    // 5. CURATED KNOWN_REPOS.JSON INTEGRITY (F-09)
    // =========================================================================

    @Test
    fun `known_repos json asset contains zero duplicate package names and only valid forge bindings`() {
        val assetFile = File("src/main/assets/repos/known_repos.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/repos/known_repos.json")
        assertTrue("known_repos.json must exist at ${assetFile.path}", assetFile.isFile)

        val entries: List<KnownRepoEntry> = gson.fromJson(
            assetFile.readText(Charsets.UTF_8),
            object : TypeToken<List<KnownRepoEntry>>() {}.type
        )
        assertTrue("known_repos.json must not be empty", entries.isNotEmpty())

        val seenPackages = HashSet<String>(entries.size)
        for (entry in entries) {
            assertTrue(
                "Invalid package name in known_repos.json: '${entry.`package`}'",
                ApkIntegrityVerifier.isValidPackageName(entry.`package`)
            )
            assertTrue(
                "Duplicate package in known_repos.json: '${entry.`package`}'",
                seenPackages.add(entry.`package`)
            )
            val normalizedType = entry.type.lowercase(Locale.ROOT)
            assertTrue(
                "Unsupported forge type '${entry.type}' for ${entry.`package`}",
                normalizedType == "github" || normalizedType == "gitlab"
            )
            val segments = entry.repo.split('/').filter { it.isNotBlank() }
            assertTrue(
                "Invalid repo slug '${entry.repo}' for ${entry.`package`}",
                RepoHostValidator.isValidRepoSlugSegments(segments)
            )
            if (!entry.host.isNullOrBlank()) {
                assertTrue(
                    "Invalid custom host '${entry.host}' for ${entry.`package`}",
                    RepoHostValidator.isAllowedRepoHost(entry.host, allowLanHosts = false)
                )
            }
        }
    }

    // =========================================================================
    // 6. F-DROID SHARE URL PARSER, UNSIGNED JAR REJECTION & STREAMING INDEX (F-02, F-23b, CRIT-02)
    // =========================================================================

    @Test
    fun `FDroidRepoConfig parseShareInput enforces HTTPS and valid 64-char SHA-256 fingerprints`() {
        val validFp = "a1b2c3d4".repeat(8)
        val parsed = FDroidRepoConfig.parseShareInput(
            "https://repo.example.org/fdroid/repo/?fingerprint=$validFp#section"
        ).getOrThrow()
        assertEquals("https://repo.example.org/fdroid/repo", parsed.url)
        assertEquals(validFp.uppercase(Locale.ROOT), parsed.fingerprint)

        // Official & built-in preset URLs auto-fill pinned fingerprint even when omitted
        val autoFilled = FDroidRepoConfig.parseShareInput("https://f-droid.org/repo/").getOrThrow()
        assertEquals(FDroidRepoConfig.FDROID_OFFICIAL_FINGERPRINT, autoFilled.fingerprint)
        val newPipeFilled = FDroidRepoConfig.parseShareInput("https://archive.newpipe.net/fdroid/repo").getOrThrow()
        assertEquals(FDroidRepoConfig.NEWPIPE_FINGERPRINT, newPipeFilled.fingerprint)

        // Optional fingerprint on unknown HTTPS repos returns blank (for TOFU on first verified fetch);
        // strict mode rejects omitted fingerprints; malformed non-empty fingerprints, HTTP, and credentials fail.
        val unpinnedTofu = FDroidRepoConfig.parseShareInput("https://unknown.example.org/fdroid/repo").getOrThrow()
        assertEquals("", unpinnedTofu.fingerprint)
        assertTrue(
            FDroidRepoConfig.parseShareInput(
                "https://unknown.example.org/fdroid/repo",
                requireFingerprint = true
            ).isFailure
        )
        assertTrue(FDroidRepoConfig.parseShareInput("https://unknown.example.org/fdroid/repo?fingerprint=badhex").isFailure)
        assertTrue(FDroidRepoConfig.parseShareInput("http://repo.example.org/fdroid/repo?fingerprint=$validFp").isFailure)
        assertTrue(FDroidRepoConfig.parseShareInput("https://user:pass@repo.example.org/fdroid/repo?fingerprint=$validFp").isFailure)
    }

    @Test
    fun `FDroidIndexEngine verifies signed entry jar when entry json precedes META-INF manifest and rejects tampered bytes`() {
        val workDir = tempFolder.newFolder("reordered_entry_jar")
        val javaHomeBin = File(System.getProperty("java.home"), "bin")
        val isWindows = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")
        val keytool = File(javaHomeBin, if (isWindows) "keytool.exe" else "keytool")
        val jarsigner = File(javaHomeBin, if (isWindows) "jarsigner.exe" else "jarsigner")
        val ksFile = File(workDir, "izzy-repro.p12")

        val genProc = ProcessBuilder(
            keytool.absolutePath,
            "-genkeypair",
            "-alias", "nebo",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "365",
            "-dname", "CN=nebo, OU=F-Droid",
            "-keystore", ksFile.absolutePath,
            "-storepass", "changeit",
            "-keypass", "changeit"
        ).redirectErrorStream(true).start()
        val genOut = genProc.inputStream.bufferedReader().readText()
        assertEquals("keytool failed: $genOut", 0, genProc.waitFor())

        val ks = java.security.KeyStore.getInstance(ksFile, "changeit".toCharArray())
        val expectedFp = java.security.MessageDigest.getInstance("SHA-256")
            .digest(ks.getCertificate("nebo").encoded)
            .joinToString("") { "%02X".format(it) }

        val rawEntryJson = """{"timestamp":1760000000000,"version":20002,"index":{"name":"/index-v2.json","sha256":"${"a".repeat(64)}"}}"""
        val signedJar = File(workDir, "entry-signed.jar")
        JarOutputStream(signedJar.outputStream()).use { jos ->
            jos.putNextEntry(JarEntry("entry.json"))
            jos.write(rawEntryJson.toByteArray(Charsets.UTF_8))
            jos.closeEntry()
        }
        val signProc = ProcessBuilder(
            jarsigner.absolutePath,
            "-keystore", ksFile.absolutePath,
            "-storepass", "changeit",
            "-keypass", "changeit",
            signedJar.absolutePath,
            "nebo"
        ).redirectErrorStream(true).start()
        val signOut = signProc.inputStream.bufferedReader().readText()
        assertEquals("jarsigner failed: $signOut", 0, signProc.waitFor())

        // Reorder ZIP entries to match IzzyOnDroid's server layout: entry.json BEFORE META-INF/*
        val entriesMap = LinkedHashMap<String, ByteArray>()
        java.util.zip.ZipFile(signedJar).use { zf ->
            val e = zf.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                entriesMap[entry.name] = zf.getInputStream(entry).readBytes()
            }
        }
        fun buildReorderedJar(tamperEntryJson: Boolean): ByteArray {
            val out = ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zos ->
                val order = listOf("entry.json") + entriesMap.keys.filter { it != "entry.json" }
                for (name in order) {
                    val bytes = entriesMap[name] ?: continue
                    zos.putNextEntry(java.util.zip.ZipEntry(name))
                    if (tamperEntryJson && name == "entry.json") {
                        val copy = bytes.copyOf()
                        copy[copy.indexOf('2'.code.toByte())] = '9'.code.toByte()
                        zos.write(copy)
                    } else {
                        zos.write(bytes)
                    }
                    zos.closeEntry()
                }
            }
            return out.toByteArray()
        }

        val validReorderedBytes = buildReorderedJar(tamperEntryJson = false)
        val verified = FDroidIndexEngine.verifyAndExtractEntryJson(
            jarStream = ByteArrayInputStream(validReorderedBytes),
            expectedCertSha256 = expectedFp,
            gson = gson,
            tempDir = workDir
        )
        assertEquals(1760000000000L, verified.payload.timestamp)
        assertEquals(expectedFp, verified.signerSha256Fingerprint)

        // Tampering 1 byte inside entry.json must fail closed with SecurityException
        val tamperedBytes = buildReorderedJar(tamperEntryJson = true)
        assertThrows(SecurityException::class.java) {
            FDroidIndexEngine.verifyAndExtractEntryJson(
                jarStream = ByteArrayInputStream(tamperedBytes),
                expectedCertSha256 = expectedFp,
                gson = gson,
                tempDir = workDir
            )
        }
    }

    @Test
    fun `FDroidIndexEngine rejects unsigned entry jar and streams V2 index selectively for installed apps`() {
        // 1. Unsigned entry.jar must throw SecurityException
        val unsignedJarBytes = ByteArrayOutputStream().also { baos ->
            JarOutputStream(baos, Manifest()).use { jos ->
                jos.putNextEntry(JarEntry("entry.json"))
                jos.write("""{"timestamp":1730000000000,"version":20002}""".toByteArray(Charsets.UTF_8))
                jos.closeEntry()
            }
        }.toByteArray()

        assertThrows(SecurityException::class.java) {
            FDroidIndexEngine.verifyAndExtractEntryJson(
                jarStream = ByteArrayInputStream(unsignedJarBytes),
                expectedCertSha256 = FDroidRepoConfig.IZZY_FINGERPRINT,
                gson = gson
            )
        }

        // 2. Streaming V2 parser materializes ONLY installed packages
        val v2File = tempFolder.newFile("index-v2.json").apply {
            writeText(
                """
                {
                  "repo": {"timestamp": 1735000000000, "name": {"en-US": "Test Repo"}},
                  "packages": {
                    "org.installed.app": {
                      "metadata": {"name": {"en-US": "Installed App"}, "summary": {"en-US": "Kept"}},
                      "versions": {
                        "v1": {
                          "file": {"name": "/org.installed.app_10.apk", "sha256": "${"a".repeat(64)}", "size": 1024},
                          "manifest": {"versionName": "1.0.0", "versionCode": 10}
                        }
                      }
                    },
                    "org.uninstalled.bloat": {
                      "metadata": {"name": {"en-US": "Ignored Bloat"}},
                      "versions": {}
                    }
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val filtered = FDroidIndexEngine.streamParseV2IndexFile(
            file = v2File,
            installedPackages = setOf("org.installed.app"),
            gson = gson,
            preferredLocale = Locale.US
        )
        assertEquals(1, filtered.packages.size)
        assertTrue(filtered.packages.containsKey("org.installed.app"))
        assertFalse(filtered.packages.containsKey("org.uninstalled.bloat"))
    }

    // =========================================================================
    // 7. SELF-UPDATE RELEASE SELECTION & SHA-256 DIGEST EXTRACTION (F-06, CRIT-06)
    // =========================================================================

    @Test
    fun `SelfUpdateRepository skips drafts and untrusted hosts and extracts GitHub SHA-256 asset digest`() = runBlocking {
        val expectedDigest = "f".repeat(64)
        val releasesJson = """
            [
              {
                "tag_name": "v1.50.0",
                "name": "Draft Release",
                "draft": true,
                "prerelease": false,
                "body": "Should be ignored",
                "assets": [
                  {
                    "name": "ApkUp-v1.50.0.apk",
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v1.50.0/ApkUp-v1.50.0.apk",
                    "size": 5000000
                  }
                ]
              },
              {
                "tag_name": "v1.45.0",
                "name": "Evil Host Release",
                "draft": false,
                "prerelease": false,
                "body": "Untrusted asset domain",
                "assets": [
                  {
                    "name": "ApkUp-v1.45.0.apk",
                    "browser_download_url": "https://evil.example.com/ApkUp-v1.45.0.apk",
                    "size": 5000000
                  }
                ]
              },
              {
                "tag_name": "v1.42.0",
                "name": "v1.42.0",
                "draft": false,
                "prerelease": false,
                "body": "Stable signed release",
                "assets": [
                  {
                    "name": "ApkUp-v1.42.0.apk",
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v1.42.0/ApkUp-v1.42.0.apk",
                    "size": 8123456,
                    "digest": "sha256:$expectedDigest"
                  }
                ]
              }
            ]
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(releasesJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val repo = SelfUpdateRepository(mockClient, gson)
        // Upgrading from legacy "1.41.0-Beta" to clean "v1.42.0" (F-06)
        val info = repo.checkForUpdate(currentVersionName = "1.41.0-Beta").getOrThrow()
        assertNotNull(info)
        assertEquals("1.42.0", info!!.versionName)
        assertEquals(expectedDigest, info.expectedSha256)

        // Same version stable > pre-release (1.41.0 > 1.41.0-Beta)
        assertTrue(repo.isNewerVersion("1.41.0-Beta", "v1.41.0"))
        assertFalse(repo.isNewerVersion("1.41.0", "v1.41.0-Beta"))
    }

    // =========================================================================
    // 8. APKMIRROR AUTH HOST ISOLATION & KEYLESS SEARCH PARSER (F-11b, F-18b, HIGH-02)
    // =========================================================================

    @Test
    fun `ApkMirrorAuthInterceptor restricts Basic Auth strictly to HTTPS apkmirror hosts and honors custom keys`() {
        val prefs = Prefs(InMemoryPrefsBackend()).apply {
            apkMirrorCustomApiUser.put("custom-user")
            apkMirrorCustomApiKey.put("custom-secret")
        }
        var capturedAuthHeader: String? = null
        var capturedUserAgent: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(ApkMirrorAuthInterceptor(prefs))
            .addInterceptor { chain ->
                capturedAuthHeader = chain.request().header("Authorization")
                capturedUserAgent = chain.request().header("User-Agent")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        // 1. HTTPS www.apkmirror.com receives custom Basic Auth + honest ApkUp User-Agent
        client.newCall(Request.Builder().url("https://www.apkmirror.com/wp-json/apkm/v1/app_exists/").build()).execute().close()
        assertEquals(Credentials.basic("custom-user", "custom-secret"), capturedAuthHeader)
        assertTrue(capturedUserAgent.orEmpty().startsWith("ApkUp/"))

        // 2. Third-party host MUST NOT receive APKMirror Basic Auth
        capturedAuthHeader = null
        client.newCall(Request.Builder().url("https://cdn.unrelated-domain.org/wp-json/apkm/v1/app_exists/").build()).execute().close()
        assertNull("Auth header must never leak to third-party hosts", capturedAuthHeader)

        // 3. Cleartext HTTP apkmirror.com MUST NOT receive Basic Auth
        capturedAuthHeader = null
        client.newCall(Request.Builder().url("http://www.apkmirror.com/wp-json/apkm/v1/app_exists/").build()).execute().close()
        assertNull("Auth header must never be sent over cleartext HTTP", capturedAuthHeader)
    }

    @Test
    fun `ApkMirrorRepository parseKeylessSearchHtml extracts newer stable releases and skips betas`() {
        val installed = AppInstalled(
            name = "Signal",
            packageName = "org.thoughtcrime.securesms",
            version = "7.20.0",
            versionCode = 7200L,
            installerPackageName = "com.apkmirror.helper.prod"
        )
        val searchHtml = """
            <html><body>
              <div class="appRow">
                <h5 class="appRowTitle">
                  <a title="Signal 7.22.0 beta" href="/apk/signal-foundation/signal/signal-7-22-0-beta-release/">Signal 7.22.0 beta</a>
                </h5>
              </div>
              <div class="appRow">
                <h5 class="appRowTitle">
                  <a title="Signal 7.21.2" href="/apk/signal-foundation/signal/signal-7-21-2-release/">Signal 7.21.2</a>
                </h5>
              </div>
            </body></html>
        """.trimIndent()

        val parsed = ApkMirrorRepository.parseKeylessSearchHtml(searchHtml, installed)
        assertNotNull(parsed)
        assertEquals("7.21.2", parsed!!.version)
        assertEquals("https://www.apkmirror.com/apk/signal-foundation/signal/signal-7-21-2-release/", (parsed.link as Link.Url).link)
    }

    // =========================================================================
    // 9. SOURCE PRIORITY TIE-BREAKING & PLAY LOCALE NORMALIZATION (F-14, F-15)
    // =========================================================================

    @Test
    fun `UpdatesRepository prioritizes custom bindings and origin-matched sources above APKMirror`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val repoManager = RepoMappingManager(context = null, prefs = prefs, gson = gson)
        repoManager.setCustomBinding("org.custom.app", RepoBinding("gitlab", "custom/app"))

        fun sample(pkg: String, source: com.apkup.data.ui.Source, origin: InstallOrigin = InstallOrigin.SYSTEM) = AppUpdate(
            name = "Sample",
            packageName = pkg,
            version = "2.0.0",
            oldVersion = "1.0.0",
            versionCode = 200L,
            oldVersionCode = 100L,
            source = source,
            installOrigin = origin
        )

        // Default tie-breaker order: Play > GitHub > F-Droid/Izzy > GitLab > APKMirror
        assertTrue(defaultSourcePriority(sample("a.b", PlaySource)) > defaultSourcePriority(sample("a.b", GitHubSource)))
        assertTrue(defaultSourcePriority(sample("a.b", GitHubSource)) > defaultSourcePriority(sample("a.b", FDroidSource)))
        assertEquals(defaultSourcePriority(sample("a.b", FDroidSource)), defaultSourcePriority(sample("a.b", IzzyOnDroidSource)))
        assertTrue(defaultSourcePriority(sample("a.b", FDroidSource)) > defaultSourcePriority(sample("a.b", GitLabSource)))
        assertTrue(defaultSourcePriority(sample("a.b", GitLabSource)) > defaultSourcePriority(sample("a.b", ApkMirrorSource)))

        // Matching InstallOrigin boosts F-Droid above Play for F-Droid-installed apps
        val fdroidOriginUpdate = sample("org.fdroid.app", FDroidSource, InstallOrigin.FDROID)
        val playCandidateForSameApp = sample("org.fdroid.app", PlaySource, InstallOrigin.FDROID)
        assertTrue(computeUpdateSourcePriority(fdroidOriginUpdate, repoManager) > computeUpdateSourcePriority(playCandidateForSameApp, repoManager))

        // Explicit user custom repo binding beats everything (score 100)
        val customGitLab = sample("org.custom.app", GitLabSource, InstallOrigin.PLAY_STORE)
        val playOrigin = sample("org.custom.app", PlaySource, InstallOrigin.PLAY_STORE)
        assertEquals(100, computeUpdateSourcePriority(customGitLab, repoManager))
        assertTrue(computeUpdateSourcePriority(customGitLab, repoManager) > computeUpdateSourcePriority(playOrigin, repoManager))

        // Play Store active locale normalization (F-15)
        val localeEs = resolveActivePlayStoreLocale(Locale.forLanguageTag("es"))
        assertEquals("es", localeEs.language)
        assertEquals("ES", localeEs.country)

        val localeJa = resolveActivePlayStoreLocale(Locale.JAPANESE)
        assertEquals("ja", localeJa.language)
        assertEquals("JP", localeJa.country)
    }
}
