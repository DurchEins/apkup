package com.apkup

import com.apkup.ui.component.ScanSourceStatus
import com.apkup.ui.component.groupScanTrayStatuses
import com.apkup.ui.component.resolveScanSourceIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceScanTrayTest {

    @Test
    fun `resolveScanSourceIcon returns correct drawable for each source`() {
        assertEquals(R.drawable.ic_apkmirror, resolveScanSourceIcon("APKMirror"))
        assertEquals(R.drawable.ic_apkmirror, resolveScanSourceIcon("apkmirror"))
        assertEquals(R.drawable.ic_play, resolveScanSourceIcon("Google Play"))
        assertEquals(R.drawable.ic_play, resolveScanSourceIcon("Google Play Store"))
        assertEquals(R.drawable.ic_izzy, resolveScanSourceIcon("IzzyOnDroid"))
        assertEquals(R.drawable.ic_fdroid, resolveScanSourceIcon("F-Droid"))
        assertEquals(R.drawable.ic_github, resolveScanSourceIcon("GitHub"))
        assertEquals(R.drawable.ic_gitlab, resolveScanSourceIcon("GitLab"))
        assertEquals(R.drawable.ic_codeberg, resolveScanSourceIcon("Codeberg"))
        assertEquals(R.drawable.ic_newpipe, resolveScanSourceIcon("NewPipe Repo"))
        assertEquals(R.drawable.ic_microg, resolveScanSourceIcon("microG"))
        assertEquals(R.drawable.ic_guardian, resolveScanSourceIcon("Guardian Project"))
        assertEquals(R.drawable.ic_fdroid, resolveScanSourceIcon("Custom F-Droid Repo"))
    }

    @Test
    fun `ScanSourceStatus properties default correctly`() {
        val status = ScanSourceStatus(name = "Google Play", isScanning = true)
        assertTrue(status.isScanning)
        assertEquals(0, status.updateCount)
        assertFalse(status.isError)

        val updated = status.copy(isScanning = false, updateCount = 4, isError = false)
        assertFalse(updated.isScanning)
        assertEquals(4, updated.updateCount)
    }

    @Test
    fun `groupScanTrayStatuses keeps single F-Droid source ungrouped`() {
        val items = groupScanTrayStatuses(
            listOf(
                ScanSourceStatus("Google Play", isScanning = false),
                ScanSourceStatus("IzzyOnDroid", isScanning = true),
                ScanSourceStatus("GitHub", isScanning = false)
            )
        )
        assertEquals(3, items.size)
        assertEquals("IzzyOnDroid", items[1].status.name)
        assertEquals(R.drawable.ic_izzy, items[1].iconRes)
    }

    @Test
    fun `groupScanTrayStatuses groups multiple F-Droid sources and shows active repo`() {
        val items = groupScanTrayStatuses(
            listOf(
                ScanSourceStatus("APKMirror", isScanning = false, updateCount = 2),
                ScanSourceStatus("IzzyOnDroid", isScanning = false, updateCount = 1),
                ScanSourceStatus("NewPipe Repo", isScanning = true),
                ScanSourceStatus("F-Droid", isScanning = false),
                ScanSourceStatus("GitHub", isScanning = false)
            )
        )
        assertEquals(3, items.size)
        assertEquals("APKMirror", items[0].status.name)
        assertEquals("NewPipe Repo (2/3)", items[1].status.name)
        assertTrue(items[1].status.isScanning)
        assertEquals(R.drawable.ic_newpipe, items[1].iconRes)
        assertEquals(1, items[1].status.updateCount)
        assertEquals("GitHub", items[2].status.name)
    }

    @Test
    fun `groupScanTrayStatuses collapses finished F-Droid group to single circle`() {
        val items = groupScanTrayStatuses(
            listOf(
                ScanSourceStatus("IzzyOnDroid", isScanning = false, updateCount = 1),
                ScanSourceStatus("F-Droid", isScanning = false, updateCount = 2),
                ScanSourceStatus("Guardian Project", isScanning = false, isError = true)
            )
        )
        assertEquals(1, items.size)
        assertEquals("F-Droid (3)", items[0].status.name)
        assertFalse(items[0].status.isScanning)
        assertEquals(3, items[0].status.updateCount)
        assertTrue(items[0].status.isError)
        assertEquals(R.drawable.ic_fdroid, items[0].iconRes)
    }
}
