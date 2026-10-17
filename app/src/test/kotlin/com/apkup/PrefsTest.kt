package com.apkup

import android.content.SharedPreferences
import com.apkup.prefs.Prefs
import com.kryptoprefs.preferences.KryptoPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {

    @Test
    fun bothUpdateSourcesAndDailyChecksDefaultToEnabled() {
        val prefs = Prefs(TestKryptoPrefs())

        assertTrue(prefs.useApkMirror.get())
        assertTrue(prefs.usePlay.get())
        assertTrue(prefs.enableBackgroundChecks.get())
    }

    @Test
    fun savedSourceAndBackgroundChoicesOverrideFreshInstallDefaults() {
        val storage = TestKryptoPrefs()
        val firstRunPrefs = Prefs(storage)
        firstRunPrefs.useApkMirror.put(false)
        firstRunPrefs.usePlay.put(false)
        firstRunPrefs.enableBackgroundChecks.put(false)

        val reopenedPrefs = Prefs(storage)

        assertFalse(reopenedPrefs.useApkMirror.get())
        assertFalse(reopenedPrefs.usePlay.get())
        assertFalse(reopenedPrefs.enableBackgroundChecks.get())
    }

    @Test
    fun shizukuAndDisplayModeDefaultsAndSavedValuesRemainAvailable() {
        val storage = TestKryptoPrefs()
        val freshPrefs = Prefs(storage)

        assertFalse(freshPrefs.useShizukuInstaller.get())
        assertEquals(0, freshPrefs.updatesDisplayMode.get())

        freshPrefs.useShizukuInstaller.put(true)
        freshPrefs.updatesDisplayMode.put(1)
        val reopenedPrefs = Prefs(storage)

        assertTrue(reopenedPrefs.useShizukuInstaller.get())
        assertEquals(1, reopenedPrefs.updatesDisplayMode.get())
    }

    @Test
    fun systemAppsAreHiddenByDefaultAndPreferencePersistsWithoutResettingOthers() {
        val storage = TestKryptoPrefs()
        val freshPrefs = Prefs(storage)

        assertFalse(freshPrefs.showSystemApps.get())
        freshPrefs.showSystemApps.put(true)
        freshPrefs.usePlay.put(false)
        freshPrefs.updatesDisplayMode.put(1)

        val reopenedPrefs = Prefs(storage)

        assertTrue(reopenedPrefs.showSystemApps.get())
        assertFalse(reopenedPrefs.usePlay.get())
        assertEquals(1, reopenedPrefs.updatesDisplayMode.get())
    }

    @Test
    fun showSystemAppsFlowEmitsUpdatesImmediatelyWhenChanged() {
        val storage = TestKryptoPrefs()
        val prefs = Prefs(storage)

        assertFalse(prefs.showSystemAppsFlow.value)
        prefs.setShowSystemApps(true)
        assertTrue(prefs.showSystemAppsFlow.value)
        assertTrue(prefs.showSystemApps.get())

        prefs.setShowSystemApps(false)
        assertFalse(prefs.showSystemAppsFlow.value)
        assertFalse(prefs.showSystemApps.get())
    }

    @Test
    fun installPermissionPromptDefaultIsFalseAndPersists() {
        val storage = TestKryptoPrefs()
        val prefs = Prefs(storage)

        assertFalse(prefs.hasSeenInstallPermissionPrompt.get())
        prefs.hasSeenInstallPermissionPrompt.put(true)
        assertTrue(prefs.hasSeenInstallPermissionPrompt.get())
    }

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
}
