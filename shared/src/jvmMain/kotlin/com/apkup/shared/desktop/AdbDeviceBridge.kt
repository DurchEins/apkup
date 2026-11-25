package com.apkup.shared.desktop

import com.apkup.shared.model.KmpInstalledApp
import com.apkup.shared.platform.computeFileSha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class AdbDeviceInfo(
    val serial: String,
    val model: String,
    val product: String,
    val state: String,
    val isWireless: Boolean,
    val abis: List<String> = listOf("arm64-v8a", "armeabi-v7a")
) {
    val displayName: String
        get() = buildString {
            append(model.ifBlank { serial })
            append(" (")
            append(if (isWireless) "WLAN" else "USB")
            append(" · ")
            append(serial)
            append(")")
        }
}

data class AdbCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val combinedOutput: String get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
}

data class LocalApkInspection(
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256Hex: String,
    val sha256Micros: Long,
    val hasAndroidManifest: Boolean,
    val abis: List<String>
)

object AdbDeviceBridge {

    fun findAdbExecutable(): String {
        val candidates = buildList {
            System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let {
                add(File(it, "platform-tools/adb.exe"))
                add(File(it, "platform-tools/adb"))
            }
            System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let {
                add(File(it, "Android/Sdk/platform-tools/adb.exe"))
            }
            System.getenv("USERPROFILE")?.takeIf { it.isNotBlank() }?.let {
                add(File(it, "AppData/Local/Android/Sdk/platform-tools/adb.exe"))
            }
        }
        return candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath ?: "adb"
    }

    suspend fun runAdb(vararg args: String, timeoutSec: Long = 30): AdbCommandResult = withContext(Dispatchers.IO) {
        val adb = findAdbExecutable()
        val pb = ProcessBuilder(listOf(adb) + args)
        pb.redirectErrorStream(false)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val err = proc.errorStream.bufferedReader(Charsets.UTF_8).readText()
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@withContext AdbCommandResult(-1, out, "ADB command timed out after ${timeoutSec}s")
        }
        AdbCommandResult(proc.exitValue(), out.trim(), err.trim())
    }

    suspend fun listDevices(): List<AdbDeviceInfo> {
        val res = runAdb("devices", "-l")
        if (!res.isSuccess) return emptyList()
        val devices = mutableListOf<AdbDeviceInfo>()
        for (line in res.stdout.lines().drop(1)) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val serial = parts[0]
            val state = parts[1]
            if (state != "device") continue
            val model = Regex("model:([^\\s]+)").find(trimmed)?.groupValues?.getOrNull(1)?.replace('_', ' ') ?: serial
            val product = Regex("product:([^\\s]+)").find(trimmed)?.groupValues?.getOrNull(1) ?: ""
            val isWireless = serial.contains(":") || serial.contains("_adb-tls-connect")
            val abiRes = runAdb("-s", serial, "shell", "getprop", "ro.product.cpu.abilist")
            val abis = abiRes.stdout.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("arm64-v8a", "armeabi-v7a") }
            devices += AdbDeviceInfo(
                serial = serial,
                model = model,
                product = product,
                state = state,
                isWireless = isWireless,
                abis = abis
            )
        }
        return devices
    }

    suspend fun pairWireless(ipPort: String, pairingCode: String): AdbCommandResult =
        runAdb("pair", ipPort.trim(), pairingCode.trim(), timeoutSec = 20)

    suspend fun connectWireless(ipPort: String): AdbCommandResult =
        runAdb("connect", ipPort.trim(), timeoutSec = 15)

    suspend fun disconnectWireless(ipPort: String): AdbCommandResult =
        runAdb("disconnect", ipPort.trim(), timeoutSec = 10)

    suspend fun listInstalledApps(
        serial: String,
        includeSystem: Boolean = false
    ): List<KmpInstalledApp> {
        val flag = if (includeSystem) "-f" else "-f -3"
        val pmArgs = mutableListOf("-s", serial, "shell", "pm", "list", "packages")
        if (includeSystem) {
            pmArgs.add("-f")
        } else {
            pmArgs.add("-f")
            pmArgs.add("-3")
        }
        pmArgs.add("--show-versioncode")
        val res = runAdb(*pmArgs.toTypedArray(), timeoutSec = 25)
        if (!res.isSuccess) return emptyList()

        // Line format: package:/data/app/.../base.apk=com.pkg.name versionCode:123
        val lineRegex = Regex("^package:(.+?)=([^\\s]+)(?:\\s+versionCode:(\\d+))?")
        val parsed = mutableListOf<KmpInstalledApp>()
        for (line in res.stdout.lines()) {
            val m = lineRegex.find(line.trim()) ?: continue
            val apkPath = m.groupValues[1]
            val pkgName = m.groupValues[2]
            val versionCode = m.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
            parsed += KmpInstalledApp(
                name = pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase(Locale.ROOT) },
                packageName = pkgName,
                version = if (versionCode > 0L) versionCode.toString() else "",
                versionCode = versionCode,
                isSystemApp = includeSystem && !apkPath.startsWith("/data/app"),
                apkPathOnDevice = apkPath
            )
        }

        // Batch-read versionName for user apps in a single fast shell invocation (up to 150 apps)
        if (!includeSystem && parsed.isNotEmpty()) {
            val pkgsToQuery = parsed.take(150).joinToString(" ") { it.packageName }
            val cmd = "for p in $pkgsToQuery; do v=$(dumpsys package \$p | grep -m1 versionName= | cut -d= -f2); echo \"\$p|\$v\"; done"
            val verRes = runAdb("-s", serial, "shell", cmd, timeoutSec = 30)
            if (verRes.isSuccess) {
                val verMap = verRes.stdout.lines()
                    .mapNotNull { line ->
                        val idx = line.indexOf('|')
                        if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                    }
                    .toMap()
                return parsed.map { app ->
                    val vn = verMap[app.packageName]?.takeIf { it.isNotBlank() }
                    if (vn != null) app.copy(version = vn) else app
                }.sortedBy { it.packageName }
            }
        }
        return parsed.sortedBy { it.packageName }
    }

    suspend fun installApks(serial: String, apkFiles: List<File>): AdbCommandResult {
        if (apkFiles.isEmpty()) return AdbCommandResult(-1, "", "No APK files selected")
        return if (apkFiles.size == 1) {
            runAdb("-s", serial, "install", "-r", apkFiles.first().absolutePath, timeoutSec = 120)
        } else {
            val args = mutableListOf("-s", serial, "install-multiple", "-r")
            apkFiles.forEach { args.add(it.absolutePath) }
            runAdb(*args.toTypedArray(), timeoutSec = 180)
        }
    }

    suspend fun pullApk(serial: String, remoteApkPath: String, destFile: File): AdbCommandResult {
        destFile.parentFile?.mkdirs()
        return runAdb("-s", serial, "pull", remoteApkPath, destFile.absolutePath, timeoutSec = 60)
    }

    suspend fun startShizukuViaAdb(serial: String): AdbCommandResult =
        runAdb("-s", serial, "shell", "sh", "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh", timeoutSec = 15)

    suspend fun inspectLocalApk(file: File): LocalApkInspection = withContext(Dispatchers.IO) {
        val hashRes = computeFileSha256Hex(file.absolutePath)
        var hasManifest = false
        val abis = linkedSetOf<String>()
        runCatching {
            ZipFile(file).use { zip ->
                hasManifest = zip.getEntry("AndroidManifest.xml") != null
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        val parts = name.split('/')
                        if (parts.size >= 3) abis += parts[1]
                    }
                }
            }
        }
        LocalApkInspection(
            filePath = file.absolutePath,
            fileName = file.name,
            sizeBytes = file.length(),
            sha256Hex = hashRes.sha256Hex,
            sha256Micros = hashRes.elapsedMicros,
            hasAndroidManifest = hasManifest,
            abis = if (abis.isEmpty()) listOf("universal / no native libs") else abis.sorted()
        )
    }
}
