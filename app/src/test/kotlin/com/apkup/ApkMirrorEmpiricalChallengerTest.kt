package com.apkup

import android.content.SharedPreferences
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.prefs.Prefs
import com.apkup.util.ApkmExtractor
import com.apkup.viewmodel.partitionInstallQueueCandidates
import com.kryptoprefs.preferences.KryptoPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ApkMirrorEmpiricalChallengerTest {

    // =========================================================================
    // 1. ADVERSARIAL ZIP SLIP & MALICIOUS ENTRY TESTS
    // =========================================================================

    @Test
    fun testZipSlipEntryWithParentTraversalIsTrappedInsideTargetDir() {
        // Construct ZIP with malicious entry containing traversal
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Path traversal attempt: ../../evil_outside.apk
            zos.putNextEntry(ZipEntry("../../evil_outside.apk"))
            zos.write("ATTACK_PAYLOAD".toByteArray())
            zos.closeEntry()

            // Legitimate base.apk
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("LEGITIMATE_BASE".toByteArray())
            zos.closeEntry()
        }

        val tempDir = File.createTempFile("challenger_zipslip_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extracted = ApkmExtractor.unpackApkmStream(
                ByteArrayInputStream(baos.toByteArray()),
                tempDir
            )

            // Verify no file escaped tempDir
            for (file in extracted) {
                assertTrue(
                    "Extracted file ${file.canonicalPath} must be inside targetDir ${tempDir.canonicalPath}",
                    file.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)
                )
            }

            // Verify simpleName flattening trapped the file inside tempDir as evil_outside.apk
            val flattenedEvilFile = File(tempDir, "evil_outside.apk")
            assertTrue(flattenedEvilFile.exists())
            assertEquals("ATTACK_PAYLOAD", flattenedEvilFile.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testZipSlipAbsoluteUnixAndWindowsPathsAreTrappedInsideTargetDir() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("/etc/cron.d/malicious.apk"))
            zos.write("UNIX_ROOT_PAYLOAD".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("LEGITIMATE_BASE".toByteArray())
            zos.closeEntry()
        }

        val tempDir = File.createTempFile("challenger_absolutepath_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extracted = ApkmExtractor.unpackApkmStream(
                ByteArrayInputStream(baos.toByteArray()),
                tempDir
            )

            for (file in extracted) {
                assertTrue(
                    "Extracted file must be inside targetDir",
                    file.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)
                )
            }
            val trappedFile = File(tempDir, "malicious.apk")
            assertTrue(trappedFile.exists())
            assertEquals("UNIX_ROOT_PAYLOAD", trappedFile.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testEmptyOrDirectoryEntriesAreIgnored() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Directory ending in /
            zos.putNextEntry(ZipEntry("subfolder/"))
            zos.closeEntry()

            // Non-apk files
            zos.putNextEntry(ZipEntry("README.txt"))
            zos.write("hello".toByteArray())
            zos.closeEntry()

            // Directory ending in .apk/
            zos.putNextEntry(ZipEntry("fake_dir.apk/"))
            zos.closeEntry()

            // Valid base.apk
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("VALID_BASE".toByteArray())
            zos.closeEntry()
        }

        val tempDir = File.createTempFile("challenger_dirs_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extracted = ApkmExtractor.unpackApkmStream(
                ByteArrayInputStream(baos.toByteArray()),
                tempDir
            )
            assertEquals(1, extracted.size)
            assertEquals("base.apk", extracted[0].name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMultipleEntriesWithIdenticalNamesOverwritesAndDeduplicationBehavior() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // First entry: nested base.apk
            zos.putNextEntry(ZipEntry("v1/base.apk"))
            zos.write("FIRST_VERSION".toByteArray())
            zos.closeEntry()

            // Second entry: another nested base.apk that flattens to the same filename
            zos.putNextEntry(ZipEntry("v2/base.apk"))
            zos.write("SECOND_OVERWRITING_VERSION".toByteArray())
            zos.closeEntry()
        }

        val tempDir = File.createTempFile("challenger_collision_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extracted = ApkmExtractor.unpackApkmStream(
                ByteArrayInputStream(baos.toByteArray()),
                tempDir
            )

            // Both entries flattened to base.apk. The file content is the second one.
            val baseFile = File(tempDir, "base.apk")
            assertTrue(baseFile.exists())
            assertEquals("SECOND_OVERWRITING_VERSION", baseFile.readText())

            // Deduplicated list contains unique File references
            assertEquals(1, extracted.size)
            assertEquals("base.apk", extracted[0].name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // 2. CORRUPT & TRUNCATED ARCHIVE TESTS
    // =========================================================================

    @Test
    fun testIsApkmArchiveOnZeroByteFileReturnsFalse() {
        val emptyFile = File.createTempFile("empty_", ".apkm")
        try {
            assertFalse(ApkmExtractor.isApkmArchive(emptyFile))
        } finally {
            emptyFile.delete()
        }
    }

    @Test
    fun testIsApkmArchiveOnTruncatedHeaderReturnsFalse() {
        val truncatedFile = File.createTempFile("trunc_", ".apkm")
        try {
            // Write only 2 bytes
            truncatedFile.writeBytes(byteArrayOf(0x50, 0x4B))
            assertFalse(ApkmExtractor.isApkmArchive(truncatedFile))

            // Write 4 bytes of invalid magic
            truncatedFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x01, 0x02))
            assertFalse(ApkmExtractor.isApkmArchive(truncatedFile))
        } finally {
            truncatedFile.delete()
        }
    }

    @Test
    fun testIsApkmArchiveOnCorruptZipWithValidHeaderReturnsFalse() {
        val corruptZipFile = File.createTempFile("corrupt_", ".apkm")
        try {
            // Write valid 4-byte ZIP magic but truncated trailing garbage
            corruptZipFile.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00))
            assertFalse("Corrupt zip must fail gracefully without throwing", ApkmExtractor.isApkmArchive(corruptZipFile))
        } finally {
            corruptZipFile.delete()
        }
    }

    @Test
    fun testIsApkmArchiveOnValidZipWithoutApkEntriesReturnsFalse() {
        val nonApkZip = File.createTempFile("non_apk_", ".zip")
        try {
            ZipOutputStream(FileOutputStream(nonApkZip)).use { zos ->
                zos.putNextEntry(ZipEntry("document.pdf"))
                zos.write("PDF_DATA".toByteArray())
                zos.closeEntry()
            }
            assertFalse("ZIP without .apk entries must return false", ApkmExtractor.isApkmArchive(nonApkZip))
        } finally {
            nonApkZip.delete()
        }
    }

    @Test
    fun testUnpackApkmStreamOnMidEntryTruncatedArchiveThrowsException() {
        // Create valid APKM bytes then truncate in the middle of DEFLATE compressed data
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("base.apk"))
            // Write substantial data so DEFLATE stream is multi-block
            zos.write(ByteArray(10240) { (it % 256).toByte() })
            zos.closeEntry()
        }
        val fullBytes = baos.toByteArray()
        // Truncate at byte 50 (in the middle of base.apk data stream)
        val midEntryTruncatedBytes = fullBytes.copyOfRange(0, 50)

        val tempDir = File.createTempFile("challenger_mid_trunc_", "").apply {
            delete()
            mkdirs()
        }

        try {
            assertThrows(IOException::class.java) {
                ApkmExtractor.unpackApkmStream(
                    ByteArrayInputStream(midEntryTruncatedBytes),
                    tempDir
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUnpackApkmStreamSilentlyDropsMissingSplitsWhenArchiveTruncatedBeforeCentralDirectory() {
        // Multi-split APKM bundle: base.apk + split_arm64.apk + split_xxhdpi.apk
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("BASE_APK_CONTENT".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("split_config.arm64.apk"))
            zos.write("SPLIT_ARM64_CONTENT".toByteArray())
            zos.closeEntry()
        }
        val fullBytes = baos.toByteArray()

        // Truncate right after base.apk entry (dropping split_config.arm64 and Central Directory)
        // Find where "base.apk" entry data ends
        val truncatedAfterBase = fullBytes.copyOfRange(0, 90)

        val tempDir = File.createTempFile("challenger_post_trunc_", "").apply {
            delete()
            mkdirs()
        }

        try {
            assertThrows(IOException::class.java) {
                ApkmExtractor.unpackApkmStream(
                    ByteArrayInputStream(truncatedAfterBase),
                    tempDir
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUnpackApkmStreamWithZeroValidApkEntriesThrowsIOException() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val tempDir = File.createTempFile("challenger_noapk_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val ex = assertThrows(IOException::class.java) {
                ApkmExtractor.unpackApkmStream(
                    ByteArrayInputStream(baos.toByteArray()),
                    tempDir
                )
            }
            assertTrue(ex.message?.contains("No valid .apk entries") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testUnpackApkmStreamWithZeroByteApkEntriesThrowsIOException() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("base.apk"))
            // Write 0 bytes
            zos.closeEntry()
        }

        val tempDir = File.createTempFile("challenger_zerobyte_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val ex = assertThrows(IOException::class.java) {
                ApkmExtractor.unpackApkmStream(
                    ByteArrayInputStream(baos.toByteArray()),
                    tempDir
                )
            }
            assertTrue(ex.message?.contains("No valid .apk entries") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // 3. QUEUE PARTITIONING & DYNAMIC TOGGLING TESTS
    // =========================================================================

    private fun createSampleUpdate(
        idSuffix: String,
        source: com.apkup.data.ui.Source,
        link: Link = Link.Url("https://example.com/download/$idSuffix"),
        alternatives: List<AppUpdate> = emptyList()
    ): AppUpdate = AppUpdate(
        name = "App_$idSuffix",
        packageName = "com.example.$idSuffix",
        version = "2.0",
        oldVersion = "1.0",
        versionCode = 200L,
        oldVersionCode = 100L,
        source = source,
        link = link,
        alternativeUpdates = alternatives
    )

    @Test
    fun testDynamicToggleDuringQueuePartitioning() {
        val kprefs = TestKryptoPrefs()
        val prefs = Prefs(kprefs)

        val playApp = createSampleUpdate("play", PlaySource, Link.Play { emptyList() })
        val directMirrorApp = createSampleUpdate("mirror1", ApkMirrorSource, Link.Url("https://www.apkmirror.com/apk/app1/download/"))
        val githubApp = createSampleUpdate("github", GitHubSource, Link.Url("https://github.com/release/app.apk"))

        val updates = listOf(playApp, directMirrorApp, githubApp)

        // Initial state: directApkMirrorDownloads is TRUE by default
        assertTrue(prefs.directApkMirrorDownloads.get())
        val (initialDirect, initialBrowser) = partitionInstallQueueCandidates(
            updates = updates,
            directApkMirrorEnabled = prefs.directApkMirrorDownloads.get()
        )
        assertEquals("When enabled, all 3 apps are direct installable", 3, initialDirect.size)
        assertEquals("When enabled, browser queue is empty", 0, initialBrowser.size)

        // DYNAMIC TOGGLE to FALSE
        prefs.directApkMirrorDownloads.put(false)
        assertFalse(prefs.directApkMirrorDownloads.get())

        val (toggledDirect, toggledBrowser) = partitionInstallQueueCandidates(
            updates = updates,
            directApkMirrorEnabled = prefs.directApkMirrorDownloads.get()
        )
        assertEquals("When disabled, Play and GitHub are direct", 2, toggledDirect.size)
        assertEquals("When disabled, APKMirror is routed to browserOnly", 1, toggledBrowser.size)
        assertEquals("com.example.mirror1", toggledBrowser[0].packageName)

        // DYNAMIC TOGGLE BACK to TRUE
        prefs.directApkMirrorDownloads.put(true)
        assertTrue(prefs.directApkMirrorDownloads.get())

        val (restoredDirect, restoredBrowser) = partitionInstallQueueCandidates(
            updates = updates,
            directApkMirrorEnabled = prefs.directApkMirrorDownloads.get()
        )
        assertEquals("Restored direct queue has 3 apps", 3, restoredDirect.size)
        assertEquals("Restored browser queue has 0 apps", 0, restoredBrowser.size)
    }

    @Test
    fun testAlternativeSourcePromotionWithDynamicToggleDemonstratesShadowBug() {
        // App where primary source is APKMirror, but alternative is Google Play
        val playAlt = createSampleUpdate("hybrid_alt", PlaySource, Link.Play { emptyList() })
        val primaryMirrorWithPlayAlt = createSampleUpdate(
            idSuffix = "hybrid",
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/hybrid/download/"),
            alternatives = listOf(playAlt)
        )

        // When directApkMirrorDownloads is FALSE:
        // Valid direct alternative (Google Play) is promoted to directQueue!
        val (directWhenDisabled, browserWhenDisabled) = partitionInstallQueueCandidates(
            updates = listOf(primaryMirrorWithPlayAlt),
            directApkMirrorEnabled = false
        )
        assertEquals(1, directWhenDisabled.size)
        assertEquals("Play", directWhenDisabled[0].source.name)
        assertEquals(0, browserWhenDisabled.size)

        // When directApkMirrorDownloads is TRUE:
        // Primary APKMirror IS direct installable, so it correctly goes to directQueue
        val (directWhenEnabled, browserWhenEnabled) = partitionInstallQueueCandidates(
            updates = listOf(primaryMirrorWithPlayAlt),
            directApkMirrorEnabled = true
        )
        assertEquals(1, directWhenEnabled.size)
        assertEquals("ApkMirror", directWhenEnabled[0].source.name)
        assertEquals(0, browserWhenEnabled.size)
    }

    @Test
    fun testConcurrentDynamicTogglingUnderPartitionLoad() = runBlocking {
        val kprefs = TestKryptoPrefs()
        val prefs = Prefs(kprefs)

        val updates = (1..50).map { i ->
            val source = if (i % 2 == 0) ApkMirrorSource else PlaySource
            val link = if (source == PlaySource) Link.Play { emptyList() } else Link.Url("https://apkmirror.com/apk/$i/")
            createSampleUpdate("app_$i", source, link)
        }

        // Launch concurrent readers and writers
        val iterations = 100
        val toggleJob = async(Dispatchers.Default) {
            for (i in 1..iterations) {
                prefs.directApkMirrorDownloads.put(i % 2 == 0)
            }
        }

        val partitionJob = async(Dispatchers.Default) {
            var directCountTotal = 0
            for (i in 1..iterations) {
                val enabled = prefs.directApkMirrorDownloads.get()
                val (direct, browser) = partitionInstallQueueCandidates(updates, enabled)
                // Invariants:
                // Total sum must always equal total updates (50)
                assertEquals(50, direct.size + browser.size)
                if (enabled) {
                    assertEquals(50, direct.size)
                    assertEquals(0, browser.size)
                } else {
                    assertEquals(25, direct.size)
                    assertEquals(25, browser.size)
                }
                directCountTotal += direct.size
            }
            directCountTotal
        }

        toggleJob.await()
        val count = partitionJob.await()
        assertTrue(count > 0)
    }

    // =========================================================================
    // 4. ADVERSARIAL STRESS TESTING: CORRUPTIONS, TRUNCATIONS, LEAKS & SCALE
    // =========================================================================

    @Test
    fun testExtractApkmZeroByteAndSub22ByteArchivesThrowIOExceptionWithoutFileDescriptorLeaks() {
        val tempDir = File.createTempFile("challenger_leak_dest_", "").apply {
            delete()
            mkdirs()
        }

        try {
            // Test 1: Zero-byte file
            val zeroByteArchive = File.createTempFile("challenger_zero_", ".apkm")
            try {
                assertThrows(IOException::class.java) {
                    ApkmExtractor.extractApkm(zeroByteArchive, tempDir)
                }
                // Verify no files written to destination
                assertEquals(0, tempDir.listFiles()?.size ?: 0)
            } finally {
                // Must be deletable immediately (proves no file descriptor or lock was leaked on Windows)
                assertTrue("Zero-byte archive file must be deletable without lock leak", zeroByteArchive.delete())
            }

            // Test 2: 15-byte file (sub-22-byte minimum EOCD size)
            val sub22Archive = File.createTempFile("challenger_sub22_", ".apkm")
            try {
                sub22Archive.writeBytes(ByteArray(15) { 0x50 })
                assertThrows(IOException::class.java) {
                    ApkmExtractor.extractApkm(sub22Archive, tempDir)
                }
                assertEquals(0, tempDir.listFiles()?.size ?: 0)
            } finally {
                assertTrue("Sub-22-byte archive file must be deletable without lock leak", sub22Archive.delete())
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testExtractApkmTruncatedBeforeCentralDirectoryThrowsIOExceptionAndLeavesNoPartialFilesOrLocks() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("BASE_APK_BINARY_PAYLOAD".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("split_config.arm64.apk"))
            zos.write("ARM64_SPLIT_PAYLOAD".toByteArray())
            zos.closeEntry()
        }
        val fullBytes = baos.toByteArray()
        // Truncate right before central directory (drop last 40 bytes containing central directory headers and EOCD)
        val truncatedBytes = fullBytes.copyOfRange(0, fullBytes.size - 40)

        val archiveFile = File.createTempFile("challenger_trunc_cd_", ".apkm")
        archiveFile.writeBytes(truncatedBytes)

        val destDir = File.createTempFile("challenger_trunc_dest_", "").apply {
            delete()
            mkdirs()
        }

        try {
            assertThrows(IOException::class.java) {
                ApkmExtractor.extractApkm(archiveFile, destDir)
            }
            // Destination directory must have 0 files (no partial extractions left behind)
            assertEquals("Destination directory must be empty after truncation failure", 0, destDir.listFiles()?.size ?: 0)
        } finally {
            // Verify file lock is completely released on Windows
            assertTrue("Archive file must be deletable after extraction exception", archiveFile.delete())
            destDir.deleteRecursively()
        }
    }

    @Test
    fun testUnpackApkmStreamCleansUpTempArchivesOnTruncatedOrCorruptStreamWithoutLeaks() {
        val targetDir = File.createTempFile("challenger_stream_dest_", "").apply {
            delete()
            mkdirs()
        }

        try {
            // Case 1: Truncated stream
            val truncatedStream = ByteArrayInputStream(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
            assertThrows(IOException::class.java) {
                ApkmExtractor.unpackApkmStream(truncatedStream, targetDir)
            }

            // Verify no temporary apkm_stream_*.zip files leaked in targetDir
            val remainingTempZips = targetDir.listFiles { _, name -> name.startsWith("apkm_stream_") }
            assertTrue(
                "Temporary archive must be purged on stream failure, but found: ${remainingTempZips?.map { it.name }}",
                remainingTempZips.isNullOrEmpty()
            )
            // Verify no partial .apk files remained
            val remainingApks = targetDir.listFiles { _, name -> name.endsWith(".apk") }
            assertTrue(
                "No APK files should remain on stream failure, but found: ${remainingApks?.map { it.name }}",
                remainingApks.isNullOrEmpty()
            )

            // Case 2: Valid stream
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("base.apk"))
                zos.write("BASE_PAYLOAD".toByteArray())
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("split_config.xxhdpi.apk"))
                zos.write("XXHDPI_PAYLOAD".toByteArray())
                zos.closeEntry()
            }
            val validStream = ByteArrayInputStream(baos.toByteArray())
            val extracted = ApkmExtractor.unpackApkmStream(validStream, targetDir)
            assertEquals(2, extracted.size)
            assertEquals("base.apk", extracted[0].name)
            assertEquals("split_config.xxhdpi.apk", extracted[1].name)

            // Verify temporary archive was deleted on success as well
            val tempZipsAfterSuccess = targetDir.listFiles { _, name -> name.startsWith("apkm_stream_") }
            assertTrue("Temporary archive must be purged on success", tempZipsAfterSuccess.isNullOrEmpty())
        } finally {
            targetDir.deleteRecursively()
        }
    }

    @Test
    fun testExtractApkmWithCorruptedEntryStreamCleansUpAndRethrowsIOException() {
        // Build a zip where entry 1 (base.apk) is valid, but entry 2 (split.apk) has corrupted deflate stream
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("VALID_BASE_DATA".toByteArray())
            zos.closeEntry()

            // Large entry so deflate stream has substantial compressed bytes
            zos.putNextEntry(ZipEntry("split_data.apk"))
            val payload = ByteArray(4096) { (it % 127).toByte() }
            zos.write(payload)
            zos.closeEntry()
        }

        val rawBytes = baos.toByteArray()
        // Corrupt compressed bytes of split_data.apk:
        // Find "split_data.apk" local file header in rawBytes
        val needle = "split_data.apk".toByteArray()
        var splitHeaderOffset = -1
        for (i in 0 until rawBytes.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (rawBytes[i + j] != needle[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                splitHeaderOffset = i
                break
            }
        }
        assertTrue("Must locate split_data.apk header in rawBytes", splitHeaderOffset > 0)

        // Local header format: 30 bytes fixed header, filename length, extra field length.
        // The compressed data starts after the filename and extra field.
        // Corrupt several bytes of the compressed data:
        val corruptedBytes = rawBytes.copyOf()
        val dataOffset = splitHeaderOffset + needle.size + 10
        for (i in dataOffset until (dataOffset + 32).coerceAtMost(corruptedBytes.size - 22)) {
            corruptedBytes[i] = (corruptedBytes[i].toInt() xor 0xFF).toByte()
        }

        val archiveFile = File.createTempFile("challenger_corrupt_entry_", ".apkm")
        archiveFile.writeBytes(corruptedBytes)

        val destDir = File.createTempFile("challenger_corrupt_dest_", "").apply {
            delete()
            mkdirs()
        }

        try {
            assertThrows(IOException::class.java) {
                ApkmExtractor.extractApkm(archiveFile, destDir)
            }

            // Destination directory must NOT leak partially extracted base.apk!
            val remainingFiles = destDir.listFiles() ?: emptyArray()
            assertEquals(
                "Partially extracted files must be cleaned up on entry corruption failure: ${remainingFiles.map { it.name }}",
                0,
                remainingFiles.size
            )
        } finally {
            assertTrue("Archive file must be deletable without lock leak", archiveFile.delete())
            destDir.deleteRecursively()
        }
    }

    @Test
    fun testExtractApkmWithLargeSplitBundleMaintainsOrderAndNoLeaks() {
        val splitCount = 50
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Add base.apk
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("BASE_CONTENT".toByteArray())
            zos.closeEntry()

            // Add 50 split APKs with 2-digit zero-padded names
            for (i in 1..splitCount) {
                val paddedIndex = String.format("%02d", i)
                zos.putNextEntry(ZipEntry("splits/split_config.$paddedIndex.apk"))
                zos.write("SPLIT_CONTENT_$paddedIndex".toByteArray())
                zos.closeEntry()
            }
        }

        val archiveFile = File.createTempFile("challenger_large_bundle_", ".apkm")
        archiveFile.writeBytes(baos.toByteArray())

        val destDir = File.createTempFile("challenger_large_dest_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extracted = ApkmExtractor.extractApkm(archiveFile, destDir)

            // Total files must equal 1 (base) + 50 (splits) = 51
            assertEquals(51, extracted.size)

            // Base APK must always be first
            assertEquals("base.apk", extracted[0].name)
            assertEquals("BASE_CONTENT", extracted[0].readText())

            // Subsequent splits must be sorted alphabetically
            for (i in 1..splitCount) {
                val paddedIndex = String.format("%02d", i)
                val expectedName = "split_config.$paddedIndex.apk"
                assertEquals(expectedName, extracted[i].name)
                assertEquals("SPLIT_CONTENT_$paddedIndex", extracted[i].readText())
            }

            // Exactly 51 files on disk in destDir
            assertEquals(51, destDir.listFiles()?.size ?: 0)
        } finally {
            assertTrue("Large bundle archive must be deletable without lock leak", archiveFile.delete())
            destDir.deleteRecursively()
        }
    }

    @Test
    fun testExtractApkmDuplicateEntryNamesWithMixedZeroByteAndValidContent() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // Entry 1: valid base.apk
            zos.putNextEntry(ZipEntry("folder_a/base.apk"))
            zos.write("BASE_V1".toByteArray())
            zos.closeEntry()

            // Entry 2: duplicate base.apk that overwrites entry 1
            zos.putNextEntry(ZipEntry("folder_b/base.apk"))
            zos.write("BASE_V2".toByteArray())
            zos.closeEntry()

            // Entry 3: zero-byte split.apk (should be deleted and removed)
            zos.putNextEntry(ZipEntry("splits/split.apk"))
            zos.closeEntry()

            // Entry 4: duplicate split.apk with valid content (should replace previous zero-byte entry)
            zos.putNextEntry(ZipEntry("other/split.apk"))
            zos.write("SPLIT_VALID".toByteArray())
            zos.closeEntry()
        }

        val archiveFile = File.createTempFile("challenger_dup_mixed_", ".apkm")
        archiveFile.writeBytes(baos.toByteArray())

        val destDir = File.createTempFile("challenger_dup_dest_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val extracted = ApkmExtractor.extractApkm(archiveFile, destDir)
            assertEquals(2, extracted.size)
            assertEquals("base.apk", extracted[0].name)
            assertEquals("BASE_V2", extracted[0].readText())

            assertEquals("split.apk", extracted[1].name)
            assertEquals("SPLIT_VALID", extracted[1].readText())

            assertEquals(2, destDir.listFiles()?.size ?: 0)
        } finally {
            assertTrue(archiveFile.delete())
            destDir.deleteRecursively()
        }
    }

    private class TestKryptoPrefs : KryptoPrefs {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()

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
        override fun name(): String = "test-prefs"
        override fun sharedPreferences(): SharedPreferences = error("Not used")

        private fun put(key: String, value: Any?): KryptoPrefs = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
