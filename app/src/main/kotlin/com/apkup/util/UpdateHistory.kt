package com.apkup.util

import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.data.ui.normalizeVersionString
import java.net.URLEncoder

private val GITHUB_REPO_URL_REGEX = Regex(
    "^https?://(?:www\\.)?github\\.com/([^/]+/[^/#?]+)(?:/|$)",
    RegexOption.IGNORE_CASE
)

private val GITLAB_PROJECT_URL_REGEX = Regex(
    "^(https?://[^/]+/.+?)(?:/-/releases|/uploads/|/-/jobs/|/-/packages/)",
    RegexOption.IGNORE_CASE
)

private val APKMIRROR_APP_BASE_REGEX = Regex(
    "^(https?://(?:www\\.)?apkmirror\\.com/apk/[^/]+/[^/]+)(?:/|$)",
    RegexOption.IGNORE_CASE
)

internal fun urlEncodeParam(raw: String): String =
    URLEncoder.encode(raw, "UTF-8")

internal fun buildApkMirrorReleaseSearchUrl(
    appName: String,
    packageName: String,
    oldVersion: String
): String {
    val subject = appName.trim().ifBlank { packageName.trim() }
    val cleanOld = normalizeVersionString(oldVersion)
    val query = listOf(subject, cleanOld).filter { it.isNotBlank() }.joinToString(" ")
    if (query.isBlank()) return ""
    return "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=${urlEncodeParam(query)}"
}

internal fun resolvePreviousVersionUrl(
    packageName: String,
    appName: String,
    oldVersion: String,
    sourceName: String,
    linkUrl: String = "",
    repoBinding: RepoBinding? = null
): String {
    val trimmedUrl = linkUrl.trim()
    val cleanOld = normalizeVersionString(oldVersion)

    when {
        sourceName.equals(GitHubSource.name, ignoreCase = true) -> {
            val fromLink = GITHUB_REPO_URL_REGEX.find(trimmedUrl)?.groupValues?.getOrNull(1)
                ?.removeSuffix(".git")
                ?.trim('/')
            val fromBinding = repoBinding?.takeIf { it.isGitHub }?.repo?.trim()?.trim('/')
            val ownerRepo = fromLink?.takeIf { it.isNotBlank() } ?: fromBinding
            if (!ownerRepo.isNullOrBlank()) {
                return if (cleanOld.isNotBlank()) {
                    "https://github.com/$ownerRepo/releases?q=${urlEncodeParam(cleanOld)}"
                } else {
                    "https://github.com/$ownerRepo/releases"
                }
            }
        }

        sourceName.equals(GitLabSource.name, ignoreCase = true) -> {
            val fromBinding = repoBinding?.takeIf { it.isGitLab && it.repo.isNotBlank() }
                ?.webUrl
                ?.trimEnd('/')
            val fromLink = GITLAB_PROJECT_URL_REGEX.find(trimmedUrl)?.groupValues?.getOrNull(1)
                ?.trimEnd('/')
            val projectUrl = fromBinding ?: fromLink
            if (!projectUrl.isNullOrBlank()) {
                return "$projectUrl/-/releases"
            }
        }

        sourceName.equals(CodebergSource.name, ignoreCase = true) -> {
            val fromBinding = repoBinding?.takeIf { it.isCodeberg && it.repo.isNotBlank() }
                ?.webUrl
                ?.trimEnd('/')
            val fromLink = Regex("^(https?://[^/]+/[^/]+/[^/]+)(?:/releases|/src|/branch|/tag|$)", RegexOption.IGNORE_CASE)
                .find(trimmedUrl)?.groupValues?.getOrNull(1)
                ?.trimEnd('/')
            val projectUrl = fromBinding ?: fromLink
            if (!projectUrl.isNullOrBlank()) {
                return "$projectUrl/releases"
            }
        }

        sourceName.equals(CustomFDroidSource.name, ignoreCase = true) -> {
            if (trimmedUrl.startsWith("http", ignoreCase = true)) {
                return trimmedUrl.substringBeforeLast('/')
            }
        }

        sourceName.equals(IzzyOnDroidSource.name, ignoreCase = true) -> {
            if (packageName.isNotBlank()) {
                return "https://apt.izzysoft.de/fdroid/index/apk/${packageName.trim()}"
            }
        }

        sourceName.equals(FDroidSource.name, ignoreCase = true) ||
            trimmedUrl.contains("f-droid.org", ignoreCase = true) -> {
            if (packageName.isNotBlank()) {
                return "https://f-droid.org/packages/${packageName.trim()}/"
            }
        }

        sourceName.equals(ApkMirrorSource.name, ignoreCase = true) -> {
            val appBase = APKMIRROR_APP_BASE_REGEX.find(trimmedUrl)?.groupValues?.getOrNull(1)
                ?.trimEnd('/')
            if (!appBase.isNullOrBlank()) {
                return "$appBase/"
            }
            return buildApkMirrorReleaseSearchUrl(
                appName = appName,
                packageName = packageName,
                oldVersion = oldVersion
            )
        }

        sourceName.equals(PlaySource.name, ignoreCase = true) -> {
            return buildApkMirrorReleaseSearchUrl(
                appName = appName,
                packageName = packageName,
                oldVersion = oldVersion
            )
        }
    }

    return buildApkMirrorReleaseSearchUrl(
        appName = appName,
        packageName = packageName,
        oldVersion = oldVersion
    )
}

internal fun buildUpdateHistoryEntry(
    update: AppUpdate,
    installMode: UpdateInstallMode,
    repoBinding: RepoBinding? = null,
    installedAtMillis: Long = System.currentTimeMillis()
): UpdateHistoryEntry {
    val linkUrl = (update.link as? Link.Url)?.link.orEmpty()
    val resolvedPrevUrl = resolvePreviousVersionUrl(
        packageName = update.packageName,
        appName = update.name,
        oldVersion = update.oldVersion,
        sourceName = update.source.name,
        linkUrl = linkUrl,
        repoBinding = repoBinding
    )
    return UpdateHistoryEntry(
        packageName = update.packageName,
        appName = update.name.ifBlank { update.packageName },
        oldVersion = update.oldVersion,
        newVersion = update.version,
        oldVersionCode = update.oldVersionCode,
        newVersionCode = update.versionCode,
        sourceName = update.source.name,
        installedAtMillis = installedAtMillis,
        installModeName = installMode.name,
        previousVersionUrl = resolvedPrevUrl
    )
}

internal fun UpdateHistoryEntry.effectivePreviousVersionUrl(
    repoBinding: RepoBinding? = null
): String {
    if (previousVersionUrl.isNotBlank()) return previousVersionUrl
    return resolvePreviousVersionUrl(
        packageName = packageName,
        appName = appName,
        oldVersion = oldVersion,
        sourceName = sourceName,
        repoBinding = repoBinding
    )
}
