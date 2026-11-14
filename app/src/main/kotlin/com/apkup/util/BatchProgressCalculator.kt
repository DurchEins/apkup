package com.apkup.util

import com.apkup.service.DownloadTaskState
import java.util.Locale

data class BatchProgressUnits(
    val secondsShort: String = "s",
    val minutesShort: String = "min",
    val hoursShort: String = "h",
    val bytesPerSec: String = "B/s",
    val kilobytesPerSec: String = "KB/s",
    val megabytesPerSec: String = "MB/s"
)

data class BatchProgressSummary(
    val currentStep: Int,
    val totalCount: Int,
    val completedCount: Int,
    val transferredBytes: Long,
    val totalKnownBytes: Long,
    val overallPercent: Int?,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?
)

/**
 * Computes consistent batch-wide progress, throughput, and ETA across active and completed
 * tasks in [com.apkup.worker.DownloadCoordinator].
 */
object BatchProgressCalculator {

    private const val MIN_ELAPSED_MS_FOR_SPEED = 1_200L
    private const val MIN_BYTES_FOR_SPEED = 32L * 1024L

    fun calculate(
        activeTasks: Collection<DownloadTaskState>,
        completedTasks: Collection<DownloadTaskState>,
        batchStartedAtMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): BatchProgressSummary? {
        if (activeTasks.isEmpty()) return null

        val completedCount = completedTasks.size
        val totalCount = (completedCount + activeTasks.size).coerceAtLeast(1)
        val currentStep = (completedCount + 1).coerceAtMost(totalCount)

        val completedBytes = completedTasks.sumOf { maxOf(it.total, it.progress).coerceAtLeast(0L) }
        val actualTransferredActiveBytes = activeTasks.sumOf { task ->
            if (task.isInstalling && task.total > 0L && task.progress == 0L) 0L else task.progress.coerceAtLeast(0L)
        }
        val effectiveActiveBytes = activeTasks.sumOf { task ->
            if (task.isInstalling && task.total > 0L && task.progress == 0L) {
                task.total.coerceAtLeast(0L)
            } else {
                task.progress.coerceAtLeast(0L)
            }
        }
        val effectiveDoneOrDownloadingBytes = completedBytes + effectiveActiveBytes
        val totalTransferredBytes = completedBytes + actualTransferredActiveBytes

        val activeKnownTotalBytes = activeTasks.sumOf { maxOf(it.total, it.progress).coerceAtLeast(0L) }
        val totalKnownBytes = completedBytes + activeKnownTotalBytes
        val allActiveHaveKnownSize = activeTasks.all { it.total > 0L }

        val elapsedMs = (nowMs - batchStartedAtMs).coerceAtLeast(0L)
        val speedBytesPerSec = if (
            elapsedMs >= MIN_ELAPSED_MS_FOR_SPEED &&
            totalTransferredBytes >= MIN_BYTES_FOR_SPEED
        ) {
            (totalTransferredBytes * 1_000L) / elapsedMs
        } else {
            0L
        }

        val overallPercent: Int? = when {
            allActiveHaveKnownSize && totalKnownBytes > 0L -> {
                ((effectiveDoneOrDownloadingBytes * 100L) / totalKnownBytes).toInt().coerceIn(0, 100)
            }
            totalCount > 1 -> {
                val weightedCompleted = completedCount.toDouble() + activeTasks.sumOf { task ->
                    when {
                        task.isInstalling -> 0.92
                        task.total > 0L -> (task.progress.toDouble() / task.total.toDouble()).coerceIn(0.0, 0.92)
                        task.progress > 0L -> 0.35
                        else -> 0.0
                    }
                }
                ((weightedCompleted / totalCount.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            }
            else -> null
        }

        val remainingKnownBytes = (totalKnownBytes - effectiveDoneOrDownloadingBytes).coerceAtLeast(0L)
        val etaSeconds = if (
            speedBytesPerSec > 0L &&
            allActiveHaveKnownSize &&
            remainingKnownBytes > 0L
        ) {
            (remainingKnownBytes / speedBytesPerSec).coerceAtLeast(1L)
        } else {
            null
        }

        return BatchProgressSummary(
            currentStep = currentStep,
            totalCount = totalCount,
            completedCount = completedCount,
            transferredBytes = effectiveDoneOrDownloadingBytes,
            totalKnownBytes = totalKnownBytes,
            overallPercent = overallPercent,
            speedBytesPerSec = speedBytesPerSec,
            etaSeconds = etaSeconds
        )
    }

    fun formatEta(seconds: Long, units: BatchProgressUnits = BatchProgressUnits()): String {
        val safe = seconds.coerceAtLeast(1L)
        return when {
            safe < 60L -> "~${safe} ${units.secondsShort}"
            safe < 3600L -> {
                val mins = safe / 60L
                val secs = safe % 60L
                if (secs == 0L) {
                    "~${mins} ${units.minutesShort}"
                } else {
                    "~${mins} ${units.minutesShort} ${secs} ${units.secondsShort}"
                }
            }
            else -> {
                val hours = safe / 3600L
                val mins = (safe % 3600L) / 60L
                "~${hours} ${units.hoursShort} ${mins} ${units.minutesShort}"
            }
        }
    }

    fun formatSpeed(bytesPerSec: Long, units: BatchProgressUnits = BatchProgressUnits()): String {
        val safe = bytesPerSec.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe ${units.bytesPerSec}"
            safe < 1024L * 1024L -> "${safe / 1024L} ${units.kilobytesPerSec}"
            else -> "${String.format(Locale.getDefault(), "%.1f", safe / (1024.0 * 1024.0))} ${units.megabytesPerSec}"
        }
    }
}
