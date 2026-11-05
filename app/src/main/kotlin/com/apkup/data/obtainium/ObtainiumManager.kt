package com.apkup.data.obtainium

import com.apkup.data.repomapping.RepoBinding
import com.apkup.repository.RepoMappingManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.net.URI

object ObtainiumManager {

    private val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }

    /**
     * Determines whether the given JSON string represents an Obtainium export
     * (either a JSON array of app entries or an object with an "apps" array).
     */
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
                    // Check that it's not an ApkUp backup (schemaVersion)
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

    /**
     * Extracts the list of [ObtainiumAppEntry] from either a JSON array or a wrapped container.
     */
    fun parseObtainiumApps(rawJson: String): List<ObtainiumAppEntry> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()

        return runCatching {
            val element = JsonParser.parseString(trimmed)
            when {
                element.isJsonArray -> {
                    val type = object : TypeToken<List<ObtainiumAppEntry>>() {}.type
                    gson.fromJson<List<ObtainiumAppEntry>>(element, type) ?: emptyList()
                }
                element.isJsonObject -> {
                    val container = gson.fromJson(element, ObtainiumExportContainer::class.java)
                    container?.apps ?: emptyList()
                }
                else -> emptyList()
            }
        }.getOrElse { emptyList() }
    }

    /**
     * Converts an Obtainium repository or release URL into an ApkUp [RepoBinding].
     * Supports GitHub and GitLab URLs including releases, tags, subgroups, and trailing slashes.
     */
    fun convertUrlToBinding(url: String): RepoBinding? {
        val raw = url.trim()
        if (raw.isBlank()) return null

        // If URL scheme is present
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            val uri = runCatching { URI.create(raw) }.getOrNull() ?: return null
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path?.trim('/') ?: return null
            val segments = path.split("/").filter { it.isNotBlank() }
            if (segments.size < 2) return null

            when {
                host.contains("github.com") -> {
                    val owner = segments[0]
                    val repo = segments[1].removeSuffix(".git")
                    return RepoBinding(
                        type = "github",
                        repo = "$owner/$repo"
                    )
                }
                host.contains("gitlab") || segments.contains("-") || host.contains("kde.org") -> {
                    // In GitLab, ignore everything after "/-/" or "releases"
                    val dashIndex = segments.indexOf("-")
                    val effectiveSegments = if (dashIndex != -1) {
                        segments.subList(0, dashIndex)
                    } else {
                        val releaseIndex = segments.indexOfFirst { it.equals("releases", ignoreCase = true) }
                        if (releaseIndex > 1) segments.subList(0, releaseIndex) else segments
                    }
                    if (effectiveSegments.size < 2) return null
                    val cleanRepo = effectiveSegments.joinToString("/").removeSuffix(".git")
                    return RepoBinding(
                        type = "gitlab",
                        repo = cleanRepo,
                        host = if (host == "gitlab.com") "gitlab.com" else host
                    )
                }
                else -> {
                    // Fallback to GitHub-style owner/repo
                    val owner = segments[0]
                    val repo = segments[1].removeSuffix(".git")
                    return RepoBinding(
                        type = "github",
                        repo = "$owner/$repo"
                    )
                }
            }
        }

        // Slug format: "owner/repo" or "group/subgroup/repo"
        val parts = raw.split("/").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val owner = parts[0]
            val repo = parts.drop(1).joinToString("/").removeSuffix(".git")
            return RepoBinding(type = "github", repo = "$owner/$repo")
        }

        return null
    }

    /**
     * Imports an Obtainium JSON payload and updates custom repository bindings
     * in the given [RepoMappingManager].
     */
    fun importObtainium(
        rawJson: String,
        repoMappingManager: RepoMappingManager
    ): ObtainiumImportResult {
        val apps = parseObtainiumApps(rawJson)
        if (apps.isEmpty()) {
            return ObtainiumImportResult()
        }

        val newBindings = mutableMapOf<String, RepoBinding>()
        val importedPackages = mutableListOf<String>()
        var skipped = 0

        for (app in apps) {
            val packageName = app.id.trim()
            if (packageName.isBlank()) {
                skipped++
                continue
            }

            val binding = convertUrlToBinding(app.url)
            if (binding != null) {
                newBindings[packageName] = binding
                importedPackages.add(packageName)
            } else {
                skipped++
            }
        }

        if (newBindings.isNotEmpty()) {
            repoMappingManager.setCustomBindings(newBindings)
        }

        return ObtainiumImportResult(
            totalEntries = apps.size,
            importedBindingsCount = newBindings.size,
            skippedCount = skipped,
            importedPackages = importedPackages
        )
    }

    /**
     * Exports all mapped bindings to a valid Obtainium JSON string.
     */
    fun exportObtainium(
        bindings: Map<String, RepoBinding>,
        appNamesByPackage: Map<String, String> = emptyMap()
    ): String {
        val obtainiumList = bindings.map { (packageName, binding) ->
            val owner = binding.repo.substringBefore("/").trim()
            val repoName = binding.repo.substringAfterLast("/").trim()
            val displayName = appNamesByPackage[packageName]?.takeIf { it.isNotBlank() } ?: repoName

            ObtainiumAppEntry(
                id = packageName,
                url = binding.webUrl,
                author = owner,
                name = displayName,
                overrideSource = if (binding.isGitLab) "GitLab" else "GitHub"
            )
        }

        return gson.toJson(obtainiumList)
    }
}
