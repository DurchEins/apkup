package com.apkup

import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.viewmodel.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTest {

    @Test
    fun backupExportAndImportIncludesIzzyOnDroidSetting() {
        val storage = InMemoryPrefsBackend()
        val prefs = Prefs(storage)
        val viewModel = SettingsViewModel(prefs = prefs)

        // Set useIzzyOnDroid, useGitHub, useGitLab to false and verify export
        viewModel.setUseIzzyOnDroid(false)
        viewModel.setUseGitHub(false)
        viewModel.setUseGitLab(false)
        assertFalse(viewModel.getUseIzzyOnDroid())
        assertFalse(viewModel.getUseGitHub())
        assertFalse(viewModel.getUseGitLab())

        val exportedJson = viewModel.exportBackupJson()
        assertTrue(exportedJson.contains("\"useIzzyOnDroid\": false"))
        assertTrue(exportedJson.contains("\"useGitHub\": false"))
        assertTrue(exportedJson.contains("\"useGitLab\": false"))

        // Reset to true
        viewModel.setUseIzzyOnDroid(true)
        viewModel.setUseGitHub(true)
        viewModel.setUseGitLab(true)
        assertTrue(viewModel.getUseIzzyOnDroid())
        assertTrue(viewModel.getUseGitHub())
        assertTrue(viewModel.getUseGitLab())

        // Re-import and verify restored to false
        val success = viewModel.importBackupJson(exportedJson)
        assertTrue(success)
        assertFalse(viewModel.getUseIzzyOnDroid())
        assertFalse(viewModel.getUseGitHub())
        assertFalse(viewModel.getUseGitLab())
    }

    @Test
    fun backupExportAndImportPreservesAutoInstallSettingsAndLegacyDefaults() {
        val storage = InMemoryPrefsBackend()
        val prefs = Prefs(storage)
        val viewModel = SettingsViewModel(prefs = prefs)

        viewModel.setAutoInstallUpdates(true)
        viewModel.setAutoInstallWifiOnly(false)
        viewModel.setAutoInstallChargingOnly(false)

        val exportedJson = viewModel.exportBackupJson()
        assertTrue(exportedJson.contains("\"autoInstallUpdates\": true"))
        assertTrue(exportedJson.contains("\"autoInstallWifiOnly\": false"))
        assertTrue(exportedJson.contains("\"autoInstallChargingOnly\": false"))

        // Reset and restore
        viewModel.setAutoInstallUpdates(false)
        viewModel.setAutoInstallWifiOnly(true)
        viewModel.setAutoInstallChargingOnly(true)
        assertTrue(viewModel.importBackupJson(exportedJson))
        assertTrue(viewModel.getAutoInstallUpdates())
        assertFalse(viewModel.getAutoInstallWifiOnly())
        assertFalse(viewModel.getAutoInstallChargingOnly())

        // Legacy v1.40 backup JSON without autoInstall* keys must default guards safely to true
        val legacyJson = """
            {
              "schemaVersion": 1,
              "useIzzyOnDroid": true,
              "useGitHub": true,
              "useGitLab": true
            }
        """.trimIndent()
        assertTrue(viewModel.importBackupJson(legacyJson))
        assertFalse(viewModel.getAutoInstallUpdates())
        assertTrue(viewModel.getAutoInstallWifiOnly())
        assertTrue(viewModel.getAutoInstallChargingOnly())

        // Verify updateHistory round-trip in JSON backup
        prefs.recordUpdateHistory(
            UpdateHistoryEntry(
                packageName = "org.schabi.newpipe",
                appName = "NewPipe",
                oldVersion = "0.27.2",
                newVersion = "0.27.3",
                oldVersionCode = 999L,
                newVersionCode = 1000L,
                sourceName = "GitHub",
                installedAtMillis = 1_700_000_000_000L,
                installModeName = UpdateInstallMode.AUTO_SHIZUKU.name,
                previousVersionUrl = "https://github.com/TeamNewPipe/NewPipe/releases?q=0.27.2"
            )
        )
        val historyBackup = viewModel.exportBackupJson()
        prefs.clearUpdateHistory()
        assertTrue(prefs.readUpdateHistorySafe().isEmpty())

        assertTrue(viewModel.importBackupJson(historyBackup))
        val restoredHistory = prefs.readUpdateHistorySafe()
        assertEquals(1, restoredHistory.size)
        assertEquals("org.schabi.newpipe", restoredHistory.first().packageName)
        assertEquals("https://github.com/TeamNewPipe/NewPipe/releases?q=0.27.2", restoredHistory.first().previousVersionUrl)
    }
}
