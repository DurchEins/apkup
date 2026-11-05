package com.apkup.data.fdroid

import com.apkup.data.izzy.IzzyAppInfo
import com.apkup.data.izzy.IzzyIndexResponse
import com.apkup.data.izzy.IzzyPackageInfo
import com.apkup.data.izzy.IzzyRepoInfo
import com.google.gson.JsonElement
import java.util.Locale

/**
 * Models representing the F-Droid Index V2 format (entry.json & index-v2.json)
 * according to the official F-Droid V2 Repository Specification.
 */
data class FDroidV2Entry(
    val timestamp: Long = 0L,
    val version: Long = 0L,
    val maxDownloadThreads: Int? = null,
    val index: FDroidV2IndexFileRef? = null,
    val diffs: Map<String, FDroidV2IndexFileRef>? = null
)

data class FDroidV2IndexFileRef(
    val name: String = "",
    val sha256: String = "",
    val size: Long = 0L,
    val numPackages: Int = 0
)

data class FDroidV2Index(
    val repo: FDroidV2RepoMetadata? = null,
    val packages: Map<String, FDroidV2PackageEntry> = emptyMap()
)

data class FDroidV2RepoMetadata(
    val timestamp: Long = 0L,
    val version: Long = 0L,
    val name: JsonElement? = null,
    val description: JsonElement? = null,
    val address: String? = null,
    val icon: JsonElement? = null
)

data class FDroidV2PackageEntry(
    val metadata: FDroidV2PackageMetadata? = null,
    val versions: Map<String, FDroidV2VersionEntry> = emptyMap()
)

data class FDroidV2PackageMetadata(
    val added: Long = 0L,
    val lastUpdated: Long = 0L,
    val name: JsonElement? = null,
    val summary: JsonElement? = null,
    val description: JsonElement? = null,
    val categories: List<String> = emptyList(),
    val sourceCode: String? = null,
    val issueTracker: String? = null,
    val webSite: String? = null,
    val icon: JsonElement? = null
)

data class FDroidV2VersionEntry(
    val added: Long = 0L,
    val file: FDroidV2FileRef? = null,
    val manifest: FDroidV2Manifest? = null
)

data class FDroidV2FileRef(
    val name: String = "",
    val sha256: String = "",
    val size: Long = 0L
)

data class FDroidV2Manifest(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val usesSdk: FDroidV2UsesSdk? = null,
    val signer: FDroidV2Signer? = null,
    val nativecode: List<String>? = null
)

data class FDroidV2UsesSdk(
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0
)

data class FDroidV2Signer(
    val sha256: List<String> = emptyList()
)

/**
 * Extracts human-readable localized text from a F-Droid V2 [JsonElement].
 * F-Droid V2 localized fields can be a map like `{"en-US": "...", "de-DE": "..."}`
 * or a plain JSON string.
 */
fun JsonElement?.getPreferredText(preferredLocale: Locale = Locale.getDefault()): String {
    if (this == null || isJsonNull) return ""
    if (isJsonPrimitive) return asString.trim()
    if (isJsonObject) {
        val obj = asJsonObject
        val lang = preferredLocale.language.lowercase()
        val tag = preferredLocale.toLanguageTag()

        // 1. Exact match e.g. "de-DE"
        obj.get(tag)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.let { if (it.isNotBlank()) return it }

        // 2. Language-only prefix match e.g. "de"
        for ((key, value) in obj.entrySet()) {
            if (key.startsWith(lang, ignoreCase = true) && value.isJsonPrimitive) {
                val str = value.asString.trim()
                if (str.isNotBlank()) return str
            }
        }

        // 3. Fallback to English
        obj.get("en-US")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.let { if (it.isNotBlank()) return it }
        obj.get("en")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.let { if (it.isNotBlank()) return it }

        // 4. First available string
        for ((_, value) in obj.entrySet()) {
            if (value.isJsonPrimitive) {
                val str = value.asString.trim()
                if (str.isNotBlank()) return str
            }
        }
    }
    return ""
}

/**
 * Converts a [FDroidV2Index] into [IzzyIndexResponse] so that existing matching,
 * caching, and package processing work seamlessly without regressions.
 */
fun FDroidV2Index.toIzzyIndexResponse(preferredLocale: Locale = Locale.getDefault()): IzzyIndexResponse {
    val repoInfo = repo?.let {
        IzzyRepoInfo(
            timestamp = it.timestamp,
            version = it.version.toInt(),
            name = it.name.getPreferredText(preferredLocale),
            description = it.description.getPreferredText(preferredLocale),
            icon = "",
            address = it.address.orEmpty()
        )
    }

    val appsList = ArrayList<IzzyAppInfo>(packages.size)
    val packagesMap = HashMap<String, List<IzzyPackageInfo>>(packages.size)

    for ((pkgName, entry) in packages) {
        val meta = entry.metadata
        val appInfo = IzzyAppInfo(
            packageName = pkgName,
            name = meta?.name.getPreferredText(preferredLocale).ifBlank { pkgName },
            summary = meta?.summary.getPreferredText(preferredLocale),
            description = meta?.description.getPreferredText(preferredLocale),
            icon = "",
            authorName = "",
            categories = meta?.categories.orEmpty(),
            source = meta?.sourceCode
        )
        appsList.add(appInfo)

        val pkgVersions = entry.versions.values.mapNotNull { ver ->
            val manifest = ver.manifest ?: return@mapNotNull null
            val file = ver.file ?: return@mapNotNull null
            val apkName = file.name.removePrefix("/")
            val signer = manifest.signer?.sha256?.firstOrNull().orEmpty()

            IzzyPackageInfo(
                versionName = manifest.versionName,
                versionCode = manifest.versionCode,
                size = file.size,
                apkName = apkName,
                hash = file.sha256,
                hashType = "sha256",
                signer = signer,
                minSdkVersion = manifest.usesSdk?.minSdkVersion ?: 0,
                targetSdkVersion = manifest.usesSdk?.targetSdkVersion ?: 0,
                nativecode = manifest.nativecode,
                added = ver.added,
                sig = ""
            )
        }
        if (pkgVersions.isNotEmpty()) {
            packagesMap[pkgName] = pkgVersions
        }
    }

    return IzzyIndexResponse(
        repo = repoInfo,
        apps = appsList,
        packages = packagesMap
    )
}
