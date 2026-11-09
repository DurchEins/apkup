package com.apkup

import com.apkup.ui.component.ScanSourceStatus
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
        assertEquals(R.drawable.ic_izzy, resolveScanSourceIcon("F-Droid"))
        assertEquals(R.drawable.ic_github, resolveScanSourceIcon("GitHub"))
        assertEquals(R.drawable.ic_gitlab, resolveScanSourceIcon("GitLab"))
        assertEquals(R.drawable.ic_refresh, resolveScanSourceIcon("Custom Unknown Source"))
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
}
