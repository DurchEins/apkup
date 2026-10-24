package com.apkup

import android.app.Activity
import com.apkup.util.AppLifecycleTracker
import com.apkup.util.SessionInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionInstallerBalTest {

    private class StubActivity : Activity()

    @Test
    fun `AppLifecycleTracker accurately tracks foreground transitions across activities`() {
        val activity1 = StubActivity()
        val activity2 = StubActivity()

        // Starting state should be false if no activities have started or after stopping
        while (AppLifecycleTracker.isAppInForeground) {
            AppLifecycleTracker.onActivityStopped(activity1)
        }
        assertFalse(AppLifecycleTracker.isAppInForeground)

        // First activity starts -> app enters foreground
        AppLifecycleTracker.onActivityStarted(activity1)
        assertTrue("App must be in foreground when activity 1 is started", AppLifecycleTracker.isAppInForeground)

        // Second activity starts (e.g. SettingsActivity opened over MainActivity)
        AppLifecycleTracker.onActivityStarted(activity2)
        assertTrue("App must remain in foreground when activity 2 is started", AppLifecycleTracker.isAppInForeground)

        // Activity 1 stopped (covered) -> still in foreground because Activity 2 is visible
        AppLifecycleTracker.onActivityStopped(activity1)
        assertTrue("App must remain in foreground as long as at least 1 activity is started", AppLifecycleTracker.isAppInForeground)

        // Activity 2 stopped -> app placed in background
        AppLifecycleTracker.onActivityStopped(activity2)
        assertFalse("App must be in background when all activities are stopped", AppLifecycleTracker.isAppInForeground)
    }

    @Test
    fun `SessionInstaller declares required BAL and notification constants`() {
        assertEquals("installAction", SessionInstaller.INSTALL_ACTION)
        assertEquals("com.apkup.installCancelAction", SessionInstaller.INSTALL_CANCEL_ACTION)
        assertEquals(8000, SessionInstaller.NOTIFICATION_ID_CONFIRM_BASE)
        assertEquals("apkup_install_confirm_channel", SessionInstaller.CONFIRM_NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun `strings xml contains all required notification channel and BAL prompt resources`() {
        val stringsFile = File("src/main/res/values/strings.xml").takeIf { it.exists() }
            ?: File("app/src/main/res/values/strings.xml")

        assertTrue("strings.xml must exist", stringsFile.exists())
        val content = stringsFile.readText()

        assertTrue(content.contains("name=\"notification_channel_downloads\""))
        assertTrue(content.contains("name=\"notification_channel_downloads_desc\""))
        assertTrue(content.contains("name=\"action_cancel\""))
        assertTrue(content.contains("name=\"notification_download_starting\""))
        assertTrue(content.contains("name=\"notification_download_progress\""))
        assertTrue(content.contains("name=\"notification_install_channel_name\""))
        assertTrue(content.contains("name=\"notification_install_channel_desc\""))
        assertTrue(content.contains("name=\"notification_install_confirm_title\""))
        assertTrue(content.contains("name=\"notification_install_confirm_text\""))
    }
}
