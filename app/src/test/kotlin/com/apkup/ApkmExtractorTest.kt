package com.apkup

import com.apkup.util.ApkmExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkmExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIsZipHeaderRecognizesZipMagicBytes() {
        val validHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val invalidHeader = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        val shortHeader = byteArrayOf(0x50, 0x4B)

        assertTrue(ApkmExtractor.isZipHeader(validHeader))
        assertFalse(ApkmExtractor.isZipHeader(invalidHeader))
        assertFalse(ApkmExtractor.isZipHeader(shortHeader))
    }

    @Test
    fun testIsApkmArchiveDistinguishesApkAndApkm() {
        val nonZip = tempFolder.newFile("plain.txt").apply { writeText("Hello World") }
        assertFalse(ApkmExtractor.isApkmArchive(nonZip))

        // Normal standalone APK has AndroidManifest.xml at root
        val normalApk = tempFolder.newFile("sample.apk")
        ZipOutputStream(FileOutputStream(normalApk)).use { zos ->
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write("manifest bytes".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("classes.dex"))
            zos.write("dex bytes".toByteArray())
            zos.closeEntry()
        }
        assertFalse("Standalone APK with root AndroidManifest.xml must not be treated as APKM bundle", ApkmExtractor.isApkmArchive(normalApk))

        // APKM bundle has nested .apk entries and no root AndroidManifest.xml
        val apkmBundle = tempFolder.newFile("bundle.apkm")
        ZipOutputStream(FileOutputStream(apkmBundle)).use { zos ->
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("base apk bytes".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("split_config.arm64_v8a.apk"))
            zos.write("split apk bytes".toByteArray())
            zos.closeEntry()
        }
        assertTrue("Archive with nested APKs and without root AndroidManifest.xml must be recognized as APKM", ApkmExtractor.isApkmArchive(apkmBundle))
    }

    @Test
    fun testExtractApkmOrdersBaseApkFirst() {
        val apkmBundle = tempFolder.newFile("order_test.apkm")
        ZipOutputStream(FileOutputStream(apkmBundle)).use { zos ->
            zos.putNextEntry(ZipEntry("split_config.xxhdpi.apk"))
            zos.write("xxhdpi".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("base".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("split_config.arm64_v8a.apk"))
            zos.write("arm64".toByteArray())
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("staging")
        val extracted = ApkmExtractor.extractApkm(apkmBundle, destDir)

        assertEquals(3, extracted.size)
        assertEquals("base.apk", extracted[0].name)
        assertEquals("split_config.arm64_v8a.apk", extracted[1].name)
        assertEquals("split_config.xxhdpi.apk", extracted[2].name)
    }

    @Test
    fun testZipSlipPathTraversalIsNeutralized() {
        val evilArchive = tempFolder.newFile("evil.apkm")
        ZipOutputStream(FileOutputStream(evilArchive)).use { zos ->
            // Try directory traversal out of destination staging folder
            zos.putNextEntry(ZipEntry("../../evil_payload.apk"))
            zos.write("evil".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("base.apk"))
            zos.write("base".toByteArray())
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("dest_staging")
        val extracted = ApkmExtractor.extractApkm(evilArchive, destDir)

        for (file in extracted) {
            assertTrue(
                "Extracted file ${file.canonicalPath} must be inside destDir ${destDir.canonicalPath}",
                file.canonicalPath.startsWith(destDir.canonicalPath + File.separator)
            )
        }
        val flattenedEvil = File(destDir, "evil_payload.apk")
        assertTrue("File with path traversal must be flattened safely inside destDir", flattenedEvil.exists())
        assertEquals("evil", flattenedEvil.readText())
    }
}
