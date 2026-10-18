package com.apkup

import com.apkup.util.InstallPart
import com.apkup.util.wrapStreamIfGzip
import com.aurora.gplayapi.data.models.PlayFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class InstallPipelineTest {

    @Test
    fun `wrapStreamIfGzip decompresses gzipped stream correctly`() {
        val originalText = "This is a simulated APK byte content compressed with GZIP."
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { it.write(originalBytes) }
        val gzippedBytes = byteOut.toByteArray()

        val input = ByteArrayInputStream(gzippedBytes)
        val wrapped = wrapStreamIfGzip(input)
        val decompressedBytes = wrapped.readBytes()

        assertArrayEquals(originalBytes, decompressedBytes)
    }

    @Test
    fun `wrapStreamIfGzip leaves standard raw zip apk stream intact`() {
        val rawApkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x08, 0x00) // PK header
        val input = ByteArrayInputStream(rawApkBytes)
        val wrapped = wrapStreamIfGzip(input)
        val result = wrapped.readBytes()

        assertArrayEquals(rawApkBytes, result)
    }

    @Test
    fun `play files are sorted so BASE is first and splits follow`() {
        val splitConfig = PlayFile(
            name = "split_config.arm64_v8a.apk",
            type = PlayFile.Type.SPLIT,
            size = 1000L
        )
        val splitLang = PlayFile(
            name = "split_config.de.apk",
            type = PlayFile.Type.SPLIT,
            size = 500L
        )
        val baseFile = PlayFile(
            name = "base.apk",
            type = PlayFile.Type.BASE,
            size = 50000L
        )

        val unordered = listOf(splitConfig, splitLang, baseFile)
        val sorted = unordered.sortedWith(
            compareByDescending<PlayFile> { it.type == PlayFile.Type.BASE }
                .thenBy { it.name }
        )

        assertEquals(PlayFile.Type.BASE, sorted[0].type)
        assertEquals("base.apk", sorted[0].name)
        assertEquals(PlayFile.Type.SPLIT, sorted[1].type)
        assertEquals("split_config.arm64_v8a.apk", sorted[1].name)
        assertEquals(PlayFile.Type.SPLIT, sorted[2].type)
        assertEquals("split_config.de.apk", sorted[2].name)
    }

    @Test
    fun `all install part filenames end with apk extension`() {
        val fileBase = PlayFile(name = "", type = PlayFile.Type.BASE)
        val fileSplitNoExt = PlayFile(name = "split_config", type = PlayFile.Type.SPLIT)
        val fileSplitWithExt = PlayFile(name = "split_lang.apk", type = PlayFile.Type.SPLIT)

        val files = listOf(fileBase, fileSplitNoExt, fileSplitWithExt)
        val parts = files.mapIndexed { index, file ->
            val partName = when {
                file.type == PlayFile.Type.BASE -> "base.apk"
                file.name.endsWith(".apk") -> file.name
                file.name.isNotBlank() -> "${file.name}.apk"
                else -> "split_$index.apk"
            }
            InstallPart(partName) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        }

        assertEquals("base.apk", parts[0].name)
        assertEquals("split_config.apk", parts[1].name)
        assertEquals("split_lang.apk", parts[2].name)
        parts.forEach {
            assertTrue("Part name ${it.name} must end with .apk", it.name.endsWith(".apk"))
        }
    }

    @Test
    fun `changelog display conditions trigger correctly based on fresh install check`() {
        val currentVersionCode = 127L

        fun shouldShowChangelog(lastSeen: Long, isFreshInstall: Boolean): Boolean {
            return when {
                lastSeen == 0L && !isFreshInstall -> true
                lastSeen in 1 until currentVersionCode -> true
                lastSeen == 0L && isFreshInstall -> false
                else -> false
            }
        }

        // Scenario 1: Update from v1.26 where last_seen_version_code was 0L (not a fresh install)
        assertTrue(shouldShowChangelog(lastSeen = 0L, isFreshInstall = false))

        // Scenario 2: Normal subsequent update (e.g. from 125 to 127)
        assertTrue(shouldShowChangelog(lastSeen = 125L, isFreshInstall = false))

        // Scenario 3: True fresh install (firstInstallTime == lastUpdateTime)
        assertTrue(!shouldShowChangelog(lastSeen = 0L, isFreshInstall = true))

        // Scenario 4: Already seen current version (no re-trigger)
        assertTrue(!shouldShowChangelog(lastSeen = 127L, isFreshInstall = false))
    }
}
