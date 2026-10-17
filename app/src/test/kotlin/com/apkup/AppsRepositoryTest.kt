package com.apkup

import android.content.pm.ApplicationInfo
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.getPackageNames
import com.apkup.repository.includeSystemApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsRepositoryTest {

    @Test
    fun systemAndUpdatedSystemPackagesAreHiddenUnlessEnabled() {
        val system = ApplicationInfo.FLAG_SYSTEM
        val updatedSystem = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

        assertFalse(includeSystemApp(system, showSystemApps = false))
        assertFalse(includeSystemApp(updatedSystem, showSystemApps = false))
        assertTrue(includeSystemApp(system or updatedSystem, showSystemApps = true))
        assertTrue(includeSystemApp(0, showSystemApps = false))
    }

    @Test
    fun manuallyIgnoredAppsStayExcludedFromUpdateQueries() {
        val apps = listOf(
            AppInstalled("Visible", "example.visible", "1", 1),
            AppInstalled("Ignored", "example.ignored", "1", 1, ignored = true)
        )

        assertEquals(listOf("example.visible"), apps.getPackageNames())
    }
}
