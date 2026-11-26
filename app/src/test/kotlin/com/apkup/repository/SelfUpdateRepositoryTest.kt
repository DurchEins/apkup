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

    @Test
    fun `semver 2_0_0 ignores build metadata after plus and preserves legacy parens tie-breaker`() {
        val semVerParsed = repo.parseVersion("1.47.0-beta.1+232.abc1234")
        assertEquals(1, semVerParsed.major)
        assertEquals(47, semVerParsed.minor)
        assertEquals(0, semVerParsed.patch)
        assertEquals("beta.1", semVerParsed.preRelease)
        assertEquals(0, semVerParsed.buildNumber)

        val stableSemVer = repo.parseVersion("1.47.0+238.74a9f8d")
        assertNull(stableSemVer.preRelease)
        assertEquals(0, stableSemVer.buildNumber)

        // Build metadata after '+' MUST be ignored in SemVer comparison
        assertFalse(repo.isNewerVersion("1.47.0+238.74a9f8d", "v1.47.0"))
        assertFalse(repo.isNewerVersion("1.47.0+238.74a9f8d-debug", "v1.47.0"))
        assertFalse(repo.isNewerVersion("1.47.0+238.abc1234", "1.47.0+239.def5678"))

        // Upgrade chain: v1.46.0 (231) < 1.47.0-alpha.1 < beta.1+232 < beta.2+233 < rc.1 < 1.47.0+234 < 2.0.0+235
        assertTrue(repo.isNewerVersion("v1.46.0", "v1.47.0"))
        assertTrue(repo.isNewerVersion("1.46.0 (231)", "1.47.0-alpha.1+231.0000001"))
        assertTrue(repo.isNewerVersion("1.47.0-alpha.1+231.0000001", "1.47.0-beta.1+232.abc1234"))
        assertTrue(repo.isNewerVersion("1.47.0-beta.1+232.abc1234", "1.47.0-beta.2+233.def5678"))
        assertTrue(repo.isNewerVersion("1.47.0-beta.2+233.def5678", "1.47.0-rc.1+233.def5678"))
        assertTrue(repo.isNewerVersion("1.47.0-beta.2+233.def5678", "1.47.0+234.9876543"))
        assertTrue(repo.isNewerVersion("1.47.0+234.9876543", "2.0.0+235.1112223"))
    }

    @Test
    fun `semver release asset filenames with build suffix never misclassify as pre-release`() {
        val stableApkParsed = repo.parseVersion("ApkUp-v1.47.0-build238.apk")
        assertEquals(1, stableApkParsed.major)
        assertEquals(47, stableApkParsed.minor)
        assertEquals(0, stableApkParsed.patch)
        assertNull(stableApkParsed.preRelease)

        val betaApkParsed = repo.parseVersion("ApkUp-v1.47.0-beta.1-build238.apk")
        assertEquals(1, betaApkParsed.major)
        assertEquals(47, betaApkParsed.minor)
        assertEquals(0, betaApkParsed.patch)
        assertEquals("beta.1", betaApkParsed.preRelease)

        val semVerRelease = GitHubRelease(
            tag_name = "v1.47.0",
            name = "v1.47.0 (238)",
            body = "SemVer 2.0.0 stable release",
            prerelease = false,
            draft = false,
            assets = listOf(
                GitHubAsset(
                    name = "ApkUp-v1.47.0-build238.apk.sha256",
                    size = 128L,
                    browser_download_url = "https://github.com/DurchEins/apkup/releases/download/v1.47.0/ApkUp-v1.47.0-build238.apk.sha256"
                ),
                GitHubAsset(
                    name = "ApkUp-v1.47.0-build238.apk",
                    size = 12_345_678L,
                    browser_download_url = "https://github.com/DurchEins/apkup/releases/download/v1.47.0/ApkUp-v1.47.0-build238.apk",
                    digest = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                )
            )
        )

        val selected = repo.selectNewestEligibleRelease(
            releases = listOf(semVerRelease),
            currentVersionName = "1.46.0 (231)",
            includePreReleases = false
        )
        assertNotNull(selected)
        assertEquals("v1.47.0", selected!!.second)
        assertEquals("ApkUp-v1.47.0-build238.apk", selected.third.safeName)
        assertFalse(repo.isPreReleaseRelease(selected.first, selected.second))
        assertFalse(repo.isNewerVersion("1.47.0+238.74a9f8d", selected.third.safeName))
    }
}
