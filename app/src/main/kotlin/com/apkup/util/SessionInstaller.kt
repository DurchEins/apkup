package com.apkup.util

/**
 * Handles the actual installation of APK files via the Android PackageInstaller API.
 * This class takes downloaded files (single APKs or split/AppBundle APKs) and commits them
 * to an Android installation session.
 */
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import androidx.core.app.NotificationCompat
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
import android.util.Log
import androidx.core.net.toUri
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.ShizukuSetupStatus
import com.apkup.prefs.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID.randomUUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.io.DEFAULT_BUFFER_SIZE

data class InstallPart(
    val name: String,
    val sizeBytes: Long = -1L,
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
    private val onFallbackNotice: ((Int) -> Unit)? = null
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
        onFallbackNotice = null
    )

    companion object {
        const val INSTALL_ACTION = "installAction"
        const val INSTALL_CANCEL_ACTION = "com.apkup.installCancelAction"
        const val NOTIFICATION_ID_CONFIRM_BASE = 8000
        const val CONFIRM_NOTIFICATION_CHANNEL_ID = "apkup_install_confirm_channel"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 9401
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }

    init {
        runCatching {
            val installer = context?.packageManager?.packageInstaller ?: return@runCatching
            installer.mySessions.forEach { installer.abandonSession(it.sessionId) }
        }
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeSessions = ConcurrentHashMap<Int, Int>()

    private val shizukuBridge: ShizukuBridge by lazy {
        shizukuBridgeOverride ?: AndroidShizukuBridge()
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

    suspend fun installParts(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        if (mutex.isLocked) {
            runCatching { mutex.unlock() }
        }
        installLog.beginInstall(id)
        if (parts.isEmpty()) {
            throw IOException("No installation parts provided for $packageName")
        }

        // Strict opt-in check: only attempt Shizuku when explicitly enabled by the user in Settings.
        if (prefs.useShizukuInstaller.get()) {
            val shizukuStatus = ensureShizukuReadyAndAuthorized()
            if (shizukuStatus == ShizukuAvailability.READY) {
                val shizukuResult = runCatching {
                    shizukuBridge.executeShizukuInstall(id, packageName, parts)
                }
                if (shizukuResult.isSuccess) {
                    val succeeded = shizukuResult.getOrDefault(false)
                    installLog.emitStatus(AppInstallStatus(succeeded, id, snack = true))
                    return succeeded
                } else {
                    runCatching {
                        Log.w(
                            "SessionInstaller",
                            "Shizuku installation failed (${shizukuResult.exceptionOrNull()?.message}); falling back to standard PackageInstaller."
                        )
                    }
                    notifyShizukuFallback(R.string.shizuku_fallback_not_running)
                }
            } else {
                val messageRes = when (shizukuStatus) {
                    ShizukuAvailability.PERMISSION_DENIED -> R.string.shizuku_fallback_no_permission
                    else -> R.string.shizuku_fallback_not_running
                }
                notifyShizukuFallback(messageRes)
            }
        }

        val fallbackOverride = packageInstallerFallbackOverride
        if (fallbackOverride != null) {
            val result = fallbackOverride(id, packageName, parts)
            installLog.emitStatus(AppInstallStatus(result, id, snack = true))
            return result
        }
        return installWithPackageInstaller(id, packageName, parts)
    }

    private fun notifyShizukuFallback(messageRes: Int) {
        onFallbackNotice?.invoke(messageRes)
        val snack = snackBar ?: return
        val str = stringer ?: return
        snack.snackBar(scope, TextSnack(str.get(messageRes)))
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

    private fun installWithShizuku(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        val ctx = requireNotNull(context) { "Android Context required for native Shizuku install" }
        val binder = Shizuku.getBinder() ?: throw IOException("Shizuku binder unavailable")
        val shizukuService = IShizukuService.Stub.asInterface(binder)
            ?: throw IOException("IShizukuService unavailable")

        val tempDir = File(ctx.cacheDir, "shizuku_staging_$id").apply { mkdirs() }
        var sessionId: String? = null
        try {
            var totalBytes = 0L
            val stagedFiles = parts.mapIndexed { index, part ->
                val rawStream = part.openStream()
                    ?: throw IOException("Download stream returned null for $packageName (part ${part.name})")
                val stream = wrapStreamIfGzip(rawStream)
                val fileName = if (part.name.endsWith(".apk")) part.name else "${part.name}.apk"
                val partFile = File(tempDir, fileName)
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
                    throw IOException("Downloaded empty APK split for $packageName (${part.name})")
                }
                partFile
            }

            val createOutput = runShizukuCommand(
                shizukuService,
                arrayOf("pm", "install-create", "-r", "-i", "com.android.vending")
            )
            sessionId = Regex("\\[(\\d+)]").find(createOutput)?.groupValues?.getOrNull(1)
                ?: throw IOException("Could not parse Shizuku install session ID from: $createOutput")

            stagedFiles.forEach { file ->
                val writeProc = shizukuService.newProcess(
                    arrayOf("pm", "install-write", "-S", file.length().toString(), sessionId, file.name, "-"),
                    null,
                    null
                )
                try {
                    file.inputStream().use { fileIn ->
                        ParcelFileDescriptor.AutoCloseOutputStream(writeProc.outputStream).use { procOut ->
                            fileIn.copyTo(procOut)
                            procOut.flush()
                        }
                    }
                    val exitCode = writeProc.waitFor()
                    if (exitCode != 0) {
                        val err = runCatching {
                            ParcelFileDescriptor.AutoCloseInputStream(writeProc.errorStream)
                                .bufferedReader()
                                .use { it.readText() }
                        }.getOrDefault("")
                        throw IOException("pm install-write failed ($exitCode): $err")
                    }
                } finally {
                    runCatching { writeProc.destroy() }
                }
            }

            val commitOutput = runShizukuCommand(
                shizukuService,
                arrayOf("pm", "install-commit", sessionId)
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

    private fun runShizukuCommand(service: IShizukuService, command: Array<String>): String {
        val process = service.newProcess(command, null, null)
        return try {
            val stdOut = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader()
                .use { it.readText() }
            val stdErr = ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                .bufferedReader()
                .use { it.readText() }
            val exit = process.waitFor()
            if (exit != 0 && !stdOut.contains("Success", ignoreCase = true)) {
                throw IOException("Command ${command.joinToString(" ")} failed ($exit): $stdErr $stdOut")
            }
            "$stdOut\n$stdErr"
        } finally {
            runCatching { process.destroy() }
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy", "UnspecifiedRegisterReceiverFlag")
    private suspend fun installWithPackageInstaller(
        id: Int,
        packageName: String,
        parts: List<InstallPart>
    ): Boolean {
        val ctx = requireNotNull(context) { "Android Context required for PackageInstaller" }
        if (!canRequestPackageInstalls()) {
            Log.w("SessionInstaller", "Cannot request package installs for $packageName; prompting permission.")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                setRequestUpdateOwnership(true)
            }
        }

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

            fun completeOnce(success: Boolean) {
                if (terminalHandled.compareAndSet(false, true)) {
                    createdSessionId?.let { sessId -> activeSessions.remove(id, sessId) } ?: activeSessions.remove(id)
                    runCatching {
                        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(NOTIFICATION_ID_CONFIRM_BASE + id)
                    }
                    installLog.emitStatus(AppInstallStatus(success, id, snack = true))
                    safeUnregisterReceiver()
                    safeUnregisterCallback()
                    if (mutex.isLocked) runCatching { mutex.unlock() }
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }
            }

            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, @SuppressLint("UnsafeIntentLaunch") intent: Intent?) {
                    if (intent?.action == "$INSTALL_CANCEL_ACTION.$id") {
                        Log.i("InstallerReceiver", "User dismissed installation prompt for $packageName ($id)")
                        completeOnce(false)
                        return
                    }

                    val status = intent?.extras?.getInt(PackageInstaller.EXTRA_STATUS)
                    val message = intent?.extras?.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.i("InstallerReceiver", "PackageInstaller callback for $packageName ($id): status=$status, message=$message")
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            installLog.currentInstallId = id
                            val confirmIntent = intent.getIntentExtra()
                            if (confirmIntent != null) {
                                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                val isForeground = AppLifecycleTracker.isAppInForeground
                                if (isForeground) {
                                    val options = ActivityOptions.makeBasic()
                                    if (Build.VERSION.SDK_INT >= 35) {
                                        options.setPendingIntentCreatorBackgroundActivityStartMode(
                                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                        )
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        @Suppress("DEPRECATION")
                                        options.setPendingIntentBackgroundActivityStartMode(
                                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                        )
                                    }
                                    scope.launch(Dispatchers.Main) {
                                        if (mutex.isLocked) runCatching { mutex.unlock() }
                                        mutex.lock()
                                        runCatching {
                                            (context.applicationContext ?: context).startActivity(confirmIntent, options.toBundle())
                                        }.onFailure { error ->
                                            Log.w("InstallerReceiver", "Direct launch failed in foreground ($error); falling back to notification")
                                            postConfirmationNotification(context, id, packageName, confirmIntent)
                                        }
                                    }
                                } else {
                                    scope.launch(Dispatchers.Main) {
                                        if (mutex.isLocked) runCatching { mutex.unlock() }
                                        mutex.lock()
                                        postConfirmationNotification(context, id, packageName, confirmIntent)
                                    }
                                }
                            } else {
                                Log.e("InstallerReceiver", "STATUS_PENDING_USER_ACTION received but confirmIntent is null for $packageName ($id)")
                                completeOnce(false)
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            Log.i("InstallerReceiver", "SUCCESS for $packageName ($id)")
                            completeOnce(true)
                        }
                        else -> {
                            Log.e("InstallerReceiver", "FAILURE ($status: $message) for $packageName ($id)")
                            completeOnce(false)
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
                        Log.i("InstallerReceiver", "PackageInstaller SessionCallback.onFinished for $packageName ($id, session=$sessionId): success=$success")
                        completeOnce(success)
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction("$INSTALL_ACTION.$id")
                addAction("$INSTALL_CANCEL_ACTION.$id")
            }
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, filter)
            }
            packageInstaller.registerSessionCallback(callback, Handler(Looper.getMainLooper()))

            var committed = false

            continuation.invokeOnCancellation {
                safeUnregisterReceiver()
                safeUnregisterCallback()
                createdSessionId?.let { sessId ->
                    activeSessions.remove(id, sessId)
                    runCatching { packageInstaller.abandonSession(sessId) }
                } ?: activeSessions.remove(id)
                if (mutex.isLocked) runCatching { mutex.unlock() }
            }

            try {
                val sessionId = packageInstaller.createSession(params)
                createdSessionId = sessionId
                activeSessions[id] = sessionId
                var totalBytes = 0L
                packageInstaller.openSession(sessionId).use { session ->
                    parts.forEachIndexed { index, part ->
                        val rawStream = part.openStream()
                            ?: throw IOException("Download stream returned null for $packageName (part ${part.name})")
                        val stream = wrapStreamIfGzip(rawStream)
                        stream.use { input ->
                            val partName = if (part.name.endsWith(".apk")) part.name else "${part.name}.apk"
                            val partSize = if (part.sizeBytes > 0L && stream !is java.util.zip.GZIPInputStream) part.sizeBytes else -1L
                            session.openWrite(partName, 0, partSize).use { output ->
                                totalBytes += input.copyToAndNotify(output, id, installLog, totalBytes)
                                session.fsync(output)
                            }
                        }
                    }

                    val broadcastIntent = Intent("$INSTALL_ACTION.$id").apply {
                        setPackage(ctx.packageName)
                        putExtra(EXTRA_APP_ID, id)
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
                if (mutex.isLocked) runCatching { mutex.unlock() }
                throw error
            }
        }
    }

    private fun postConfirmationNotification(
        context: Context,
        id: Int,
        packageName: String,
        confirmIntent: Intent
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CONFIRM_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.notification_install_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_install_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent("$INSTALL_CANCEL_ACTION.$id").setPackage(context.packageName)
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
        if (mutex.isLocked) {
            runCatching { mutex.unlock() }
        }
    }

    fun checkPermission(): Boolean {
        if (isShizukuReady()) return true
        val ctx = context ?: return false
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()
            val intent = Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            ctx.startActivity(intent, null)
            return false
        }
        return true
    }

}

private const val EXTRA_APP_ID = "appId"

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
