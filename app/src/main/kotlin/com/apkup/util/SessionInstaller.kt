package com.apkup.util

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.InstallFailureReason
import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.data.ui.resolveInstallFailureReason
import com.apkup.prefs.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.io.DEFAULT_BUFFER_SIZE

data class InstallPart(
    val name: String,
    val sizeBytes: Long = -1L,
    val expectedSha256: String? = null,
    val backingFile: File? = null,
    val openStream: () -> InputStream?
)

interface ShizukuBridge {
    fun isPackageInstalled(): Boolean
    fun pingBinder(): Boolean
    fun isPermissionGranted(): Boolean
    fun requestPermission(requestCode: Int)
    fun observeEvents(
        onPermissionResult: (Boolean) -> Unit,
        onBinderChanged: () -> Unit
    ): () -> Unit
    suspend fun awaitPermissionResult(timeoutMs: Long): Boolean
    fun executeShizukuInstall(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean
}

class SessionInstaller internal constructor(
    private val context: Context?,
    private val installLog: InstallLog,
    private val prefs: Prefs,
    private val snackBar: SnackBar? = null,
    private val stringer: Stringer? = null,
    private val shizukuBridgeOverride: ShizukuBridge? = null,
    private val packageInstallerFallbackOverride: (suspend (Int, String, List<InstallPart>) -> Boolean)? = null,
    private val onFallbackNotice: ((Int) -> Unit)? = null,
    private val rootInstallerOverride: RootInstaller? = null
) {
    constructor(
        context: Context,
        installLog: InstallLog,
        prefs: Prefs,
        snackBar: SnackBar? = null,
        stringer: Stringer? = null
    ) : this(
        context = context,
        installLog = installLog,
        prefs = prefs,
        snackBar = snackBar,
        stringer = stringer,
        shizukuBridgeOverride = null,
        packageInstallerFallbackOverride = null,
        onFallbackNotice = null,
        rootInstallerOverride = null
    )

    companion object {
        const val INSTALL_ACTION = "com.apkup.action.PACKAGE_INSTALL_STATUS"
        const val INSTALL_CANCEL_ACTION = "com.apkup.action.PACKAGE_INSTALL_CANCEL"
        const val NOTIFICATION_ID_CONFIRM_BASE = 8000
        const val CONFIRM_NOTIFICATION_CHANNEL_ID = "apkup_install_confirm_channel"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 9401
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val SHIZUKU_CMD_TIMEOUT_SECONDS = 90L
        private const val MAX_PROCESS_OUTPUT_CHARS = 32_768

        internal fun buildBackgroundActivityLaunchOptions(): Bundle? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            return runCatching {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 36) {
                    options.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    )
                } else if (Build.VERSION.SDK_INT >= 35) {
                    @Suppress("DEPRECATION")
                    options.setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                } else {
                    @Suppress("DEPRECATION")
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                options.toBundle()
            }.getOrNull()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSessions = ConcurrentHashMap<Int, Int>()

    private val shizukuBridge: ShizukuBridge by lazy {
        shizukuBridgeOverride ?: AndroidShizukuBridge()
    }

    private val rootInstaller: RootInstaller by lazy {
        rootInstallerOverride ?: RootInstaller()
    }

    /**
     * Cleans up inactive orphaned PackageInstaller sessions off the main thread without killing
     * any session currently tracked in `activeSessions` (LOW-01).
     */
    fun cleanOrphanedSessions() {
        val ctx = context ?: return
        runCatching {
            val installer = ctx.packageManager.packageInstaller
            val activeIds = activeSessions.values.toSet()
            installer.mySessions.forEach { sessionInfo ->
                if (sessionInfo.sessionId !in activeIds && !sessionInfo.isActive) {
                    runCatching { installer.abandonSession(sessionInfo.sessionId) }
                }
            }
        }
    }

    suspend fun install(id: Int, packageName: String, stream: InputStream) {
        installParts(id, packageName, listOf(InstallPart("base.apk") { stream }))
    }

    suspend fun install(id: Int, packageName: String, streams: List<InputStream>) {
        val parts = streams.mapIndexed { index, stream ->
            val name = if (index == 0) "base.apk" else "split_$index.apk"
            InstallPart(name) { stream }
        }
        installParts(id, packageName, parts)
    }

    suspend fun installStreamProviders(
        id: Int,
        packageName: String,
        streamProviders: List<() -> InputStream?>
    ): Boolean {
        val parts = streamProviders.mapIndexed { index, provider ->
            val name = if (index == 0) "base.apk" else "split_$index.apk"
            InstallPart(name, openStream = provider)
        }
        return installParts(id, packageName, parts)
    }

    /**
     * Stages parts (if not already file-backed) and executes 100% Fail-Closed `ApkIntegrityVerifier`
     * BEFORE either Shizuku or PackageInstaller touches the APK (F-01, F-02).
     *
     * On `ApkIntegrityException`, immediately deletes staged & `.ready` files and NEVER falls back
     * between installers.
     */
    private fun prepareAndVerifyParts(
        id: Int,
        packageName: String,
        parts: List<InstallPart>,
        emitProgress: Boolean
    ): Pair<List<InstallPart>, File?> {
        val needsVerificationStaging = context != null || parts.any { !it.expectedSha256.isNullOrBlank() }
        if (!needsVerificationStaging) {
            return parts to null
        }

        val allHaveBackingFiles = parts.all { part ->
            val f = part.backingFile
            f != null && f.isFile && f.length() > 0L
        }

        val tempVerifyDir: File?
        val stagedFiles: List<Pair<File, String?>>
        val verifiedParts: List<InstallPart>

        if (allHaveBackingFiles) {
            tempVerifyDir = null
            stagedFiles = parts.map { it.backingFile!! to it.expectedSha256 }
            verifiedParts = parts.mapIndexed { index, part ->
                val f = part.backingFile!!
                InstallPart(
                    name = ApkIntegrityVerifier.sanitizePartFileName(part.name, index),
                    sizeBytes = f.length(),
                    expectedSha256 = part.expectedSha256,
                    backingFile = f,
                    openStream = { f.inputStream() }
                )
            }
        } else {
            val baseCache = context?.cacheDir
                ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_verify_cache")
            tempVerifyDir = File(baseCache, "installer_verify_${id}_${UUID.randomUUID()}").apply { mkdirs() }
            var totalStaged = 0L
            try {
                val created = parts.mapIndexed { index, part ->
                    val safeName = ApkIntegrityVerifier.sanitizePartFileName(part.name, index)
                    val outFile = File(tempVerifyDir, safeName)
                    val rawStream = part.openStream()
                        ?: throw IOException("Download stream returned null for $packageName (part $safeName)")
                    wrapStreamIfGzip(rawStream).use { input ->
                        outFile.outputStream().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read = input.read(buffer)
                            while (read >= 0) {
                                out.write(buffer, 0, read)
                                totalStaged += read
                                if (emitProgress) {
                                    installLog.emitProgress(AppInstallProgress(id, totalStaged))
                                }
                                read = input.read(buffer)
                            }
                            out.flush()
                        }
                    }
                    outFile to part.expectedSha256
                }
                stagedFiles = created
                verifiedParts = created.map { (file, sha) ->
                    InstallPart(
                        name = file.name,
                        sizeBytes = file.length(),
                        expectedSha256 = sha,
                        backingFile = file,
                        openStream = { file.inputStream() }
                    )
                }
            } catch (t: Throwable) {
                runCatching { tempVerifyDir.deleteRecursively() }
                throw t
            }
        }

        try {
            ApkIntegrityVerifier.verifyStagedApkFiles(
                context = context,
                expectedPackageName = packageName,
                stagedFiles = stagedFiles
            )
        } catch (sec: ApkIntegrityException) {
            tempVerifyDir?.let { runCatching { it.deleteRecursively() } }
            // Delete any tampered/mismatched cached staging directory (including .ready marker) immediately (F-01)
            stagedFiles.forEach { (f, _) ->
                runCatching {
                    val parent = f.parentFile
                    if (parent != null && (parent.name == "download_staging_$id" || parent.name == "shizuku_staging_$id")) {
                        parent.deleteRecursively()
                    } else {
                        f.delete()
                    }
                }
            }
            context?.let { DownloadStorageManager.deleteStagingDir(it, id) }
            throw sec
        } catch (t: Throwable) {
            tempVerifyDir?.let { runCatching { it.deleteRecursively() } }
            throw t
        }

        return verifiedParts to tempVerifyDir
    }

    private fun handleIntegrityAbort(id: Int, error: ApkIntegrityException, showSnack: Boolean) {
        Log.e("SessionInstaller", "Blocked unsafe APK installation ($id, reason=${error.reason}): ${error.message}")
        val failureReason = if (error.reason == ApkIntegrityException.Reason.SIGNATURE_MISMATCH) {
            InstallFailureReason.SIGNATURE_MISMATCH
        } else {
            InstallFailureReason.GENERIC
        }
        installLog.emitStatus(
            AppInstallStatus(
                success = false,
                id = id,
                snack = showSnack && failureReason == InstallFailureReason.SIGNATURE_MISMATCH,
                failureReason = failureReason
            )
        )
        if (showSnack && failureReason != InstallFailureReason.SIGNATURE_MISMATCH) {
            val snack = snackBar
            val str = stringer
            if (snack != null && str != null) {
                val msgRes = when (error.reason) {
                    ApkIntegrityException.Reason.HASH_MISMATCH -> R.string.install_error_hash_mismatch
                    ApkIntegrityException.Reason.VERSION_DOWNGRADE -> R.string.install_error_downgrade_blocked
                    else -> R.string.install_error_integrity_failed
                }
                snack.snackBar(scope, TextSnack(str.get(msgRes), visualType = com.apkup.data.snack.SnackVisualType.ERROR))
            }
        }
    }

    suspend fun installParts(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        installLog.beginInstall(id)
        if (parts.isEmpty()) {
            installLog.emitStatus(AppInstallStatus(false, id, snack = true))
            throw IOException("No installation parts provided for $packageName")
        }

        val (verifiedParts, tempVerifyDir) = try {
            prepareAndVerifyParts(id, packageName, parts, emitProgress = false)
        } catch (sec: ApkIntegrityException) {
            handleIntegrityAbort(id, sec, showSnack = true)
            return false
        }

        try {
            var pendingShizukuFallbackRes: Int? = null
            if (prefs.useShizukuInstaller.get()) {
                val shizukuStatus = ensureShizukuReadyAndAuthorized()
                if (shizukuStatus == ShizukuAvailability.READY) {
                    val shizukuResult = runCatching {
                        shizukuBridge.executeShizukuInstall(id, packageName, verifiedParts)
                    }
                    val failure = shizukuResult.exceptionOrNull()
                    if (failure is ApkIntegrityException) {
                        handleIntegrityAbort(id, failure, showSnack = true)
                        return false
                    }
                    if (failure is SecurityException) {
                        Log.e("SessionInstaller", "Security rejection during Shizuku install; refusing fallback", failure)
                        installLog.emitStatus(AppInstallStatus(false, id, snack = true))
                        return false
                    }
                    if (shizukuResult.isSuccess) {
                        val succeeded = shizukuResult.getOrDefault(false)
                        installLog.emitStatus(AppInstallStatus(succeeded, id, snack = true))
                        return succeeded
                    } else {
                        Log.w("SessionInstaller", "Shizuku installation failed; trying fallback installer")
                        pendingShizukuFallbackRes = R.string.shizuku_fallback_not_running
                    }
                } else {
                    pendingShizukuFallbackRes = when (shizukuStatus) {
                        ShizukuAvailability.PERMISSION_DENIED -> R.string.shizuku_fallback_no_permission
                        else -> R.string.shizuku_fallback_not_running
                    }
                }
            }

            if (prefs.useRootInstaller.get()) {
                tryRootInstall(id, packageName, verifiedParts, emitSnackOnResult = true)?.let { rootOutcome ->
                    return rootOutcome
                }
            }

            pendingShizukuFallbackRes?.let { notifyShizukuFallback(it) }

            val fallbackOverride = packageInstallerFallbackOverride
            if (fallbackOverride != null) {
                val result = fallbackOverride(id, packageName, verifiedParts)
                installLog.emitStatus(AppInstallStatus(result, id, snack = true))
                return result
            }
            val result = withTimeoutOrNull(120_000L) {
                installWithPackageInstaller(id, packageName, verifiedParts)
            } ?: run {
                finish(id)
                installLog.emitStatus(AppInstallStatus(false, id, snack = true))
                false
            }
            return result
        } finally {
            tempVerifyDir?.let { runCatching { it.deleteRecursively() } }
        }
    }

    suspend fun installPartsSilently(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        if (parts.isEmpty() || (!prefs.useShizukuInstaller.get() && !prefs.useRootInstaller.get())) {
            return false
        }

        val shizukuUsable = if (prefs.useShizukuInstaller.get()) {
            val binderActive = runCatching { shizukuBridge.pingBinder() }.getOrDefault(false)
            binderActive && runCatching { shizukuBridge.isPermissionGranted() }.getOrDefault(false)
        } else {
            false
        }
        val rootUsable = prefs.useRootInstaller.get() && testRootAvailability()
        if (!shizukuUsable && !rootUsable) {
            return false
        }

        installLog.beginInstall(id)
        val (verifiedParts, tempVerifyDir) = try {
            prepareAndVerifyParts(id, packageName, parts, emitProgress = false)
        } catch (sec: ApkIntegrityException) {
            handleIntegrityAbort(id, sec, showSnack = false)
            return false
        } catch (e: Exception) {
            Log.w("SessionInstaller", "Silent pre-install verification failed", e)
            installLog.emitStatus(AppInstallStatus(false, id, snack = false))
            return false
        }

        try {
            if (shizukuUsable) {
                val shizukuResult = runCatching {
                    shizukuBridge.executeShizukuInstall(id, packageName, verifiedParts)
                }
                val shizukuError = shizukuResult.exceptionOrNull()
                if (shizukuError is ApkIntegrityException) {
                    handleIntegrityAbort(id, shizukuError, showSnack = false)
                    return false
                }
                if (shizukuError is SecurityException) {
                    installLog.emitStatus(AppInstallStatus(false, id, snack = false))
                    return false
                }
                if (shizukuResult.isSuccess && shizukuResult.getOrDefault(false)) {
                    installLog.emitStatus(AppInstallStatus(true, id, snack = false))
                    return true
                }
            }

            if (prefs.useRootInstaller.get()) {
                installLog.beginInstall(id)
                tryRootInstall(id, packageName, verifiedParts, emitSnackOnResult = false)?.let { rootOutcome ->
                    return rootOutcome
                }
            }

            installLog.emitStatus(AppInstallStatus(false, id, snack = false))
            return false
        } finally {
            tempVerifyDir?.let { runCatching { it.deleteRecursively() } }
        }
    }

    private fun tryRootInstall(
        id: Int,
        packageName: String,
        parts: List<InstallPart>,
        emitSnackOnResult: Boolean
    ): Boolean? {
        if (!prefs.useRootInstaller.get()) return null
        if (!rootInstaller.isRootAvailable()) {
            runCatching {
                Log.w("SessionInstaller", "Root installer enabled, but su access is unavailable for $packageName")
            }
            return null
        }

        val preservedInstaller = context?.let { resolvePreservedInstallerPackage(it, packageName) }
            ?: BuildConfig.APPLICATION_ID
        val rootResult = rootInstaller.executeRootInstall(
            packageName = packageName,
            parts = parts,
            installerPackageName = preservedInstaller
        )
        AppJournal.record(
            "Root",
            "Root install for $packageName: success=${rootResult.success}, reason=${rootResult.failureReason ?: "none"}"
        )
        if (rootResult.success) {
            installLog.emitProgress(AppInstallProgress(id, 0L))
            installLog.emitStatus(AppInstallStatus(true, id, snack = emitSnackOnResult))
            return true
        }
        if (rootResult.failureReason == InstallFailureReason.SIGNATURE_MISMATCH) {
            installLog.emitProgress(AppInstallProgress(id, 0L))
            installLog.emitStatus(
                AppInstallStatus(
                    success = false,
                    id = id,
                    snack = emitSnackOnResult,
                    failureReason = InstallFailureReason.SIGNATURE_MISMATCH
                )
            )
            return false
        }
        runCatching {
            Log.w("SessionInstaller", "Root installation failed for $packageName: ${rootResult.details}")
        }
        return null
    }

    private fun notifyShizukuFallback(messageRes: Int) {
        onFallbackNotice?.invoke(messageRes)
        val snack = snackBar ?: return
        val str = stringer ?: return
        snack.snackBar(scope, TextSnack(str.get(messageRes), visualType = com.apkup.data.snack.SnackVisualType.WARNING))
    }

    fun getShizukuSetupStatus(): ShizukuSetupStatus {
        val isInstalled = runCatching { shizukuBridge.isPackageInstalled() }.getOrDefault(false)
        val isBinderRunning = runCatching { shizukuBridge.pingBinder() }.getOrDefault(false)
        val isPermissionGranted = isBinderRunning && runCatching {
            shizukuBridge.isPermissionGranted()
        }.getOrDefault(false)

        return ShizukuSetupStatus(
            isInstalled = isInstalled,
            isBinderRunning = isBinderRunning,
            isPermissionGranted = isPermissionGranted
        )
    }

    fun isShizukuReady(): Boolean =
        prefs.useShizukuInstaller.get() && getShizukuSetupStatus().isReady

    fun testRootAvailability(): Boolean = rootInstaller.isRootAvailable()

    fun isRootReady(): Boolean =
        prefs.useRootInstaller.get() && testRootAvailability()

    fun isSilentInstallerReady(): Boolean =
        isShizukuReady() || isRootReady()

    fun canRequestPackageInstalls(): Boolean {
        val ctx = context ?: return true
        return ctx.packageManager.canRequestPackageInstalls()
    }

    fun requestShizukuPermission(): Boolean = runCatching {
        if (!shizukuBridge.pingBinder()) return@runCatching false
        if (shizukuBridge.isPermissionGranted()) {
            return@runCatching true
        }
        shizukuBridge.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        false
    }.getOrDefault(false)

    fun observeShizukuEvents(
        onPermissionResult: (Boolean) -> Unit,
        onBinderChanged: () -> Unit
    ): () -> Unit = shizukuBridge.observeEvents(onPermissionResult, onBinderChanged)

    private suspend fun ensureShizukuReadyAndAuthorized(): ShizukuAvailability = runCatching {
        if (!shizukuBridge.pingBinder()) return@runCatching ShizukuAvailability.NOT_RUNNING
        if (shizukuBridge.isPermissionGranted()) {
            return@runCatching ShizukuAvailability.READY
        }
        val granted = shizukuBridge.awaitPermissionResult(12_000L)
        if (granted) ShizukuAvailability.READY else ShizukuAvailability.PERMISSION_DENIED
    }.getOrDefault(ShizukuAvailability.NOT_RUNNING)

    private inner class AndroidShizukuBridge : ShizukuBridge {
        override fun isPackageInstalled(): Boolean {
            val ctx = context ?: return false
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx.packageManager.getPackageInfo(
                        SHIZUKU_PACKAGE_NAME,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
                }
                true
            }.getOrDefault(false)
        }

        override fun pingBinder(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

        override fun isPermissionGranted(): Boolean = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        override fun requestPermission(requestCode: Int) {
            Shizuku.requestPermission(requestCode)
        }

        override fun observeEvents(
            onPermissionResult: (Boolean) -> Unit,
            onBinderChanged: () -> Unit
        ): () -> Unit {
            val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                    onPermissionResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            val binderReceivedListener = Shizuku.OnBinderReceivedListener {
                onBinderChanged()
            }
            val binderDeadListener = Shizuku.OnBinderDeadListener {
                onBinderChanged()
            }

            runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
            runCatching { Shizuku.addBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }

            return {
                runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
                runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
                runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            }
        }

        override suspend fun awaitPermissionResult(timeoutMs: Long): Boolean =
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val listener = object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                                Shizuku.removeRequestPermissionResultListener(this)
                                if (continuation.isActive) {
                                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                                }
                            }
                        }
                    }
                    Shizuku.addRequestPermissionResultListener(listener)
                    continuation.invokeOnCancellation {
                        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                    }
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } ?: false

        override fun executeShizukuInstall(
            id: Int,
            packageName: String,
            parts: List<InstallPart>
        ): Boolean = installWithShizuku(id, packageName, parts)
    }

    /**
     * Resolves the original `installingPackageName` for `packageName` so Shizuku updates preserve
     * the app's existing Play Store / F-Droid / Droid-ify install origin (F-12a).
     */
    private fun resolvePreservedInstallerPackage(ctx: Context, targetPackageName: String): String {
        val existing = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ctx.packageManager.getInstallSourceInfo(targetPackageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getInstallerPackageName(targetPackageName)
            }
        }.getOrNull()

        return if (ApkIntegrityVerifier.isValidPackageName(existing)) {
            existing!!
        } else {
            ctx.packageName
        }
    }

    private fun installWithShizuku(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        val ctx = requireNotNull(context) { "Android Context required for native Shizuku install" }
        if (!ApkIntegrityVerifier.isValidPackageName(packageName)) {
            throw SecurityException("Invalid packageName '$packageName' rejected before Shizuku execution")
        }
        val binder = Shizuku.getBinder() ?: throw IOException("Shizuku binder unavailable")
        val shizukuService = IShizukuService.Stub.asInterface(binder)
            ?: throw IOException("IShizukuService unavailable")

        val tempDir = File(ctx.cacheDir, "shizuku_staging_${id}_${UUID.randomUUID()}").apply { mkdirs() }
        var sessionId: String? = null
        try {
            var totalBytes = 0L
            val stagedFiles = parts.mapIndexed { index, part ->
                val safeFileName = ApkIntegrityVerifier.sanitizePartFileName(part.name, index)
                val existingBacking = part.backingFile
                if (existingBacking != null && existingBacking.isFile && existingBacking.length() > 0L) {
                    totalBytes += existingBacking.length()
                    installLog.emitProgress(AppInstallProgress(id, totalBytes))
                    safeFileName to existingBacking
                } else {
                    val rawStream = part.openStream()
                        ?: throw IOException("Download stream returned null for $packageName (part $safeFileName)")
                    val stream = wrapStreamIfGzip(rawStream)
                    val partFile = File(tempDir, safeFileName)
                    stream.use { input ->
                        partFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read = input.read(buffer)
                            while (read >= 0) {
                                output.write(buffer, 0, read)
                                totalBytes += read
                                installLog.emitProgress(AppInstallProgress(id, totalBytes))
                                read = input.read(buffer)
                            }
                            output.flush()
                        }
                    }
                    if (partFile.length() <= 0L) {
                        throw IOException("Downloaded empty APK split for $packageName ($safeFileName)")
                    }
                    safeFileName to partFile
                }
            }

            // Re-verify staged files right before privileged shell install (eliminates any TOCTOU window)
            ApkIntegrityVerifier.verifyStagedApkFiles(
                context = ctx,
                expectedPackageName = packageName,
                stagedFiles = stagedFiles.map { (_, f) -> f to null }
            )

            // Always bind session to `-p packageName` and preserve original installingPackageName (F-12a, HIGH-01)
            val installerPkg = resolvePreservedInstallerPackage(ctx, packageName)
            val createArgs = arrayOf("pm", "install-create", "-r", "-p", packageName, "-i", installerPkg)
            val createOutput = runShizukuCommand(shizukuService, createArgs)
            sessionId = Regex("\\[(\\d+)]").find(createOutput)?.groupValues?.getOrNull(1)
                ?: throw IOException("Could not parse Shizuku install session ID from: $createOutput")

            stagedFiles.forEach { (safeEntryName, file) ->
                val writeProc = shizukuService.newProcess(
                    arrayOf("pm", "install-write", "-S", file.length().toString(), sessionId!!, safeEntryName, "-"),
                    null,
                    null
                )
                try {
                    val stdOutBuf = StringBuilder()
                    val stdErrBuf = StringBuilder()
                    val outReader = drainPipeAsync(writeProc.inputStream, stdOutBuf)
                    val errReader = drainPipeAsync(writeProc.errorStream, stdErrBuf)

                    file.inputStream().use { fileIn ->
                        ParcelFileDescriptor.AutoCloseOutputStream(writeProc.outputStream).use { procOut ->
                            fileIn.copyTo(procOut)
                            procOut.flush()
                        }
                    }
                    val exitCode = awaitProcessExitWithTimeout(
                        timeoutSeconds = SHIZUKU_CMD_TIMEOUT_SECONDS,
                        destroyFn = { writeProc.destroy() },
                        waitForFn = { writeProc.waitFor() }
                    )
                    outReader.join(2_000L)
                    errReader.join(2_000L)
                    if (exitCode == null) {
                        throw IOException("pm install-write timed out after ${SHIZUKU_CMD_TIMEOUT_SECONDS}s")
                    }
                    if (exitCode != 0) {
                        throw IOException("pm install-write failed ($exitCode): $stdErrBuf $stdOutBuf")
                    }
                } finally {
                    runCatching { writeProc.destroy() }
                }
            }

            val commitOutput = runShizukuCommand(
                shizukuService,
                arrayOf("pm", "install-commit", sessionId!!)
            )
            val success = commitOutput.contains("Success", ignoreCase = true)
            if (success) {
                sessionId = null
            }
            return success
        } finally {
            sessionId?.let { activeSession ->
                runCatching {
                    runShizukuCommand(shizukuService, arrayOf("pm", "install-abandon", activeSession))
                }
            }
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun drainPipeAsync(pfd: ParcelFileDescriptor?, sink: StringBuilder): Thread {
        return thread(start = true, isDaemon = true, name = "shizuku-pipe-drain") {
            if (pfd == null) return@thread
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { reader ->
                    val buf = CharArray(2048)
                    var n = reader.read(buf)
                    while (n >= 0) {
                        if (sink.length < MAX_PROCESS_OUTPUT_CHARS) {
                            val allowed = (MAX_PROCESS_OUTPUT_CHARS - sink.length).coerceAtMost(n)
                            sink.append(buf, 0, allowed)
                        }
                        n = reader.read(buf)
                    }
                }
            }
        }
    }

    private fun runShizukuCommand(service: IShizukuService, command: Array<String>): String {
        val process = service.newProcess(command, null, null)
        return try {
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream).close() }
            val stdOut = StringBuilder()
            val stdErr = StringBuilder()
            val outThread = drainPipeAsync(process.inputStream, stdOut)
            val errThread = drainPipeAsync(process.errorStream, stdErr)

            val exit = awaitProcessExitWithTimeout(
                timeoutSeconds = SHIZUKU_CMD_TIMEOUT_SECONDS,
                destroyFn = { process.destroy() },
                waitForFn = { process.waitFor() }
            )
            outThread.join(2_000L)
            errThread.join(2_000L)
            if (exit == null) {
                throw IOException("Shizuku command '${command.firstOrNull()}' timed out after ${SHIZUKU_CMD_TIMEOUT_SECONDS}s")
            }
            val outStr = stdOut.toString()
            val errStr = stdErr.toString()
            if (exit != 0 && !outStr.contains("Success", ignoreCase = true)) {
                throw IOException("Command ${command.firstOrNull()} failed ($exit): $errStr $outStr")
            }
            "$outStr\n$errStr"
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun awaitProcessExitWithTimeout(
        timeoutSeconds: Long,
        destroyFn: () -> Unit,
        waitForFn: () -> Int
    ): Int? {
        val resultHolder = java.util.concurrent.atomic.AtomicInteger(Int.MIN_VALUE)
        val latch = java.util.concurrent.CountDownLatch(1)
        thread(start = true, isDaemon = true, name = "shizuku-proc-wait") {
            try {
                resultHolder.set(waitForFn())
            } catch (_: Throwable) {
            } finally {
                latch.countDown()
            }
        }
        val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            runCatching { destroyFn() }
            return null
        }
        val code = resultHolder.get()
        return if (code == Int.MIN_VALUE) null else code
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private suspend fun installWithPackageInstaller(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        val ctx = requireNotNull(context) { "Android Context required for PackageInstaller" }
        if (!canRequestPackageInstalls()) {
            Log.w("SessionInstaller", "Cannot request package installs; prompting permission.")
            checkPermission()
            return false
        }
        val packageInstaller: PackageInstaller = ctx.packageManager.packageInstaller
        val knownTotalSize = parts.filter { it.sizeBytes > 0L }.sumOf { it.sizeBytes }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setOriginatingUid(Process.myUid())
            if (knownTotalSize > 0L) {
                setSize(knownTotalSize)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            }
            // Note: setRequestUpdateOwnership(true) intentionally omitted per user decision F-12b
        }

        val sessionToken = UUID.randomUUID().toString()

        return suspendCancellableCoroutine { continuation ->
            val receiverUnregistered = AtomicBoolean(false)
            val callbackUnregistered = AtomicBoolean(false)
            val terminalHandled = AtomicBoolean(false)

            lateinit var receiver: BroadcastReceiver
            lateinit var callback: PackageInstaller.SessionCallback

            fun safeUnregisterReceiver() {
                if (receiverUnregistered.compareAndSet(false, true)) {
                    runCatching { ctx.unregisterReceiver(receiver) }
                }
            }

            fun safeUnregisterCallback() {
                if (callbackUnregistered.compareAndSet(false, true)) {
                    runCatching { packageInstaller.unregisterSessionCallback(callback) }
                }
            }

            var createdSessionId: Int? = null
            val mainHandler = Handler(Looper.getMainLooper())

            fun completeOnce(
                success: Boolean,
                abandonIfFailed: Boolean = false,
                failureReason: InstallFailureReason? = null
            ) {
                if (terminalHandled.compareAndSet(false, true)) {
                    val sessId = createdSessionId
                    if (sessId != null) {
                        activeSessions.remove(id, sessId)
                        if (!success && abandonIfFailed) {
                            runCatching { packageInstaller.abandonSession(sessId) }
                        }
                    } else {
                        activeSessions.remove(id)
                    }
                    runCatching {
                        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(NOTIFICATION_ID_CONFIRM_BASE + id)
                    }
                    installLog.emitStatus(
                        AppInstallStatus(
                            success = success,
                            id = id,
                            snack = true,
                            failureReason = if (success) null else failureReason
                        )
                    )
                    safeUnregisterReceiver()
                    safeUnregisterCallback()
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, @SuppressLint("UnsafeIntentLaunch") intent: Intent?) {
                    if (intent == null) return
                    // Cryptographic per-session token verification (HIGH-02)
                    val receivedToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
                    if (receivedToken != sessionToken) {
                        Log.w("InstallerReceiver", "Ignored broadcast with invalid or missing session token")
                        return
                    }

                    if (intent.action == "$INSTALL_CANCEL_ACTION.$id") {
                        completeOnce(success = false, abandonIfFailed = true, failureReason = InstallFailureReason.ABORTED)
                        return
                    }

                    // Explicit key presence check — never allow missing EXTRA_STATUS to default to 0 (STATUS_SUCCESS)!
                    val extras = intent.extras
                    if (extras == null || !extras.containsKey(PackageInstaller.EXTRA_STATUS)) {
                        Log.w("InstallerReceiver", "Ignored PackageInstaller callback missing EXTRA_STATUS")
                        return
                    }

                    val status = extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    val message = extras.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            installLog.currentInstallId = id
                            val confirmIntent = intent.getIntentExtra()
                            if (confirmIntent == null) {
                                Log.e("InstallerReceiver", "STATUS_PENDING_USER_ACTION received with null confirmIntent")
                                completeOnce(success = false, abandonIfFailed = true)
                                return
                            }

                            // Verify confirmIntent resolves strictly to a system Activity (HIGH-03 defense against FLAG_MUTABLE hijack)
                            val systemResolved = runCatching {
                                context.packageManager.resolveActivity(confirmIntent, PackageManager.MATCH_SYSTEM_ONLY)
                            }.getOrNull()
                            if (systemResolved == null) {
                                Log.e("InstallerReceiver", "Blocked non-system EXTRA_INTENT in PackageInstaller callback!")
                                completeOnce(success = false, abandonIfFailed = true)
                                return
                            }

                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                            if (AppLifecycleTracker.isAppInForeground) {
                                val balBundle = buildBackgroundActivityLaunchOptions()
                                scope.launch(Dispatchers.Main) {
                                    runCatching {
                                        val launchCtx = context.applicationContext ?: context
                                        if (balBundle != null) {
                                            launchCtx.startActivity(confirmIntent, balBundle)
                                        } else {
                                            launchCtx.startActivity(confirmIntent)
                                        }
                                    }.onFailure { error ->
                                        Log.w("InstallerReceiver", "Direct foreground launch failed ($error); trying notification")
                                        if (!postConfirmationNotification(context, id, packageName, confirmIntent, sessionToken)) {
                                            completeOnce(success = false, abandonIfFailed = true)
                                        }
                                    }
                                }
                            } else {
                                scope.launch(Dispatchers.Main) {
                                    if (!postConfirmationNotification(context, id, packageName, confirmIntent, sessionToken)) {
                                        completeOnce(success = false, abandonIfFailed = true)
                                    }
                                }
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            completeOnce(success = true)
                        }
                        else -> {
                            Log.e("InstallerReceiver", "PackageInstaller failure ($status: $message)")
                            completeOnce(
                                success = false,
                                abandonIfFailed = true,
                                failureReason = resolveInstallFailureReason(status, message)
                            )
                        }
                    }
                }
            }

            callback = object : PackageInstaller.SessionCallback() {
                override fun onBadgingChanged(sessionId: Int) {}
                override fun onActiveChanged(sessionId: Int, active: Boolean) {}
                override fun onCreated(sessionId: Int) {}
                override fun onProgressChanged(sessionId: Int, p: Float) {}
                override fun onFinished(sessionId: Int, success: Boolean) {
                    if (createdSessionId != null && sessionId == createdSessionId) {
                        if (success) {
                            completeOnce(true)
                        } else {
                            mainHandler.postDelayed(
                                { completeOnce(false, abandonIfFailed = false, failureReason = InstallFailureReason.GENERIC) },
                                600L
                            )
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction("$INSTALL_ACTION.$id")
                addAction("$INSTALL_CANCEL_ACTION.$id")
            }
            ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            packageInstaller.registerSessionCallback(callback, mainHandler)

            var committed = false

            continuation.invokeOnCancellation {
                safeUnregisterReceiver()
                safeUnregisterCallback()
                createdSessionId?.let { sessId ->
                    activeSessions.remove(id, sessId)
                    runCatching { packageInstaller.abandonSession(sessId) }
                } ?: activeSessions.remove(id)
            }

            try {
                val sessionId = packageInstaller.createSession(params)
                createdSessionId = sessionId
                activeSessions[id] = sessionId
                var totalBytes = 0L
                packageInstaller.openSession(sessionId).use { session ->
                    parts.forEachIndexed { index, part ->
                        val safePartName = ApkIntegrityVerifier.sanitizePartFileName(part.name, index)
                        val rawStream = part.openStream()
                            ?: throw IOException("Download stream returned null for $packageName (part $safePartName)")
                        val stream = wrapStreamIfGzip(rawStream)
                        stream.use { input ->
                            val partSize = if (part.sizeBytes > 0L && stream !is java.util.zip.GZIPInputStream) part.sizeBytes else -1L
                            session.openWrite(safePartName, 0, partSize).use { output ->
                                totalBytes += input.copyToAndNotify(output, id, installLog, totalBytes)
                                session.fsync(output)
                            }
                        }
                    }

                    val broadcastIntent = Intent("$INSTALL_ACTION.$id").apply {
                        setPackage(ctx.packageName)
                        putExtra(EXTRA_APP_ID, id)
                        putExtra(EXTRA_SESSION_TOKEN, sessionToken)
                    }
                    val pending = PendingIntent.getBroadcast(
                        ctx,
                        sessionId,
                        broadcastIntent,
                        FLAG_UPDATE_CURRENT or FLAG_MUTABLE
                    )
                    session.commit(pending.intentSender)
                    committed = true
                }
            } catch (error: Throwable) {
                safeUnregisterReceiver()
                safeUnregisterCallback()
                createdSessionId?.let { sessId ->
                    activeSessions.remove(id, sessId)
                    if (!committed) {
                        runCatching { packageInstaller.abandonSession(sessId) }
                    }
                } ?: activeSessions.remove(id)
                throw error
            }
        }
    }

    /**
     * Posts a high-priority install confirmation notification when backgrounded.
     * Returns `false` immediately if notifications are disabled (`POST_NOTIFICATIONS` denied),
     * preventing a 120-second silent hang per app (HIGH-16).
     */
    private fun postConfirmationNotification(
        context: Context,
        id: Int,
        packageName: String,
        confirmIntent: Intent,
        sessionToken: String
    ): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w("SessionInstaller", "Notifications disabled while app is in background; failing fast instead of hanging.")
            return false
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CONFIRM_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_install_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_install_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)

        val balOptions = buildBackgroundActivityLaunchOptions()
        val pendingIntent = if (balOptions != null) {
            PendingIntent.getActivity(
                context,
                id,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                balOptions
            )
        } else {
            PendingIntent.getActivity(
                context,
                id,
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val cancelIntent = Intent("$INSTALL_CANCEL_ACTION.$id").apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SESSION_TOKEN, sessionToken)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appName = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

        val notification = NotificationCompat.Builder(context, CONFIRM_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_install)
            .setContentTitle(context.getString(R.string.notification_install_confirm_title, appName))
            .setContentText(context.getString(R.string.notification_install_confirm_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_CONFIRM_BASE + id, notification)
        return true
    }

    fun finish(id: Int? = null) {
        if (id != null) {
            activeSessions.remove(id)?.let { sessId ->
                context?.let { ctx ->
                    runCatching {
                        ctx.packageManager.packageInstaller.abandonSession(sessId)
                    }
                }
            }
            context?.let { ctx ->
                runCatching {
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(NOTIFICATION_ID_CONFIRM_BASE + id)
                }
            }
        }
    }

    fun checkPermission(): Boolean {
        if (isSilentInstallerReady()) return true
        val ctx = context ?: return false
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()
            val intent = Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent, null)
            return false
        }
        return true
    }
}

private const val EXTRA_APP_ID = "appId"
private const val EXTRA_SESSION_TOKEN = "sessionToken"

private enum class ShizukuAvailability {
    READY,
    NOT_RUNNING,
    PERMISSION_DENIED
}

fun InputStream.copyToAndNotify(
    out: OutputStream,
    id: Int,
    installLog: InstallLog,
    total: Long,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        installLog.emitProgress(AppInstallProgress(id, progress = total + bytesCopied))
        bytes = read(buffer)
    }
    return bytesCopied
}
