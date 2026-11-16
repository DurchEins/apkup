package com.apkup.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

data class ApkCompatibilityResult(
    val isCompatible: Boolean,
    val failureReason: FailureReason? = null,
    val requiredMinSdk: Int = 0,
    val targetSdk: Int = 0,
    val apkAbis: Set<String> = emptySet(),
    val deviceAbis: List<String> = emptyList(),
    val needsLowTargetSdkBypass: Boolean = false
) {
    enum class FailureReason {
        MIN_SDK_TOO_HIGH,
        NO_MATCHING_ABI
    }
}

object ApkCompatibilityChecker {

    /**
     * Evaluates compatibility given extracted `minSdk`, `targetSdk`, and the set of native ABIs
     * (`lib/<abi>/...`) bundled across the base + split APKs.
     */
    fun evaluate(
        minSdkVersion: Int,
        targetSdkVersion: Int,
        bundledAbis: Set<String>,
        deviceSdkInt: Int = Build.VERSION.SDK_INT,
        deviceSupportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()
    ): ApkCompatibilityResult {
        if (minSdkVersion > 0 && minSdkVersion > deviceSdkInt) {
            return ApkCompatibilityResult(
                isCompatible = false,
                failureReason = ApkCompatibilityResult.FailureReason.MIN_SDK_TOO_HIGH,
                requiredMinSdk = minSdkVersion,
                targetSdk = targetSdkVersion,
                apkAbis = bundledAbis,
                deviceAbis = deviceSupportedAbis
            )
        }

        val cleanBundled = bundledAbis.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val cleanDevice = deviceSupportedAbis.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanBundled.isNotEmpty() && cleanDevice.isNotEmpty()) {
            val hasOverlap = cleanBundled.any { abi ->
                cleanDevice.any { devAbi -> devAbi.equals(abi, ignoreCase = true) }
            }
            if (!hasOverlap) {
                return ApkCompatibilityResult(
                    isCompatible = false,
                    failureReason = ApkCompatibilityResult.FailureReason.NO_MATCHING_ABI,
                    requiredMinSdk = minSdkVersion,
                    targetSdk = targetSdkVersion,
                    apkAbis = cleanBundled,
                    deviceAbis = cleanDevice
                )
            }
        }

        val lowTargetBypass = isLowTargetSdkBlockedByOs(targetSdkVersion, deviceSdkInt)
        return ApkCompatibilityResult(
            isCompatible = true,
            requiredMinSdk = minSdkVersion,
            targetSdk = targetSdkVersion,
            apkAbis = cleanBundled,
            deviceAbis = cleanDevice,
            needsLowTargetSdkBypass = lowTargetBypass
        )
    }

    fun isLowTargetSdkBlockedByOs(targetSdkVersion: Int, deviceSdkInt: Int): Boolean {
        if (targetSdkVersion <= 0) return false
        return when {
            deviceSdkInt >= 35 -> targetSdkVersion < 24 // Android 15+ blocks targetSdk < 24 by default
            deviceSdkInt >= 34 -> targetSdkVersion < 23 // Android 14 blocks targetSdk < 23 by default
            else -> false
        }
    }

    /**
     * Inspects a single `.apk` file or a directory of split `.apk` files before installation.
     */
    fun checkStagedTarget(
        context: Context,
        target: File,
        deviceSdkInt: Int = Build.VERSION.SDK_INT,
        deviceSupportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty()
    ): ApkCompatibilityResult {
        val apkFiles = if (target.isDirectory) {
            target.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }?.toList().orEmpty()
        } else if (target.isFile && target.name.endsWith(".apk", ignoreCase = true)) {
            listOf(target)
        } else {
            emptyList()
        }
        if (apkFiles.isEmpty()) {
            return ApkCompatibilityResult(isCompatible = true, deviceAbis = deviceSupportedAbis)
        }

        val baseApk = apkFiles.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
            ?: apkFiles.maxByOrNull { it.length() }
            ?: apkFiles.first()

        val pkgInfo: PackageInfo? = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(baseApk.absolutePath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(baseApk.absolutePath, 0)
            }
        }.getOrNull()

        val appInfo = pkgInfo?.applicationInfo
        val minSdk = appInfo?.minSdkVersion ?: 0
        val targetSdk = appInfo?.targetSdkVersion ?: 0
        val bundledAbis = extractBundledAbis(apkFiles)

        return evaluate(
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            bundledAbis = bundledAbis,
            deviceSdkInt = deviceSdkInt,
            deviceSupportedAbis = deviceSupportedAbis
        )
    }

    internal fun extractBundledAbis(apkFiles: List<File>): Set<String> {
        val abis = LinkedHashSet<String>()
        for (file in apkFiles) {
            runCatching {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.startsWith("lib/") && name.endsWith(".so", ignoreCase = true)) {
                            val parts = name.split('/')
                            if (parts.size >= 3 && parts[1].isNotBlank()) {
                                abis.add(parts[1])
                            }
                        }
                    }
                }
            }
        }
        return abis
    }
}
