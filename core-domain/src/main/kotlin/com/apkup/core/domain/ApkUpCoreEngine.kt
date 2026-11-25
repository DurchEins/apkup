package com.apkup.core.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BatchVersionEvalInput(
    val id: String,
    val currentVersion: String,
    val candidateTag: String
)

data class BatchVersionEvalOutput(
    val id: String,
    val cleanedCandidate: String,
    val isNewer: Boolean,
    val isPreRelease: Boolean,
    val preReleaseChannel: String?
)

data class ApkAssetInput(
    val index: Int,
    val filename: String
)

data class Sha256Result(
    val sha256Hex: String,
    val bytesHashed: Long,
    val elapsedMicros: Long
)

data class Sha256VerifyResult(
    val actualSha256Hex: String,
    val matches: Boolean,
    val bytesHashed: Long,
    val elapsedMicros: Long
)

data class FDroidJarVerifyResult(
    val signerSha256Hex: String,
    val extractedBytes: Long,
    val elapsedMicros: Long
)

data class FDroidFilteredJsonResult(
    val filteredJson: String,
    val matchedPackageCount: Int,
    val elapsedMicros: Long
)

data class EngineBenchmarkSnapshot(
    val engineId: String = "rust-uniffi",
    val engineLabel: String = "Rust Native Core (UniFFI)",
    val nativeLoaded: Boolean = false,
    val nativeAbi: String = "arm64-v8a",
    val lastScanDurationMs: Long = 0L,
    val lastVersionBatchMicros: Long = 0L,
    val lastVersionBatchCount: Int = 0,
    val lastSha256DurationMs: Double = 0.0,
    val lastSha256Bytes: Long = 0L,
    val lastFDroidJarVerifyMs: Double = 0.0,
    val lastFDroidParseMs: Double = 0.0,
    val lastFDroidMatchedPackages: Int = 0,
    val selfTestThroughputMbps: Double = 0.0
)

object EngineTelemetryRegistry {
    private val _snapshot = MutableStateFlow(EngineBenchmarkSnapshot())
    val snapshot: StateFlow<EngineBenchmarkSnapshot> = _snapshot.asStateFlow()

    fun setEngineStatus(engineId: String, engineLabel: String, nativeLoaded: Boolean, nativeAbi: String) {
        _snapshot.update {
            it.copy(
                engineId = engineId,
                engineLabel = engineLabel,
                nativeLoaded = nativeLoaded,
                nativeAbi = nativeAbi
            )
        }
    }

    fun recordScanDuration(durationMs: Long) {
        _snapshot.update { it.copy(lastScanDurationMs = durationMs.coerceAtLeast(0L)) }
    }

    fun recordVersionBatch(micros: Long, count: Int) {
        _snapshot.update {
            it.copy(
                lastVersionBatchMicros = (it.lastVersionBatchMicros + micros).coerceAtLeast(0L),
                lastVersionBatchCount = it.lastVersionBatchCount + count
            )
        }
    }

    fun resetBatchScanCounters() {
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

    fun recordSha256(elapsedMicros: Long, bytesHashed: Long) {
        val ms = elapsedMicros / 1000.0
        val mbps = if (elapsedMicros > 0L && bytesHashed > 0L) {
            (bytesHashed.toDouble() / (1024.0 * 1024.0)) / (elapsedMicros.toDouble() / 1_000_000.0)
        } else {
            0.0
        }
        _snapshot.update {
            it.copy(
                lastSha256DurationMs = ms,
                lastSha256Bytes = bytesHashed,
                selfTestThroughputMbps = if (mbps > 0.0) mbps else it.selfTestThroughputMbps
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

interface ApkUpCoreEngine {
    val engineName: String
    val isNativeActive: Boolean

    fun cleanVersionString(tagOrName: String): String
    fun isNewerVersion(currentVersion: String, candidateTag: String): Boolean
    fun evaluateUpdateCandidatesBatch(requests: List<BatchVersionEvalInput>): List<BatchVersionEvalOutput>

    fun selectBestApkAssetIndex(
        assets: List<ApkAssetInput>,
        supportedAbis: List<String>,
        requiredSubstring: String?,
        assetFilterRegex: String?,
        preferFossFlavor: Boolean
    ): Int?

    fun computeFileSha256(filePath: String): Sha256Result
    fun verifyFileSha256(filePath: String, expectedSha256: String): Sha256VerifyResult

    fun verifyAndExtractFDroidJar(
        jarPath: String,
        targetEntryName: String,
        expectedCertSha256: String,
        allowUnpinnedCert: Boolean,
        outputEntryPath: String,
        maxJarBytes: Long,
        maxTargetBytes: Long
    ): FDroidJarVerifyResult

    fun streamFilterFDroidV2Index(
        indexPath: String,
        installedPackages: List<String>?
    ): FDroidFilteredJsonResult

    fun streamFilterFDroidV1Index(
        indexPath: String,
        installedPackages: List<String>?
    ): FDroidFilteredJsonResult
}

object CoreEngineHolder {
    @Volatile
    var engine: ApkUpCoreEngine? = null
        private set

    fun register(instance: ApkUpCoreEngine) {
        engine = instance
    }
}
