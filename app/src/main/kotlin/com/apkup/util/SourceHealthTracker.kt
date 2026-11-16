package com.apkup.util

import java.util.concurrent.ConcurrentHashMap

data class SourceHealthEntry(
    val sourceName: String,
    val lastSuccessMillis: Long = 0L,
    val lastErrorMillis: Long = 0L,
    val lastErrorMessage: String = "",
    val cooldownUntilMillis: Long = 0L,
    val lastDurationMillis: Long = 0L,
    val lastFoundUpdates: Int = 0
) {
    fun isCooldownActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        cooldownUntilMillis > nowMillis

    fun remainingCooldownSeconds(nowMillis: Long = System.currentTimeMillis()): Long =
        if (cooldownUntilMillis > nowMillis) (cooldownUntilMillis - nowMillis + 999L) / 1_000L else 0L

    val hasRecentError: Boolean
        get() = lastErrorMillis > 0L && lastErrorMillis >= lastSuccessMillis
}

object SourceHealthTracker {
    private val entries = ConcurrentHashMap<String, SourceHealthEntry>()

    fun recordSuccess(
        sourceName: String,
        foundUpdates: Int = 0,
        durationMillis: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val key = sourceName.trim()
        if (key.isEmpty()) return
        entries.compute(key) { _, prev ->
            (prev ?: SourceHealthEntry(sourceName = key)).copy(
                lastSuccessMillis = nowMillis,
                lastDurationMillis = durationMillis.coerceAtLeast(0L),
                lastFoundUpdates = foundUpdates.coerceAtLeast(0),
                lastErrorMessage = if ((prev?.cooldownUntilMillis ?: 0L) > nowMillis) prev?.lastErrorMessage.orEmpty() else ""
            )
        }
    }

    fun recordError(
        sourceName: String,
        message: String,
        cooldownDurationMillis: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val key = sourceName.trim()
        if (key.isEmpty()) return
        val cooldownUntil = if (cooldownDurationMillis > 0L) nowMillis + cooldownDurationMillis else 0L
        entries.compute(key) { _, prev ->
            (prev ?: SourceHealthEntry(sourceName = key)).copy(
                lastErrorMillis = nowMillis,
                lastErrorMessage = message.trim().take(240),
                cooldownUntilMillis = maxOf(prev?.cooldownUntilMillis ?: 0L, cooldownUntil)
            )
        }
    }

    fun recordCooldown(
        sourceName: String,
        cooldownUntilMillis: Long,
        reason: String = ""
    ) {
        val key = sourceName.trim()
        if (key.isEmpty()) return
        entries.compute(key) { _, prev ->
            val current = prev ?: SourceHealthEntry(sourceName = key)
            current.copy(
                cooldownUntilMillis = maxOf(current.cooldownUntilMillis, cooldownUntilMillis),
                lastErrorMessage = reason.trim().ifEmpty { current.lastErrorMessage }
            )
        }
    }

    fun snapshot(): List<SourceHealthEntry> =
        entries.values.sortedBy { it.sourceName.lowercase() }

    fun clear() {
        entries.clear()
    }
}
