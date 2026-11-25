package com.apkup.shared.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KmpEngineBenchmarkSnapshot(
    val engineId: String = "kmp-shared",
    val engineLabel: String = "Kotlin Multiplatform Core (:shared · Ktor)",
    val lastScanDurationMs: Long = 0L,
    val lastVersionBatchMicros: Long = 0L,
    val lastVersionBatchCount: Int = 0,
    val lastSha256DurationMs: Double = 0.0,
    val lastSha256Bytes: Long = 0L,
    val lastSha256ThroughputMbps: Double = 0.0,
    val lastFDroidJarVerifyMs: Double = 0.0,
    val lastFDroidParseMs: Double = 0.0,
    val lastFDroidMatchedPackages: Int = 0
)

object KmpTelemetryRegistry {
    private val _snapshot = MutableStateFlow(KmpEngineBenchmarkSnapshot())
    val snapshot: StateFlow<KmpEngineBenchmarkSnapshot> = _snapshot.asStateFlow()

    fun resetScanCounters() {
        _snapshot.update {
            it.copy(
                lastVersionBatchMicros = 0L,
                lastVersionBatchCount = 0,
                lastFDroidJarVerifyMs = 0.0,
                lastFDroidParseMs = 0.0,
                lastFDroidMatchedPackages = 0
            )
        }
    }

    fun recordScanDuration(durationMs: Long) {
        _snapshot.update { it.copy(lastScanDurationMs = durationMs.coerceAtLeast(0L)) }
    }

    fun recordVersionBatch(elapsedMicros: Long, count: Int) {
        _snapshot.update {
            it.copy(
                lastVersionBatchMicros = (it.lastVersionBatchMicros + elapsedMicros).coerceAtLeast(0L),
                lastVersionBatchCount = it.lastVersionBatchCount + count
            )
        }
    }

    fun recordSha256(elapsedMicros: Long, bytesHashed: Long) {
        val ms = elapsedMicros / 1000.0
        val mbps = if (elapsedMicros > 0L && bytesHashed > 0L) {
            (bytesHashed.toDouble() / (1024.0 * 1024.0)) / (elapsedMicros.toDouble() / 1_000_000.0)
        } else 0.0
        _snapshot.update {
            it.copy(
                lastSha256DurationMs = ms,
                lastSha256Bytes = bytesHashed,
                lastSha256ThroughputMbps = if (mbps > 0.0) mbps else it.lastSha256ThroughputMbps
            )
        }
    }

    fun recordFDroidJarVerify(elapsedMicros: Long) {
        _snapshot.update { it.copy(lastFDroidJarVerifyMs = elapsedMicros / 1000.0) }
    }

    fun recordFDroidParse(elapsedMicros: Long, matchedPackages: Int) {
        _snapshot.update {
            it.copy(
                lastFDroidParseMs = it.lastFDroidParseMs + (elapsedMicros / 1000.0),
                lastFDroidMatchedPackages = it.lastFDroidMatchedPackages + matchedPackages
            )
        }
    }
}
