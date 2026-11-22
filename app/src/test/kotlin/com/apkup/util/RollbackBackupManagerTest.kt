package com.apkup.util

import android.content.ContextWrapper
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RollbackBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createFakeContext(filesDir: File): ContextWrapper =
        object : ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
        }

    private fun writeBackupEntry(
        manager: RollbackBackupManager,
        pkg: String,
        backedUpAtMillis: Long,
        payloadSizeBytes: Int
    ) {
        val pkgDir = File(manager.rootDir, pkg).apply { mkdirs() }
        File(pkgDir, "base.apk").writeBytes(ByteArray(payloadSizeBytes) { 1 })
        val meta = RollbackBackupMeta(
            packageName = pkg,
            appName = pkg,
            versionName = "1.0.0",
            versionCode = 1L,
            backedUpAtMillis = backedUpAtMillis,
            totalSizeBytes = payloadSizeBytes.toLong(),
            partCount = 1
        )
        File(pkgDir, "meta.json").writeText(Gson().toJson(meta), Charsets.UTF_8)
    }

    @Test
    fun cleanExpiredBackupsRemovesTtlExpiredAndEnforcesReducedBudgetLru() {
        val manager = RollbackBackupManager(createFakeContext(tempFolder.newFolder("files")))
        val dayMs = 86_400_000L
        val now = 100L * dayMs

        writeBackupEntry(manager, "com.expired.old", backedUpAtMillis = now - 20L * dayMs, payloadSizeBytes = 1000)
        writeBackupEntry(manager, "com.recent.older", backedUpAtMillis = now - 5L * dayMs, payloadSizeBytes = 1000)
        writeBackupEntry(manager, "com.recent.newest", backedUpAtMillis = now - 1L * dayMs, payloadSizeBytes = 1000)

        assertEquals(3 to 3000L, manager.getBackupStats())

        // 14-day TTL deletes com.expired.old; 1500-byte budget then LRU-evicts com.recent.older
        val removed = manager.cleanExpiredBackups(
            maxAgeDays = 14,
            maxTotalBytes = 1500L,
            nowMillis = now
        )

        assertEquals(2, removed)
        assertNull(manager.getBackupMeta("com.expired.old"))
        assertNull(manager.getBackupMeta("com.recent.older"))
        assertNotNull(manager.getBackupMeta("com.recent.newest"))
        assertEquals(1 to 1000L, manager.getBackupStats())

        manager.clearAllBackups()
        assertEquals(0 to 0L, manager.getBackupStats())
    }

    @Test
    fun cleanExpiredBackupsWithNeverTtlKeepsOldBackupsWhenWithinBudget() {
        val manager = RollbackBackupManager(createFakeContext(tempFolder.newFolder("files_never")))
        val dayMs = 86_400_000L
        val now = 200L * dayMs

        writeBackupEntry(manager, "com.ancient.app", backedUpAtMillis = now - 120L * dayMs, payloadSizeBytes = 500)

        val removed = manager.cleanExpiredBackups(
            maxAgeDays = 0,
            maxTotalBytes = 2048L,
            nowMillis = now
        )

        assertEquals(0, removed)
        assertNotNull(manager.getBackupMeta("com.ancient.app"))
        assertEquals(1 to 500L, manager.getBackupStats())
    }
}
