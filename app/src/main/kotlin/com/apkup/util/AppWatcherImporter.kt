package com.apkup.util

import com.apkup.data.ui.WatchedAppEntry
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class AppWatcherImportSummary(
    val addedEntries: List<WatchedAppEntry>,
    val updatedSeenCount: Int,
    val skippedInstalledCount: Int,
    val alreadyWatchedCount: Int
) {
    val totalProcessed: Int
        get() = addedEntries.size + updatedSeenCount + skippedInstalledCount + alreadyWatchedCount
}

object AppWatcherImporter {
    private const val STATUS_DELETED = "2"

    /**
     * Parses an App Watcher JSON export (`{ "apps": [ ... ] }`) into [WatchedAppEntry] items.
     * Skips entries with `status == "2"` (deleted in App Watcher) and separates apps that are
     * already installed on the device.
     */
    fun parseExportJson(
        rawJson: String,
        installedPackages: Set<String>,
        existingWatched: List<WatchedAppEntry>,
        nowMillis: Long = System.currentTimeMillis()
    ): AppWatcherImportSummary? = runCatching {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null
        val root = JsonParser.parseString(trimmed)
        val appsArray = when {
            root.isJsonObject -> root.asJsonObject.getAsJsonArray("apps")
            root.isJsonArray -> root.asJsonArray
            else -> null
        } ?: return null

        val installedLower = installedPackages.map { it.lowercase() }.toSet()
        val existingByPkg = existingWatched.associateBy { it.packageName.lowercase() }

        val toUpsert = LinkedHashMap<String, WatchedAppEntry>()
        var updatedSeenCount = 0
        var skippedInstalled = 0
        var alreadyWatched = 0

        for (element in appsArray) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (obj.optString("status") == STATUS_DELETED) continue

            val pkg = obj.optString("packageName")
                .ifBlank { obj.optString("id") }
                .trim()
            if (pkg.isEmpty() || !pkg.contains('.')) continue

            val pkgLower = pkg.lowercase()
            if (pkgLower in installedLower) {
                skippedInstalled++
                continue
            }

            val title = obj.optString("title")
                .ifBlank { obj.optString("name") }
                .trim()
                .ifEmpty { pkg }
            val versionName = obj.optString("versionName")
                .ifBlank { obj.optString("version") }
                .trim()
            val versionCode = obj.optString("versionCode").toLongOrNull()?.coerceAtLeast(0L) ?: 0L

            val existing = existingByPkg[pkgLower]
            if (existing != null) {
                val hasNewerSeen = (versionCode > existing.seenVersionCode) ||
                    (existing.seenVersion.isBlank() && versionName.isNotBlank())
                if (hasNewerSeen) {
                    toUpsert[pkgLower] = existing.copy(
                        appName = existing.appName.ifBlank { title },
                        seenVersion = versionName.ifBlank { existing.seenVersion },
                        seenVersionCode = maxOf(existing.seenVersionCode, versionCode)
                    )
                    updatedSeenCount++
                } else {
                    alreadyWatched++
                }
            } else if (pkgLower !in toUpsert) {
                toUpsert[pkgLower] = WatchedAppEntry(
                    packageName = pkg,
                    appName = title,
                    addedAtMillis = nowMillis,
                    seenVersion = versionName,
                    seenVersionCode = versionCode
                )
            }
        }

        if (toUpsert.isEmpty() && skippedInstalled == 0 && alreadyWatched == 0) {
            return null
        }

        val netNewCount = toUpsert.size - updatedSeenCount
        AppWatcherImportSummary(
            addedEntries = toUpsert.values.toList(),
            updatedSeenCount = updatedSeenCount.coerceAtLeast(0),
            skippedInstalledCount = skippedInstalled,
            alreadyWatchedCount = alreadyWatched + (if (netNewCount < 0) 0 else 0)
        )
    }.getOrNull()

    private fun JsonObject.optString(key: String): String =
        runCatching {
            val el = get(key) ?: return ""
            if (el.isJsonPrimitive) el.asString.orEmpty() else ""
        }.getOrDefault("")
}
