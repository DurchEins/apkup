package com.apkup.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class KmpInstalledApp(
    val name: String,
    val packageName: String,
    val version: String,
    val versionCode: Long,
    val signatureSha256: String = "",
    val shortSha256: String = "",
    val isSystemApp: Boolean = false,
    val installerPackageName: String = "",
    val apkPathOnDevice: String = "",
    val isWatchedOnly: Boolean = false
)

@Serializable
data class KmpRepoBinding(
    val type: String = "github", // "github", "gitlab", "codeberg"
    val repo: String = "",
    val host: String? = null,
    val assetFilter: String? = null,
    val assetFilterRegex: String? = null
) {
    val resolvedHost: String
        get() = host?.takeIf { it.isNotBlank() } ?: when (type.trim().lowercase()) {
            "gitlab" -> "gitlab.com"
            "codeberg", "forgejo", "gitea" -> "codeberg.org"
            else -> "github.com"
        }

    val webUrl: String
        get() = "https://$resolvedHost/${repo.trim()}"
}

@Serializable
data class KmpUpdateCandidate(
    val name: String,
    val packageName: String,
    val currentVersion: String,
    val newVersion: String,
    val currentVersionCode: Long,
    val newVersionCode: Long,
    val sourceName: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L,
    val expectedSha256: String? = null,
    val releaseNotes: String = "",
    val publishedAt: String = "",
    val installedShortSha256: String = "",
    val isPreRelease: Boolean = false,
    val requiresBrowserFallback: Boolean = false
)

@Serializable
data class KmpSourceCheckReport(
    val sourceName: String,
    val updates: List<KmpUpdateCandidate> = emptyList(),
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val errorMessage: String? = null,
    val durationMs: Long = 0L
)

@Serializable
data class KmpGitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<KmpGitHubAssetDto> = emptyList()
)

@Serializable
data class KmpGitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String = "",
    val digest: String? = null
)

@Serializable
data class KmpGitLabReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val description: String = "",
    @SerialName("released_at") val releasedAt: String = "",
    val assets: KmpGitLabAssetsDto = KmpGitLabAssetsDto()
)

@Serializable
data class KmpGitLabAssetsDto(
    val count: Int = 0,
    val links: List<KmpGitLabLinkDto> = emptyList()
)

@Serializable
data class KmpGitLabLinkDto(
    val id: Long = 0L,
    val name: String = "",
    val url: String = "",
    @SerialName("direct_asset_url") val directAssetUrl: String = "",
    @SerialName("link_type") val linkType: String = ""
)

@Serializable
data class KmpFDroidV2Entry(
    val timestamp: Long = 0L,
    val version: Long = 0L,
    val maxDownloadThreads: Int? = null,
    val index: KmpFDroidV2IndexFileRef? = null,
    val diffs: Map<String, KmpFDroidV2IndexFileRef>? = null
)

@Serializable
data class KmpFDroidV2IndexFileRef(
    val name: String = "",
    val sha256: String = "",
    val size: Long = 0L,
    val numPackages: Int = 0
)

@Serializable
data class KmpFDroidV2Index(
    val repo: KmpFDroidV2RepoMetadata? = null,
    val packages: Map<String, KmpFDroidV2PackageEntry> = emptyMap()
)

@Serializable
data class KmpFDroidV2RepoMetadata(
    val timestamp: Long = 0L,
    val version: Long = 0L,
    val name: JsonElement? = null,
    val description: JsonElement? = null,
    val address: String? = null
)

@Serializable
data class KmpFDroidV2PackageEntry(
    val metadata: KmpFDroidV2PackageMetadata? = null,
    val versions: Map<String, KmpFDroidV2VersionEntry> = emptyMap()
)

@Serializable
data class KmpFDroidV2PackageMetadata(
    val added: Long = 0L,
    val lastUpdated: Long = 0L,
    val name: JsonElement? = null,
    val summary: JsonElement? = null,
    val description: JsonElement? = null,
    val categories: List<String> = emptyList(),
    val sourceCode: String? = null
)

@Serializable
data class KmpFDroidV2VersionEntry(
    val added: Long = 0L,
    val file: KmpFDroidV2FileRef? = null,
    val manifest: KmpFDroidV2Manifest? = null
)

@Serializable
data class KmpFDroidV2FileRef(
    val name: String = "",
    val sha256: String = "",
    val size: Long = 0L
)

@Serializable
data class KmpFDroidV2Manifest(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val usesSdk: KmpFDroidV2UsesSdk? = null,
    val signer: KmpFDroidV2Signer? = null,
    val nativecode: List<String>? = null
)

@Serializable
data class KmpFDroidV2UsesSdk(
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0
)

@Serializable
data class KmpFDroidV2Signer(
    val sha256: List<String> = emptyList()
)

@Serializable
data class KmpFDroidV1Index(
    val repo: KmpFDroidV1Repo? = null,
    val apps: List<KmpFDroidV1App> = emptyList(),
    val packages: Map<String, List<KmpFDroidV1Package>> = emptyMap()
)

@Serializable
data class KmpFDroidV1Repo(
    val timestamp: Long = 0L,
    val version: Int = 0,
    val name: String = "",
    val description: String = "",
    val address: String = ""
)

@Serializable
data class KmpFDroidV1App(
    val packageName: String = "",
    val name: String = "",
    val summary: String = "",
    val description: String = "",
    val sourceCode: String? = null
)

@Serializable
data class KmpFDroidV1Package(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val size: Long = 0L,
    val apkName: String = "",
    val hash: String = "",
    val hashType: String = "",
    val signer: String = "",
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val nativecode: List<String>? = null,
    val added: Long = 0L
)

@Serializable
data class KmpApkMirrorAppExistsRequest(
    val pname: List<String>
)

@Serializable
data class KmpApkMirrorAppExistsResponse(
    val data: List<KmpApkMirrorAppItem> = emptyList()
)

@Serializable
data class KmpApkMirrorAppItem(
    val pname: String = "",
    val exists: Boolean = false,
    val app: KmpApkMirrorAppInfo? = null,
    val release: KmpApkMirrorReleaseInfo? = null,
    val apks: List<KmpApkMirrorApkInfo> = emptyList()
)

@Serializable
data class KmpApkMirrorAppInfo(
    val name: String = "",
    val link: String = ""
)

@Serializable
data class KmpApkMirrorReleaseInfo(
    val version: String = "",
    @SerialName("publish_date") val publishDate: String = "",
    val whatsNew: String? = null,
    val link: String = ""
)

@Serializable
data class KmpApkMirrorApkInfo(
    @SerialName("version_code") val versionCode: String = "",
    val link: String = "",
    val arches: List<String> = emptyList(),
    val minapi: String = "0"
)
