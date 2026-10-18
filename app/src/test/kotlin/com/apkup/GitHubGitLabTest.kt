package com.apkup

import com.apkup.data.repomapping.GitHubAssetDto
import com.apkup.data.repomapping.GitHubReleaseDto
import com.apkup.data.repomapping.GitLabAssetsDto
import com.apkup.data.repomapping.GitLabLinkDto
import com.apkup.data.repomapping.GitLabReleaseDto
import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.repomapping.RepoBinding
import com.apkup.repository.KnownRepoEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GitHubGitLabTest {

    private val gson = Gson()

    @Test
    fun testAbiFilterPrefersArm64OverUniversalAndRejectsIncompatible() {
        val assets = listOf(
            GitHubAssetDto(name = "App-x86_64.apk", browser_download_url = "https://example.com/x86_64.apk", size = 15000),
            GitHubAssetDto(name = "App-x86.apk", browser_download_url = "https://example.com/x86.apk", size = 14000),
            GitHubAssetDto(name = "App-universal.apk", browser_download_url = "https://example.com/universal.apk", size = 25000),
            GitHubAssetDto(name = "App-arm64-v8a.apk", browser_download_url = "https://example.com/arm64.apk", size = 18000),
            GitHubAssetDto(name = "App-armeabi-v7a.apk", browser_download_url = "https://example.com/armv7.apk", size = 16000),
            GitHubAssetDto(name = "checksums.txt", browser_download_url = "https://example.com/checksums.txt", size = 500)
        )

        // On 64-bit device (e.g. Pixel), arm64-v8a should be highest score (100)
        val best = ReleaseAssetFilter.findBestApk(
            assets = assets,
            getFilename = { it.name },
            supportedAbis = arrayOf("arm64-v8a")
        )

        assertNotNull(best)
        assertEquals("App-arm64-v8a.apk", best?.name)
    }

    @Test
    fun testAbiFilterFallsBackToUniversalWhenArm64NotPresent() {
        val assets = listOf(
            GitHubAssetDto(name = "app-release.apk", browser_download_url = "https://example.com/release.apk", size = 20000),
            GitHubAssetDto(name = "sources.tar.gz", browser_download_url = "https://example.com/sources.tar.gz", size = 100000)
        )

        val best = ReleaseAssetFilter.findBestApk(
            assets = assets,
            getFilename = { it.name },
            supportedAbis = arrayOf("arm64-v8a")
        )

        assertNotNull(best)
        assertEquals("app-release.apk", best?.name)
    }

    @Test
    fun testAbiFilterRejectsIncompatibleAbisWhenOnlyX86Provided() {
        val assets = listOf(
            GitHubAssetDto(name = "app-x86.apk", browser_download_url = "https://example.com/x86.apk", size = 15000),
            GitHubAssetDto(name = "app-x86_64.apk", browser_download_url = "https://example.com/x86_64.apk", size = 16000)
        )

        val best = ReleaseAssetFilter.findBestApk(
            assets = assets,
            getFilename = { it.name },
            supportedAbis = arrayOf("arm64-v8a")
        )

        assertNull(best)
    }

    @Test
    fun testVersionComparisonAndCleaning() {
        assertEquals("1.25.0", ReleaseAssetFilter.cleanVersionString("v1.25.0"))
        assertEquals("2.0.1", ReleaseAssetFilter.cleanVersionString("release-2.0.1"))
        assertEquals("3.4.1", ReleaseAssetFilter.cleanVersionString("V3.4.1"))

        // Candidate newer than installed
        assertTrue(ReleaseAssetFilter.isNewer("1.29.9", "1.30.0"))
        assertTrue(ReleaseAssetFilter.isNewer("1.9.9", "v2.0.0"))
        assertTrue(ReleaseAssetFilter.isNewer("1.2.3.3", "1.2.3.4"))

        // Equal versions
        assertFalse(ReleaseAssetFilter.isNewer("1.0.0", "1.0.0"))
        assertFalse(ReleaseAssetFilter.isNewer("1.0.0", "v1.0"))

        // Older version
        assertFalse(ReleaseAssetFilter.isNewer("1.0.0", "0.9.5"))
    }

    @Test
    fun testGitHubReleaseDtoParsing() {
        val json = """
            {
              "tag_name": "v0.27.0",
              "name": "NewPipe v0.27.0",
              "body": "Fixed YouTube playback and added new features.",
              "prerelease": false,
              "published_at": "2026-03-01T12:00:00Z",
              "assets": [
                {
                  "name": "NewPipe_v0.27.0.apk",
                  "browser_download_url": "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.27.0/NewPipe_v0.27.0.apk",
                  "size": 12582912,
                  "content_type": "application/vnd.android.package-archive"
                }
              ]
            }
        """.trimIndent()

        val release = gson.fromJson(json, GitHubReleaseDto::class.java)
        assertEquals("v0.27.0", release.tag_name)
        assertEquals(1, release.assets.size)
        assertEquals("NewPipe_v0.27.0.apk", release.assets[0].name)
        assertEquals(12582912L, release.assets[0].size)
    }

    @Test
    fun testGitLabReleaseDtoParsing() {
        val json = """
            {
              "name": "v1.5.0",
              "tag_name": "v1.5.0",
              "description": "Latest release. Download APK below.",
              "released_at": "2026-02-15T10:00:00Z",
              "assets": {
                "count": 1,
                "links": [
                  {
                    "name": "fdroidclient-v1.5.0.apk",
                    "url": "https://gitlab.com/fdroid/fdroidclient/uploads/fdroidclient-v1.5.0.apk",
                    "direct_asset_url": "https://gitlab.com/fdroid/fdroidclient/uploads/fdroidclient-v1.5.0.apk",
                    "link_type": "package"
                  }
                ]
              }
            }
        """.trimIndent()

        val release = gson.fromJson(json, GitLabReleaseDto::class.java)
        assertEquals("v1.5.0", release.tag_name)
        assertEquals(1, release.assets.links.size)
        assertEquals("fdroidclient-v1.5.0.apk", release.assets.links[0].name)
    }

    @Test
    fun testKnownReposAssetIntegrity() {
        val file = listOf(
            File("src/main/assets/repos/known_repos.json"),
            File("app/src/main/assets/repos/known_repos.json")
        ).firstOrNull { it.exists() }
        assertNotNull("known_repos.json must exist in assets", file)

        val type = object : TypeToken<List<KnownRepoEntry>>() {}.type
        val list: List<KnownRepoEntry> = gson.fromJson(file!!.readText(Charsets.UTF_8), type)

        assertTrue("Known repos list should not be empty", list.isNotEmpty())
        val newPipe = list.find { it.`package` == "org.schabi.newpipe" }
        assertNotNull(newPipe)
        assertEquals("TeamNewPipe/NewPipe", newPipe?.repo)
        assertEquals("github", newPipe?.type)
    }
}
