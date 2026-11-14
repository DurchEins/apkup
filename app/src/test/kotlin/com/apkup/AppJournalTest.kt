package com.apkup

import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.util.AppJournal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory

class AppJournalTest {

    @Test
    fun testRingBufferPersistsAndCapsAtMaxEntries() {
        val tempDir = createTempDirectory("apkup_journal_test").toFile()
        try {
            val file = File(tempDir, AppJournal.JOURNAL_FILENAME)
            var now = 1_700_000_000_000L
            val journal = AppJournal(
                journalFile = file,
                maxEntries = 5,
                timeProvider = { now++ },
                persistDebounceMs = 5_000L
            )

            repeat(8) { idx ->
                journal.log("Updates", "Check iteration #$idx")
            }

            // Debounced async persistence must not write synchronously on caller thread
            assertFalse(file.exists())

            journal.flush()
            assertTrue(file.exists())

            val entries = journal.entries()
            assertEquals(5, entries.size)
            assertFalse(entries.any { it.contains("#0") || it.contains("#2") })
            assertTrue(entries.first().contains("#3"))
            assertTrue(entries.last().contains("#7"))

            // Reload from disk in fresh instance
            val reloaded = AppJournal(journalFile = file, maxEntries = 5)
            assertEquals(entries, reloaded.entries())

            // Clear + flush deletes file
            journal.clear()
            journal.flush()
            assertFalse(file.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testSanitizationRedactsTokensAndSecrets() {
        val journal = AppJournal(journalFile = null, maxEntries = 10)
        journal.log(
            "GitHub",
            "Auth Bearer secretToken123 failed with ghp_abc123XYZ and glpat-myGitLabSecret99 and " +
                "github_pat_11ABC_xyz987 Authorization: token hdrTok99 PRIVATE-TOKEN: glHdr88 " +
                "Basic dXNlcjpwYXNzMTIz Authorization: basic bG93ZXJCYXNpYzE= " +
                "Auth=auroraSecret777 aas_et/playSecret888 " +
                "https://example.com/dl?key=abc999&auth=qSecret&foo=bar basic settings"
        )

        val entry = journal.entries().single()
        assertFalse(entry.contains("secretToken123"))
        assertFalse(entry.contains("ghp_abc123XYZ"))
        assertFalse(entry.contains("glpat-myGitLabSecret99"))
        assertFalse(entry.contains("github_pat_11ABC_xyz987"))
        assertFalse(entry.contains("hdrTok99"))
        assertFalse(entry.contains("glHdr88"))
        assertFalse(entry.contains("dXNlcjpwYXNzMTIz"))
        assertFalse(entry.contains("bG93ZXJCYXNpYzE="))
        assertFalse(entry.contains("auroraSecret777"))
        assertFalse(entry.contains("playSecret888"))
        assertFalse(entry.contains("key=abc999"))
        assertFalse(entry.contains("qSecret"))

        assertTrue(entry.contains("Bearer [REDACTED]"))
        assertTrue(entry.contains("Authorization: token [REDACTED]"))
        assertTrue(entry.contains("PRIVATE-TOKEN: [REDACTED]"))
        assertTrue(entry.contains("Basic [REDACTED]"))
        assertTrue(entry.contains("Auth=[REDACTED]"))
        assertTrue(entry.contains("aas_et/[REDACTED]"))
        assertTrue(entry.contains("[TOKEN_REDACTED]"))
        assertTrue(entry.contains("key=[REDACTED]&auth=[REDACTED]&foo=bar"))
        assertTrue(entry.contains("basic settings"))
    }

    @Test
    fun testBuildDiagnosticReportOmitsLogcatByDefaultAndIncludesWhenOptedIn() {
        val journal = AppJournal(journalFile = null, maxEntries = 10)
        journal.log("ApkMirror", "Cloudflare cooldown triggered for com.example.app")

        val collectorCalled = AtomicBoolean(false)
        val defaultReport = journal.buildDiagnosticReport(
            appVersionName = "1.43.0-Beta (184)",
            appVersionCode = 184,
            packageName = "com.apkup.debug",
            logcatCollector = {
                collectorCalled.set(true)
                "I/ApkUp: Sample logcat line Bearer hiddenTok"
            }
        )
        assertFalse(collectorCalled.get())
        assertFalse(defaultReport.contains("Sample logcat line"))
        assertTrue(defaultReport.contains("omitted"))

        val optInReport = journal.buildDiagnosticReport(
            appVersionName = "1.43.0-Beta (184)",
            appVersionCode = 184,
            packageName = "com.apkup.debug",
            shizukuStatus = ShizukuSetupStatus(
                isInstalled = true,
                isBinderRunning = true,
                isPermissionGranted = true
            ),
            activeSources = listOf("ApkMirror", "GitHub"),
            includeLogcat = true,
            logcatCollector = {
                collectorCalled.set(true)
                "I/ApkUp: Sample logcat line Bearer hiddenTok"
            }
        )

        assertTrue(collectorCalled.get())
        assertTrue(optInReport.contains("com.apkup.debug v1.43.0-Beta (184) (code=184)"))
        assertTrue(optInReport.contains("Shizuku: installed=true, binderRunning=true, permission=true, ready=true"))
        assertTrue(optInReport.contains("ActiveSources: ApkMirror, GitHub"))
        assertTrue(optInReport.contains("Cloudflare cooldown triggered for com.example.app"))
        assertTrue(optInReport.contains("Sample logcat line"))
        assertTrue(optInReport.contains("Bearer [REDACTED]"))
        assertFalse(optInReport.contains("hiddenTok"))
    }

    @Test
    fun testReadProcessLogcatWithTimeoutDestroysHungProcess() {
        val destroyed = AtomicBoolean(false)
        val hungProcess = object : Process() {
            override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
            override fun getInputStream(): InputStream = ByteArrayInputStream("I/ApkUp: line1 Bearer tok1\n".toByteArray())
            override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun waitFor(): Int = 0
            override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = false
            override fun exitValue(): Int = 0
            override fun destroy() {
                destroyed.set(true)
            }
            override fun destroyForcibly(): Process {
                destroyed.set(true)
                return this
            }
        }

        val output = AppJournal.readProcessLogcatWithTimeout(
            process = hungProcess,
            maxLines = 50,
            timeout = 10L,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(destroyed.get())
        assertTrue(output.contains("line1"))
        assertFalse(output.contains("tok1"))
    }

    @Test
    fun testCleanupDiagnosticLogShareFilesRemovesOnlyStaleFiles() {
        val tempCache = createTempDirectory("apkup_diag_cache_test").toFile()
        try {
            val diagDir = File(tempCache, AppJournal.DIAGNOSTIC_LOGS_CACHE_DIR).apply { mkdirs() }
            val now = 1_700_000_000_000L
            val staleFile = File(diagDir, "old.txt").apply {
                writeText("old")
                setLastModified(now - 100_000L)
            }
            val freshFile = File(diagDir, "fresh.txt").apply {
                writeText("fresh")
                setLastModified(now - 5_000L)
            }

            AppJournal.cleanupDiagnosticLogShareFiles(
                cacheDir = tempCache,
                maxAgeMs = 60_000L,
                nowMs = now
            )

            assertFalse(staleFile.exists())
            assertTrue(freshFile.exists())
        } finally {
            tempCache.deleteRecursively()
        }
    }
}
