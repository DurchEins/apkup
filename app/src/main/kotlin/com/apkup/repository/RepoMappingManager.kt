package com.apkup.repository

import android.content.Context
import android.util.Log
import com.apkup.data.repomapping.RepoBinding
import com.apkup.prefs.Prefs
import com.apkup.util.ApkSignatureVerifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class KnownRepoEntry(
    val `package`: String = "",
    val type: String = "github",
    val repo: String = "",
    val host: String? = null,
    val assetFilter: String? = null,
    val assetFilterRegex: String? = null
)

object RepoHostValidator {

    /**
     * Trusted public forge hosts allowed for passive `sourceCode` metadata harvesting (`F-04a`).
     */
    val TRUSTED_HARVEST_HOSTS = setOf(
        "github.com",
        "www.github.com",
        "gitlab.com",
        "www.gitlab.com",
        "codeberg.org",
        "www.codeberg.org",
        "code.videolan.org",
        "code.briarproject.org",
        "invent.kde.org",
        "framagit.org"
    )

    /**
     * Known public self-hosted GitLab instances recognized automatically without `/-/` in the URL.
     */
    val KNOWN_PUBLIC_GITLAB_HOSTS = setOf(
        "gitlab.com",
        "www.gitlab.com",
        "invent.kde.org",
        "code.videolan.org",
        "code.briarproject.org",
        "framagit.org",
        "salsa.debian.org",
        "gitlab.gnome.org",
        "gitlab.freedesktop.org",
        "source.puri.sm"
    )

    private val NON_FORGE_DOMAINS = setOf(
        "f-droid.org",
        "www.f-droid.org",
        "apt.izzysoft.de",
        "izzyondroid.org",
        "apkmirror.com",
        "www.apkmirror.com",
        "apkpure.com",
        "www.apkpure.com",
        "play.google.com",
        "uptodown.com"
    )

    private val SAFE_SLUG_SEGMENT_REGEX = Regex("^[A-Za-z0-9_.-]+$")
    private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun isNonForgeStoreDomain(host: String): Boolean {
        val h = host.trim().lowercase(Locale.ROOT)
        return h in NON_FORGE_DOMAINS || NON_FORGE_DOMAINS.any { h.endsWith(".$it") }
    }

    /**
     * Validates a repository host against SSRF / loopback / cloud-metadata / private-LAN rules (`F-04b`, `HIGH-04`).
     *
     * - Loopback (`127.0.0.0/8`, `::1`, `localhost`), `0.0.0.0/8`, link-local / AWS-GCP metadata (`169.254.0.0/16`,
     *   `fe80::/10`), multicast (`224.0.0.0+`), and CGNAT (`100.64.0.0/10`) are ALWAYS rejected.
     * - RFC1918 LAN IPs (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), IPv6 ULA (`fc00::/7`), and `.local`/`.lan`
     *   hosts are ONLY allowed when `allowLanHosts == true` (manual user entry in `LinkRepoDialog` over HTTPS).
     */
    fun isAllowedRepoHost(rawHost: String?, allowLanHosts: Boolean): Boolean {
        val host = rawHost?.trim()?.lowercase(Locale.ROOT)?.removePrefix("[")?.removeSuffix("]").orEmpty()
        if (host.isEmpty() || host.length > 253) return false
        if (host.any { it.isWhitespace() || it in "/\\?#@%" }) return false

        if (host == "localhost" || host.endsWith(".localhost") || host == "ip6-localhost") {
            return false
        }

        // IPv6 literal checks (must contain at least two colons and only valid IPv6 hex/colon/dot chars)
        if (host.contains(':')) {
            if (host.count { it == ':' } < 2 || !host.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }) {
                return false
            }
            if (host == "::1" || host == "::" || host.startsWith("0:0:0:0:0:0:0:1") || host.startsWith("0:0:0:0:0:0:0:0")) {
                return false
            }
            if (host.startsWith("fe8") || host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")) {
                return false // IPv6 link-local
            }
            if (host.startsWith("ff")) return false // IPv6 multicast
            if (host.startsWith("fc") || host.startsWith("fd")) {
                return allowLanHosts // IPv6 Unique Local Address (LAN)
            }
            if (host.startsWith("::ffff:")) {
                val mappedV4 = host.removePrefix("::ffff:")
                return isAllowedRepoHost(mappedV4, allowLanHosts)
            }
            return true
        }

        // IPv4 literal checks
        val v4Match = IPV4_REGEX.matchEntire(host)
        if (v4Match != null) {
            val octets = v4Match.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            val a = octets[0]
            val b = octets[1]
            // Always blocked: 0.0.0.0/8, 127.0.0.0/8 (loopback), 169.254.0.0/16 (link-local / cloud metadata),
            // 100.64.0.0/10 (CGNAT), 224.0.0.0+ (multicast/reserved)
            if (a == 0 || a == 127 || a >= 224) return false
            if (a == 169 && b == 254) return false
            if (a == 100 && b in 64..127) return false

            // RFC1918 private LAN ranges
            val isRfc1918 = a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
            if (isRfc1918) {
                return allowLanHosts
            }
            return true
        }

        // Reject hex/octal integer IP obfuscations (e.g. 0x7f000001 or 2130706433)
        if (host.all { it.isDigit() } || host.startsWith("0x")) {
            return false
        }

        // Hostname label validation
        val labels = host.split('.')
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }) {
            return false
        }
        if (!labels.all { label -> label.all { ch -> ch in 'a'..'z' || ch in '0'..'9' || ch == '-' } }) {
            return false
        }

        val tld = labels.last()
        val isLocalDomain = labels.size < 2 || tld in setOf("local", "lan", "internal", "home", "arpa", "corp", "intranet")
        if (isLocalDomain) {
            return allowLanHosts
        }

        return true
    }

    fun isValidRepoSlugSegments(segments: List<String>): Boolean {
        if (segments.size < 2) return false
        return segments.all { seg ->
            seg.isNotBlank() && seg != "." && seg != ".." && SAFE_SLUG_SEGMENT_REGEX.matches(seg)
        }
    }
}

class RepoMappingManager(
    private val context: Context?,
    private val prefs: Prefs,
    private val gson: Gson
) {
    private val knownReposMap = ConcurrentHashMap<String, RepoBinding>()
    private val harvestedReposMap = ConcurrentHashMap<String, RepoBinding>()
    private val incompatibleRepoSignaturesMap = ConcurrentHashMap<String, String>()
    private var isKnownReposLoaded = false

    init {
        loadKnownRepos()
        loadIncompatibleSignatures()
    }

    private fun loadKnownRepos() {
        val ctx = context ?: return
        if (isKnownReposLoaded) return
        runCatching {
            ctx.assets.open("repos/known_repos.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    val type = object : TypeToken<List<KnownRepoEntry>>() {}.type
                    val list: List<KnownRepoEntry>? = gson.fromJson(reader, type)
                    list?.forEach { entry ->
                        if (entry.`package`.isNotBlank() && entry.repo.isNotBlank()) {
                            knownReposMap[entry.`package`] = RepoBinding(
                                type = entry.type.lowercase(Locale.ROOT),
                                repo = entry.repo.trim(),
                                host = entry.host?.trim()?.takeIf { it.isNotBlank() },
                                assetFilter = entry.assetFilter?.trim()?.takeIf { it.isNotBlank() },
                                assetFilterRegex = entry.assetFilterRegex?.trim()?.takeIf { it.isNotEmpty() }
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

    private fun loadIncompatibleSignatures() {
        runCatching {
            incompatibleRepoSignaturesMap.putAll(prefs.readIncompatibleRepoSignaturesSafe())
        }
    }

    internal fun putKnownRepoForTest(packageName: String, binding: RepoBinding) {
        knownReposMap[packageName] = binding
    }

    fun hasCustomBinding(packageName: String): Boolean =
        getCustomBindings().containsKey(packageName)

    fun getCustomBinding(packageName: String): RepoBinding? =
        getCustomBindings()[packageName]

    fun getMappedPackages(): Set<String> =
        getCustomBindings().keys + knownReposMap.keys + harvestedReposMap.keys

    fun getBinding(packageName: String): RepoBinding? {
        // 1. User manual binding has highest priority
        val custom = getCustomBindings()
        custom[packageName]?.let { return it }

        // 2. Curated built-in known repos
        knownReposMap[packageName]?.let { return it }

        // 3. Passively harvested repo links (restricted to trusted public forges, F-04a)
        return harvestedReposMap[packageName]
    }

    fun getCompatibleBinding(packageName: String, installedSha256: String?): RepoBinding? {
        val binding = getBinding(packageName) ?: return null
        if (isSignatureIncompatible(packageName, binding, installedSha256)) {
            runCatching {
                Log.i(
                    "RepoMappingManager",
                    "Skipping ${binding.type} repo ${binding.repo} for $packageName: known signing certificate mismatch with installed app"
                )
            }
            return null
        }
        return binding
    }

    fun getCustomBindings(): Map<String, RepoBinding> = prefs.readCustomRepoBindingsSafe()

    fun setCustomBinding(packageName: String, binding: RepoBinding?) {
        if (binding == null) {
            prefs.removeCustomRepoBinding(packageName)
        } else {
            prefs.putCustomRepoBinding(packageName, binding)
        }
        clearSignatureIncompatible(packageName)
    }

    fun setCustomBindings(newBindings: Map<String, RepoBinding>) {
        if (newBindings.isEmpty()) return
        val sanitized = newBindings.filter { (pkg, binding) ->
            pkg.isNotBlank() &&
                binding.repo.isNotBlank() &&
                RepoHostValidator.isAllowedRepoHost(binding.resolvedHost, allowLanHosts = false)
        }
        if (sanitized.isEmpty()) return
        val current = getCustomBindings().toMutableMap()
        current.putAll(sanitized)
        prefs.writeCustomRepoBindings(current)
        sanitized.keys.forEach { clearSignatureIncompatible(it) }
    }

    /**
     * Passively harvests repository URLs from F-Droid/IzzyOnDroid metadata ONLY if the URL uses HTTPS
     * and points to a trusted public forge (`F-04a`).
     */
    fun registerHarvestedRepo(packageName: String, sourceUrl: String) {
        if (packageName.isBlank() || sourceUrl.isBlank()) return
        val trimmed = sourceUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return
        val uri = runCatching { URI.create(trimmed) }.getOrNull() ?: return
        val host = uri.host?.lowercase(Locale.ROOT) ?: return
        if (host !in RepoHostValidator.TRUSTED_HARVEST_HOSTS) return

        val parsed = parseRepoUrl(trimmed, allowLanHosts = false) ?: return
        harvestedReposMap[packageName] = parsed
    }

    fun removeHarvestedRepo(packageName: String) {
        if (packageName.isBlank()) return
        harvestedReposMap.remove(packageName)
    }

    @Synchronized
    fun markSignatureIncompatible(
        packageName: String,
        binding: RepoBinding,
        installedSha256: String?
    ) {
        val cleanSha = ApkSignatureVerifier.normalizeHash(installedSha256)
        if (packageName.isBlank() || cleanSha.isEmpty() || cleanSha == ApkSignatureVerifier.EMPTY_SHA256) {
            return
        }
        val harvested = harvestedReposMap[packageName]
        if (harvested != null && harvested.canonicalKey() == binding.canonicalKey()) {
            harvestedReposMap.remove(packageName)
        }
        val value = "${binding.canonicalKey()}|$cleanSha"
        if (incompatibleRepoSignaturesMap[packageName] != value) {
            incompatibleRepoSignaturesMap[packageName] = value
            prefs.writeIncompatibleRepoSignatures(incompatibleRepoSignaturesMap.toMap())
        }
    }

    @Synchronized
    fun clearSignatureIncompatible(packageName: String) {
        if (incompatibleRepoSignaturesMap.remove(packageName) != null) {
            prefs.writeIncompatibleRepoSignatures(incompatibleRepoSignaturesMap.toMap())
        }
    }

    fun isSignatureIncompatible(
        packageName: String,
        binding: RepoBinding,
        installedSha256: String?
    ): Boolean {
        val cleanSha = ApkSignatureVerifier.normalizeHash(installedSha256)
        if (packageName.isBlank() || cleanSha.isEmpty() || cleanSha == ApkSignatureVerifier.EMPTY_SHA256) {
            return false
        }
        val expected = "${binding.canonicalKey()}|$cleanSha"
        return incompatibleRepoSignaturesMap[packageName] == expected
    }

    /**
     * Parses a GitHub/GitLab/Codeberg repository URL or `owner/repo` slug (`F-03`, `F-04b`).
     *
     * @param allowLanHosts False by default (blocks private/LAN IPs on imports/harvesting);
     *                      set to true ONLY when the user manually enters a repo URL in `LinkRepoDialog`.
     */
    fun parseRepoUrl(
        input: String,
        allowLanHosts: Boolean = false,
        hintSource: String? = null
    ): RepoBinding? = Companion.parseRepoUrlStatic(input, allowLanHosts, hintSource)

    companion object {
        fun parseRepoUrlStatic(
            input: String,
            allowLanHosts: Boolean = false,
            hintSource: String? = null
        ): RepoBinding? {
            val raw = input.trim()
            if (raw.isBlank()) return null

            if (raw.contains("://")) {
                if (!raw.startsWith("https://", ignoreCase = true)) {
                    // Reject cleartext http:// or non-HTTPS schemes
                    return null
                }
                val uri = runCatching { URI.create(raw) }.getOrNull() ?: return null
                if (uri.userInfo != null || uri.port != -1) {
                    // Disallow embedded credentials; allow custom port only when manual LAN host is explicitly opted in
                    if (uri.userInfo != null || !allowLanHosts) return null
                }
                val rawHost = uri.host?.lowercase(Locale.ROOT).orEmpty()
                if (!RepoHostValidator.isAllowedRepoHost(rawHost, allowLanHosts)) {
                    return null
                }
                if (RepoHostValidator.isNonForgeStoreDomain(rawHost)) {
                    return null
                }

                val path = uri.path?.trim('/') ?: return null
                val segments = path.split("/").filter { it.isNotBlank() }
                if (segments.size < 2) return null

                val normalizedHint = hintSource?.trim()?.lowercase(Locale.ROOT).orEmpty()

                return when {
                    rawHost == "github.com" || rawHost == "www.github.com" -> {
                        val owner = segments[0]
                        val repo = segments[1].removeSuffix(".git")
                        if (!RepoHostValidator.isValidRepoSlugSegments(listOf(owner, repo))) return null
                        RepoBinding(type = "github", repo = "$owner/$repo")
                    }

                    rawHost == "codeberg.org" ||
                        rawHost == "www.codeberg.org" ||
                        rawHost.endsWith(".codeberg.org") ||
                        rawHost.contains("forgejo") ||
                        rawHost.contains("gitea") ||
                        normalizedHint.contains("codeberg") ||
                        normalizedHint.contains("forgejo") ||
                        normalizedHint.contains("gitea") -> {
                        val owner = segments[0]
                        val repo = segments[1].removeSuffix(".git")
                        if (!RepoHostValidator.isValidRepoSlugSegments(listOf(owner, repo))) return null
                        val normalizedHost = if (rawHost == "www.codeberg.org") "codeberg.org" else rawHost
                        RepoBinding(
                            type = "codeberg",
                            repo = "$owner/$repo",
                            host = normalizedHost
                        )
                    }

                    rawHost in RepoHostValidator.KNOWN_PUBLIC_GITLAB_HOSTS ||
                        rawHost.startsWith("gitlab.") ||
                        rawHost.contains(".gitlab.") ||
                        segments.contains("-") ||
                        normalizedHint.contains("gitlab") ||
                        allowLanHosts -> {
                        val dashIndex = segments.indexOf("-")
                        val effectiveSegments = if (dashIndex != -1) {
                            segments.subList(0, dashIndex)
                        } else {
                            val releaseIndex = segments.indexOfFirst { it.equals("releases", ignoreCase = true) }
                            if (releaseIndex > 1) segments.subList(0, releaseIndex) else segments
                        }
                        if (effectiveSegments.size < 2) return null
                        val cleanedSegments = effectiveSegments.mapIndexed { idx, seg ->
                            if (idx == effectiveSegments.lastIndex) seg.removeSuffix(".git") else seg
                        }
                        if (!RepoHostValidator.isValidRepoSlugSegments(cleanedSegments)) return null
                        val cleanRepo = cleanedSegments.joinToString("/")
                        val normalizedHost = if (rawHost == "www.gitlab.com") "gitlab.com" else rawHost
                        RepoBinding(
                            type = "gitlab",
                            repo = cleanRepo,
                            host = normalizedHost
                        )
                    }

                    else -> {
                        // Do NOT hijack unknown third-party domains to GitHub (F-03 / HIGH-03)!
                        null
                    }
                }
            }

            // Reject strings with whitespace or URL-like punctuation when parsing `owner/repo` slug
            if (raw.any { it.isWhitespace() || it in "?#@:" }) return null
            val normalizedHint = hintSource?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val resolvedType = when {
                normalizedHint.contains("codeberg") || normalizedHint.contains("forgejo") || normalizedHint.contains("gitea") -> "codeberg"
                normalizedHint.contains("gitlab") -> "gitlab"
                else -> "github"
            }
            val parts = raw.split("/").filter { it.isNotBlank() }
            val validArity = if (resolvedType == "gitlab") parts.size >= 2 else parts.size == 2
            if (validArity) {
                val cleanedParts = parts.mapIndexed { idx, seg ->
                    if (idx == parts.lastIndex) seg.removeSuffix(".git") else seg
                }
                if (!RepoHostValidator.isValidRepoSlugSegments(cleanedParts)) return null
                return RepoBinding(type = resolvedType, repo = cleanedParts.joinToString("/"))
            }

            return null
        }
    }
}
