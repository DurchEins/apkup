package com.apkup.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ApkExportResult(
    val success: Boolean,
    val fileName: String,
    val displayLocation: String,
    val errorMessage: String? = null,
    val usedSafFallback: Boolean = false
)

/**
 * Centralized manager for persistent download storage and staging directories.
 * Uses `context.filesDir/downloads` to prevent premature deletion by the Android OS cache cleaner,
 * cleans orphaned temporary directories across both `filesDir` and `cacheDir` (MED-10),
 * and handles user-requested APK/APKS exports into `Download/ApkUp/` or a custom SAF folder.
 */
object DownloadStorageManager {
    private const val DOWNLOADS_DIR_NAME = "downloads"
    private const val PUBLIC_EXPORT_SUBFOLDER = "ApkUp"
    private const val ABANDONED_TEMP_MAX_AGE_MS = 2L * 60L * 60L * 1_000L
    private val UNSAFE_FILENAME_CHARS = Regex("""[^a-zA-Z0-9._+\- ]""")

    @Volatile
    internal var legacyDownloadsDirOverrideForTest: File? = null

    fun getDownloadDir(context: Context): File {
        val base = context.filesDir ?: context.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_cache")
        return File(base, DOWNLOADS_DIR_NAME).apply { mkdirs() }
    }

    fun getStagingDir(context: Context, updateId: Int): File {
        return File(getDownloadDir(context), "download_staging_$updateId").apply { mkdirs() }
    }

    fun deleteStagingDir(context: Context, updateId: Int) {
        runCatching {
            val dir = File(getDownloadDir(context), "download_staging_$updateId")
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    fun cleanExpiredDownloads(
        context: Context,
        maxAgeMs: Long = Downloader.READY_CACHE_TTL_MS,
        activeUpdateIds: Set<Int> = emptySet()
    ) {
        val now = System.currentTimeMillis()

        // 1. Clean filesDir/downloads staging folders & stale .tmp files
        val downloadsDir = context.filesDir?.let { File(it, DOWNLOADS_DIR_NAME) }
        if (downloadsDir != null && downloadsDir.isDirectory) {
            downloadsDir.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name.startsWith("download_staging_")) {
                    val id = child.name.removePrefix("download_staging_").toIntOrNull()
                    if (id != null && id in activeUpdateIds) return@forEach

                    val marker = File(child, Downloader.READY_MARKER_FILE)
                    if (marker.exists()) {
                        val parts = runCatching { marker.readText().trim().split("|", ":") }.getOrDefault(emptyList())
                        val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: marker.lastModified()
                        if (now - timestamp > maxAgeMs) {
                            runCatching { child.deleteRecursively() }
                        }
                    } else if (now - child.lastModified() > ABANDONED_TEMP_MAX_AGE_MS) {
                        runCatching { child.deleteRecursively() }
                    }
                } else if (child.isFile && child.name.endsWith(".tmp") && now - child.lastModified() > ABANDONED_TEMP_MAX_AGE_MS) {
                    runCatching { child.delete() }
                }
            }
        }

        // 2. Clean orphaned staging directories in cacheDir (Shizuku, self-update, APKM extraction, verifier)
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.isDirectory) {
            val transientPrefixes = listOf(
                "shizuku_staging_",
                "shizuku_install_",
                "shizuku_auto_",
                "installer_verify_",
                "self_update_staging",
                "apkm_extract_",
                "download_staging_"
            )
            cacheDir.listFiles()?.forEach { child ->
                val matchesTransient = transientPrefixes.any { prefix -> child.name.startsWith(prefix) }
                if (matchesTransient && now - child.lastModified() > ABANDONED_TEMP_MAX_AGE_MS) {
                    runCatching { child.deleteRecursively() }
                }
            }
        }
    }

    internal fun buildExportFileName(
        appName: String,
        packageName: String,
        version: String,
        isSplitBundle: Boolean
    ): String {
        val rawTitle = appName.trim().ifBlank { packageName.trim() }.ifBlank { "app" }
        val cleanTitle = rawTitle
            .replace(UNSAFE_FILENAME_CHARS, " ")
            .replace(Regex("[\\s_]+"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '_')
            .ifBlank { "app" }
        val cleanVersion = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .replace(UNSAFE_FILENAME_CHARS, "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_{2,}"), "_")
            .trim('_')
        val ext = if (isSplitBundle) "apks" else "apk"
        return if (cleanVersion.isNotBlank()) {
            "$cleanTitle-v$cleanVersion.$ext"
        } else {
            "$cleanTitle.$ext"
        }
    }

    internal fun resolveSafFolderDisplayName(treeUriString: String): String {
        if (treeUriString.isBlank()) return "Download/$PUBLIC_EXPORT_SUBFOLDER/"
        return runCatching {
            val uri = Uri.parse(treeUriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            docId.substringAfter(':', docId).ifBlank { uri.lastPathSegment ?: treeUriString }
        }.getOrElse { treeUriString }
    }

    internal fun writeApkOrBundleToStream(files: List<File>, outputStream: OutputStream) {
        if (files.isEmpty()) {
            throw IOException("No APK files to export")
        }
        val validFiles = files.filter { it.exists() && it.isFile && it.length() > 0L }
        if (validFiles.isEmpty()) {
            throw IOException("All APK files are empty or missing")
        }
        if (validFiles.size == 1) {
            validFiles.first().inputStream().use { input ->
                input.copyTo(outputStream, DEFAULT_BUFFER_SIZE)
            }
            outputStream.flush()
            return
        }

        val ordered = validFiles.sortedWith(
            compareByDescending<File> { it.name.equals("base.apk", ignoreCase = true) }
                .thenBy { it.name.lowercase() }
        )
        ZipOutputStream(outputStream).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            ordered.forEach { file ->
                val entry = ZipEntry(file.name)
                zip.putNextEntry(entry)
                file.inputStream().use { input ->
                    input.copyTo(zip, DEFAULT_BUFFER_SIZE)
                }
                zip.closeEntry()
            }
            zip.finish()
        }
        outputStream.flush()
    }

    fun exportApkFiles(
        context: Context,
        files: List<File>,
        appName: String,
        packageName: String,
        version: String,
        customFolderUri: String = ""
    ): ApkExportResult {
        val validFiles = files.filter { it.exists() && it.isFile && it.length() > 0L }
        if (validFiles.isEmpty()) {
            return ApkExportResult(
                success = false,
                fileName = "",
                displayLocation = "",
                errorMessage = "No valid APK files available"
            )
        }
        val isSplitBundle = validFiles.size > 1
        val fileName = buildExportFileName(appName, packageName, version, isSplitBundle)
        val mimeType = if (isSplitBundle) "application/zip" else "application/vnd.android.package-archive"

        var safFallbackTriggered = false
        if (customFolderUri.isNotBlank()) {
            val safResult = runCatching {
                val treeUri = Uri.parse(customFolderUri)
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocUri,
                    mimeType,
                    fileName
                ) ?: throw IOException("Failed to create SAF document for $fileName")

                context.contentResolver.openOutputStream(createdUri)?.use { out ->
                    writeApkOrBundleToStream(validFiles, out)
                } ?: throw IOException("Could not open SAF output stream")

                ApkExportResult(
                    success = true,
                    fileName = fileName,
                    displayLocation = "${resolveSafFolderDisplayName(customFolderUri)}/$fileName",
                    usedSafFallback = false
                )
            }
            if (safResult.isSuccess) {
                return safResult.getOrThrow()
            }
            safFallbackTriggered = true
        }

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$PUBLIC_EXPORT_SUBFOLDER"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore failed to allocate Download/$PUBLIC_EXPORT_SUBFOLDER/$fileName")
                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        writeApkOrBundleToStream(validFiles, out)
                    } ?: throw IOException("Could not open MediaStore output stream")

                    val completedValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, completedValues, null, null)
                } catch (err: Throwable) {
                    runCatching { resolver.delete(itemUri, null, null) }
                    throw err
                }
                ApkExportResult(
                    success = true,
                    fileName = fileName,
                    displayLocation = "Download/$PUBLIC_EXPORT_SUBFOLDER/$fileName",
                    usedSafFallback = safFallbackTriggered
                )
            } else {
                @Suppress("DEPRECATION")
                val downloadsRoot = legacyDownloadsDirOverrideForTest
                    ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsRoot, PUBLIC_EXPORT_SUBFOLDER).apply { mkdirs() }
                val destFile = resolveUniqueLegacyFile(targetDir, fileName)
                destFile.outputStream().use { out ->
                    writeApkOrBundleToStream(validFiles, out)
                }
                ApkExportResult(
                    success = true,
                    fileName = fileName,
                    displayLocation = "Download/$PUBLIC_EXPORT_SUBFOLDER/${destFile.name}",
                    usedSafFallback = safFallbackTriggered
                )
            }
        }.getOrElse { err ->
            ApkExportResult(
                success = false,
                fileName = fileName,
                displayLocation = "Download/$PUBLIC_EXPORT_SUBFOLDER/$fileName",
                errorMessage = err.message ?: err.javaClass.simpleName,
                usedSafFallback = safFallbackTriggered
            )
        }
    }

    private fun resolveUniqueLegacyFile(dir: File, fileName: String): File {
        val candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val stem = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var index = 1
        while (true) {
            val numbered = File(dir, "$stem ($index)$ext")
            if (!numbered.exists()) return numbered
            index++
        }
    }
}
