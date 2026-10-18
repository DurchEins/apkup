package com.apkup

import com.apkup.data.selfupdate.GitHubRelease
import com.apkup.repository.SelfUpdateRepository
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdateTest {

    private val repo = SelfUpdateRepository(
        okHttpClient = OkHttpClient(),
        gson = Gson()
    )

    @Test
    fun semVerVersionParsingExtractsComponentsCorrectly() {
        val v1 = repo.parseVersion("1.30-Beta")
        assertEquals(1, v1.major)
        assertEquals(30, v1.minor)
        assertEquals(0, v1.patch)
        assertEquals("Beta", v1.preRelease)

        val v2 = repo.parseVersion("v2.1.4-rc2")
        assertEquals(2, v2.major)
        assertEquals(1, v2.minor)
        assertEquals(4, v2.patch)
        assertEquals("rc2", v2.preRelease)

        val v3 = repo.parseVersion("1.0")
        assertEquals(1, v3.major)
        assertEquals(0, v3.minor)
        assertEquals(0, v3.patch)
        assertNull(v3.preRelease)
    }

    @Test
    fun semVerDetectsNewerMajorMinorAndPatchReleases() {
        assertTrue(repo.isNewerVersion("1.27-Beta", "v1.30-Beta"))
        assertTrue(repo.isNewerVersion("1.27", "1.30"))
        assertTrue(repo.isNewerVersion("1.27.1", "1.27.2"))
        assertTrue(repo.isNewerVersion("1.30", "2.0.0"))

        assertFalse(repo.isNewerVersion("1.30", "1.27"))
        assertFalse(repo.isNewerVersion("1.30", "1.30"))
        assertFalse(repo.isNewerVersion("1.30-Beta", "1.30-Beta"))
    }

    @Test
    fun semVerHandlesBetaToStableAndSubsequentBetas() {
        // Stable release is newer than beta of same version number
        assertTrue(repo.isNewerVersion("1.30-Beta", "v1.30"))
        assertTrue(repo.isNewerVersion("1.30-Beta", "1.30"))

        // Beta is older than final stable release
        assertFalse(repo.isNewerVersion("1.30", "v1.30-Beta"))

        // Incremental betas (Beta2 > Beta1)
        assertTrue(repo.isNewerVersion("1.30-Beta", "v1.30-Beta2"))
        assertTrue(repo.isNewerVersion("1.30-Beta1", "v1.30-Beta2"))
        assertFalse(repo.isNewerVersion("1.30-Beta2", "v1.30-Beta1"))

        // Next beta version
        assertTrue(repo.isNewerVersion("1.30-Beta", "v1.31-Beta"))
    }

    @Test
    fun gitHubReleaseJsonCorrectlyParsesAndIdentifiesApkAsset() {
        val sampleReleaseJson = """
            {
              "tag_name": "v1.30-Beta",
              "name": "ApkUp v1.30 Beta",
              "body": "## What's new in v1.30\n- IzzyOnDroid update source\n- ApkUp Self-Updater",
              "draft": false,
              "prerelease": true,
              "assets": [
                {
                  "name": "source_code.tar.gz",
                  "size": 500000,
                  "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v1.30-Beta/source_code.tar.gz"
                },
                {
                  "name": "ApkUp-v1.30-Beta.apk",
                  "size": 14500000,
                  "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v1.30-Beta/ApkUp-v1.30-Beta.apk"
                }
              ]
            }
        """.trimIndent()

        val release = Gson().fromJson(sampleReleaseJson, GitHubRelease::class.java)
        assertEquals("v1.30-Beta", release.tag_name)
        assertEquals(2, release.assets.size)

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        assertNotNull(apkAsset)
        assertEquals("ApkUp-v1.30-Beta.apk", apkAsset!!.name)
        assertEquals(14500000L, apkAsset.size)
        assertEquals(
            "https://github.com/DurchEins/apkup/releases/download/v1.30-Beta/ApkUp-v1.30-Beta.apk",
            apkAsset.browser_download_url
        )
    }
}
