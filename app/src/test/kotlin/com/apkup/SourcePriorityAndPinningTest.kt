package com.apkup

import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.Source
import com.apkup.data.ui.SourcePriorityKeys
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.combineSourceResults
import com.apkup.repository.computeUpdateSourcePriority
import com.apkup.repository.enrichWithSourceAlternatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePriorityAndPinningTest {

    private fun fakeUpdate(
        pkg: String = "com.example.app",
        source: Source,
        versionCode: Long = 200L,
        origin: InstallOrigin = InstallOrigin.SYSTEM
    ) = AppUpdate(
        name = "Example",
        packageName = pkg,
        version = "2.0.0",
        oldVersion = "1.0.0",
        versionCode = versionCode,
        oldVersionCode = 100L,
        source = source,
        installOrigin = origin
    )

    @Test
    fun `normalizeOrder preserves valid order deduplicates and appends missing keys`() {
        val raw = listOf(SourcePriorityKeys.GITHUB, "UnknownSource", SourcePriorityKeys.FDROID, SourcePriorityKeys.GITHUB)
        val normalized = SourcePriorityKeys.normalizeOrder(raw)

        assertEquals(SourcePriorityKeys.DEFAULT_ORDER.size, normalized.size)
        assertEquals(SourcePriorityKeys.GITHUB, normalized[0])
        assertEquals(SourcePriorityKeys.FDROID, normalized[1])
        assertTrue(normalized.containsAll(SourcePriorityKeys.DEFAULT_ORDER))
    }

    @Test
    fun `custom global priority order controls tie breaking between sources`() {
        val customOrder = listOf(
            SourcePriorityKeys.FDROID,
            SourcePriorityKeys.GITLAB,
            SourcePriorityKeys.GITHUB,
            SourcePriorityKeys.CODEBERG,
            SourcePriorityKeys.CUSTOM_FDROID,
            SourcePriorityKeys.PLAY,
            SourcePriorityKeys.APKMIRROR
        )
        val fdroidCandidate = fakeUpdate(source = IzzyOnDroidSource)
        val playCandidate = fakeUpdate(source = PlaySource)
        val gitlabCandidate = fakeUpdate(source = GitLabSource)

        val combined = combineSourceResults(
            results = listOf(
                SourceUpdateResult(listOf(playCandidate), successfulRequests = 1, failedRequests = 0),
                SourceUpdateResult(listOf(gitlabCandidate), successfulRequests = 1, failedRequests = 0),
                SourceUpdateResult(listOf(fdroidCandidate), successfulRequests = 1, failedRequests = 0)
            ),
            packageName = AppUpdate::packageName,
            versionCode = AppUpdate::versionCode,
            sourcePriority = { computeUpdateSourcePriority(it, sourcePriorityOrder = customOrder) }
        )

        assertEquals(1, combined.updates.size)
        assertEquals(IzzyOnDroidSource, combined.updates.first().source)
    }

    @Test
    fun `pinned app source overrides installOrigin and global order`() {
        val pkg = "org.example.foss"
        val playUpdate = fakeUpdate(pkg = pkg, source = PlaySource, origin = InstallOrigin.PLAY_STORE)
        val githubUpdate = fakeUpdate(pkg = pkg, source = GitHubSource, origin = InstallOrigin.PLAY_STORE)
        val pinned = mapOf(pkg to SourcePriorityKeys.GITHUB)

        val prioritySelector: (AppUpdate) -> Int = {
            computeUpdateSourcePriority(it, pinnedSources = pinned)
        }

        val combined = combineSourceResults(
            results = listOf(
                SourceUpdateResult(listOf(playUpdate), successfulRequests = 1, failedRequests = 0),
                SourceUpdateResult(listOf(githubUpdate), successfulRequests = 1, failedRequests = 0)
            ),
            packageName = AppUpdate::packageName,
            versionCode = AppUpdate::versionCode,
            sourcePriority = prioritySelector
        )

        val enriched = enrichWithSourceAlternatives(
            primaryUpdates = combined.updates,
            allCandidates = listOf(playUpdate, githubUpdate),
            sourcePriority = prioritySelector
        )

        assertEquals(GitHubSource, enriched.first().source)
        assertEquals(listOf(GitHubSource, PlaySource), enriched.first().alternativeUpdates.map { it.source })
    }

    @Test
    fun `pinned app falls back to other available sources when pinned source has no update`() {
        val pkg = "org.example.fallback"
        val fdroidUpdate = fakeUpdate(pkg = pkg, source = FDroidSource)
        val apkMirrorUpdate = fakeUpdate(pkg = pkg, source = ApkMirrorSource)
        val pinned = mapOf(pkg to SourcePriorityKeys.CODEBERG)

        val combined = combineSourceResults(
            results = listOf(
                SourceUpdateResult(listOf(apkMirrorUpdate), successfulRequests = 1, failedRequests = 0),
                SourceUpdateResult(listOf(fdroidUpdate), successfulRequests = 1, failedRequests = 0)
            ),
            packageName = AppUpdate::packageName,
            versionCode = AppUpdate::versionCode,
            sourcePriority = { computeUpdateSourcePriority(it, pinnedSources = pinned) }
        )

        assertEquals(1, combined.updates.size)
        assertEquals(FDroidSource, combined.updates.first().source)
    }

    @Test
    fun `sourceKeyFromSource maps custom repo names to CUSTOM_FDROID`() {
        val customRepoSource = Source("MyPrivateFDroid", R.drawable.ic_izzy)
        assertEquals(SourcePriorityKeys.CUSTOM_FDROID, SourcePriorityKeys.fromSource(customRepoSource))
        assertEquals(SourcePriorityKeys.CUSTOM_FDROID, SourcePriorityKeys.fromSource(CustomFDroidSource))
        assertEquals(SourcePriorityKeys.CODEBERG, SourcePriorityKeys.fromSource(CodebergSource))
    }
}
