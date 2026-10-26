package com.apkup.util

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

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
                // A standalone Android APK always has AndroidManifest.xml at its archive root.
                // An APKM/APKS bundle archive does NOT have AndroidManifest.xml at root; instead it contains base.apk and split .apk entries.
                if (zip.getEntry("AndroidManifest.xml") != null) {
                    return@use false
                }
                zip.entries().asSequence().any { entry ->
                    !entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)
                }
            }
        }.getOrDefault(false)
    }

    /**
     * Extracts all .apk entries from an APKM archive file into the destination staging directory.
     * Enforces full ZIP archive integrity (validating End of Central Directory and Central Directory),
     * Zip Slip path traversal security, deduplication by canonical path, and orders base.apk first.
     *
     * @param archiveFile The .apkm ZIP file.
     * @param destinationDir The target staging directory (e.g. download_staging_<id>/).
     * @return Ordered, deduplicated list of extracted .apk files with base.apk first.
     * @throws IOException If the archive is truncated, corrupted, invalid, or has no valid .apk entries.
     */
    @Throws(IOException::class)
    fun extractApkm(archiveFile: File, destinationDir: File): List<File> {
        if (!archiveFile.exists() || archiveFile.length() < 22L) {
            throw IOException("Archive file does not exist or is too small to be a valid ZIP: ${archiveFile.absolutePath}")
        }

        destinationDir.mkdirs()
        val canonicalDest = destinationDir.canonicalFile

        // Step 1: Open ZipFile. This immediately validates the ZIP End of Central Directory (EOCD)
        // record and parses all Central Directory headers. If the file is truncated anywhere
        // (including after base.apk), ZipFile throws ZipException / IOException.
        val zip = try {
            ZipFile(archiveFile)
        } catch (e: Exception) {
            throw IOException("Corrupt or truncated APKM archive: ${archiveFile.name}", e)
        }

        // Map canonical path -> File to deduplicate collisions when multiple entries flatten to the same name
        val extractedFilesMap = LinkedHashMap<String, File>()
        val allCreatedFiles = mutableSetOf<File>()

        try {
            zip.use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) {
                        continue
                    }

                    // Flatten entry name to simple filename
                    val simpleName = File(entry.name).name
                    if (simpleName.isBlank()) continue

                    val targetFile = File(destinationDir, simpleName).canonicalFile

                    // Defense-in-depth Zip Slip security guard
                    if (targetFile.parentFile != canonicalDest) {
                        throw SecurityException("Zip Slip path traversal detected in archive entry: ${entry.name}")
                    }

                    // Record target file before opening output stream so partial writes are cleaned up on failure
                    allCreatedFiles.add(targetFile)

                    // Extract entry stream to target file
                    zipFile.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }

                    if (targetFile.length() > 0L) {
                        extractedFilesMap[targetFile.canonicalPath] = targetFile
                    } else {
                        targetFile.delete()
                        extractedFilesMap.remove(targetFile.canonicalPath)
                    }
                }
            }
        } catch (e: Exception) {
            // Clean up all created and partially extracted files on failure
            allCreatedFiles.forEach { runCatching { it.delete() } }
            if (e is IOException) throw e
            if (e is SecurityException) throw e
            throw IOException("Failed to extract APKM archive: ${archiveFile.name}", e)
        }

        if (extractedFilesMap.isEmpty()) {
            throw IOException("No valid .apk entries found inside archive")
        }

        // Sort base.apk first, followed alphabetically by split filename
        return extractedFilesMap.values.sortedWith(
            compareByDescending<File> { it.name.equals("base.apk", ignoreCase = true) }
                .thenBy { it.name }
        )
    }

    /**
     * Extracts all .apk entries from an input stream into the destination staging directory.
     * Spools the stream to a temporary archive to ensure full ZIP archive integrity
     * (preventing silent split drops on truncated streams), deduplicates entries, and orders base.apk first.
     *
     * @param inputStream Stream containing the .apkm ZIP archive.
     * @param targetDir Destination directory where splits will be extracted.
     * @return Ordered, deduplicated list of extracted .apk files with base.apk sorted first.
     * @throws IOException If the stream is truncated, corrupt, or contains no valid .apk entries.
     */
    @Throws(IOException::class)
    fun unpackApkmStream(inputStream: InputStream, targetDir: File): List<File> {
        targetDir.mkdirs()
        val tempArchive = File.createTempFile("apkm_stream_", ".zip", targetDir)
        try {
            tempArchive.outputStream().use { out ->
                inputStream.copyTo(out)
                out.flush()
            }
            return extractApkm(tempArchive, targetDir)
        } finally {
            tempArchive.delete()
        }
    }
}
