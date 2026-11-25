package com.apkup.core.android

import com.apkup.core.domain.ApkAssetInput
import com.apkup.core.domain.ApkUpCoreEngine
import com.apkup.core.domain.BatchVersionEvalInput
import com.apkup.core.domain.BatchVersionEvalOutput
import com.apkup.core.domain.CoreEngineHolder
import com.apkup.core.domain.EngineTelemetryRegistry
import com.apkup.core.domain.FDroidFilteredJsonResult
import com.apkup.core.domain.FDroidJarVerifyResult
import com.apkup.core.domain.Sha256Result
import com.apkup.core.domain.Sha256VerifyResult
import uniffi.apkup_rust_core.RustApkAssetInput
import uniffi.apkup_rust_core.RustBatchVersionEvalInput
import uniffi.apkup_rust_core.RustCoreException
import uniffi.apkup_rust_core.rustCleanVersionString
import uniffi.apkup_rust_core.rustComputeFileSha256
import uniffi.apkup_rust_core.rustCoreEngineInfo
import uniffi.apkup_rust_core.rustEvaluateUpdateCandidatesBatch
import uniffi.apkup_rust_core.rustIsNewerVersion
import uniffi.apkup_rust_core.rustSelectBestApkAssetIndex
import uniffi.apkup_rust_core.rustStreamFilterFdroidV1Index
import uniffi.apkup_rust_core.rustStreamFilterFdroidV2Index
import uniffi.apkup_rust_core.rustVerifyAndExtractFdroidJar
import uniffi.apkup_rust_core.rustVerifyFileSha256
import java.io.IOException

object RustCoreEngineProvider {

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val nativeInfo = runCatching { rustCoreEngineInfo() }.getOrNull()
            if (nativeInfo != null) {
                CoreEngineHolder.register(NativeRustCoreEngine(nativeInfo))
                EngineTelemetryRegistry.setEngineStatus(
                    engineId = "rust-uniffi",
                    engineLabel = nativeInfo,
                    nativeLoaded = true,
                    nativeAbi = "arm64-v8a"
                )
            } else {
                EngineTelemetryRegistry.setEngineStatus(
                    engineId = "rust-uniffi-fallback",
                    engineLabel = "Kotlin Fallback (Native lib unavailable)",
                    nativeLoaded = false,
                    nativeAbi = "jvm"
                )
            }
            initialized = true
        }
    }
}

private class NativeRustCoreEngine(
    override val engineName: String
) : ApkUpCoreEngine {

    override val isNativeActive: Boolean = true

    override fun cleanVersionString(tagOrName: String): String =
        rustCleanVersionString(tagOrName)

    override fun isNewerVersion(currentVersion: String, candidateTag: String): Boolean =
        rustIsNewerVersion(currentVersion, candidateTag)

    override fun evaluateUpdateCandidatesBatch(
        requests: List<BatchVersionEvalInput>
    ): List<BatchVersionEvalOutput> {
        val rustInputs = requests.map {
            RustBatchVersionEvalInput(
                id = it.id,
                currentVersion = it.currentVersion,
                candidateTag = it.candidateTag
            )
        }
        return rustEvaluateUpdateCandidatesBatch(rustInputs).map {
            BatchVersionEvalOutput(
                id = it.id,
                cleanedCandidate = it.cleanedCandidate,
                isNewer = it.isNewer,
                isPreRelease = it.isPreRelease,
                preReleaseChannel = it.preReleaseChannel
            )
        }
    }

    override fun selectBestApkAssetIndex(
        assets: List<ApkAssetInput>,
        supportedAbis: List<String>,
        requiredSubstring: String?,
        assetFilterRegex: String?,
        preferFossFlavor: Boolean
    ): Int? {
        val rustAssets = assets.map {
            RustApkAssetInput(index = it.index, filename = it.filename)
        }
        return rustSelectBestApkAssetIndex(
            assets = rustAssets,
            supportedAbis = supportedAbis,
            requiredSubstring = requiredSubstring,
            assetFilterRegex = assetFilterRegex,
            preferFossFlavor = preferFossFlavor
        )
    }

    override fun computeFileSha256(filePath: String): Sha256Result {
        try {
            val res = rustComputeFileSha256(filePath)
            return Sha256Result(
                sha256Hex = res.sha256Hex,
                bytesHashed = res.bytesHashed.toLong(),
                elapsedMicros = res.elapsedMicros.toLong()
            )
        } catch (e: RustCoreException) {
            throw mapRustException(e)
        }
    }

    override fun verifyFileSha256(filePath: String, expectedSha256: String): Sha256VerifyResult {
        try {
            val res = rustVerifyFileSha256(filePath, expectedSha256)
            return Sha256VerifyResult(
                actualSha256Hex = res.actualSha256Hex,
                matches = res.matches,
                bytesHashed = res.bytesHashed.toLong(),
                elapsedMicros = res.elapsedMicros.toLong()
            )
        } catch (e: RustCoreException) {
            throw mapRustException(e)
        }
    }

    override fun verifyAndExtractFDroidJar(
        jarPath: String,
        targetEntryName: String,
        expectedCertSha256: String,
        allowUnpinnedCert: Boolean,
        outputEntryPath: String,
        maxJarBytes: Long,
        maxTargetBytes: Long
    ): FDroidJarVerifyResult {
        try {
            val res = rustVerifyAndExtractFdroidJar(
                jarPath = jarPath,
                targetEntryName = targetEntryName,
                expectedCertSha256 = expectedCertSha256,
                allowUnpinnedCert = allowUnpinnedCert,
                outputEntryPath = outputEntryPath,
                maxJarBytes = maxJarBytes.toULong(),
                maxTargetBytes = maxTargetBytes.toULong()
            )
            return FDroidJarVerifyResult(
                signerSha256Hex = res.signerSha256Hex,
                extractedBytes = res.extractedBytes.toLong(),
                elapsedMicros = res.elapsedMicros.toLong()
            )
        } catch (e: RustCoreException) {
            throw mapRustException(e)
        }
    }

    override fun streamFilterFDroidV2Index(
        indexPath: String,
        installedPackages: List<String>?
    ): FDroidFilteredJsonResult {
        try {
            val res = rustStreamFilterFdroidV2Index(indexPath, installedPackages)
            return FDroidFilteredJsonResult(
                filteredJson = res.filteredJson,
                matchedPackageCount = res.matchedPackageCount.toInt(),
                elapsedMicros = res.elapsedMicros.toLong()
            )
        } catch (e: RustCoreException) {
            throw mapRustException(e)
        }
    }

    override fun streamFilterFDroidV1Index(
        indexPath: String,
        installedPackages: List<String>?
    ): FDroidFilteredJsonResult {
        try {
            val res = rustStreamFilterFdroidV1Index(indexPath, installedPackages)
            return FDroidFilteredJsonResult(
                filteredJson = res.filteredJson,
                matchedPackageCount = res.matchedPackageCount.toInt(),
                elapsedMicros = res.elapsedMicros.toLong()
            )
        } catch (e: RustCoreException) {
            throw mapRustException(e)
        }
    }

    private fun mapRustException(e: RustCoreException): Throwable = when (e) {
        is RustCoreException.SecurityException -> SecurityException(e.msg, e)
        is RustCoreException.IoException -> IOException(e.msg, e)
        is RustCoreException.InvalidFormat -> IOException(e.msg, e)
    }
}
