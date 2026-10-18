package com.apkup

import android.content.SharedPreferences
import com.apkup.prefs.Prefs
import com.apkup.viewmodel.SettingsViewModel
import com.kryptoprefs.preferences.KryptoPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreTest {

    private class TestKryptoPrefs : KryptoPrefs {
        private val values = mutableMapOf<String, Any>()

        override fun putString(key: String, value: String?): KryptoPrefs = put(key, value)
        override fun putBoolean(key: String, value: Boolean?): KryptoPrefs = put(key, value)
        override fun putInt(key: String, value: Int?): KryptoPrefs = put(key, value)
        override fun putLong(key: String, value: Long?): KryptoPrefs = put(key, value)

        override fun getString(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun remove(key: String): KryptoPrefs = apply { values.remove(key) }
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun clear() = values.clear()
        override fun name(): String = "unit-test"
        override fun sharedPreferences(): SharedPreferences = error("Not used by these preference tests")

        private fun put(key: String, value: Any?): KryptoPrefs = apply {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    @Test
    fun backupExportAndImportIncludesIzzyOnDroidSetting() {
        val storage = TestKryptoPrefs()
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
}
