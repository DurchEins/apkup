package com.apkup

import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.viewmodel.partitionInstallQueueCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSelectUpdateTest {

    private fun sampleUpdate(
        id: Int,
        pkg: String,
        source: com.apkup.data.ui.Source,
        alt: List<AppUpdate> = emptyList()
    ) = AppUpdate(
        id = id,
        name = pkg,
        packageName = pkg,
        version = "2.0.0",
        oldVersion = "1.0.0",
        versionCode = 200L,
        oldVersionCode = 100L,
        source = source,
        link = Link.Url("https://example.com/$pkg"),
        alternativeUpdates = alt
    )

    @Test
    fun `selected subset partitions direct and browser-only updates accurately`() {
        val githubApp = sampleUpdate(1, "com.example.github", GitHubSource)
        val izzyApp = sampleUpdate(2, "com.example.izzy", IzzyOnDroidSource)
        val apkmirrorApp = sampleUpdate(3, "com.example.apkmirror", ApkMirrorSource)
        val unselectedApp = sampleUpdate(4, "com.example.unselected", GitHubSource)

        val allUpdates = listOf(githubApp, izzyApp, apkmirrorApp, unselectedApp)
        val selectedIds = setOf(1, 3)
        val selectedSubset = allUpdates.filter { it.id in selectedIds }

        val (directNoMirror, browserNoMirror) = partitionInstallQueueCandidates(
            updates = selectedSubset,
            directApkMirrorEnabled = false
        )
        assertEquals(listOf(1), directNoMirror.map { it.id })
        assertEquals(listOf(3), browserNoMirror.map { it.id })

        val (directWithMirror, browserWithMirror) = partitionInstallQueueCandidates(
            updates = selectedSubset,
            directApkMirrorEnabled = true
        )
        assertEquals(listOf(1, 3), directWithMirror.map { it.id })
        assertTrue(browserWithMirror.isEmpty())
    }

    @Test
    fun `selected subset prefers direct alternative source when primary is browser-only`() {
        val directAlt = sampleUpdate(12, "com.example.dual", GitHubSource)
        val primaryMirror = sampleUpdate(11, "com.example.dual", ApkMirrorSource, alt = listOf(directAlt))

        val (direct, browserOnly) = partitionInstallQueueCandidates(
            updates = listOf(primaryMirror),
            directApkMirrorEnabled = false
        )

        assertEquals(listOf(12), direct.map { it.id })
        assertTrue(browserOnly.isEmpty())
    }
}
