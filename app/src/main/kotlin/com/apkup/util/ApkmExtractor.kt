package com.apkup.util

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ApkmExtractor {

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * Checks if the byte array matches the standard ZIP Local File Header magic bytes (PK\x03\x04).
     */
    fun isZipHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == ZIP_MAGIC[0] &&
            header[1] == ZIP_MAGIC[1] &&
            header[2] == ZIP_MAGIC[2] &&
            header[3] == ZIP_MAGIC[3]
    }

    /**
     * Determines whether a downloaded file is an APKM/APKS split-APK archive.
     * Verifies ZIP magic bytes and checks for nested .apk entries.
     */
    fun isApkmArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false

        val isZipHeader = runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                read == 4 && isZipHeader(header)
            }
        }.getOrDefault(false)

        if (!isZipHeader) return false

        return runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Extracts all .apk entries from an input stream into the destination staging directory.
     * Enforces Zip Slip validation and orders base.apk first.
     *
     * @param inputStream Stream containing the .apkm ZIP archive.
     * @param targetDir Destination directory where splits will be extracted.
     * @return Ordered list of extracted .apk files with base.apk sorted first.
     */
    @Throws(IOException::class)
    fun unpackApkmStream(inputStream: InputStream, targetDir: File): List<File> {
        targetDir.mkdirs()
        val canonicalDest = targetDir.canonicalPath
        val extractedFiles = mutableListOf<File>()

        ZipInputStream(inputStream).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) {
                    zis.closeEntry()
                    continue
                }

                // Flatten file structure by extracting only the simple filename
                val simpleName = File(entry.name).name
                val targetFile = File(targetDir, simpleName)

                // Zip Slip security guard
                if (!targetFile.canonicalPath.startsWith(canonicalDest)) {
                    throw SecurityException("Zip Slip path traversal detected in archive entry: ${entry.name}")
                }

                targetFile.outputStream().use { output ->
                    zis.copyTo(output)
                    output.flush()
                }
                zis.closeEntry()

                if (targetFile.length() > 0L) {
                    extractedFiles.add(targetFile)
                } else {
                    targetFile.delete()
                }
            }
        }

        if (extractedFiles.isEmpty()) {
            throw IOException("No valid .apk entries found inside archive")
        }

        // Sort base.apk first, followed by alphabetical split names
        return extractedFiles.sortedWith(
            compareByDescending<File> { it.name.equals("base.apk", ignoreCase = true) }
                .thenBy { it.name }
        )
    }

    /**
     * Extracts all .apk entries from an APKM archive into the destination staging directory.
     * Enforces Zip Slip validation and orders base.apk first.
     *
     * @param archiveFile The .apkm ZIP file.
     * @param destinationDir The target staging directory (e.g. download_staging_<id>/).
     * @return Ordered list of extracted .apk files with base.apk first.
     */
    @Throws(IOException::class)
    fun extractApkm(archiveFile: File, destinationDir: File): List<File> {
        return archiveFile.inputStream().use { stream ->
            unpackApkmStream(stream, destinationDir)
        }
    }
}
