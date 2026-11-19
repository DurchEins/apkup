package com.apkup.repository

import com.apkup.data.selfupdate.GitHubAsset
import com.apkup.data.selfupdate.GitHubRelease
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdateRepositoryTest {

    private val repo = SelfUpdateRepository(OkHttpClient(), Gson())

    @Test
    fun `parseVersion strips build number suffix from preRelease and supports 3-part versions`() {
        val parsed = repo.parseVersion("v1.39.3-Beta (148)")
        assertEquals(1, parsed.major)
        assertEquals(39, parsed.minor)
        assertEquals(3, parsed.patch)
        assertEquals("Beta", parsed.preRelease)
        assertEquals(148, parsed.buildNumber)
    }

    @Test
    fun `parseVersion supports 3 to 4 digit version segments and build numbers`() {
        val parsed = repo.parseVersion("v100.1234.5678-Beta (12345)")
        assertEquals(100, parsed.major)
        assertEquals(1234, parsed.minor)
        assertEquals(5678, parsed.patch)
        assertEquals("Beta", parsed.preRelease)
        assertEquals(12345, parsed.buildNumber)
    }

    @Test
    fun `isNewerVersion compares patch and 3 to 4 digit numbers accurately`() {
        assertTrue(repo.isNewerVersion("1.39.2-Beta (147)", "v1.39.3-Beta (148)"))
        assertFalse(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.3-Beta (148)"))
        assertFalse(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.2-Beta (147)"))
        assertTrue(repo.isNewerVersion("1.39.3-Beta (148)", "v1.39.10-Beta (155)"))
        assertTrue(repo.isNewerVersion("1.999.999-Beta (9999)", "1.1000.0-Beta (10000)"))
    }

    @Test
    fun `isNewerVersion uses buildNumber as tie-breaker when semantic version and preRelease match`() {
        assertTrue(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.3-Beta (149)"))
        assertFalse(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.3-Beta"))
    }

    @Test
    fun `parseVersion and isNewerVersion support non-Beta release formatting`() {
        val parsed = repo.parseVersion("1.43.1 (193)")
        assertEquals(1, parsed.major)
        assertEquals(43, parsed.minor)
        assertEquals(1, parsed.patch)
        assertEquals(null, parsed.preRelease)
        assertEquals(193, parsed.buildNumber)
        assertTrue(repo.isNewerVersion("1.43.0-Beta (192)", "1.43.1 (193)"))
        assertTrue(repo.isNewerVersion("1.43.1 (193)", "1.43.2 (194)"))
    }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        apkUrl: String = "https://github.com/DurchEins/apkup/releases/download/$tag/ApkUp-$tag.apk",
        digest: String? = "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    ): GitHubRelease = GitHubRelease(
        tag_name = tag,
        name = "ApkUp $tag",
        body = "Release notes for $tag",
        prerelease = prerelease,
        draft = draft,
        assets = listOf(
            GitHubAsset(
                name = "ApkUp-$tag.apk",
                size = 10_000_000L,
                browser_download_url = apkUrl,
                digest = digest
            )
        )
    )

    @Test
    fun `stable user with opt-in disabled ignores pre-releases even if prerelease flag was forgotten`() {
        val releases = listOf(
            release(tag = "v1.45.0-beta.2", prerelease = true),
            release(tag = "v1.45.0-beta.1", prerelease = false), // forgotten GitHub pre-release checkbox
            release(tag = "v1.45.0", prerelease = true), // tagged without suffix but flagged as pre-release
            release(tag = "v1.44.1", prerelease = false)
        )

        val selected = repo.selectNewestEligibleRelease(
            releases = releases,
            currentVersionName = "1.44.0 (203)",
            includePreReleases = false
        )
        assertNotNull(selected)
        assertEquals("v1.44.1", selected!!.second)
        assertFalse(repo.isPreReleaseRelease(selected.first, selected.second))
    }

    @Test
    fun `stable user with opt-in disabled gets null when only pre-releases are newer`() {
        val releases = listOf(
            release(tag = "v1.45.0-beta.1", prerelease = true),
            release(tag = "v1.45.0-alpha.3", prerelease = false),
            release(tag = "v1.44.0", prerelease = false)
        )

        val selected = repo.selectNewestEligibleRelease(
            releases = releases,
            currentVersionName = "1.44.0 (203)",
            includePreReleases = false
        )
        assertNull(selected)
    }

    @Test
    fun `stable user with opt-in enabled selects newest beta and sets isPreRelease via checkForUpdate`() = runBlocking {
        val releasesJson = """
            [
              {
                "tag_name": "v1.45.0-beta.2",
                "name": "ApkUp v1.45.0 Beta 2",
                "body": "Beta 2 notes",
                "prerelease": true,
                "draft": false,
                "assets": [
                  {
                    "name": "ApkUp-v1.45.0-beta.2.apk",
                    "size": 12345678,
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v1.45.0-beta.2/ApkUp-v1.45.0-beta.2.apk",
                    "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                  }
                ]
              },
              {
                "tag_name": "v1.44.0",
                "name": "ApkUp v1.44.0",
                "body": "Stable notes",
                "prerelease": false,
                "draft": false,
                "assets": [
                  {
                    "name": "ApkUp-v1.44.0.apk",
                    "size": 12000000,
                    "browser_download_url": "https://github.com/DurchEins/apkup/releases/download/v1.44.0/ApkUp-v1.44.0.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        var requestedUrl = ""
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestedUrl = chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(releasesJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val testRepo = SelfUpdateRepository(
            okHttpClient = client,
            gson = Gson(),
            releasesEndpointUrl = SelfUpdateRepository.GITHUB_RELEASES_LIST_URL
        )

        assertTrue(SelfUpdateRepository.GITHUB_RELEASES_LIST_URL.contains("per_page=20"))

        val stableResult = testRepo.checkForUpdate(
            currentVersionName = "1.44.0 (203)",
            includePreReleases = false
        ).getOrThrow()
        assertNull(stableResult)

        val betaResult = testRepo.checkForUpdate(
            currentVersionName = "1.44.0 (203)",
            includePreReleases = true
        ).getOrThrow()
        assertTrue(requestedUrl.contains("per_page=20"))
        assertNotNull(betaResult)
        assertEquals("1.45.0-beta.2", betaResult!!.versionName)
        assertEquals("v1.45.0-beta.2", betaResult.tagName)
        assertTrue(betaResult.isPreRelease)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", betaResult.expectedSha256)
    }

    @Test
    fun `semver preRelease ranking orders nightly lt alpha lt beta lt rc lt stable and compares numbers numerically`() {
        assertTrue(repo.isNewerVersion("1.45.0-nightly.1", "v1.45.0-alpha.1"))
        assertTrue(repo.isNewerVersion("1.45.0-dev.5", "v1.45.0-alpha.1"))
        assertTrue(repo.isNewerVersion("1.45.0-alpha.9", "v1.45.0-beta.1"))
        assertTrue(repo.isNewerVersion("1.45.0-beta.2", "v1.45.0-beta.10"))
        assertFalse(repo.isNewerVersion("1.45.0-beta.10", "v1.45.0-beta.2"))
        assertTrue(repo.isNewerVersion("1.45.0-beta.10", "v1.45.0-rc.1"))
        assertTrue(repo.isNewerVersion("1.45.0-rc.2", "v1.45.0"))

        // Separator normalization: beta.1 == beta-1 == beta1
        assertFalse(repo.isNewerVersion("1.45.0-beta.1", "v1.45.0-beta1"))
        assertFalse(repo.isNewerVersion("1.45.0-beta1", "v1.45.0-beta-1"))
        assertTrue(repo.isNewerVersion("1.45.0-beta1", "v1.45.0-beta.2"))

        // Selecting newest among stable and multiple betas always prefers stable of same base version
        val selected = repo.selectNewestEligibleRelease(
            releases = listOf(
                release("v1.45.0-nightly.1", prerelease = true),
                release("v1.45.0-alpha.4", prerelease = true),
                release("v1.45.0-beta.2", prerelease = true),
                release("v1.45.0-beta.10", prerelease = true),
                release("v1.45.0-rc.1", prerelease = true),
                release("v1.45.0", prerelease = false)
            ),
            currentVersionName = "1.44.0 (203)",
            includePreReleases = true
        )
        assertNotNull(selected)
        assertEquals("v1.45.0", selected!!.second)
        assertFalse(repo.isPreReleaseRelease(selected.first, selected.second))
    }

    @Test
    fun `beta user disabling opt-in skips newer betas and cleanly upgrades to next stable`() {
        val selectedBetaOnly = repo.selectNewestEligibleRelease(
            releases = listOf(
                release("v1.45.0-beta.3", prerelease = true),
                release("v1.45.0-beta.2", prerelease = true)
            ),
            currentVersionName = "1.45.0-beta.1 (205)",
            includePreReleases = false
        )
        assertNull(selectedBetaOnly)

        val selectedWithStable = repo.selectNewestEligibleRelease(
            releases = listOf(
                release("v1.45.1-beta.1", prerelease = true),
                release("v1.45.0-beta.2", prerelease = true),
                release("v1.45.0", prerelease = false)
            ),
            currentVersionName = "1.45.0-beta.1 (205)",
            includePreReleases = false
        )
        assertNotNull(selectedWithStable)
        assertEquals("v1.45.0", selectedWithStable!!.second)
        assertFalse(repo.isPreReleaseRelease(selectedWithStable.first, selectedWithStable.second))
    }

    @Test
    fun `identical installed beta version does not trigger update loop`() {
        assertFalse(repo.isNewerVersion("1.45.0-beta.1 (205)", "v1.45.0-beta.1"))
        val selected = repo.selectNewestEligibleRelease(
            releases = listOf(release("v1.45.0-beta.1", prerelease = true)),
            currentVersionName = "1.45.0-beta.1 (205)",
            includePreReleases = true
        )
        assertNull(selected)
    }

    @Test
    fun `pre-release still rejects untrusted download host`() {
        val selected = repo.selectNewestEligibleRelease(
            releases = listOf(
                release(
                    tag = "v1.45.0-beta.1",
                    prerelease = true,
                    apkUrl = "https://evil.example.com/ApkUp-v1.45.0-beta.1.apk"
                )
            ),
            currentVersionName = "1.44.0 (203)",
            includePreReleases = true
        )
        assertNull(selected)
    }
}
