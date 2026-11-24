package com.apkup

import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.UpdateHistoryEntry
import com.apkup.data.ui.UpdateInstallMode
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.util.resolvePreviousVersionUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateHistoryTest {

    @Test
    fun `resolvePreviousVersionUrl builds accurate rollback links across all 5 sources`() {
        // 1. GitHub release asset link -> filtered releases page for oldVersion
        val githubUrl = resolvePreviousVersionUrl(
            packageName = "org.schabi.newpipe",
            appName = "NewPipe",
            oldVersion = "v0.27.2",
            sourceName = GitHubSource.name,
            linkUrl = "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.27.3/NewPipe_v0.27.3.apk"
        )
        assertEquals("https://github.com/TeamNewPipe/NewPipe/releases?q=0.27.2", githubUrl)

        // 2. GitLab custom subgroup binding -> project releases archive
        val gitlabUrl = resolvePreviousVersionUrl(
            packageName = "com.aurora.store",
            appName = "Aurora Store",
            oldVersion = "4.6.1",
            sourceName = GitLabSource.name,
            repoBinding = RepoBinding(
                type = "gitlab",
                repo = "AuroraOSS/AuroraStore",
                host = "gitlab.com"
            )
        )
        assertEquals("https://gitlab.com/AuroraOSS/AuroraStore/-/releases", gitlabUrl)

        // 3. IzzyOnDroid -> official package index archive page
        val izzyUrl = resolvePreviousVersionUrl(
            packageName = "de.tutao.tutanota",
            appName = "Tuta Mail",
            oldVersion = "250.2410.0",
            sourceName = IzzyOnDroidSource.name
        )
        assertEquals("https://apt.izzysoft.de/fdroid/index/apk/de.tutao.tutanota", izzyUrl)

        // 4. APKMirror variant link -> canonical app release archive page
        val apkMirrorUrl = resolvePreviousVersionUrl(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            oldVersion = "19.43.41",
            sourceName = ApkMirrorSource.name,
            linkUrl = "https://www.apkmirror.com/apk/google-inc/youtube/youtube-19-44-39-release/youtube-19-44-39-android-apk-download/"
        )
        assertEquals("https://www.apkmirror.com/apk/google-inc/youtube/", apkMirrorUrl)

        // 5. Google Play -> developer-signed APKMirror release search for App Name + oldVersion
        val playFallbackUrl = resolvePreviousVersionUrl(
            packageName = "org.thoughtcrime.securesms",
            appName = "Signal",
            oldVersion = "7.23.2",
            sourceName = PlaySource.name
        )
        assertEquals(
            "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=Signal+7.23.2",
            playFallbackUrl
        )
    }

    @Test
    fun `Prefs recordUpdateHistory deduplicates within window, caps at max entries, and clears cleanly`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val baseTime = 1_760_000_000_000L

        val first = UpdateHistoryEntry(
            packageName = "com.example.alpha",
            appName = "Alpha",
            oldVersion = "1.0.0",
            newVersion = "1.1.0",
            oldVersionCode = 10L,
            newVersionCode = 11L,
            sourceName = GitHubSource.name,
            installedAtMillis = baseTime,
            installModeName = UpdateInstallMode.MANUAL_SHIZUKU.name,
            previousVersionUrl = "https://github.com/example/alpha/releases?q=1.0.0"
        )
        prefs.recordUpdateHistory(first)
        // Duplicate emission 3 seconds later (e.g., Service + ViewModel) must be ignored
        prefs.recordUpdateHistory(first.copy(installedAtMillis = baseTime + 3_000L))
        assertEquals(1, prefs.readUpdateHistorySafe().size)

        // Cap enforcement
        for (idx in 1..60) {
            prefs.recordUpdateHistory(
                UpdateHistoryEntry(
                    packageName = "com.example.pkg$idx",
                    appName = "App $idx",
                    oldVersion = "1.0",
                    newVersion = "2.0",
                    newVersionCode = idx.toLong(),
                    installedAtMillis = baseTime + idx * 10_000L
                ),
                maxEntries = 50
            )
        }
        val capped = prefs.readUpdateHistorySafe()
        assertEquals(50, capped.size)
        assertEquals("com.example.pkg60", capped.first().packageName)

        prefs.clearUpdateHistory()
        assertTrue(prefs.readUpdateHistorySafe().isEmpty())
        assertTrue(prefs.updateHistoryFlow.value.isEmpty())
    }

    @Test
    fun `filterUpdateHistoryEntries filters by query, backup availability, and source`() {
        val entries = listOf(
            UpdateHistoryEntry(
                packageName = "com.example.alpha",
                appName = "Alpha Browser",
                oldVersion = "1.0",
                newVersion = "1.1",
                sourceName = "GitHub",
                installedAtMillis = 1000L
            ),
            UpdateHistoryEntry(
                packageName = "com.example.beta",
                appName = "Beta Notes",
                oldVersion = "2.0",
                newVersion = "2.1",
                sourceName = "PlayStore",
                installedAtMillis = 2000L
            )
        )

        val byQuery = com.apkup.util.filterUpdateHistoryEntries(
            entries = entries,
            query = "alpha",
            filterMode = com.apkup.util.UpdateHistoryFilterMode.ALL,
            selectedSourceName = null,
            hasBackup = { false }
        )
        assertEquals(listOf("com.example.alpha"), byQuery.map { it.packageName })

        val withBackup = com.apkup.util.filterUpdateHistoryEntries(
            entries = entries,
            query = "",
            filterMode = com.apkup.util.UpdateHistoryFilterMode.WITH_BACKUP,
            selectedSourceName = null,
            hasBackup = { it == "com.example.beta" }
        )
        assertEquals(listOf("com.example.beta"), withBackup.map { it.packageName })

        val bySource = com.apkup.util.filterUpdateHistoryEntries(
            entries = entries,
            query = "",
            filterMode = com.apkup.util.UpdateHistoryFilterMode.ALL,
            selectedSourceName = "github",
            hasBackup = { true }
        )
        assertEquals(listOf("com.example.alpha"), bySource.map { it.packageName })
    }

    @Test
    fun `formatUpdateHistoryCsv and formatUpdateHistoryMarkdown escape special characters`() {
        val entries = listOf(
            UpdateHistoryEntry(
                packageName = "com.example.app",
                appName = "My, App",
                oldVersion = "1.0",
                newVersion = "2.0",
                sourceName = "GitHub",
                installedAtMillis = 1_700_000_000_000L
            )
        )
        val csv = com.apkup.util.formatUpdateHistoryCsv(entries)
        assertTrue(csv.contains("\"My, App\""))
        assertTrue(csv.contains("com.example.app,1.0,2.0,GitHub"))

        val md = com.apkup.util.formatUpdateHistoryMarkdown(entries)
        assertTrue(md.contains("| My, App | com.example.app | 1.0 → 2.0 | GitHub |"))
    }

    @Test
    fun `formatShareableAppList produces clean plain text without Markdown symbols`() {
        val apps = listOf(
            com.apkup.data.ui.AppInstalled(
                name = "NewPipe",
                packageName = "org.schabi.newpipe",
                version = "0.27.3",
                versionCode = 1000L
            ),
            com.apkup.data.ui.AppInstalled(
                name = "Signal",
                packageName = "org.thoughtcrime.securesms",
                version = "7.23.2",
                versionCode = 2000L
            )
        )
        val formatted = com.apkup.util.formatShareableAppList(
            apps = apps,
            repoUrls = mapOf("org.schabi.newpipe" to "https://github.com/TeamNewPipe/NewPipe")
        )
        assertTrue(formatted.startsWith("ApkUp App List (2)\n\n"))
        assertTrue(formatted.contains("• NewPipe (0.27.3) — https://github.com/TeamNewPipe/NewPipe"))
        assertTrue(formatted.contains("• Signal (7.23.2) — org.thoughtcrime.securesms"))
        assertTrue(!formatted.contains("**"))
        assertTrue(!formatted.contains("###"))
        assertTrue(!formatted.contains("`"))
    }

    @Test
    fun `countVisibleWidgetUpdates excludes watched, ignored, and snoozed entries`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        val ignoredUpdate = com.apkup.data.ui.CachedAppUpdateDto(
            name = "IgnoredVer",
            packageName = "com.example.ignoredver",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            sourceName = GitHubSource.name
        )
        val computedIgnoredId = "${GitHubSource.name}.com.example.ignoredver.20.2.0".hashCode()
        prefs.ignoredVersions.put(listOf(computedIgnoredId))
        prefs.addIgnoredApp("com.example.ignoredapp")
        prefs.snoozeUpdate("com.example.snoozed", "Beta", "3.0", 30L, 60_000L)

        val snapshot = com.apkup.data.ui.CachedUpdateSnapshot(
            timestampMillis = 1_700_000_000_000L,
            updates = listOf(
                com.apkup.data.ui.CachedAppUpdateDto(
                    name = "VisibleApp",
                    packageName = "com.example.visible",
                    version = "1.1",
                    oldVersion = "1.0",
                    versionCode = 11L,
                    oldVersionCode = 10L,
                    sourceName = GitHubSource.name
                ),
                ignoredUpdate,
                com.apkup.data.ui.CachedAppUpdateDto(
                    name = "IgnoredApp",
                    packageName = "com.example.ignoredapp",
                    version = "1.5",
                    oldVersion = "1.0",
                    versionCode = 15L,
                    oldVersionCode = 10L,
                    sourceName = GitHubSource.name
                ),
                com.apkup.data.ui.CachedAppUpdateDto(
                    name = "SnoozedApp",
                    packageName = "com.example.snoozed",
                    version = "3.0",
                    oldVersion = "2.0",
                    versionCode = 30L,
                    oldVersionCode = 20L,
                    sourceName = GitHubSource.name
                ),
                com.apkup.data.ui.CachedAppUpdateDto(
                    name = "WatchedApp",
                    packageName = "com.example.watched",
                    version = "1.0",
                    oldVersion = "",
                    versionCode = 1L,
                    oldVersionCode = -1L,
                    sourceName = GitHubSource.name
                )
            )
        )
        assertEquals(1, com.apkup.widget.countVisibleWidgetUpdates(snapshot, prefs))
    }
}
