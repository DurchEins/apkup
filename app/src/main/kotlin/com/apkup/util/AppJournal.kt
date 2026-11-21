package com.apkup.util

import android.content.Context
import android.os.Build
import android.os.Process
import com.apkup.data.ui.ShizukuSetupStatus
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class AppJournal(
    private val journalFile: File? = null,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val persistDebounceMs: Long = DEFAULT_PERSIST_DEBOUNCE_MS
) {
    constructor(filesDir: File, maxEntries: Int = DEFAULT_MAX_ENTRIES) : this(
        journalFile = File(filesDir, JOURNAL_FILENAME),
        maxEntries = maxEntries
    )

    private val lock = Any()
    private val writeLock = Any()
    private val buffer = ArrayDeque<String>()
    private var loadedFromDisk = false
    private var dirtyVersion = 0L
    private var persistedVersion = 0L
    private var pendingDelete = false
    private var pendingPersistFuture: ScheduledFuture<*>? = null

    fun log(tag: String, message: String) {
        val cleanTag = tag.trim().ifEmpty { "ApkUp" }.take(32)
        val sanitizedMsg = sanitizeJournalText(message)
        if (sanitizedMsg.isEmpty()) return

        val line = "${formatTimestamp(timeProvider())} | $cleanTag | $sanitizedMsg"
        synchronized(lock) {
            ensureLoadedLocked()
            buffer.addLast(line)
            while (buffer.size > maxEntries) {
                buffer.removeFirst()
            }
            pendingDelete = false
            schedulePersistLocked()
        }
    }

    fun entries(): List<String> = synchronized(lock) {
        ensureLoadedLocked()
        buffer.toList()
    }

    fun clear() {
        synchronized(lock) {
            loadedFromDisk = true
            buffer.clear()
            pendingPersistFuture?.cancel(false)
            pendingPersistFuture = null
            dirtyVersion++
            persistedVersion = dirtyVersion
            pendingDelete = journalFile != null
        }
        if (journalFile != null) {
            synchronized(lock) {
                pendingPersistFuture = persistExecutor.schedule(
                    { flush() },
                    0L,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    fun flush() {
        val file = journalFile ?: return
        val pendingWork: Triple<Boolean, Long, String?> = synchronized(lock) {
            pendingPersistFuture?.cancel(false)
            pendingPersistFuture = null
            if (pendingDelete) {
                Triple(true, dirtyVersion, null)
            } else if (persistedVersion == dirtyVersion) {
                Triple(false, persistedVersion, null)
            } else {
                ensureLoadedLocked()
                val content = if (buffer.isEmpty()) "" else buffer.joinToString(separator = "\n", postfix = "\n")
                Triple(false, dirtyVersion, content)
            }
        }
        val (deleteRequested, targetVersion, content) = pendingWork
        if (!deleteRequested && content == null) return

        synchronized(writeLock) {
            if (deleteRequested) {
                val shouldDelete = synchronized(lock) { pendingDelete }
                if (shouldDelete) {
                    runCatching { file.delete() }
                    synchronized(lock) {
                        if (pendingDelete && buffer.isEmpty()) {
                            pendingDelete = false
                        }
                    }
                }
                return
            }
            val stillStale = synchronized(lock) { !pendingDelete && targetVersion > persistedVersion }
            if (stillStale && content != null) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(content, Charsets.UTF_8)
                }.onSuccess {
                    synchronized(lock) {
                        if (targetVersion > persistedVersion) {
                            persistedVersion = targetVersion
                        }
                    }
                }
            }
        }
    }

    fun buildDiagnosticReport(
        appVersionName: String,
        appVersionCode: Int,
        packageName: String = "com.apkup",
        shizukuStatus: ShizukuSetupStatus? = null,
        activeSources: List<String> = emptyList(),
        logcatMaxLines: Int = 250,
        includeLogcat: Boolean = false,
        logcatCollector: () -> String = { collectProcessLogcat(logcatMaxLines) }
    ): String {
        val journalLines = entries()
        val logcatText = if (includeLogcat) {
            runCatching { logcatCollector() }
                .getOrDefault("")
                .lines()
                .joinToString("\n") { sanitizeJournalText(it) }
        } else {
            ""
        }
        val abis = runCatching { Build.SUPPORTED_ABIS?.joinToString(", ").orEmpty() }.getOrDefault("")
        val deviceLabel = runCatching {
            "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()} (Android ${Build.VERSION.RELEASE.orEmpty()}, SDK ${Build.VERSION.SDK_INT})"
        }.getOrDefault("Android")

        return buildString {
            appendLine("=== ApkUp Diagnostic Report ===")
            appendLine("GeneratedAt: ${formatTimestamp(timeProvider())}")
            appendLine("App: $packageName v$appVersionName (code=$appVersionCode)")
            appendLine("Device: $deviceLabel")
            if (abis.isNotBlank()) {
                appendLine("ABIs: $abis")
            }
            if (shizukuStatus != null) {
                appendLine(
                    "Shizuku: installed=${shizukuStatus.isInstalled}, binderRunning=${shizukuStatus.isBinderRunning}, " +
                        "permission=${shizukuStatus.isPermissionGranted}, ready=${shizukuStatus.isReady}"
                )
            }
            if (activeSources.isNotEmpty()) {
                appendLine("ActiveSources: ${activeSources.joinToString(", ")}")
            }
            appendLine()
            appendLine("=== In-App Update Journal (${journalLines.size} entries) ===")
            if (journalLines.isEmpty()) {
                appendLine("(no journal entries recorded yet)")
            } else {
                journalLines.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("=== Android Process Logcat ===")
            if (!includeLogcat) {
                appendLine("(omitted — opt-in disabled)")
            } else if (logcatText.isBlank()) {
                appendLine("(no process logcat output available)")
            } else {
                appendLine(logcatText.trimEnd())
            }
        }
    }

    private fun ensureLoadedLocked() {
        if (loadedFromDisk) return
        loadedFromDisk = true
        val file = journalFile ?: return
        runCatching {
            if (file.exists()) {
                val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
                val tail = if (lines.size > maxEntries) lines.takeLast(maxEntries) else lines
                buffer.addAll(tail)
            }
        }
    }

    private fun schedulePersistLocked() {
        if (journalFile == null) return
        dirtyVersion++
        pendingPersistFuture?.cancel(false)
        pendingPersistFuture = persistExecutor.schedule(
            { flush() },
            persistDebounceMs.coerceAtLeast(0L),
            TimeUnit.MILLISECONDS
        )
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 500
        const val JOURNAL_FILENAME = "apkup_journal.log"
        const val DIAGNOSTIC_LOGS_CACHE_DIR = "diagnostic_logs"
        private const val DEFAULT_PERSIST_DEBOUNCE_MS = 250L
        private const val DIAGNOSTIC_SHARE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        private const val LOGCAT_WAIT_TIMEOUT_SECONDS = 3L

        private val persistExecutor: ScheduledExecutorService by lazy {
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "apkup-journal-writer").apply { isDaemon = true }
            }
        }

        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .withZone(ZoneId.systemDefault())

        private val BEARER_REGEX = Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE)
        private val AUTHORIZATION_TOKEN_REGEX = Regex("""Authorization:\s*token\s+\S+""", RegexOption.IGNORE_CASE)
        private val PRIVATE_TOKEN_HEADER_REGEX = Regex("""PRIVATE-TOKEN:\s*\S+""", RegexOption.IGNORE_CASE)
        private val AUTH_BASIC_HEADER_REGEX = Regex("""Authorization:\s*Basic\s+[A-Za-z0-9+/=]{8,}""", RegexOption.IGNORE_CASE)
        private val BASIC_AUTH_REGEX = Regex("""\bBasic\s+[A-Za-z0-9+/=]{8,}""")
        private val QUERY_SECRET_REGEX = Regex("""([?&](?:key|token|auth|password|secret)=)[^&\s]+""", RegexOption.IGNORE_CASE)
        private val AURORA_AUTH_REGEX = Regex("""(?<![?&A-Za-z0-9_])Auth=(?!\[REDACTED\])\S+""", RegexOption.IGNORE_CASE)
        private val AAS_ET_REGEX = Regex("""\baas_et/\S+""", RegexOption.IGNORE_CASE)
        private val PAT_TOKEN_REGEX = Regex("""\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[A-Za-z0-9_]+\b|\bglpat-[A-Za-z0-9_-]+\b""")
        private val COOKIE_HEADER_REGEX = Regex("""\b(Set-Cookie|Cookie):\s*\S+(?:\s+[^\r\n]*?(?=\s+[A-Za-z-]+:\s|$))?""", RegexOption.IGNORE_CASE)
        private val URL_USERINFO_REGEX = Regex("""(https?://)([^/\s:@]+:[^/\s@]+)@""", RegexOption.IGNORE_CASE)

        @Volatile
        private var sharedInstance: AppJournal? = null

        fun registerShared(journal: AppJournal): AppJournal {
            sharedInstance = journal
            return journal
        }

        fun record(tag: String, message: String) {
            sharedInstance?.log(tag, message)
        }

        fun cleanupDiagnosticLogShareFiles(
            context: Context,
            maxAgeMs: Long = DIAGNOSTIC_SHARE_MAX_AGE_MS,
            nowMs: Long = System.currentTimeMillis()
        ) {
            val cacheDir = context.cacheDir ?: return
            cleanupDiagnosticLogShareFiles(cacheDir, maxAgeMs, nowMs)
        }

        internal fun cleanupDiagnosticLogShareFiles(
            cacheDir: File,
            maxAgeMs: Long = DIAGNOSTIC_SHARE_MAX_AGE_MS,
            nowMs: Long = System.currentTimeMillis()
        ) {
            val dir = File(cacheDir, DIAGNOSTIC_LOGS_CACHE_DIR)
            if (!dir.isDirectory) return
            runCatching {
                dir.listFiles()?.forEach { child ->
                    if (child.isFile && nowMs - child.lastModified() >= maxAgeMs) {
                        runCatching { child.delete() }
                    }
                }
            }
        }

        internal fun formatTimestamp(epochMillis: Long): String =
            TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis))

        internal fun sanitizeJournalText(raw: String): String {
            var clean = raw.replace('\r', ' ').replace('\n', ' ').trim()
            clean = BEARER_REGEX.replace(clean, "Bearer [REDACTED]")
            clean = AUTHORIZATION_TOKEN_REGEX.replace(clean, "Authorization: token [REDACTED]")
            clean = PRIVATE_TOKEN_HEADER_REGEX.replace(clean, "PRIVATE-TOKEN: [REDACTED]")
            clean = AUTH_BASIC_HEADER_REGEX.replace(clean, "Authorization: Basic [REDACTED]")
            clean = BASIC_AUTH_REGEX.replace(clean, "Basic [REDACTED]")
            clean = QUERY_SECRET_REGEX.replace(clean) { match -> "${match.groupValues[1]}[REDACTED]" }
            clean = AURORA_AUTH_REGEX.replace(clean, "Auth=[REDACTED]")
            clean = AAS_ET_REGEX.replace(clean, "aas_et/[REDACTED]")
            clean = PAT_TOKEN_REGEX.replace(clean, "[TOKEN_REDACTED]")
            clean = COOKIE_HEADER_REGEX.replace(clean) { match -> "${match.groupValues[1]}: [REDACTED]" }
            clean = URL_USERINFO_REGEX.replace(clean) { match -> "${match.groupValues[1]}[REDACTED]@" }
            return clean
        }

        internal fun collectProcessLogcat(maxLines: Int = 250): String {
            return runCatching {
                val pid = Process.myPid()
                val process = ProcessBuilder("logcat", "-d", "--pid=$pid", "-v", "time")
                    .redirectErrorStream(true)
                    .start()
                readProcessLogcatWithTimeout(
                    process = process,
                    maxLines = maxLines,
                    timeout = LOGCAT_WAIT_TIMEOUT_SECONDS,
                    unit = TimeUnit.SECONDS
                )
            }.getOrDefault("")
        }

        internal fun readProcessLogcatWithTimeout(
            process: java.lang.Process,
            maxLines: Int = 250,
            timeout: Long = LOGCAT_WAIT_TIMEOUT_SECONDS,
            unit: TimeUnit = TimeUnit.SECONDS
        ): String {
            val linesLock = Any()
            val rawLines = ArrayDeque<String>()
            val readerLimit = (maxLines * 2).coerceAtLeast(maxLines)
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(linesLock) {
                                rawLines.addLast(line)
                                while (rawLines.size > readerLimit) {
                                    rawLines.removeFirst()
                                }
                            }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "apkup-logcat-reader"
                start()
            }

            val finished = process.waitFor(timeout, unit)
            if (!finished) {
                process.destroyForcibly()
            }
            readerThread.join(500L)

            val snapshot = synchronized(linesLock) { rawLines.toList() }
            val tail = if (snapshot.size > maxLines) snapshot.takeLast(maxLines) else snapshot
            return tail.joinToString("\n") { sanitizeJournalText(it) }
        }
    }
}
