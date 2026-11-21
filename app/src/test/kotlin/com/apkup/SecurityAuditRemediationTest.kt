package com.apkup

import android.content.ContextWrapper
import com.apkup.repository.SelfUpdateRepository
import com.apkup.util.AppJournal
import com.apkup.util.AppWatcherImporter
import com.apkup.util.Downloader
import com.apkup.util.InstallPart
import com.apkup.util.RollbackBackupManager
import com.apkup.util.RootCommandOutput
import com.apkup.util.RootInstaller
import com.apkup.util.RootShellExecutor
import com.apkup.util.isSafeHttpsUrl
import com.apkup.util.readUtf8Bounded
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
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
import java.io.File
import java.io.IOException

class SecurityAuditRemediationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `SEC-02 RollbackBackupManager blocks path traversal package names`() {
        val filesDir = tempFolder.newFolder("app_files")
        val sentinelFile = File(filesDir, "important_prefs.json").apply { writeText("keep-me") }
        val backupRoot = File(filesDir, "rollback_backups").apply { mkdirs() }
        val validBackup = File(backupRoot, "com.example.ok").apply {
            mkdirs()
            File(this, "base.apk").writeBytes("apk".toByteArray())
            File(this, "meta.json").writeText(
                """{"packageName":"com.example.ok","appName":"Ok","versionName":"1.0","versionCode":1,"backedUpAtMillis":1,"totalSizeBytes":3,"partCount":1}"""
            )
        }

        val fakeContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
        }
        val manager = RollbackBackupManager(fakeContext)

        assertNotNull(manager.getBackupMeta("com.example.ok"))
        assertTrue(validBackup.exists())

        val traversalInputs = listOf("", " ", ".", "..", "...", "../", "com.evil/../../files", "..\\..")
        for (bad in traversalInputs) {
            assertNull("Expected null meta for '$bad'", manager.getBackupMeta(bad))
            assertNull("Expected null target for '$bad'", manager.getBackupTargetFileOrDir(bad))
            assertFalse("Expected false deleteBackup for '$bad'", manager.deleteBackup(bad))
            assertNull("Expected null backup creation for '$bad'", manager.backupInstalledPackageBeforeUpdate(bad))
        }

        // Neither filesDir nor valid siblings must ever be touched
        assertTrue(sentinelFile.exists())
        assertTrue(validBackup.exists())
    }

    @Test
    fun `SEC-03 RootInstaller executeCleanRootRollback never runs pm uninstall on empty or unverified stream parts`() {
        val commands = mutableListOf<String>()
        val fakeExecutor = object : RootShellExecutor {
            override fun runCommand(command: String): RootCommandOutput {
                commands += command
                return RootCommandOutput(0, "Success")
            }

            override fun pipeToCommand(command: String, input: java.io.InputStream): RootCommandOutput {
                commands += command
                return RootCommandOutput(0, "Success")
            }
        }
        val installer = RootInstaller(shellExecutor = fakeExecutor)

        val emptyResult = installer.executeCleanRootRollback("com.example.app", emptyList())
        assertFalse(emptyResult.success)
        assertTrue("pm uninstall must not run for empty parts", commands.isEmpty())

        val tamperedStreamPart = InstallPart(
            name = "base.apk",
            sizeBytes = 7L,
            expectedSha256 = "f".repeat(64),
            backingFile = null,
            openStream = { ByteArrayInputStream("INVALID".toByteArray()) }
        )
        val tamperedResult = installer.executeCleanRootRollback("com.example.app", listOf(tamperedStreamPart))
        assertFalse(tamperedResult.success)
        assertTrue("pm uninstall must not run when stream-only part fails SHA-256/APK verification", commands.isEmpty())
    }

    @Test
    fun `BUG-02 Downloader downloadStream unregisters active call when execute throws`() {
        val failingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { _ ->
                throw IOException("Simulated network timeout")
            })
            .build()
        val downloader = Downloader(failingClient, failingClient)
        val tag = "stream_err_tag"

        val result = downloader.downloadStream("https://example.com/app.apk", callTag = tag)
        assertNull(result)
        assertFalse("Call tag must be unregistered after execute() exception", downloader.isCallActive(tag))
    }

    @Test
    fun `SEC-05 SelfUpdateRepository prefers exact sha256 sidecar and handles chunked stream reads`() = runBlocking {
        val expectedHash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val wrongHash = "1111111111111111111111111111111111111111111111111111111111111111"
        val releasesJson = """
            [
              {
                "tag_name": "v2.0.0",
                "name": "ApkUp 2.0.0",
                "body": "Release notes",
                "prerelease": false,
                "draft": false,
                "assets": [
                  {
                    "name": "other-flavor.apk.sha256",
                    "size": 90,
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v2.0.0/other-flavor.apk.sha256"
                  },
                  {
                    "name": "ApkUp-v2.0.0.apk",
                    "size": 1024000,
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v2.0.0/ApkUp-v2.0.0.apk"
                  },
                  {
                    "name": "ApkUp-v2.0.0.apk.sha256",
                    "size": 90,
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v2.0.0/ApkUp-v2.0.0.apk.sha256"
                  }
                ]
              }
            ]
        """.trimIndent()

        // Returns at most 7 bytes per read() to verify multi-chunk loop reads
        fun chunkedBody(content: String): ResponseBody {
            val raw = content.toByteArray(Charsets.UTF_8)
            val source = object : ForwardingSource(Buffer().write(raw)) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    return super.read(sink, minOf(byteCount, 7L))
                }
            }.buffer()
            return object : ResponseBody() {
                override fun contentType() = "text/plain".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = source
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("api.github.com") ->
                        releasesJson.toResponseBody("application/json".toMediaType())
                    url.endsWith("ApkUp-v2.0.0.apk.sha256") ->
                        chunkedBody("$expectedHash  ApkUp-v2.0.0.apk\n")
                    url.endsWith("other-flavor.apk.sha256") ->
                        chunkedBody("$wrongHash  other-flavor.apk\n")
                    else -> "".toResponseBody("text/plain".toMediaType())
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val repo = SelfUpdateRepository(client, Gson())
        val info = repo.checkForUpdate(currentVersionName = "1.45.1 (215)").getOrThrow()
        assertNotNull(info)
        assertEquals(expectedHash, info!!.expectedSha256)
    }

    @Test
    fun `readUtf8Bounded reads multi-chunk input and throws when exceeding maxBytes`() {
        val chunkedStream = object : java.io.InputStream() {
            private val data = "0123456789abcdef".toByteArray(Charsets.UTF_8)
            private var idx = 0
            override fun read(): Int = if (idx < data.size) data[idx++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (idx >= data.size) return -1
                val n = minOf(len, 3, data.size - idx)
                System.arraycopy(data, idx, b, off, n)
                idx += n
                return n
            }
        }
        assertEquals("0123456789abcdef", readUtf8Bounded(chunkedStream, maxBytes = 16))

        assertThrows(IOException::class.java) {
            readUtf8Bounded(ByteArrayInputStream("0123456789abcdefX".toByteArray()), maxBytes = 16)
        }
    }

    @Test
    fun `SEC-06 isSafeHttpsUrl accepts only valid HTTPS URLs with non-blank hosts`() {
        assertTrue(isSafeHttpsUrl("https://github.com/DurchEins/apkup"))
        assertTrue(isSafeHttpsUrl("  HTTPS://www.apkmirror.com/apk/test  "))
        assertFalse(isSafeHttpsUrl(""))
        assertFalse(isSafeHttpsUrl("http://github.com/DurchEins/apkup"))
        assertFalse(isSafeHttpsUrl("javascript:alert(1)"))
        assertFalse(isSafeHttpsUrl("intent://scan/#Intent;end"))
        assertFalse(isSafeHttpsUrl("file:///sdcard/evil.apk"))
        assertFalse(isSafeHttpsUrl("https://"))
    }

    @Test
    fun `SEC-07 AppJournal redacts cookies and URL userinfo credentials`() {
        val journal = AppJournal(journalFile = null, maxEntries = 10)
        journal.log(
            "Network",
            "Failed https://alice:superSecretPass@git.example.com/repo.git with Cookie: cf_clearance=secretCookie123; session=xyz and Set-Cookie: sid=topSecret999"
        )
        val entry = journal.entries().single()
        assertFalse(entry.contains("superSecretPass"))
        assertFalse(entry.contains("secretCookie123"))
        assertFalse(entry.contains("topSecret999"))
        assertTrue(entry.contains("https://[REDACTED]@git.example.com/repo.git"))
        assertTrue(entry.contains("Cookie: [REDACTED]"))
        assertTrue(entry.contains("Set-Cookie: [REDACTED]"))
    }

    @Test
    fun `BUG-05 AppWatcherImporter imports valid JSON cleanly`() {
        val raw = """
            {
              "apps": [
                {
                  "packageName": "org.example.app",
                  "title": "Example App",
                  "versionName": "1.2.3",
                  "versionCode": "123"
                }
              ]
            }
        """.trimIndent()
        val summary = AppWatcherImporter.parseExportJson(
            rawJson = raw,
            installedPackages = emptySet(),
            existingWatched = emptyList()
        )
        assertNotNull(summary)
        assertEquals(1, summary!!.addedEntries.size)
        assertEquals("org.example.app", summary.addedEntries.single().packageName)
        assertEquals(0, summary.alreadyWatchedCount)
    }

    @Test
    fun `SEC-07 AppJournal redacts single-token URL userinfo and multi-clause cookies with colons`() {
        val journal = AppJournal(journalFile = null, maxEntries = 10)
        journal.log(
            "Network",
            "Clone https://ghp_singleTokenSecret99@github.com/org/repo.git with Cookie: session=abc; note: foo; secret=xyz\nX-Trace: safe"
        )
        journal.log(
            "UI",
            "Visit https://example.com/@handle?email=user@domain.com#frag@1"
        )
        val entries = journal.entries()
        assertEquals(2, entries.size)
        val netEntry = entries[0]
        assertFalse(netEntry.contains("ghp_singleTokenSecret99"))
        assertFalse(netEntry.contains("session=abc"))
        assertFalse(netEntry.contains("secret=xyz"))
        assertTrue(netEntry.contains("https://[REDACTED]@github.com/org/repo.git"))
        assertTrue(netEntry.contains("Cookie: [REDACTED]"))
        assertTrue(netEntry.contains("X-Trace: safe"))

        val uiEntry = entries[1]
        assertTrue(uiEntry.contains("https://example.com/@handle?email=user@domain.com#frag@1"))
    }

    @Test
    fun `SEC-03 ApkIntegrityVerifier uniqueSafePartFileName disambiguates colliding part names`() {
        val used = HashSet<String>()
        val first = com.apkup.util.ApkIntegrityVerifier.uniqueSafePartFileName("../split.apk", 0, used)
        val second = com.apkup.util.ApkIntegrityVerifier.uniqueSafePartFileName("split.apk", 1, used)
        val third = com.apkup.util.ApkIntegrityVerifier.uniqueSafePartFileName("SPLIT.apk", 2, used)
        val fourth = com.apkup.util.ApkIntegrityVerifier.uniqueSafePartFileName("SPLIT.APK", 3, used)
        assertEquals("split.apk", first)
        assertEquals("split_1.apk", second)
        assertEquals("SPLIT_2.apk", third)
        assertEquals("split_3.apk", fourth)
        assertEquals(4, setOf(first.lowercase(), second.lowercase(), third.lowercase(), fourth.lowercase()).size)
    }

    @Test
    fun `SEC-M03 ReleaseAssetFilter blocks nested quantifier ReDoS patterns while keeping valid filters`() {
        assertFalse(com.apkup.data.repomapping.ReleaseAssetFilter.isValidRegex("(a+)+$"))
        assertFalse(com.apkup.data.repomapping.ReleaseAssetFilter.isValidRegex("(.*)*\\.apk$"))
        assertFalse(com.apkup.data.repomapping.ReleaseAssetFilter.isValidRegex("([a-z0-9]+){2,}"))
        assertNull(
            com.apkup.data.repomapping.ReleaseAssetFilter.findBestApk(
                assets = listOf("aaaaaaaaaaaaaaaaaaaaaaaaa!.apk"),
                getFilename = { it },
                supportedAbis = arrayOf("arm64-v8a"),
                assetFilterRegex = "(a+)+$"
            )
        )

        assertTrue(com.apkup.data.repomapping.ReleaseAssetFilter.isValidRegex("^(?!.*debug).*\\.apk$"))
        assertTrue(com.apkup.data.repomapping.ReleaseAssetFilter.isValidRegex(".*(arm64|universal).*\\.apk$"))
        assertTrue(com.apkup.data.repomapping.ReleaseAssetFilter.isValidRegex("(?:[a-z]+)\\.apk"))
        assertEquals(
            "app-arm64-release.apk",
            com.apkup.data.repomapping.ReleaseAssetFilter.findBestApk(
                assets = listOf("app-arm64-debug.apk", "app-arm64-release.apk"),
                getFilename = { it },
                supportedAbis = arrayOf("arm64-v8a"),
                assetFilterRegex = "^(?!.*debug).*\\.apk$"
            )
        )
    }
}
