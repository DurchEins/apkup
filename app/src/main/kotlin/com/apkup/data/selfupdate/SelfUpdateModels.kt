package com.apkup.data.selfupdate

data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    val browser_download_url: String = "",
    val content_type: String = ""
)

data class SelfUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long
)

sealed interface SelfUpdateState {
    data object Idle : SelfUpdateState
    data object Checking : SelfUpdateState
    data class Available(val info: SelfUpdateInfo) : SelfUpdateState
    data class Downloading(val info: SelfUpdateInfo, val progressPercent: Int) : SelfUpdateState
    data class Installing(val info: SelfUpdateInfo) : SelfUpdateState
    data class UpToDate(val currentVersion: String) : SelfUpdateState
    data class Error(val message: String) : SelfUpdateState
}
