package com.apkup.util

import android.content.Context
import java.io.File

/**
 * Centralized manager for persistent download storage and staging directories.
 * Uses context.filesDir/downloads to prevent premature deletion by the Android OS cache cleaner.
 */
object DownloadStorageManager {
    private const val DOWNLOADS_DIR_NAME = "downloads"

    fun getDownloadDir(context: Context): File {
        val base = context.filesDir ?: context.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_cache")
        return File(base, DOWNLOADS_DIR_NAME).apply { mkdirs() }
    }

    fun getStagingDir(context: Context, updateId: Int): File {
        return File(getDownloadDir(context), "download_staging_$updateId").apply { mkdirs() }
    }

    fun cleanExpiredDownloads(context: Context, maxAgeMs: Long = Downloader.READY_CACHE_TTL_MS) {
        val dir = File(context.filesDir, DOWNLOADS_DIR_NAME)
        if (!dir.exists() || !dir.isDirectory) return

        val now = System.currentTimeMillis()
        val stagingDirs = dir.listFiles { file ->
            file.isDirectory && file.name.startsWith("download_staging_")
        } ?: return

        for (staging in stagingDirs) {
            val marker = File(staging, Downloader.READY_MARKER_FILE)
            if (marker.exists()) {
                val parts = marker.readText().trim().split(":")
                val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: marker.lastModified()
                if (now - timestamp > maxAgeMs) {
                    staging.deleteRecursively()
                }
            } else {
                // If it's an abandoned non-ready staging dir older than 2 hours, clean it up
                if (now - staging.lastModified() > 2 * 60 * 60 * 1000L) {
                    staging.deleteRecursively()
                }
            }
        }
    }
}
