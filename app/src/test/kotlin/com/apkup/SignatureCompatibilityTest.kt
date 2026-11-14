package com.apkup

import android.content.pm.PackageInstaller
import com.apkup.data.repomapping.RepoBinding
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallFailureReason
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.hasPotentialSignatureConflict
import com.apkup.data.ui.resolveInstallFailureReason
import com.apkup.prefs.InMemoryPrefsBackend
import com.apkup.prefs.Prefs
import com.apkup.repository.IzzyOnDroidRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.util.ApkSignatureCheckResult
import com.apkup.util.ApkSignatureVerifier
import com.apkup.util.ParsedPackageSigningInfo
import com.apkup.viewmodel.pruneSignatureMismatchedCandidate
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class SignatureCompatibilityTest {

    private val mastodonPlaySha256 = "2d3e76fcb0ba313dc05e537283d4ca2f3eddb31bf94d0f7e7333c3ba63145f8a"
    private val mastodonGitHubSha256 = "a3f19b26da3fe12b0285685f6ecc41515e6a9adcc5530b47c95d847cdae4a7e8"
    private val rotatedNewSha256 = "b4a28c37eb4af23c1396796a7fdd52626f7b0bedd6641c58d06e958debf5b8f9"

    @Test
    fun areSignerSetsCompatibleDetectsMatchMismatchRotationAndIndeterminate() {
        // 1. Matching single signer (case & colon formatting normalized)
        val formattedPlay = mastodonPlaySha256.chunked(2).joinToString(":").uppercase()
        assertEquals(
            ApkSignatureCheckResult.Compatible,
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = setOf(formattedPlay),
                installedHistorySigners = emptySet(),
                archiveCurrentSigners = setOf(mastodonPlaySha256),
                archiveHistorySigners = emptySet()
            )
        )

        // 2. Mastodon Play Store vs GitHub release -> strict Mismatch
        val mismatch = ApkSignatureVerifier.areSignerSetsCompatible(
            installedCurrentSigners = setOf(mastodonPlaySha256),
            installedHistorySigners = emptySet(),
            archiveCurrentSigners = setOf(mastodonGitHubSha256),
            archiveHistorySigners = emptySet()
        )
        assertTrue(mismatch is ApkSignatureCheckResult.Mismatch)
        val mismatchDetails = mismatch as ApkSignatureCheckResult.Mismatch
        assertEquals(setOf(mastodonPlaySha256), mismatchDetails.installedSignersSha256)
        assertEquals(setOf(mastodonGitHubSha256), mismatchDetails.archiveSignersSha256)

        // 3. APK Signature Scheme v3 key-rotation lineage (old key K1 in archive history, new key K2 current)
        assertEquals(
            ApkSignatureCheckResult.Compatible,
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = setOf(mastodonGitHubSha256),
                installedHistorySigners = emptySet(),
                archiveCurrentSigners = setOf(rotatedNewSha256),
                archiveHistorySigners = setOf(mastodonGitHubSha256, rotatedNewSha256)
            )
        )

        // 4. Empty or default empty-array SHA-256 -> Indeterminate
        assertEquals(
            ApkSignatureCheckResult.Indeterminate,
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = setOf(ApkSignatureVerifier.EMPTY_SHA256),
                installedHistorySigners = emptySet(),
                archiveCurrentSigners = setOf(mastodonGitHubSha256),
                archiveHistorySigners = emptySet()
            )
        )
        assertEquals(
            ApkSignatureCheckResult.Indeterminate,
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = emptySet(),
                installedHistorySigners = emptySet(),
                archiveCurrentSigners = setOf(mastodonGitHubSha256),
                archiveHistorySigners = emptySet()
            )
        )

        // 5. Multi-signer APK requires exact current signer set equality
        assertEquals(
            ApkSignatureCheckResult.Compatible,
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = setOf(mastodonPlaySha256, mastodonGitHubSha256),
                installedHistorySigners = emptySet(),
                archiveCurrentSigners = setOf(mastodonGitHubSha256, mastodonPlaySha256),
                archiveHistorySigners = emptySet(),
                installedHasMultipleSigners = true,
                archiveHasMultipleSigners = true
            )
        )
        assertTrue(
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = setOf(mastodonPlaySha256, mastodonGitHubSha256),
                installedHistorySigners = emptySet(),
                archiveCurrentSigners = setOf(mastodonPlaySha256),
                archiveHistorySigners = emptySet(),
                installedHasMultipleSigners = true,
                archiveHasMultipleSigners = false
            ) is ApkSignatureCheckResult.Mismatch
        )
    }

    @Test
    fun izzyOnDroidHarvestsOnlySignatureMatchingAppsAndMarksMismatchedMastodonIncompatible() = runBlocking {
        val tempDir = Files.createTempDirectory("izzy_harvest_test").toFile()
        try {
            val gson = Gson()
            val prefs = Prefs(InMemoryPrefsBackend())
            val repoManager = RepoMappingManager(context = null, prefs = prefs, gson = gson)

            val sampleIndexJson = """
                {
                  "repo": { "name": "IzzyOnDroid", "timestamp": 1731490000 },
                  "apps": [
                    {
                      "packageName": "org.joinmastodon.android",
                      "name": "Mastodon",
                      "source": "https://github.com/mastodon/mastodon-android"
                    },
                    {
                      "packageName": "org.foss.matching",
                      "name": "Matching FOSS App",
                      "source": "https://github.com/foss/matching-app"
                    },
                    {
                      "packageName": "org.uninstalled.other",
                      "name": "Uninstalled App",
                      "source": "https://github.com/other/uninstalled"
                    }
                  ],
                  "packages": {
                    "org.joinmastodon.android": [
                      {
                        "versionName": "2.13.7",
                        "versionCode": 161,
                        "apkName": "mastodon_161.apk",
                        "signer": "$mastodonGitHubSha256"
                      }
                    ],
                    "org.foss.matching": [
                      {
                        "versionName": "1.5.0",
                        "versionCode": 15,
                        "apkName": "matching_15.apk",
                        "signer": "$mastodonGitHubSha256"
                      }
                    ]
                  }
                }
            """.trimIndent()

            File(tempDir, "index-v1.json").writeText(sampleIndexJson, Charsets.UTF_8)

            val offlineClient = OkHttpClient.Builder()
                .addInterceptor { throw IOException("offline unit test uses cached index-v1.json") }
                .build()

            val izzyRepo = IzzyOnDroidRepository(
                context = null,
                okHttpClient = offlineClient,
                gson = gson,
                prefs = prefs,
                repoMappingManager = repoManager,
                cacheDirectory = tempDir
            )

            val installedApps = listOf(
                AppInstalled(
                    name = "Mastodon",
                    packageName = "org.joinmastodon.android",
                    version = "2.11.4",
                    versionCode = 149L,
                    signatureSha256 = mastodonPlaySha256,
                    installerPackageName = "com.android.vending"
                ),
                AppInstalled(
                    name = "Matching FOSS App",
                    packageName = "org.foss.matching",
                    version = "1.4.0",
                    versionCode = 14L,
                    signatureSha256 = mastodonGitHubSha256
                )
            )

            val harvestCallbackFired = AtomicBoolean(false)
            izzyRepo.updates(
                apps = installedApps,
                onHarvestComplete = { harvestCallbackFired.set(true) }
            ).first()

            assertTrue(harvestCallbackFired.get())

            // Play-Store-signed Mastodon must NOT be harvested and must be recorded as incompatible
            assertNull(repoManager.getBinding("org.joinmastodon.android"))
            assertNull(repoManager.getCompatibleBinding("org.joinmastodon.android", mastodonPlaySha256))
            assertTrue(
                repoManager.isSignatureIncompatible(
                    "org.joinmastodon.android",
                    RepoBinding(type = "github", repo = "mastodon/mastodon-android"),
                    mastodonPlaySha256
                )
            )

            // Even if Mastodon were in built-in known_repos, getCompatibleBinding blocks Play-Store cert
            // while allowing GitHub cert if the user reinstalls Mastodon from GitHub!
            repoManager.putKnownRepoForTest(
                "org.joinmastodon.android",
                RepoBinding(type = "github", repo = "mastodon/mastodon-android")
            )
            assertNull(repoManager.getCompatibleBinding("org.joinmastodon.android", mastodonPlaySha256))
            val compatibleAfterReinstall = repoManager.getCompatibleBinding(
                "org.joinmastodon.android",
                mastodonGitHubSha256
            )
            assertNotNull(compatibleAfterReinstall)
            assertEquals("mastodon/mastodon-android", compatibleAfterReinstall?.repo)

            // Matching FOSS app IS harvested; uninstalled IzzyOnDroid apps are ignored
            val matchingBinding = repoManager.getCompatibleBinding("org.foss.matching", mastodonGitHubSha256)
            assertNotNull(matchingBinding)
            assertEquals("foss/matching-app", matchingBinding?.repo)
            assertNull(repoManager.getBinding("org.uninstalled.other"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun resolveInstallFailureReasonDetectsSignatureConflictAbortedStorageAndGeneric() {
        assertEquals(
            InstallFailureReason.SIGNATURE_MISMATCH,
            resolveInstallFailureReason(
                status = PackageInstaller.STATUS_FAILURE_CONFLICT,
                message = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package org.joinmastodon.android signatures do not match newer version; ignoring!"
            )
        )
        assertEquals(
            InstallFailureReason.SIGNATURE_MISMATCH,
            resolveInstallFailureReason(
                status = null,
                message = "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package signatures do not match]"
            )
        )
        assertEquals(
            InstallFailureReason.ABORTED,
            resolveInstallFailureReason(status = PackageInstaller.STATUS_FAILURE_ABORTED, message = "User cancelled")
        )
        assertEquals(
            InstallFailureReason.STORAGE_FULL,
            resolveInstallFailureReason(status = PackageInstaller.STATUS_FAILURE_STORAGE, message = "Insufficient storage")
        )
        assertEquals(
            InstallFailureReason.GENERIC,
            resolveInstallFailureReason(status = PackageInstaller.STATUS_FAILURE, message = "Unknown error")
        )
    }

    @Test
    fun pruneSignatureMismatchedCandidateRemovesSingleSourceOrPromotesCompatibleAlternative() {
        val githubMastodon = AppUpdate(
            name = "Mastodon",
            packageName = "org.joinmastodon.android",
            version = "2.13.7",
            oldVersion = "2.11.4",
            versionCode = 161L,
            oldVersionCode = 149L,
            source = GitHubSource,
            link = Link.Url("https://github.com/mastodon/mastodon-android/releases/download/v2.13.7/mastodon-githubRelease.apk")
        )

        // Single-source incompatible update is completely pruned
        val prunedSingle = pruneSignatureMismatchedCandidate(listOf(githubMastodon), githubMastodon.id)
        assertTrue(prunedSingle.isEmpty())

        // Multi-source update promotes PlaySource when GitHubSource fails with signature mismatch
        val playMastodon = AppUpdate(
            name = "Mastodon",
            packageName = "org.joinmastodon.android",
            version = "2.13.7",
            oldVersion = "2.11.4",
            versionCode = 161L,
            oldVersionCode = 149L,
            source = PlaySource,
            link = Link.Play { emptyList() }
        )
        val combinedPrimary = githubMastodon.copy(
            isInstalling = true,
            alternativeUpdates = listOf(githubMastodon, playMastodon)
        )

        val prunedMulti = pruneSignatureMismatchedCandidate(listOf(combinedPrimary), githubMastodon.id)
        assertEquals(1, prunedMulti.size)
        assertEquals(PlaySource, prunedMulti.single().source)
        assertFalse(prunedMulti.single().isInstalling)
        assertTrue(prunedMulti.single().alternativeUpdates.isEmpty())
    }

    @Test
    fun hasPotentialSignatureConflictWarnsOnPlayStoreToGitHubOrGitLab() {
        assertTrue(hasPotentialSignatureConflict(InstallOrigin.PLAY_STORE, GitHubSource))
        assertTrue(hasPotentialSignatureConflict(InstallOrigin.PLAY_STORE, GitLabSource))
        assertFalse(hasPotentialSignatureConflict(InstallOrigin.PLAY_STORE, PlaySource))
        assertFalse(hasPotentialSignatureConflict(InstallOrigin.FDROID, IzzyOnDroidSource))
    }

    @Test
    fun areSignerSetsCompatibleEnforcesDirectionalV3LineageAndBlocksKeyRollback() {
        // Installed on rotated K2 (history has K1, K2); candidate signed by retired K1 -> MUST fail (no rollback!)
        val rollbackAttempt = ApkSignatureVerifier.areSignerSetsCompatible(
            installedCurrentSigners = setOf(rotatedNewSha256),
            installedHistorySigners = setOf(mastodonGitHubSha256, rotatedNewSha256),
            archiveCurrentSigners = setOf(mastodonGitHubSha256),
            archiveHistorySigners = setOf(mastodonGitHubSha256)
        )
        assertTrue(rollbackAttempt is ApkSignatureCheckResult.Mismatch)

        // Installed on rotated K2 (history has K1, K2); candidate rotated K1 -> K3 (K2 not in archive history) -> MUST fail
        val branchedRotation = ApkSignatureVerifier.areSignerSetsCompatible(
            installedCurrentSigners = setOf(rotatedNewSha256),
            installedHistorySigners = setOf(mastodonGitHubSha256, rotatedNewSha256),
            archiveCurrentSigners = setOf(mastodonPlaySha256),
            archiveHistorySigners = setOf(mastodonGitHubSha256, mastodonPlaySha256)
        )
        assertTrue(branchedRotation is ApkSignatureCheckResult.Mismatch)

        // Installed on K2, candidate also signed by K2 -> Compatible
        assertEquals(
            ApkSignatureCheckResult.Compatible,
            ApkSignatureVerifier.areSignerSetsCompatible(
                installedCurrentSigners = setOf(rotatedNewSha256),
                installedHistorySigners = setOf(mastodonGitHubSha256, rotatedNewSha256),
                archiveCurrentSigners = setOf(rotatedNewSha256),
                archiveHistorySigners = setOf(mastodonGitHubSha256, rotatedNewSha256)
            )
        )
    }

    @Test
    fun verifyParsedSigningInfosEnforcesConsistentPackageAndSignersAcrossBaseAndSplitApks() {
        val installed = ParsedPackageSigningInfo(
            packageName = "org.example.app",
            currentSigners = setOf(mastodonGitHubSha256),
            historySigners = emptySet()
        )
        val validBase = ParsedPackageSigningInfo(
            packageName = "org.example.app",
            currentSigners = setOf(mastodonGitHubSha256),
            historySigners = emptySet()
        )
        val validSplit = ParsedPackageSigningInfo(
            packageName = "org.example.app",
            currentSigners = setOf(mastodonGitHubSha256),
            historySigners = emptySet()
        )
        assertEquals(
            ApkSignatureCheckResult.Compatible,
            ApkSignatureVerifier.verifyParsedSigningInfos(
                packageName = "org.example.app",
                installed = installed,
                archives = listOf(validBase, validSplit)
            )
        )

        val wrongPkgSplit = validSplit.copy(packageName = "org.evil.other")
        assertTrue(
            ApkSignatureVerifier.verifyParsedSigningInfos(
                packageName = "org.example.app",
                installed = installed,
                archives = listOf(validBase, wrongPkgSplit)
            ) is ApkSignatureCheckResult.Mismatch
        )

        val wrongSignerSplit = validSplit.copy(currentSigners = setOf(mastodonPlaySha256))
        assertTrue(
            ApkSignatureVerifier.verifyParsedSigningInfos(
                packageName = "org.example.app",
                installed = installed,
                archives = listOf(validBase, wrongSignerSplit)
            ) is ApkSignatureCheckResult.Mismatch
        )

        // Even when not installed yet, inconsistent split signer vs base must be rejected
        assertTrue(
            ApkSignatureVerifier.verifyParsedSigningInfos(
                packageName = "org.example.app",
                installed = null,
                archives = listOf(validBase, wrongSignerSplit)
            ) is ApkSignatureCheckResult.Mismatch
        )
    }
}
