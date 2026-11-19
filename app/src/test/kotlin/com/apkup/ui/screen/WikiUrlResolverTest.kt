package com.apkup.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class WikiUrlResolverTest {

    private val troubleshootingBase =
        "https://github.com/DurchEins/apkup/wiki/Troubleshooting-Source-Errors"
    private val sourcesBase =
        "https://github.com/DurchEins/apkup/wiki/Update-Sources,-Repository-Linking-&-Signature-Protection"

    @Test
    fun `resolveWikiUrlForSourceError maps error reasons to canonical Troubleshooting anchors`() {
        assertEquals(
            "$troubleshootingBase#dns-resolution-failed",
            resolveWikiUrlForSourceError("GitHub", "UnknownHostException: dns lookup failed")
        )
        assertEquals(
            "$troubleshootingBase#connection-timeout",
            resolveWikiUrlForSourceError("APKMirror", "SocketTimeoutException")
        )
        assertEquals(
            "$troubleshootingBase#play-store-rate-limit",
            resolveWikiUrlForSourceError("Google Play", "HTTP 429 Too Many Requests")
        )
        assertEquals(
            "$troubleshootingBase#github-rate-limit",
            resolveWikiUrlForSourceError("GitHub", "HTTP 403 rate limit exceeded")
        )
        assertEquals(
            "$troubleshootingBase#gitlab-auth-error",
            resolveWikiUrlForSourceError("GitLab", "HTTP 401 Unauthorized")
        )
        assertEquals(
            "$troubleshootingBase#apkmirror-404",
            resolveWikiUrlForSourceError("APKMirror", "HTTP 404 Not Found")
        )
        assertEquals(
            "$troubleshootingBase#apkmirror-cloudflare",
            resolveWikiUrlForSourceError("APKMirror", "Cloudflare challenge")
        )
        assertEquals(
            "$troubleshootingBase#signature-conflicts",
            resolveWikiUrlForSourceError("APKMirror", "Signature mismatch")
        )
        assertEquals(
            "$troubleshootingBase#custom-repo-linking",
            resolveWikiUrlForSourceError("GitHub", "No matching release asset")
        )
    }

    @Test
    fun `resolveWikiUrlForSourceError maps source fallbacks to valid wiki pages and anchors`() {
        assertEquals(
            "$sourcesBase#23-direct-apkmirror-resolution--hardened-apkm-split-extraction",
            resolveWikiUrlForSourceError("APKMirror")
        )
        assertEquals(
            "$sourcesBase#25-three-tier-repository-resolution-for-github-gitlab--codeberg",
            resolveWikiUrlForSourceError("GitLab")
        )
        assertEquals(
            "https://github.com/DurchEins/apkup/wiki/Configuring-GitHub-&-GitLab-Tokens",
            resolveWikiUrlForSourceError("Codeberg")
        )
        assertEquals(
            "https://github.com/DurchEins/apkup/wiki/F-Droid-&-Index-V2-Repositories",
            resolveWikiUrlForSourceError("IzzyOnDroid")
        )
        assertEquals(
            "$troubleshootingBase#diagnostic-checklist",
            resolveWikiUrlForSourceError("Unknown")
        )
    }
}
