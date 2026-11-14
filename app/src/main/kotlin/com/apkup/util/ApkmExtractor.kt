package com.apkup.util

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

object ApkmExtractor {

    const val MAX_ENTRY_BYTES = 1024L * 1024L * 1024L // 1 GB per APK
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB total
    const val MAX_ARCHIVE_SPOOL_BYTES = 1024L * 1024L * 1024L // 1 GB
    const val MAX_TOTAL_ZIP_ENTRIES = 500
    const val MAX_APK_SPLIT_ENTRIES = 128

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun isZipHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == ZIP_MAGIC[0] &&
            header[1] == ZIP_MAGIC[1] &&
            header[2] == ZIP_MAGIC[2] &&
            header[3] == ZIP_MAGIC[3]
    }

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
                if (zip.getEntry("AndroidManifest.xml") != null) {
                    return@use false
                }
                var inspected = 0
                val entries = zip.entries()
                while (entries.hasMoreElements() && inspected < MAX_TOTAL_ZIP_ENTRIES) {
                    val entry = entries.nextElement()
                    inspected++
                    if (!entry.isDirectory && !isMacOsMetadataEntry(entry.name) && entry.name.endsWith(".apk", ignoreCase = true)) {
                        return@use true
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    private fun isMacOsMetadataEntry(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("__MACOSX/", ignoreCase = true) || normalized.contains("/__MACOSX/", ignoreCase = true)) {
            return true
        }
        val simpleName = File(normalized).name
        return simpleName.startsWith("._")
    }

    @Throws(IOException::class)
    fun extractApkm(archiveFile: File, destinationDir: File): List<File> {
        if (!archiveFile.exists() || archiveFile.length() < 22L) {
            throw IOException("Archive file does not exist or is too small to be a valid ZIP: ${archiveFile.absolutePath}")
        }

        destinationDir.mkdirs()
        val canonicalDest = destinationDir.canonicalFile

        val zip = try {
            ZipFile(archiveFile)
        } catch (e: Exception) {
            throw IOException("Corrupt or truncated APKM archive: ${archiveFile.name}", e)
        }

        val extractedFilesMap = LinkedHashMap<String, File>()
        val allCreatedFiles = mutableSetOf<File>()
        var totalExtractedBytes = 0L
        var totalZipEntries = 0

        try {
            zip.use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    totalZipEntries++
                    if (totalZipEntries > MAX_TOTAL_ZIP_ENTRIES) {
                        throw SecurityException("ZIP archive exceeds maximum allowed entry count ($MAX_TOTAL_ZIP_ENTRIES)")
                    }
                    val entry = entries.nextElement()
                    if (entry.isDirectory || isMacOsMetadataEntry(entry.name) || !entry.name.endsWith(".apk", ignoreCase = true)) {
                        continue
                    }

                    val normalizedEntryName = entry.name.replace('\\', '/')
                    val rawSimpleName = File(normalizedEntryName).name
                    if (rawSimpleName.isBlank() || rawSimpleName == "." || rawSimpleName == "..") continue
                    val safeName = ApkIntegrityVerifier.sanitizePartFileName(rawSimpleName, extractedFilesMap.size)

                    val targetFile = File(destinationDir, safeName).canonicalFile
                    if (targetFile.parentFile != canonicalDest) {
                        throw SecurityException("Zip Slip path traversal detected in archive entry: ${entry.name}")
                    }

                    if (extractedFilesMap.size >= MAX_APK_SPLIT_ENTRIES && !extractedFilesMap.containsKey(targetFile.canonicalPath)) {
                        throw SecurityException("APKM archive exceeds maximum allowed APK split count ($MAX_APK_SPLIT_ENTRIES)")
                    }

                    allCreatedFiles.add(targetFile)

                    var entryBytes = 0L
                    zipFile.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read = input.read(buffer)
                            while (read >= 0) {
                                entryBytes += read
                                totalExtractedBytes += read
                                if (entryBytes > MAX_ENTRY_BYTES || totalExtractedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    throw SecurityException("Decompression bomb detected in archive entry: ${entry.name}")
                                }
                                output.write(buffer, 0, read)
                                read = input.read(buffer)
                            }
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
            allCreatedFiles.forEach { runCatching { it.delete() } }
            if (e is IOException) throw e
            if (e is SecurityException) throw e
            throw IOException("Failed to extract APKM archive: ${archiveFile.name}", e)
        }

        if (extractedFilesMap.isEmpty()) {
            throw IOException("No valid .apk entries found inside archive")
        }

        return sortSplitApkFiles(extractedFilesMap.values)
    }

    internal fun sortSplitApkFiles(files: Collection<File>): List<File> {
        return files.sortedWith(
            compareByDescending<File> { it.name.equals("base.apk", ignoreCase = true) }
                .thenByDescending { file ->
                    val lower = file.name.lowercase(Locale.ROOT)
                    !lower.startsWith("split_") && !lower.startsWith("split.") && !lower.startsWith("config.")
                }
                .thenBy { it.name }
        )
    }

    @Throws(IOException::class)
    fun unpackApkmStream(inputStream: InputStream, targetDir: File): List<File> {
        targetDir.mkdirs()
        val tempArchive = File.createTempFile("apkm_stream_", ".zip", targetDir)
        try {
            var spooledBytes = 0L
            tempArchive.outputStream().use { out ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = inputStream.read(buffer)
                while (read >= 0) {
                    spooledBytes += read
                    if (spooledBytes > MAX_ARCHIVE_SPOOL_BYTES) {
                        throw SecurityException("Archive stream exceeds maximum allowed spool size ($MAX_ARCHIVE_SPOOL_BYTES bytes)")
                    }
                    out.write(buffer, 0, read)
                    read = inputStream.read(buffer)
                }
                out.flush()
            }
            return extractApkm(tempArchive, targetDir)
        } finally {
            tempArchive.delete()
        }
    }
}
