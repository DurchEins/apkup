package com.apkup

import android.content.ContextWrapper
import com.apkup.util.ApkExportResult
import com.apkup.util.DownloadStorageManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class DownloadStorageManagerExportTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun buildExportFileNameHandlesSingleApkAndSplitApksAndSanitizesChars() {
        assertEquals(
            "Signal-Private-Messenger-v7.25.1.apk",
            DownloadStorageManager.buildExportFileName("Signal: Private Messenger", "org.thoughtcrime.securesms", "v7.25.1", isSplitBundle = false)
        )
        assertEquals(
            "MyApp-Beta-v2.0.apks",
            DownloadStorageManager.buildExportFileName("MyApp/Beta", "com.example.app", "2.0", isSplitBundle = true)
        )
        assertEquals(
            "com.example.only.apk",
            DownloadStorageManager.buildExportFileName("", "com.example.only", "", isSplitBundle = false)
        )
    }

    @Test
    fun writeApkOrBundleCopiesSingleFileDirectly() {
        val payload = "PK_SINGLE_APK_BYTES".toByteArray()
        val file = tempFolder.newFile("base.apk").apply { writeBytes(payload) }
        val out = ByteArrayOutputStream()

        DownloadStorageManager.writeApkOrBundleToStream(listOf(file), out)

        assertArrayEquals(payload, out.toByteArray())
    }

    @Test
    fun writeApkOrBundleCreatesValidApksZipWithBaseApkFirst() {
        val base = tempFolder.newFile("base.apk").apply { writeBytes("BASE".toByteArray()) }
        val splitArm = tempFolder.newFile("split_config.arm64_v8a.apk").apply { writeBytes("ARM".toByteArray()) }
        val splitLang = tempFolder.newFile("split_config.de.apk").apply { writeBytes("DE".toByteArray()) }
        val out = ByteArrayOutputStream()

        DownloadStorageManager.writeApkOrBundleToStream(listOf(splitLang, base, splitArm), out)

        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().decodeToString()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        assertEquals(listOf("base.apk", "split_config.arm64_v8a.apk", "split_config.de.apk"), entries.keys.toList())
        assertEquals("BASE", entries["base.apk"])
        assertEquals("ARM", entries["split_config.arm64_v8a.apk"])
        assertEquals("DE", entries["split_config.de.apk"])
    }

    @Test
    fun exportApkFilesSetsUsedSafFallbackWhenCustomSafFailsAndFallsBackToDownloads() {
        val defaultResult = ApkExportResult(success = true, fileName = "a.apk", displayLocation = "Download/ApkUp/a.apk")
        assertFalse(defaultResult.usedSafFallback)

        val fallbackRoot = tempFolder.newFolder("downloads_fallback")
        val sourceApk = tempFolder.newFile("base.apk").apply { writeBytes("PK_BYTES".toByteArray()) }
        val fakeContext = object : ContextWrapper(null) {}

        DownloadStorageManager.legacyDownloadsDirOverrideForTest = fallbackRoot
        try {
            val safFallbackResult = DownloadStorageManager.exportApkFiles(
                context = fakeContext,
                files = listOf(sourceApk),
                appName = "Sample App",
                packageName = "com.example.sample",
                version = "1.2.3",
                customFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ABrokenFolder"
            )
            assertTrue(safFallbackResult.success)
            assertTrue(safFallbackResult.usedSafFallback)
            assertTrue(safFallbackResult.displayLocation.startsWith("Download/ApkUp/"))
            assertTrue(File(File(fallbackRoot, "ApkUp"), safFallbackResult.fileName).exists())

            val directDefaultResult = DownloadStorageManager.exportApkFiles(
                context = fakeContext,
                files = listOf(sourceApk),
                appName = "Sample App",
                packageName = "com.example.sample",
                version = "1.2.4",
                customFolderUri = ""
            )
            assertTrue(directDefaultResult.success)
            assertFalse(directDefaultResult.usedSafFallback)
        } finally {
            DownloadStorageManager.legacyDownloadsDirOverrideForTest = null
        }
    }
}
