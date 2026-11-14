package com.apkup

import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {

    @Test
    fun bothUpdateSourcesAndDailyChecksDefaultToEnabled() {
        val prefs = Prefs(InMemoryPrefsBackend())

        assertTrue(prefs.useApkMirror.get())
        assertTrue(prefs.usePlay.get())
        assertTrue(prefs.useIzzyOnDroid.get())
        assertTrue(prefs.enableBackgroundChecks.get())
    }

    @Test
    fun savedSourceAndBackgroundChoicesOverrideFreshInstallDefaults() {
        val storage = InMemoryPrefsBackend()
        val firstRunPrefs = Prefs(storage)
        firstRunPrefs.useApkMirror.put(false)
        firstRunPrefs.usePlay.put(false)
        firstRunPrefs.useIzzyOnDroid.put(false)
        firstRunPrefs.enableBackgroundChecks.put(false)

        val reopenedPrefs = Prefs(storage)

        assertFalse(reopenedPrefs.useApkMirror.get())
        assertFalse(reopenedPrefs.usePlay.get())
        assertFalse(reopenedPrefs.useIzzyOnDroid.get())
        assertFalse(reopenedPrefs.enableBackgroundChecks.get())
    }

    @Test
    fun shizukuAndDisplayModeDefaultsAndSavedValuesRemainAvailable() {
        val storage = InMemoryPrefsBackend()
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
        val storage = InMemoryPrefsBackend()
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
        val storage = InMemoryPrefsBackend()
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
        val storage = InMemoryPrefsBackend()
        val prefs = Prefs(storage)

        assertFalse(prefs.hasSeenInstallPermissionPrompt.get())
        prefs.hasSeenInstallPermissionPrompt.put(true)
        assertTrue(prefs.hasSeenInstallPermissionPrompt.get())
    }
}
