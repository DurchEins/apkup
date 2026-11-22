package com.apkup.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class RollbackBackupMeta(
    @SerializedName("packageName") val packageName: String = "",
    @SerializedName("appName") val appName: String = "",
    @SerializedName("versionName") val versionName: String = "",
    @SerializedName("versionCode") val versionCode: Long = 0L,
    @SerializedName("backedUpAtMillis") val backedUpAtMillis: Long = 0L,
    @SerializedName("totalSizeBytes") val totalSizeBytes: Long = 0L,
    @SerializedName("partCount") val partCount: Int = 1
)

class RollbackBackupManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "RollbackBackupManager"
        private const val BACKUP_DIR_NAME = "rollback_backups"
        private const val META_FILE_NAME = "meta.json"
        private const val MILLIS_PER_DAY = 86_400_000L
        const val DEFAULT_MAX_BUDGET_MB: Int = 1024
        const val DEFAULT_MAX_AGE_DAYS: Int = 14
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 2L * 1024L * 1024L * 1024L // 2 GB
        val BUDGET_OPTIONS_MB: List<Int> = listOf(256, 512, 1024, 2048)
        val MAX_AGE_OPTIONS_DAYS: List<Int> = listOf(7, 14, 30, 0)

        fun normalizeBudgetMb(rawMb: Int): Int =
            if (rawMb in BUDGET_OPTIONS_MB) rawMb else DEFAULT_MAX_BUDGET_MB

        fun budgetMbToBytes(budgetMb: Int): Long =
            normalizeBudgetMb(budgetMb).toLong() * 1024L * 1024L

        fun normalizeMaxAgeDays(rawDays: Int): Int =
            if (rawDays in MAX_AGE_OPTIONS_DAYS) rawDays else DEFAULT_MAX_AGE_DAYS
    }

    private val gson = Gson()
    private val lock = Any()

    internal val rootDir: File
        get() = File(context.filesDir, BACKUP_DIR_NAME)

    /**
     * Captures the currently installed base APK and any split APKs of [packageName] into private
     * app storage before an update is installed. Keeps only the latest backup per app, deletes
     * backups older than [maxAgeDays] when > 0, and enforces [maxTotalBytes] via LRU eviction.
     */
    fun backupInstalledPackageBeforeUpdate(
        packageName: String,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        maxAgeDays: Int = 0
    ): RollbackBackupMeta? = synchronized(lock) {
        val cleanPkg = packageName.trim()
        val pkgDir = getPackageBackupDir(cleanPkg) ?: return null
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(cleanPkg, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(cleanPkg, 0)
            }
            val appInfo = pkgInfo.applicationInfo ?: return null
            val baseSource = appInfo.sourceDir?.let(::File)?.takeIf { it.isFile && it.canRead() }
                ?: return null
            val splitFiles = appInfo.splitSourceDirs
                ?.map(::File)
                ?.filter { it.isFile && it.canRead() }
                .orEmpty()

            val versionCode = pkgInfo.longVersionCode
            val versionName = pkgInfo.versionName.orEmpty().ifBlank { versionCode.toString() }
            val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(cleanPkg)
                .ifBlank { cleanPkg }

            // Skip re-copying if we already have an identical backup for this exact versionCode
            val existing = getBackupMeta(cleanPkg)
            if (existing != null && existing.versionCode == versionCode && hasValidApkFiles(pkgDir)) {
                return existing
            }

            val safePkg = pkgDir.name
            val tempDir = File(rootDir, "$safePkg.tmp")
            if (!isStrictChildOfRoot(tempDir)) return null
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            var copiedBytes = 0L
            val destBase = File(tempDir, "base.apk")
            baseSource.inputStream().use { input ->
                destBase.outputStream().use { output ->
                    copiedBytes += input.copyTo(output)
                }
            }

            splitFiles.forEachIndexed { idx, splitFile ->
                val rawName = splitFile.name.takeIf { it.endsWith(".apk", ignoreCase = true) && it != "base.apk" }
                    ?: "split_$idx.apk"
                val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val destSplit = File(tempDir, safeName)
                splitFile.inputStream().use { input ->
                    destSplit.outputStream().use { output ->
                        copiedBytes += input.copyTo(output)
                    }
                }
            }

            val meta = RollbackBackupMeta(
                packageName = cleanPkg,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                backedUpAtMillis = System.currentTimeMillis(),
                totalSizeBytes = copiedBytes,
                partCount = 1 + splitFiles.size
            )
            File(tempDir, META_FILE_NAME).writeText(gson.toJson(meta), Charsets.UTF_8)

            if (pkgDir.exists()) pkgDir.deleteRecursively()
            if (!tempDir.renameTo(pkgDir)) {
                tempDir.copyRecursively(pkgDir, overwrite = true)
                tempDir.deleteRecursively()
            }

            cleanExpiredBackupsInternal(maxAgeDays, System.currentTimeMillis())
            evictOldestIfOverLimit(maxTotalBytes, keepPackage = cleanPkg)
            meta
        }.onFailure { err ->
            Log.w(TAG, "Could not create rollback backup for $cleanPkg: ${err.message}")
        }.getOrNull()
    }

    fun getBackupMeta(packageName: String): RollbackBackupMeta? = synchronized(lock) {
        val dir = getPackageBackupDir(packageName) ?: return null
        val metaFile = File(dir, META_FILE_NAME)
        if (!metaFile.isFile || !hasValidApkFiles(dir)) return null
        return runCatching {
            gson.fromJson(metaFile.readText(Charsets.UTF_8), RollbackBackupMeta::class.java)
        }.getOrNull()
    }

    fun getBackupTargetFileOrDir(packageName: String): File? = synchronized(lock) {
        val dir = getPackageBackupDir(packageName) ?: return null
        if (!hasValidApkFiles(dir)) return null
        val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }.orEmpty()
        return if (apks.size == 1) apks.first() else dir
    }

    fun listAllBackups(): List<RollbackBackupMeta> = synchronized(lock) {
        val root = rootDir
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory && !it.name.endsWith(".tmp") }
            ?.mapNotNull { dir ->
                val metaFile = File(dir, META_FILE_NAME)
                if (!metaFile.isFile || !hasValidApkFiles(dir)) return@mapNotNull null
                runCatching {
                    gson.fromJson(metaFile.readText(Charsets.UTF_8), RollbackBackupMeta::class.java)
                }.getOrNull()
            }
            ?.sortedByDescending { it.backedUpAtMillis }
            .orEmpty()
    }

    fun totalBackupBytes(): Long = synchronized(lock) {
        listAllBackups().sumOf { it.totalSizeBytes }
    }

    fun getBackupStats(): Pair<Int, Long> = synchronized(lock) {
        val all = listAllBackups()
        all.size to all.sumOf { it.totalSizeBytes }
    }

    fun cleanExpiredBackups(
        maxAgeDays: Int,
        maxTotalBytes: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Int = synchronized(lock) {
        val expiredDeleted = cleanExpiredBackupsInternal(maxAgeDays, nowMillis)
        val beforeEvict = listAllBackups().size
        evictOldestIfOverLimit(maxTotalBytes.coerceAtLeast(0L), keepPackage = "")
        val afterEvict = listAllBackups().size
        expiredDeleted + (beforeEvict - afterEvict).coerceAtLeast(0)
    }

    private fun cleanExpiredBackupsInternal(maxAgeDays: Int, nowMillis: Long): Int {
        if (maxAgeDays <= 0) return 0
        val maxAgeMs = maxAgeDays.toLong() * MILLIS_PER_DAY
        var deleted = 0
        for (item in listAllBackups()) {
            if (item.backedUpAtMillis > 0L && (nowMillis - item.backedUpAtMillis) > maxAgeMs) {
                if (deleteBackup(item.packageName)) {
                    deleted++
                }
            }
        }
        return deleted
    }

    fun deleteBackup(packageName: String): Boolean = synchronized(lock) {
        val dir = getPackageBackupDir(packageName) ?: return false
        if (!dir.exists()) return false
        dir.deleteRecursively()
    }

    fun clearAllBackups() = synchronized(lock) {
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
    }

    /**
     * Exports a stored rollback backup to a user-selected SAF [destinationUri].
     * Single-APK backups are written directly as `.apk`; split backups are bundled as `.apks` (ZIP).
     */
    fun exportBackupToUri(packageName: String, destinationUri: Uri): Boolean = synchronized(lock) {
        val dir = getPackageBackupDir(packageName) ?: return false
        val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (apks.isEmpty()) return false
        return runCatching {
            val outStream = context.contentResolver.openOutputStream(destinationUri) ?: return false
            outStream.use { rawOut ->
                if (apks.size == 1) {
                    apks.first().inputStream().use { input -> input.copyTo(rawOut) }
                } else {
                    ZipOutputStream(BufferedOutputStream(rawOut)).use { zos ->
                        for (apk in apks) {
                            zos.putNextEntry(ZipEntry(apk.name))
                            apk.inputStream().use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun getPackageBackupDir(packageName: String): File? {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == ".." || trimmed.contains('/') || trimmed.contains('\\')) {
            return null
        }
        val safeName = trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safeName.isEmpty() || safeName == "." || safeName == ".." || safeName.all { it == '.' }) {
            return null
        }
        val candidate = File(rootDir, safeName)
        return candidate.takeIf { isStrictChildOfRoot(it) }
    }

    private fun isStrictChildOfRoot(candidate: File): Boolean = runCatching {
        val rootCanonical = rootDir.canonicalFile
        val candCanonical = candidate.canonicalFile
        candCanonical.parentFile == rootCanonical && candCanonical != rootCanonical
    }.getOrDefault(false)

    private fun hasValidApkFiles(dir: File): Boolean {
        if (!dir.isDirectory) return false
        val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) && f.length() > 0L }
        return !apks.isNullOrEmpty()
    }

    private fun evictOldestIfOverLimit(maxTotalBytes: Long, keepPackage: String) {
        val all = listAllBackups().toMutableList()
        var total = all.sumOf { it.totalSizeBytes }
        val oldestFirst = all.sortedBy { it.backedUpAtMillis }
        for (item in oldestFirst) {
            if (total <= maxTotalBytes) break
            if (item.packageName.equals(keepPackage, ignoreCase = true)) continue
            if (deleteBackup(item.packageName)) {
                total = (total - item.totalSizeBytes).coerceAtLeast(0L)
            }
        }
    }
}
