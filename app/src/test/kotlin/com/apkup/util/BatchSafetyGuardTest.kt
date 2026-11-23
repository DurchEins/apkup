package com.apkup.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchSafetyGuardTest {

    @Test
    fun `evaluateBatchSafetyStatus flags low battery only when not charging`() {
        val lowUnplugged = evaluateBatchSafetyStatus(
            batteryPercent = 12,
            isCharging = false,
            freeStorageBytes = 2L * 1024 * 1024 * 1024
        )
        assertTrue(lowUnplugged.isLowBattery)
        assertFalse(lowUnplugged.isLowStorage)
        assertTrue(lowUnplugged.hasAnyWarning)

        val lowCharging = evaluateBatchSafetyStatus(
            batteryPercent = 12,
            isCharging = true,
            freeStorageBytes = 2L * 1024 * 1024 * 1024
        )
        assertTrue(lowCharging.isLowBattery)
        assertFalse(lowCharging.hasAnyWarning)
    }

    @Test
    fun `evaluateBatchSafetyStatus flags low storage under 500 MB`() {
        val lowStorage = evaluateBatchSafetyStatus(
            batteryPercent = 80,
            isCharging = false,
            freeStorageBytes = 350L * 1024 * 1024
        )
        assertFalse(lowStorage.isLowBattery)
        assertTrue(lowStorage.isLowStorage)
        assertTrue(lowStorage.hasAnyWarning)
        assertEquals(350L * 1024 * 1024, lowStorage.freeStorageBytes)

        val healthy = evaluateBatchSafetyStatus(
            batteryPercent = 15,
            isCharging = false,
            freeStorageBytes = 500L * 1024 * 1024
        )
        assertFalse(healthy.isLowBattery)
        assertFalse(healthy.isLowStorage)
        assertFalse(healthy.hasAnyWarning)
    }
}
