package com.apkup

import com.apkup.service.DownloadTaskState
import com.apkup.util.BatchProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchProgressCalculatorTest {

    @Test
    fun calculateComputesBatchPercentSpeedAndEtaForKnownSizes() {
        val completed = listOf(
            DownloadTaskState(id = 1, packageName = "a", appName = "A", progress = 2_000_000L, total = 2_000_000L, isQueued = false)
        )
        val active = listOf(
            DownloadTaskState(id = 2, packageName = "b", appName = "B", progress = 3_000_000L, total = 6_000_000L, isQueued = false),
            DownloadTaskState(id = 3, packageName = "c", appName = "C", progress = 0L, total = 2_000_000L, isQueued = true)
        )

        val summary = BatchProgressCalculator.calculate(
            activeTasks = active,
            completedTasks = completed,
            batchStartedAtMs = 10_000L,
            nowMs = 15_000L
        )

        assertNotNull(summary)
        summary!!
        assertEquals(2, summary.currentStep)
        assertEquals(3, summary.totalCount)
        assertEquals(5_000_000L, summary.transferredBytes)
        assertEquals(10_000_000L, summary.totalKnownBytes)
        assertEquals(50, summary.overallPercent)
        assertEquals(1_000_000L, summary.speedBytesPerSec)
        assertEquals(5L, summary.etaSeconds)
    }

    @Test
    fun calculateFallsBackToItemWeightedPercentWhenSizesMissing() {
        val completed = listOf(
            DownloadTaskState(id = 1, packageName = "a", appName = "A", progress = 0L, total = 0L, isQueued = false)
        )
        val active = listOf(
            DownloadTaskState(id = 2, packageName = "b", appName = "B", progress = 500_000L, total = 1_000_000L, isQueued = false),
            DownloadTaskState(id = 3, packageName = "c", appName = "C", progress = 0L, total = 0L, isQueued = true)
        )

        val summary = BatchProgressCalculator.calculate(
            activeTasks = active,
            completedTasks = completed,
            batchStartedAtMs = 10_000L,
            nowMs = 13_000L
        )

        assertNotNull(summary)
        summary!!
        assertEquals(50, summary.overallPercent)
        assertNull(summary.etaSeconds)
    }

    @Test
    fun formatHelpersProduceReadableStrings() {
        assertEquals("~45 s", BatchProgressCalculator.formatEta(45L))
        assertEquals("~2 min 5 s", BatchProgressCalculator.formatEta(125L))
        assertEquals("~1 h 5 min", BatchProgressCalculator.formatEta(3900L))
        assertTrue(BatchProgressCalculator.formatSpeed(512_000L).contains("KB/s"))
        assertTrue(BatchProgressCalculator.formatSpeed(2_500_000L).contains("MB/s"))
    }

    @Test
    fun formatHelpersSupportLocalizedUnits() {
        val ruUnits = com.apkup.util.BatchProgressUnits(
            secondsShort = "с",
            minutesShort = "мин",
            hoursShort = "ч",
            bytesPerSec = "Б/с",
            kilobytesPerSec = "КБ/с",
            megabytesPerSec = "МБ/с"
        )
        assertEquals("~45 с", BatchProgressCalculator.formatEta(45L, ruUnits))
        assertEquals("~2 мин 5 с", BatchProgressCalculator.formatEta(125L, ruUnits))
        assertEquals("~1 ч 5 мин", BatchProgressCalculator.formatEta(3900L, ruUnits))
        assertTrue(BatchProgressCalculator.formatSpeed(500L, ruUnits).contains("Б/с"))
        assertTrue(BatchProgressCalculator.formatSpeed(512_000L, ruUnits).contains("КБ/с"))
        assertTrue(BatchProgressCalculator.formatSpeed(2_500_000L, ruUnits).contains("МБ/с"))
    }
}
