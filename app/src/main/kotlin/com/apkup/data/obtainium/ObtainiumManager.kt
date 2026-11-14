package com.apkup.data.obtainium

import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.CustomFDroidRepo
import com.apkup.prefs.Prefs
import com.apkup.repository.RepoMappingManager
import com.apkup.util.ApkIntegrityVerifier
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.util.Locale

object ObtainiumManager {

    private val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }

    fun isObtainiumJson(rawJson: String): Boolean {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return false

        return runCatching {
            val element: JsonElement = JsonParser.parseString(trimmed)
            when {
                element.isJsonArray -> {
                    val array = element.asJsonArray
                    if (array.size() == 0) return true
                    val first = array[0]
                    if (first.isJsonObject) {
                        val obj = first.asJsonObject
                        obj.has("url") || obj.has("overrideSource") || obj.has("id")
                    } else false
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    if (obj.has("schemaVersion")) return false
                    if (obj.has("apps") && obj.get("apps").isJsonArray) {
                        val array = obj.getAsJsonArray("apps")
                        if (array.size() == 0) return true
                        val first = array[0]
                        if (first.isJsonObject) {
                            val firstObj = first.asJsonObject
                            firstObj.has("url") || firstObj.has("id")
                        } else false
                    } else false
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    fun parseObtainiumApps(rawJson: String): List<ObtainiumAppEntry> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        return runCatching {
            val element = JsonParser.parseString(trimmed)
            when {
                element.isJsonArray -> {
                    val type = object : TypeToken<List<ObtainiumAppEntry>>() {}.type
                    gson.fromJson<List<ObtainiumAppEntry>>(element, type).orEmpty()
                }
                element.isJsonObject -> {
                    val container = gson.fromJson(element, ObtainiumExportContainer::class.java)
                    container?.apps.orEmpty()
                }
                else -> emptyList()
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Converts an Obtainium repository or release URL into an ApkUp [RepoBinding] (`F-03`, `F-04b`, `HIGH-03`).
     * Supports GitHub, GitLab, and Codeberg / Forgejo URLs over HTTPS (or `owner/repo` slugs) and
     * NEVER hijacks unrelated domains (such as `f-droid.org`, `apkpure.com`, or private LAN hosts) to GitHub.
     */
    fun convertUrlToBinding(url: String, overrideSource: String? = null): RepoBinding? {
        val raw = url.trim()
        if (raw.isBlank()) return null
        val lower = raw.lowercase(Locale.ROOT)
        val lowerSource = overrideSource?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (lower.startsWith("fdroidrepo://") ||
            lowerSource.contains("fdroid") ||
            lower.contains("f-droid.org") ||
            lower.contains("izzysoft.de")
        ) {
            return null
        }
        return RepoMappingManager.parseRepoUrlStatic(raw, allowLanHosts = false, hintSource = overrideSource)
    }

    /**
     * Extracts a custom F-Droid repository configuration from an Obtainium app entry if applicable.
     */
    fun parseFDroidRepoFromEntry(entry: ObtainiumAppEntry): CustomFDroidRepo? {
        val rawUrl = entry.url.trim()
        if (rawUrl.isEmpty()) return null
        val lowerUrl = rawUrl.lowercase(Locale.ROOT)
        val lowerSource = entry.overrideSource?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val isExplicitFDroidRepoSource = lowerSource.contains("fdroid") &&
            (lowerSource.contains("thirdparty") || lowerSource.contains("repo"))
        val isFDroidScheme = lowerUrl.startsWith("fdroidrepo://")
        val pathWithoutQuery = rawUrl.substringBefore("?").trim().trimEnd('/')
        val isRepoPath = pathWithoutQuery.endsWith("/repo", ignoreCase = true) ||
            pathWithoutQuery.endsWith("/fdroid/repo", ignoreCase = true)
        val isFDroidPackagePage = lowerUrl.contains("/packages/") || lowerUrl.contains("/index/apk/")
        if (isFDroidPackagePage && !isFDroidScheme && !isExplicitFDroidRepoSource) {
            return null
        }
        if (!isFDroidScheme && !isExplicitFDroidRepoSource && !(lowerUrl.contains("fdroid") && isRepoPath)) {
            return null
        }

        val httpUrl = if (isFDroidScheme) {
            "https://" + pathWithoutQuery.substringAfter("://")
        } else {
            pathWithoutQuery
        }
        if (!httpUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val parsedHost = runCatching { URI.create(httpUrl).host }.getOrNull()
        if (!com.apkup.repository.RepoHostValidator.isAllowedRepoHost(parsedHost, allowLanHosts = false)) {
            return null
        }

        val query = rawUrl.substringAfter("?", missingDelimiterValue = "")
        val fingerprint = Regex("(?:^|&)fingerprint=([0-9a-fA-F]{64})(?:&|$)")
            .find(query)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        val derivedName = entry.name.trim().ifBlank {
            runCatching { URI(httpUrl).host?.removePrefix("www.") ?: httpUrl }.getOrDefault(httpUrl)
        }
        return CustomFDroidRepo(
            name = derivedName,
            baseUrl = httpUrl.trimEnd('/'),
            fingerprint = fingerprint,
            enabled = true
        )
    }

    /**
     * Builds an interactive preview for the Obtainium import sheet, separating
     * installed app mappings, uninstalled watchlist candidates, and custom F-Droid repos.
     */
    fun buildImportPreview(
        rawJson: String,
        installedPackageNames: Set<String>
    ): ObtainiumImportPreview {
        val entries = parseObtainiumApps(rawJson)
        val matchedBindings = linkedMapOf<String, RepoBinding>()
        val matchedNames = linkedMapOf<String, String>()
        val watchedCandidates = mutableListOf<ObtainiumWatchedCandidate>()
        val seenWatchedPackages = hashSetOf<String>()
        val fDroidRepos = linkedMapOf<String, CustomFDroidRepo>()
        var skipped = 0

        for (entry in entries) {
            val repo = parseFDroidRepoFromEntry(entry)
            if (repo != null) {
                fDroidRepos.putIfAbsent(repo.baseUrl.lowercase(Locale.ROOT), repo)
                continue
            }

            val pkg = entry.id.trim()
            if (!ApkIntegrityVerifier.isValidPackageName(pkg)) {
                skipped++
                continue
            }

            val baseBinding = convertUrlToBinding(entry.url, entry.overrideSource)
            if (baseBinding == null) {
                skipped++
                continue
            }
            val filterRegex = extractApkFilterRegex(entry.additionalSettings)
            val binding = if (filterRegex != null) baseBinding.copy(assetFilterRegex = filterRegex) else baseBinding

            if (pkg in installedPackageNames) {
                matchedBindings[pkg] = binding
                if (entry.name.isNotBlank()) {
                    matchedNames[pkg] = entry.name.trim()
                }
            } else if (seenWatchedPackages.add(pkg)) {
                watchedCandidates.add(
                    ObtainiumWatchedCandidate(
                        packageName = pkg,
                        appName = entry.name.trim().ifBlank { pkg },
                        repoUrl = entry.url.trim(),
                        binding = binding
                    )
                )
            }
        }

        return ObtainiumImportPreview(
            totalEntries = entries.size,
            matchedInstalledBindings = matchedBindings,
            matchedInstalledNames = matchedNames,
            uninstalledCandidateApps = watchedCandidates,
            detectedFDroidRepos = fDroidRepos.values.toList(),
            skippedEntriesCount = skipped
        )
    }

    private fun extractApkFilterRegex(additionalSettings: String?): String? {
        if (additionalSettings.isNullOrBlank()) return null
        return runCatching {
            val obj = JsonParser.parseString(additionalSettings).asJsonObject
            val raw = obj.get("apkFilterRegEx")?.asString?.trim()?.takeIf { it.isNotEmpty() } ?: return@runCatching null
            if (ReleaseAssetFilter.isValidRegex(raw)) raw else null
        }.getOrNull()
    }

    internal enum class SpecialStoreType {
        FDROID_OFFICIAL,
        IZZYONDROID,
        NONE
    }

    internal fun detectSpecialStore(url: String?, overrideSource: String?): SpecialStoreType {
        val host = runCatching { URI.create(url.orEmpty().trim()).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        val src = overrideSource.orEmpty().lowercase(Locale.ROOT)
        return when {
            host == "apt.izzysoft.de" || host.endsWith(".izzysoft.de") || host.contains("izzyondroid") ->
                SpecialStoreType.IZZYONDROID
            host == "f-droid.org" || host == "www.f-droid.org" || src == "fdroid" ->
                SpecialStoreType.FDROID_OFFICIAL
            else -> SpecialStoreType.NONE
        }
    }

    /**
     * Imports an Obtainium JSON payload (`F-03`):
     * - Maps supported GitHub / GitLab repositories into [RepoMappingManager].
     * - Automatically activates F-Droid / IzzyOnDroid in [prefs] when F-Droid / Izzy URLs are present.
     * - Skips unsupported domains cleanly (instead of hijacking them to GitHub) and reports them for UI warning.
     */
    @Suppress("SENSELESS_COMPARISON")
    fun importObtainium(
        rawJson: String,
        repoMappingManager: RepoMappingManager? = null,
        prefs: Prefs? = null
    ): ObtainiumImportResult {
        val apps = parseObtainiumApps(rawJson)
        if (apps.isEmpty()) {
            return ObtainiumImportResult()
        }

        val newBindings = mutableMapOf<String, RepoBinding>()
        val importedPackages = mutableListOf<String>()
        val unsupportedDomains = linkedSetOf<String>()
        var skipped = 0
        var fdroidCount = 0
        var izzyCount = 0

        for (app in apps) {
            val packageName = (app.id as String?).orEmpty().trim()
            val rawUrl = (app.url as String?).orEmpty().trim()
            if (!ApkIntegrityVerifier.isValidPackageName(packageName)) {
                skipped++
                continue
            }

            when (detectSpecialStore(rawUrl, app.overrideSource)) {
                SpecialStoreType.FDROID_OFFICIAL -> {
                    fdroidCount++
                    importedPackages.add(packageName)
                    continue
                }
                SpecialStoreType.IZZYONDROID -> {
                    izzyCount++
                    importedPackages.add(packageName)
                    continue
                }
                SpecialStoreType.NONE -> Unit
            }

            val binding = convertUrlToBinding(rawUrl, app.overrideSource)
            if (binding != null) {
                val filterRegex = extractApkFilterRegex(app.additionalSettings)
                newBindings[packageName] = if (filterRegex != null) binding.copy(assetFilterRegex = filterRegex) else binding
                importedPackages.add(packageName)
            } else {
                skipped++
                val host = runCatching { URI.create(rawUrl).host?.lowercase(Locale.ROOT) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                if (host != null) {
                    unsupportedDomains.add(host)
                }
            }
        }

        if (newBindings.isNotEmpty()) {
            if (repoMappingManager != null) {
                repoMappingManager.setCustomBindings(newBindings)
            } else if (prefs != null) {
                val current = prefs.readCustomRepoBindingsSafe().toMutableMap()
                current.putAll(newBindings)
                prefs.writeCustomRepoBindings(current)
            }
        }

        if (prefs != null && (fdroidCount > 0 || izzyCount > 0)) {
            prefs.useIzzyOnDroid.put(true)
            if (fdroidCount > 0) {
                prefs.setFDroidRepoEnabled(FDroidRepoConfig.ID_FDROID_OFFICIAL, true)
            }
            if (izzyCount > 0) {
                prefs.setFDroidRepoEnabled(FDroidRepoConfig.ID_IZZYONDROID, true)
            }
        }

        return ObtainiumImportResult(
            totalEntries = apps.size,
            importedBindingsCount = newBindings.size + fdroidCount + izzyCount,
            skippedCount = skipped,
            importedPackages = importedPackages,
            detectedFDroidCount = fdroidCount,
            detectedIzzyCount = izzyCount,
            unsupportedDomains = unsupportedDomains.toList()
        )
    }

    fun exportObtainium(
        bindings: Map<String, RepoBinding>,
        appNamesByPackage: Map<String, String> = emptyMap()
    ): String {
        val obtainiumList = bindings.map { (packageName, binding) ->
            val owner = binding.repo.substringBefore("/").trim()
            val repoName = binding.repo.substringAfterLast("/").trim()
            val displayName = appNamesByPackage[packageName]?.takeIf { it.isNotBlank() } ?: repoName

            val additionalSettingsJson = binding.assetFilterRegex
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { regex ->
                    JsonObject().apply {
                        addProperty("apkFilterRegEx", regex)
                    }.toString()
                }

            ObtainiumAppEntry(
                id = packageName,
                url = binding.webUrl,
                author = owner,
                name = displayName,
                overrideSource = when {
                    binding.isCodeberg -> "Codeberg"
                    binding.isGitLab -> "GitLab"
                    else -> "GitHub"
                },
                additionalSettings = additionalSettingsJson
            )
        }

        return gson.toJson(obtainiumList)
    }
}
