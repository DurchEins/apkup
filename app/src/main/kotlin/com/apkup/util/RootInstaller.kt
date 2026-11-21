package com.apkup.util

import android.content.Context
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.ui.InstallFailureReason
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

data class RootCommandOutput(
    val exitCode: Int,
    val output: String
)

data class RootInstallResult(
    val success: Boolean,
    val failureReason: InstallFailureReason? = null,
    val details: String = ""
)

interface RootShellExecutor {
    fun runCommand(command: String): RootCommandOutput
    fun pipeToCommand(command: String, input: InputStream): RootCommandOutput
}

class ProcessRootShellExecutor(
    private val timeoutSeconds: Long = 120L
) : RootShellExecutor {
    companion object {
        private const val MAX_OUTPUT_CHARS = 65_536

        internal fun executeProcess(
            process: Process,
            input: InputStream? = null,
            timeoutSeconds: Long = 120L,
            timeoutMessage: String = "Timed out waiting for su command"
        ): RootCommandOutput {
            val outputBuffer = StringBuilder()
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        val chunk = CharArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = reader.read(chunk)
                            if (read < 0) break
                            if (outputBuffer.length < MAX_OUTPUT_CHARS) {
                                val remaining = MAX_OUTPUT_CHARS - outputBuffer.length
                                outputBuffer.append(chunk, 0, minOf(read, remaining))
                            }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "root-shell-reader"
                start()
            }

            var pipeError: Throwable? = null
            if (input != null) {
                try {
                    process.outputStream.use { out ->
                        input.copyTo(out, DEFAULT_BUFFER_SIZE)
                        out.flush()
                    }
                } catch (t: Throwable) {
                    pipeError = t
                }
            }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(1_000L)
                return RootCommandOutput(-1, timeoutMessage)
            }
            readerThread.join(2_000L)
            val exit = process.exitValue()
            val trimmedOutput = outputBuffer.toString().trim()
            if (pipeError == null) {
                return RootCommandOutput(exit, trimmedOutput)
            }
            val combined = buildString {
                append(trimmedOutput)
                if (isNotEmpty()) append(" | ")
                append(pipeError.message ?: pipeError.javaClass.simpleName)
            }
            return RootCommandOutput(if (exit == 0) -1 else exit, combined)
        }
    }

    override fun runCommand(command: String): RootCommandOutput {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        return executeProcess(
            process = process,
            input = null,
            timeoutSeconds = timeoutSeconds,
            timeoutMessage = "Timed out waiting for su command"
        )
    }

    override fun pipeToCommand(command: String, input: InputStream): RootCommandOutput {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        return executeProcess(
            process = process,
            input = input,
            timeoutSeconds = timeoutSeconds,
            timeoutMessage = "Timed out streaming APK to su pm install-write"
        )
    }
}

/**
 * Executes silent multi-part APK installations via `su` using `pm install-create`,
 * `pm install-write`, and `pm install-commit`.
 */
class RootInstaller(
    private val shellExecutor: RootShellExecutor = ProcessRootShellExecutor(),
    private val context: Context? = null,
    private val integrityVerifier: (String, List<Pair<File, String?>>) -> Unit = { pkg, staged ->
        ApkIntegrityVerifier.verifyStagedApkFiles(
            context = context,
            expectedPackageName = pkg,
            stagedFiles = staged
        )
    }
) {
    companion object {
        private const val TAG = "RootInstaller"
        private val SESSION_ID_REGEX = Regex("""\[(\d+)]""")

        internal fun parseSessionId(output: String): Int? =
            SESSION_ID_REGEX.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()

        internal fun isSignatureMismatchMessage(output: String): Boolean =
            output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ||
                output.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ||
                output.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE", ignoreCase = true) ||
                output.contains("signatures do not match", ignoreCase = true)

        internal fun sanitizePartName(raw: String, index: Int): String =
            ApkIntegrityVerifier.sanitizePartFileName(raw, index)
    }

    fun isRootAvailable(): Boolean = runCatching {
        val res = shellExecutor.runCommand("id")
        res.exitCode == 0 && res.output.contains("uid=0")
    }.getOrDefault(false)

    fun executeRootInstall(
        packageName: String,
        parts: List<InstallPart>,
        installerPackageName: String = BuildConfig.APPLICATION_ID,
        allowDowngrade: Boolean = false,
        bypassLowTargetSdkBlock: Boolean = false
    ): RootInstallResult {
        if (!ApkIntegrityVerifier.isValidPackageName(packageName)) {
            return RootInstallResult(false, details = "Invalid target package name: '$packageName'")
        }
        if (parts.isEmpty()) {
            return RootInstallResult(false, details = "No APK parts supplied for $packageName")
        }

        var tempVerifyDir: File? = null
        try {
            val resolvedParts = mutableListOf<Triple<String, File, String?>>()
            parts.forEachIndexed { index, part ->
                val safeName = sanitizePartName(part.name, index)
                val backing = part.backingFile
                if (backing != null && backing.isFile && backing.length() > 0L) {
                    resolvedParts += Triple(safeName, backing, part.expectedSha256)
                } else {
                    val verifyDir = tempVerifyDir ?: run {
                        val baseCache = runCatching { context?.cacheDir }.getOrNull()
                            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_root_verify")
                        File(baseCache, "root_install_verify_${UUID.randomUUID()}").apply { mkdirs() }.also {
                            tempVerifyDir = it
                        }
                    }
                    val stagedFile = File(verifyDir, safeName)
                    val raw = part.openStream()
                        ?: return RootInstallResult(false, details = "Null stream for $packageName ($safeName)")
                    raw.use { input ->
                        stagedFile.outputStream().use { out ->
                            input.copyTo(out, DEFAULT_BUFFER_SIZE)
                            out.flush()
                        }
                    }
                    if (!stagedFile.isFile || stagedFile.length() <= 0L) {
                        return RootInstallResult(false, details = "Empty stream for $packageName ($safeName)")
                    }
                    resolvedParts += Triple(safeName, stagedFile, part.expectedSha256)
                }
            }

            // Defense-in-depth fail-closed APK integrity & signature verification before invoking su
            try {
                if (allowDowngrade) {
                    ApkIntegrityVerifier.verifyStagedApkFiles(
                        context = context,
                        expectedPackageName = packageName,
                        stagedFiles = resolvedParts.map { (_, file, sha256) -> file to sha256 },
                        allowDowngrade = true
                    )
                } else {
                    integrityVerifier(
                        packageName,
                        resolvedParts.map { (_, file, sha256) -> file to sha256 }
                    )
                }
            } catch (sec: ApkIntegrityException) {
                runCatching { Log.e(TAG, "Blocked unsafe root install for $packageName (${sec.reason}): ${sec.message}") }
                val reason = if (sec.reason == ApkIntegrityException.Reason.SIGNATURE_MISMATCH) {
                    InstallFailureReason.SIGNATURE_MISMATCH
                } else {
                    null
                }
                return RootInstallResult(
                    success = false,
                    failureReason = reason,
                    details = sec.message ?: "APK integrity verification failed (${sec.reason})"
                )
            }

            val totalSize = resolvedParts.sumOf { (_, file, _) -> file.length() }
            val safeInstallerFlag = if (ApkIntegrityVerifier.isValidPackageName(installerPackageName)) {
                "-i $installerPackageName "
            } else ""
            val downgradeFlag = if (allowDowngrade) "-d " else ""
            val bypassLowTargetFlag = if (bypassLowTargetSdkBlock) "--bypass-low-target-sdk-block " else ""
            val createCmd = "pm install-create -r $downgradeFlag$bypassLowTargetFlag$safeInstallerFlag-S $totalSize"
            val createOut = runCatching { shellExecutor.runCommand(createCmd) }
                .getOrElse { err ->
                    return RootInstallResult(false, details = err.message ?: "su install-create failed")
                }

            val sessionId = if (createOut.exitCode == 0) parseSessionId(createOut.output) else null
            if (sessionId == null) {
                val reason = if (isSignatureMismatchMessage(createOut.output)) {
                    InstallFailureReason.SIGNATURE_MISMATCH
                } else null
                return RootInstallResult(
                    success = false,
                    failureReason = reason,
                    details = "pm install-create failed: ${createOut.output}"
                )
            }

            var committed = false
            try {
                resolvedParts.forEach { (partName, file, _) ->
                    val sizeBytes = file.length()
                    val writeCmd = "pm install-write -S $sizeBytes $sessionId $partName -"
                    val writeOut = file.inputStream().use { input ->
                        shellExecutor.pipeToCommand(writeCmd, input)
                    }
                    if (writeOut.exitCode != 0 && !writeOut.output.contains("Success", ignoreCase = true)) {
                        val reason = if (isSignatureMismatchMessage(writeOut.output)) {
                            InstallFailureReason.SIGNATURE_MISMATCH
                        } else null
                        return RootInstallResult(
                            success = false,
                            failureReason = reason,
                            details = "pm install-write ($partName) failed: ${writeOut.output}"
                        )
                    }
                }

                val commitOut = shellExecutor.runCommand("pm install-commit $sessionId")
                val succeeded = commitOut.exitCode == 0 && commitOut.output.contains("Success", ignoreCase = true)
                if (succeeded) {
                    committed = true
                    return RootInstallResult(success = true, details = commitOut.output)
                }
                val reason = if (isSignatureMismatchMessage(commitOut.output)) {
                    InstallFailureReason.SIGNATURE_MISMATCH
                } else null
                return RootInstallResult(
                    success = false,
                    failureReason = reason,
                    details = "pm install-commit failed: ${commitOut.output}"
                )
            } catch (t: Throwable) {
                runCatching { Log.w(TAG, "Root install threw for $packageName", t) }
                return RootInstallResult(false, details = t.message ?: t.javaClass.simpleName)
            } finally {
                if (!committed) {
                    runCatching { shellExecutor.runCommand("pm install-abandon $sessionId") }
                }
            }
        } finally {
            tempVerifyDir?.let { runCatching { it.deleteRecursively() } }
        }
    }

    /**
     * Clean Root rollback fallback: stages and verifies all backup parts first (fail-closed, even
     * for stream-only parts), then uninstalls the newer package and installs the backup via `su`.
     */
    fun executeCleanRootRollback(
        packageName: String,
        parts: List<InstallPart>,
        installerPackageName: String = BuildConfig.APPLICATION_ID,
        bypassLowTargetSdkBlock: Boolean = false
    ): RootInstallResult {
        if (!ApkIntegrityVerifier.isValidPackageName(packageName)) {
            return RootInstallResult(false, details = "Invalid target package name: '$packageName'")
        }
        if (parts.isEmpty()) {
            return RootInstallResult(false, details = "No APK parts supplied for $packageName")
        }

        var tempVerifyDir: File? = null
        try {
            val verifiedParts = mutableListOf<InstallPart>()
            val stagedFiles = mutableListOf<Pair<File, String?>>()
            parts.forEachIndexed { index, part ->
                val safeName = sanitizePartName(part.name, index)
                val backing = part.backingFile
                val stagedFile = if (backing != null && backing.isFile && backing.length() > 0L) {
                    backing
                } else {
                    val verifyDir = tempVerifyDir ?: run {
                        val baseCache = runCatching { context?.cacheDir }.getOrNull()
                            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_root_verify")
                        File(baseCache, "root_rollback_verify_${UUID.randomUUID()}").apply { mkdirs() }.also {
                            tempVerifyDir = it
                        }
                    }
                    val target = File(verifyDir, safeName)
                    val raw = part.openStream()
                        ?: return RootInstallResult(false, details = "Null stream for $packageName ($safeName)")
                    raw.use { input ->
                        target.outputStream().use { out ->
                            input.copyTo(out, DEFAULT_BUFFER_SIZE)
                            out.flush()
                        }
                    }
                    if (!target.isFile || target.length() <= 0L) {
                        return RootInstallResult(false, details = "Empty stream for $packageName ($safeName)")
                    }
                    target
                }
                stagedFiles += stagedFile to part.expectedSha256
                verifiedParts += InstallPart(
                    name = safeName,
                    sizeBytes = stagedFile.length(),
                    expectedSha256 = part.expectedSha256,
                    backingFile = stagedFile,
                    openStream = { stagedFile.inputStream() }
                )
            }

            try {
                ApkIntegrityVerifier.verifyStagedApkFiles(
                    context = context,
                    expectedPackageName = packageName,
                    stagedFiles = stagedFiles,
                    allowDowngrade = true
                )
            } catch (sec: ApkIntegrityException) {
                return RootInstallResult(
                    success = false,
                    failureReason = if (sec.reason == ApkIntegrityException.Reason.SIGNATURE_MISMATCH) {
                        InstallFailureReason.SIGNATURE_MISMATCH
                    } else null,
                    details = sec.message ?: "Backup verification failed"
                )
            }

            runCatching { shellExecutor.runCommand("pm uninstall $packageName") }
            return executeRootInstall(
                packageName = packageName,
                parts = verifiedParts,
                installerPackageName = installerPackageName,
                allowDowngrade = true,
                bypassLowTargetSdkBlock = bypassLowTargetSdkBlock
            )
        } finally {
            tempVerifyDir?.let { runCatching { it.deleteRecursively() } }
        }
    }
}
