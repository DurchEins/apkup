package com.apkup.repository

import android.content.Context
import android.util.Log
import com.apkup.data.repomapping.RepoBinding
import com.apkup.prefs.Prefs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class KnownRepoEntry(
    val `package`: String = "",
    val type: String = "github",
    val repo: String = "",
    val host: String? = null
)

class RepoMappingManager(
    private val context: Context,
    private val prefs: Prefs,
    private val gson: Gson
) {
    private val knownReposMap = ConcurrentHashMap<String, RepoBinding>()
    private val harvestedReposMap = ConcurrentHashMap<String, RepoBinding>()
    private var isKnownReposLoaded = false

    init {
        loadKnownRepos()
    }

    private fun loadKnownRepos() {
        if (isKnownReposLoaded) return
        runCatching {
            context.assets.open("repos/known_repos.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val type = object : TypeToken<List<KnownRepoEntry>>() {}.type
                    val list: List<KnownRepoEntry>? = gson.fromJson(reader, type)
                    list?.forEach { entry ->
                        if (entry.`package`.isNotBlank() && entry.repo.isNotBlank()) {
                            knownReposMap[entry.`package`] = RepoBinding(
                                type = entry.type.lowercase(),
                                repo = entry.repo.trim(),
                                host = entry.host?.trim()
                            )
                        }
                    }
                    isKnownReposLoaded = true
                    Log.i("RepoMappingManager", "Loaded ${knownReposMap.size} known repo mappings from assets.")
                }
            }
        }.onFailure { error ->
            Log.e("RepoMappingManager", "Failed to load known repos from assets", error)
        }
    }

    fun getBinding(packageName: String): RepoBinding? {
        // 1. User manual binding has highest priority
        val custom = getCustomBindings()
        custom[packageName]?.let { return it }

        // 2. Curated built-in known repos
        knownReposMap[packageName]?.let { return it }

        // 3. Passively harvested repo links (e.g. from IzzyOnDroid)
        return harvestedReposMap[packageName]
    }

    fun getCustomBindings(): Map<String, RepoBinding> = runCatching {
        prefs.customRepoBindings.get()
    }.getOrDefault(emptyMap())

    fun setCustomBinding(packageName: String, binding: RepoBinding?) {
        val current = getCustomBindings().toMutableMap()
        if (binding == null) {
            current.remove(packageName)
        } else {
            current[packageName] = binding
        }
        prefs.customRepoBindings.put(current)
    }

    fun registerHarvestedRepo(packageName: String, sourceUrl: String) {
        if (packageName.isBlank() || sourceUrl.isBlank()) return
        val parsed = parseRepoUrl(sourceUrl) ?: return
        harvestedReposMap[packageName] = parsed
    }

    fun parseRepoUrl(input: String): RepoBinding? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        // Check if URL
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            val uri = runCatching { URI.create(raw) }.getOrNull() ?: return null
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path?.trim('/') ?: return null
            val segments = path.split("/").filter { it.isNotBlank() }
            if (segments.size < 2) return null
            val owner = segments[0]
            val repo = segments.drop(1).joinToString("/").removeSuffix(".git")
            val fullRepo = "$owner/$repo"

            return when {
                host.contains("github.com") -> RepoBinding(type = "github", repo = fullRepo)
                host.contains("gitlab.com") -> RepoBinding(type = "gitlab", repo = fullRepo, host = "gitlab.com")
                host.contains("gitlab") -> RepoBinding(type = "gitlab", repo = fullRepo, host = host)
                else -> RepoBinding(type = "github", repo = fullRepo)
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
}
