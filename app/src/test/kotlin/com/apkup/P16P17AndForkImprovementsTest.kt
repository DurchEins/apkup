package com.apkup

import com.apkup.data.repomapping.ReleaseAssetFilter
import com.apkup.data.ui.PreReleaseOverride
import com.apkup.data.ui.UpdateWindowEvaluator
import com.apkup.data.ui.UpdateWindowMode
import com.apkup.data.ui.WatchedAppEntry
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.util.ApkCompatibilityChecker
import com.apkup.util.ApkCompatibilityResult
import com.apkup.util.AppWatcherImporter
import com.apkup.util.SourceHealthTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class P16P17AndForkImprovementsTest {

    @Before
    fun setUp() {
        SourceHealthTracker.clear()
    }

    @Test
    fun `bare build number tag does not beat multi-segment semver`() {
        assertFalse(ReleaseAssetFilter.isNewer("2.4.0-261014", "v2026072802"))
        assertFalse(ReleaseAssetFilter.isNewer("1.12.3", "20261115"))
        assertTrue(ReleaseAssetFilter.isNewer("2.4.0-261014", "2.4.1"))
        assertTrue(ReleaseAssetFilter.isNewer("2026072801", "2026072802"))
    }

    @Test
    fun `isPreReleaseVersion detects pre-release tokens without false positives`() {
        assertTrue(ReleaseAssetFilter.isPreReleaseVersion("1.2.0-alpha1"))
        assertTrue(ReleaseAssetFilter.isPreReleaseVersion("v2.0.0-beta.2"))
        assertTrue(ReleaseAssetFilter.isPreReleaseVersion("3.1.0_rc1"))
        assertTrue(ReleaseAssetFilter.isPreReleaseVersion("4.0.0-nightly.20261115"))
        assertTrue(ReleaseAssetFilter.isPreReleaseVersion("1.0.0-dev"))
        assertTrue(ReleaseAssetFilter.isPreReleaseVersion("1.0.0", isPrereleaseFlag = true))

        assertFalse(ReleaseAssetFilter.isPreReleaseVersion("1.2.0"))
        assertFalse(ReleaseAssetFilter.isPreReleaseVersion("v2.4.1"))
    }

    @Test
    fun `Prefs pre-release global and per-app overrides work as expected`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        assertFalse(prefs.shouldAllowPreReleases("org.example.app"))

        prefs.setAppPreReleaseOverride("org.example.app", PreReleaseOverride.ALLOW)
        assertTrue(prefs.shouldAllowPreReleases("org.example.app"))
        assertTrue(prefs.hasAnyPreReleaseAllowed())

        prefs.includePreReleasesGlobal.put(true)
        assertTrue(prefs.shouldAllowPreReleases("org.other.app"))

        prefs.setAppPreReleaseOverride("org.other.app", PreReleaseOverride.BLOCK)
        assertFalse(prefs.shouldAllowPreReleases("org.other.app"))

        prefs.setAppPreReleaseOverride("org.other.app", PreReleaseOverride.DEFAULT)
        assertTrue(prefs.shouldAllowPreReleases("org.other.app"))
    }

    @Test
    fun `Prefs preReleaseChannels filters Beta, RC, Alpha and Nightly individually`() {
        val prefs = Prefs(InMemoryPrefsBackend())
        prefs.includePreReleasesGlobal.put(true)

        // Default channels: BETA + RC
        assertTrue(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0-beta1"))
        assertTrue(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0-rc1"))
        assertFalse(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0-alpha1"))
        assertFalse(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0-nightly"))

        // Enable Alpha explicitly
        prefs.setPreReleaseChannelEnabled(ReleaseAssetFilter.PreReleaseChannel.ALPHA, true)
        assertTrue(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0-alpha1"))

        // Disable Beta explicitly
        prefs.setPreReleaseChannelEnabled(ReleaseAssetFilter.PreReleaseChannel.BETA, false)
        assertFalse(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0-beta2"))
        // Stable versions remain allowed regardless of channels
        assertTrue(prefs.isPreReleaseCandidateAllowed("org.example.app", "1.2.0"))
    }

    @Test
    fun `UpdateWindowEvaluator handles quiet hours across midnight and allow-only windows`() {
        val startQuiet = 22 * 60 // 22:00
        val endQuiet = 7 * 60    // 07:00

        // 23:00 is inside quiet hours -> not allowed
        assertFalse(
            UpdateWindowEvaluator.isAllowedNow(
                enabled = true,
                mode = UpdateWindowMode.QUIET_PAUSE,
                startMinutes = startQuiet,
                endMinutes = endQuiet,
                nowMinuteOfDay = 23 * 60
            )
        )
        // 03:15 is inside quiet hours -> 225 minutes until 07:00
        assertEquals(
            225,
            UpdateWindowEvaluator.minutesUntilNextAllowedWindow(
                enabled = true,
                mode = UpdateWindowMode.QUIET_PAUSE,
                startMinutes = startQuiet,
                endMinutes = endQuiet,
                nowMinuteOfDay = 3 * 60 + 15
            )
        )
        // 12:00 is outside quiet hours -> allowed
        assertTrue(
            UpdateWindowEvaluator.isAllowedNow(
                enabled = true,
                mode = UpdateWindowMode.QUIET_PAUSE,
                startMinutes = startQuiet,
                endMinutes = endQuiet,
                nowMinuteOfDay = 12 * 60
            )
        )

        // ALLOW_ONLY mode: 02:00 - 06:00
        val startAllow = 2 * 60
        val endAllow = 6 * 60
        assertTrue(
            UpdateWindowEvaluator.isAllowedNow(
                enabled = true,
                mode = UpdateWindowMode.ALLOW_ONLY,
                startMinutes = startAllow,
                endMinutes = endAllow,
                nowMinuteOfDay = 3 * 60
            )
        )
        assertFalse(
            UpdateWindowEvaluator.isAllowedNow(
                enabled = true,
                mode = UpdateWindowMode.ALLOW_ONLY,
                startMinutes = startAllow,
                endMinutes = endAllow,
                nowMinuteOfDay = 1 * 60
            )
        )
        assertEquals(
            60,
            UpdateWindowEvaluator.minutesUntilNextAllowedWindow(
                enabled = true,
                mode = UpdateWindowMode.ALLOW_ONLY,
                startMinutes = startAllow,
                endMinutes = endAllow,
                nowMinuteOfDay = 1 * 60
            )
        )
    }

    @Test
    fun `ApkCompatibilityChecker detects minSdk, ABI mismatch and low targetSdk bypass`() {
        val sdkTooHigh = ApkCompatibilityChecker.evaluate(
            minSdkVersion = 36,
            targetSdkVersion = 36,
            bundledAbis = setOf("arm64-v8a"),
            deviceSdkInt = 35,
            deviceSupportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        assertFalse(sdkTooHigh.isCompatible)
        assertEquals(ApkCompatibilityResult.FailureReason.MIN_SDK_TOO_HIGH, sdkTooHigh.failureReason)

        val abiMismatch = ApkCompatibilityChecker.evaluate(
            minSdkVersion = 29,
            targetSdkVersion = 34,
            bundledAbis = setOf("x86_64"),
            deviceSdkInt = 35,
            deviceSupportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        assertFalse(abiMismatch.isCompatible)
        assertEquals(ApkCompatibilityResult.FailureReason.NO_MATCHING_ABI, abiMismatch.failureReason)

        val compatibleLowTarget = ApkCompatibilityChecker.evaluate(
            minSdkVersion = 21,
            targetSdkVersion = 22,
            bundledAbis = emptySet(),
            deviceSdkInt = 35,
            deviceSupportedAbis = listOf("arm64-v8a")
        )
        assertTrue(compatibleLowTarget.isCompatible)
        assertNull(compatibleLowTarget.failureReason)
        assertTrue(compatibleLowTarget.needsLowTargetSdkBypass)
    }

    @Test
    fun `AppWatcherImporter parses uninstalled apps, skips deleted and installed, and sets seenVersion`() {
        val rawJson = """
            {
              "apps": [
                {
                  "packageName": "org.schabi.newpipe",
                  "title": "NewPipe",
                  "versionName": "0.27.2",
                  "versionCode": 998,
                  "status": 0
                },
                {
                  "packageName": "com.installed.app",
                  "title": "Installed App",
                  "versionName": "1.0.0",
                  "versionCode": 10,
                  "status": 0
                },
                {
                  "packageName": "com.deleted.app",
                  "title": "Deleted App",
                  "versionName": "2.0.0",
                  "versionCode": 20,
                  "status": 2
                }
              ]
            }
        """.trimIndent()

        val summary = AppWatcherImporter.parseExportJson(
            rawJson = rawJson,
            installedPackages = setOf("com.installed.app"),
            existingWatched = emptyList(),
            nowMillis = 123456L
        )
        assertNotNull(summary)
        assertEquals(1, summary!!.addedEntries.size)
        assertEquals(1, summary.skippedInstalledCount)
        val entry = summary.addedEntries.first()
        assertEquals("org.schabi.newpipe", entry.packageName)
        assertEquals("NewPipe", entry.appName)
        assertEquals("0.27.2", entry.seenVersion)
        assertEquals(998L, entry.seenVersionCode)

        // Same version is not unseen; newer version is unseen
        assertFalse(entry.isCandidateUnseen("0.27.2", 998L))
        assertTrue(entry.isCandidateUnseen("0.27.3", 999L))
    }

    @Test
    fun `SourceHealthTracker tracks success, error and active cooldown`() {
        SourceHealthTracker.recordSuccess("GitHub", foundUpdates = 4, durationMillis = 310L, nowMillis = 1_000L)
        SourceHealthTracker.recordCooldown("APKMirror", cooldownUntilMillis = 61_000L, reason = "Cloudflare cooldown")

        val snap = SourceHealthTracker.snapshot()
        assertEquals(2, snap.size)
        val apkMirror = snap.first { it.sourceName == "APKMirror" }
        assertTrue(apkMirror.isCooldownActive(1_000L))
        assertEquals(60L, apkMirror.remainingCooldownSeconds(1_000L))
    }
}
