package com.apkup

import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.AppsQuickFilter
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.Source
import com.apkup.data.ui.cleanChangelogText
import com.apkup.data.ui.combineSourceResults
import com.apkup.data.ui.filterInstalledApps
import com.apkup.data.ui.filterNewerCandidates
import com.apkup.data.ui.hasPotentialSignatureConflict
import com.apkup.data.ui.isCompatibleFormFactor
import com.apkup.data.ui.isVerifiableVersionUpgrade
import com.apkup.data.ui.matchesSignature
import com.apkup.data.ui.resolveInstallOrigin
import com.apkup.data.ui.supportsArchitecture
import com.apkup.data.ui.supportsMinApi
import com.apkup.data.ui.uniqueUpdatesByPackage
import com.apkup.repository.captureUpdateSourceCheck
import com.apkup.repository.enrichWithSourceAlternatives
import com.apkup.repository.requestSequentialBatches
import com.apkup.repository.requestConcurrentBatches
import com.apkup.repository.APKMIRROR_REQUEST_BATCH_SIZE
import com.apkup.repository.APKMIRROR_REQUEST_CONCURRENCY
import com.apkup.repository.describeApkMirrorFailure
import com.apkup.repository.PLAY_DETAILS_BATCH_SIZE
import com.apkup.repository.PLAY_DETAILS_REQUEST_CONCURRENCY
import com.apkup.repository.awaitNextCompleted
import com.apkup.viewmodel.partitionInstallQueueCandidates
import com.apkup.worker.filterNotifiableUpdates
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

class UpdateProcessingTest {

    @Test
    fun completedSourceCanBeConsumedBeforeAnUnfinishedSource() = runBlocking {
        val slowGate = CompletableDeferred<String>()
        val slow = async { slowGate.await() }
        val fast = async { "fast" }

        val (completed, result) = awaitNextCompleted(listOf(slow, fast))

        assertSame(fast, completed)
        assertEquals("fast", result)
        slowGate.complete("slow")
        assertEquals("slow", slow.await())
    }

    @Test
    fun emptySuccessfulResponseIsDifferentFromRequestFailure() = runBlocking {
        val result = requestSequentialBatches<String, String>(
            items = listOf("installed.app"),
            batchSize = 100,
            request = { emptyList() }
        )

        assertTrue(result.responses.isEmpty())
        assertEquals(1, result.successfulRequests)
        assertEquals(0, result.failedRequests)
    }

    @Test
    fun emptyConcurrentBatchResponseIsCountedAsSuccessful() = runBlocking {
        val result = requestConcurrentBatches<String, String>(
            items = listOf("installed.app"),
            batchSize = PLAY_DETAILS_BATCH_SIZE,
            concurrency = PLAY_DETAILS_REQUEST_CONCURRENCY,
            request = { emptyList() }
        )

        assertTrue(result.responses.isEmpty())
        assertEquals(1, result.successfulRequests)
        assertEquals(0, result.failedRequests)
    }

    @Test
    fun requestsAreSplitIntoSequentialGroupsAndSuccessfulGroupsSurviveFailure() = runBlocking {
        val starts = mutableListOf<Int>()
        val result = requestSequentialBatches<Int, Int>(
            items = (0 until 205).toList(),
            batchSize = 100,
            request = { batch ->
                starts.add(batch.first())
                if (batch.first() == 0) throw SocketTimeoutException("simulated timeout")
                listOf(batch.first())
            }
        )

        assertEquals(listOf(0, 100, 200), starts)
        assertEquals(listOf(100, 200), result.responses)
        assertEquals(2, result.successfulRequests)
        assertEquals(1, result.failedRequests)
    }

    @Test
    fun apkMirrorUsesTwentyFiveIdBatchesAndKeepsSuccessesAroundATimeout() = runBlocking {
        assertEquals(25, APKMIRROR_REQUEST_BATCH_SIZE)
        val batchSizes = CopyOnWriteArrayList<Int>()
        val result = requestConcurrentBatches<Int, Int>(
            items = (0 until 462).toList(),
            batchSize = APKMIRROR_REQUEST_BATCH_SIZE,
            concurrency = APKMIRROR_REQUEST_CONCURRENCY,
            request = { batch ->
                batchSizes.add(batch.size)
                if (batch.first() == 0) throw SocketTimeoutException("simulated timeout")
                delay(5)
                listOf(batch.first())
            }
        )

        assertEquals(19, batchSizes.size)
        assertTrue(batchSizes.all { it in 1..APKMIRROR_REQUEST_BATCH_SIZE })
        assertEquals(12, batchSizes.minOrNull())
        assertEquals(18, result.responses.size)
        assertEquals(18, result.successfulRequests)
        assertEquals(1, result.failedRequests)
    }

    @Test
    fun apkMirrorKeepsAtMostTwoRequestsAtOnce() = runBlocking {
        assertEquals(25, APKMIRROR_REQUEST_BATCH_SIZE)
        assertEquals(8, APKMIRROR_REQUEST_CONCURRENCY)
        assertEquals(19, (462 + APKMIRROR_REQUEST_BATCH_SIZE - 1) / APKMIRROR_REQUEST_BATCH_SIZE)

        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val result = requestConcurrentBatches<Int, Int>(
            items = (0 until 12).toList(),
            batchSize = 1,
            concurrency = APKMIRROR_REQUEST_CONCURRENCY,
            request = { batch ->
                val nowActive = active.incrementAndGet()
                maximumActive.updateAndGet { previous -> maxOf(previous, nowActive) }
                delay(25)
                active.decrementAndGet()
                batch
            }
        )

        assertEquals(12, result.successfulRequests)
        assertEquals(0, result.failedRequests)
        assertEquals(APKMIRROR_REQUEST_CONCURRENCY, maximumActive.get())
    }

    @Test
    fun concurrentBatchProgressReportsOnlySuccessfulBatchesOnce() = runBlocking {
        val completedBatches = CopyOnWriteArrayList<List<Int>>()
        val result = requestConcurrentBatches(
            items = (1..6).toList(),
            batchSize = 2,
            concurrency = 2,
            request = { batch ->
                if (batch.first() == 3) throw IOException("simulated batch failure")
                batch
            },
            onBatchSuccess = { batch -> completedBatches += batch.toList() }
        )

        assertEquals(2, result.successfulRequests)
        assertEquals(1, result.failedRequests)
        assertEquals(listOf(listOf(1, 2), listOf(5, 6)), completedBatches.sortedBy { it.first() })
        assertFalse(completedBatches.flatten().contains(3))
        assertFalse(completedBatches.flatten().contains(4))
    }

    @Test
    fun apkMirrorTimeoutLogDistinguishesConnectAndReadWhenClientReportsThePhase() {
        assertEquals("connect timeout", describeApkMirrorFailure(SocketTimeoutException("connect timed out")))
        assertEquals("read timeout", describeApkMirrorFailure(SocketTimeoutException("read timed out")))
        assertEquals(
            "socket timeout (phase not reported by HTTP client)",
            describeApkMirrorFailure(SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun unknownHostStopsSchedulingMoreBatchesButKeepsInFlightSuccesses() = runBlocking {
        val attempted = AtomicInteger(0)
        val result = requestConcurrentBatches<Int, Int>(
            items = (0 until 12).toList(),
            batchSize = 1,
            concurrency = 2,
            request = { batch ->
                attempted.incrementAndGet()
                if (batch.single() == 0) throw UnknownHostException()
                delay(30)
                batch
            },
            stopSchedulingAfterFailure = { it is UnknownHostException }
        )

        assertTrue(attempted.get() <= 2)
        assertEquals(1, result.failedRequests)
        assertTrue(result.successfulRequests <= 1)
    }

    @Test
    fun playLookupsUseBoundedBatches() {
        assertEquals(20, PLAY_DETAILS_BATCH_SIZE)
        assertEquals(2, PLAY_DETAILS_REQUEST_CONCURRENCY)
    }

    @Test
    fun timeoutIsReportedAsSourceFailureRatherThanSuccessfulEmptyResult() = runBlocking {
        val request = requestSequentialBatches<String, String>(
            items = listOf("installed.app"),
            batchSize = APKMIRROR_REQUEST_BATCH_SIZE,
            request = { throw SocketTimeoutException("simulated timeout") }
        )
        val result = UpdateCheckResult(
            updates = request.responses,
            successfulRequests = request.successfulRequests,
            failedRequests = request.failedRequests
        )

        assertTrue(result.updates.isEmpty())
        assertTrue(result.hasFailures)
        assertTrue(result.isCompleteFailure)
    }

    @Test
    fun timeoutNetworkHttpAndMalformedResponseFailuresAreCountedWithoutAbortingLaterGroups() = runBlocking {
        val failures: List<Exception> = listOf(
            SocketTimeoutException("timeout"),
            IOException("offline"),
            HttpException(Response.error<Unit>(500, ByteArray(0).toResponseBody(null))),
            JsonSyntaxException("invalid response")
        )
        val attempted = mutableListOf<Int>()
        val result = requestSequentialBatches<Int, String>(
            items = failures.indices.toList(),
            batchSize = 1,
            request = { batch ->
                val item = batch.single()
                attempted.add(item)
                throw failures[item]
            }
        )

        assertEquals(failures.indices.toList(), attempted)
        assertTrue(result.responses.isEmpty())
        assertEquals(0, result.successfulRequests)
        assertEquals(4, result.failedRequests)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedToAnEmptyResponse() {
        runBlocking {
            requestSequentialBatches<Int, String>(
                items = listOf(1),
                batchSize = 1,
                request = { throw CancellationException("cancelled") }
            )
        }
    }

    @Test
    fun onlyStrictlyHigherVersionCodesAreUpdatesAndUnknownPackagesAreIgnored() {
        val installedVersions = mapOf("new" to 50L, "same" to 50L, "old" to 50L)
        val candidates = listOf(
            VersionCandidate("new", 51),
            VersionCandidate("same", 50),
            VersionCandidate("old", 49),
            VersionCandidate("not-installed", 90)
        )

        val updates = filterNewerCandidates(
            candidates,
            installedVersions,
            VersionCandidate::packageName,
            VersionCandidate::versionCode
        )

        assertEquals(listOf(VersionCandidate("new", 51)), updates)
    }

    @Test
    fun duplicatePackageKeepsHighestVersionAndPrefersPlayForVersionTies() {
        val candidates = listOf(
            VersionCandidate("same", 20, priority = 0),
            VersionCandidate("same", 21, priority = 0),
            VersionCandidate("same", 21, priority = 1)
        )

        val updates = uniqueUpdatesByPackage(
            candidates,
            VersionCandidate::packageName,
            VersionCandidate::versionCode,
            VersionCandidate::priority
        )

        assertEquals(listOf(candidates.last()), updates)
    }

    @Test
    fun partialSourceFailureKeepsSuccessfulSourceResults() = runBlocking {
        val failedSource = captureUpdateSourceCheck<VersionCandidate>(check = {
            throw IOException("simulated source failure")
        })
        val result = combineSourceResults(
            listOf(
                SourceUpdateResult(listOf(VersionCandidate("app", 12)), successfulRequests = 1, failedRequests = 0),
                failedSource
            ),
            VersionCandidate::packageName,
            VersionCandidate::versionCode,
            VersionCandidate::priority
        )

        assertEquals(listOf(VersionCandidate("app", 12)), result.updates)
        assertEquals("IOException", failedSource.failureSummary)
        assertTrue(result.hasFailures)
        assertFalse(result.isCompleteFailure)
        assertEquals(1, result.successfulRequests)
        assertEquals(1, result.failedRequests)
    }

    @Test
    fun allSourceFailuresAreReportedAsCompleteFailure() = runBlocking {
        val failedMirror = captureUpdateSourceCheck<VersionCandidate>(check = {
            throw IOException("simulated APKMirror failure")
        })
        val failedPlay = captureUpdateSourceCheck<VersionCandidate>(check = {
            throw IOException("simulated Play failure")
        })

        val result = combineSourceResults(
            listOf(failedMirror, failedPlay),
            VersionCandidate::packageName,
            VersionCandidate::versionCode,
            VersionCandidate::priority
        )

        assertTrue(result.updates.isEmpty())
        assertEquals(0, result.successfulRequests)
        assertEquals(2, result.failedRequests)
        assertTrue(result.isCompleteFailure)
    }

    @Test
    fun apkMirrorUpdateIsKeptAlongsidePlayUpdateWithItsSource() {
        val apkMirrorUpdate = SourceCandidate("mirror.app", 12, ApkMirrorSource)
        val playUpdate = SourceCandidate("play.app", 18, PlaySource)

        val result = combineSourceResults(
            listOf(
                SourceUpdateResult(listOf(apkMirrorUpdate), successfulRequests = 1, failedRequests = 0),
                SourceUpdateResult(listOf(playUpdate), successfulRequests = 1, failedRequests = 0)
            ),
            SourceCandidate::packageName,
            SourceCandidate::versionCode,
            { if (it.source == PlaySource) 1 else 0 }
        )

        assertEquals(listOf(apkMirrorUpdate, playUpdate), result.updates)
        assertEquals(ApkMirrorSource, result.updates[0].source)
        assertEquals(PlaySource, result.updates[1].source)
        assertFalse(result.hasFailures)
    }

    @Test
    fun apkMirrorFailureDoesNotDiscardPlayUpdate() = runBlocking {
        val playUpdate = SourceCandidate("play.app", 18, PlaySource)
        val failedApkMirror = captureUpdateSourceCheck<SourceCandidate>(check = {
            throw IOException("simulated APKMirror failure")
        })

        val result = combineSourceResults(
            listOf(
                failedApkMirror,
                SourceUpdateResult(listOf(playUpdate), successfulRequests = 1, failedRequests = 0)
            ),
            SourceCandidate::packageName,
            SourceCandidate::versionCode,
            { if (it.source == PlaySource) 1 else 0 }
        )

        assertEquals(listOf(playUpdate), result.updates)
        assertEquals(PlaySource, result.updates.single().source)
        assertTrue(result.hasFailures)
        assertFalse(result.isCompleteFailure)
    }

    @Test
    fun playFailureDoesNotDiscardApkMirrorUpdate() = runBlocking {
        val apkMirrorUpdate = SourceCandidate("mirror.app", 12, ApkMirrorSource)
        val failedPlay = captureUpdateSourceCheck<SourceCandidate>(check = {
            throw IOException("simulated Google Play failure")
        })

        val result = combineSourceResults(
            listOf(
                SourceUpdateResult(listOf(apkMirrorUpdate), successfulRequests = 1, failedRequests = 0),
                failedPlay
            ),
            SourceCandidate::packageName,
            SourceCandidate::versionCode,
            { if (it.source == PlaySource) 1 else 0 }
        )

        assertEquals(listOf(apkMirrorUpdate), result.updates)
        assertEquals(ApkMirrorSource, result.updates.single().source)
        assertTrue(result.hasFailures)
        assertFalse(result.isCompleteFailure)
    }

    @Test
    fun minimumApiFilterRejectsUnsupportedAndMalformedValues() {
        assertTrue(supportsMinApi("23", 36))
        assertFalse(supportsMinApi("37", 36))
        assertFalse(supportsMinApi("unknown", 36))
    }

    @Test
    fun architectureFilterAllowsUniversalAndMatchingAbiButRejectsIncompatibleBuilds() {
        val deviceAbis = listOf("arm64-v8a", "armeabi-v7a")
        assertTrue(supportsArchitecture(emptyList(), deviceAbis, "arm"))
        assertTrue(supportsArchitecture(listOf("universal"), deviceAbis, "arm"))
        assertTrue(supportsArchitecture(listOf("arm64-v8a"), deviceAbis, "arm"))
        assertTrue(supportsArchitecture(listOf("arm"), deviceAbis, "arm"))
        assertFalse(supportsArchitecture(listOf("x86_64"), deviceAbis, "arm"))
    }

    @Test
    fun signatureAndDeviceFormFactorFiltersKeepTheirExistingRules() {
        assertTrue(matchesSignature(emptyList(), "installed-signature"))
        assertTrue(matchesSignature(listOf("installed-signature"), "installed-signature"))
        assertFalse(matchesSignature(listOf("different-signature"), "installed-signature"))

        assertTrue(isCompatibleFormFactor(null, isAndroidTv = false))
        assertFalse(isCompatibleFormFactor(listOf("leanback_standalone"), isAndroidTv = false))
        assertTrue(isCompatibleFormFactor(listOf("leanback"), isAndroidTv = true))
        assertFalse(isCompatibleFormFactor(emptyList(), isAndroidTv = true))
        assertFalse(isCompatibleFormFactor(listOf("wear_standalone"), isAndroidTv = false))
    }

    @Test
    fun falsePositiveSameVersionIsRejectedWithoutHigherVersionCode() {
        // 17 -> 17 must be rejected even if versionCode is 0, equal, or higher when version strings are identical
        assertFalse(
            isVerifiableVersionUpgrade(
                installedVersion = "17",
                availableVersion = "17",
                installedVersionCode = 36L,
                availableVersionCode = 0L
            )
        )
        assertFalse(
            isVerifiableVersionUpgrade(
                installedVersion = "17",
                availableVersion = "v17",
                installedVersionCode = 36L,
                availableVersionCode = 36L
            )
        )
        assertFalse(
            isVerifiableVersionUpgrade(
                installedVersion = "17",
                availableVersion = "17",
                installedVersionCode = 36L,
                availableVersionCode = 37L
            )
        )
        // Distinct version string with strictly higher versionCode is allowed
        assertTrue(
            isVerifiableVersionUpgrade(
                installedVersion = "1.0.0",
                availableVersion = "1.1.0",
                installedVersionCode = 100L,
                availableVersionCode = 110L
            )
        )
    }

    @Test
    fun workerNotificationFilterExcludesIgnoredVersionsAndIgnoredApps() {
        val updateKeep = AppUpdate(
            name = "Keep",
            packageName = "com.example.keep",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = PlaySource
        )
        val updateIgnoredVersion = AppUpdate(
            name = "SkipVersion",
            packageName = "com.example.skipver",
            version = "3.0",
            oldVersion = "2.0",
            versionCode = 30L,
            oldVersionCode = 20L,
            source = PlaySource
        )
        val updateIgnoredApp = AppUpdate(
            name = "IgnoredApp",
            packageName = "com.example.ignored",
            version = "4.0",
            oldVersion = "3.0",
            versionCode = 40L,
            oldVersionCode = 30L,
            source = ApkMirrorSource
        )

        val filtered = filterNotifiableUpdates(
            result = UpdateCheckResult(
                updates = listOf(updateKeep, updateIgnoredVersion, updateIgnoredApp),
                successfulRequests = 1,
                failedRequests = 0,
                isChecking = false
            ),
            ignoredVersionIds = listOf(updateIgnoredVersion.id),
            ignoredPackages = listOf("com.example.ignored")
        )

        assertEquals(listOf(updateKeep), filtered)
    }

    @Test
    fun installQueuePartitionsDirectInstallableFromBrowserOnlyUpdates() {
        val playUpdate = AppUpdate(
            name = "PlayApp",
            packageName = "com.example.play",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = PlaySource,
            link = Link.Play { emptyList() }
        )
        val mirrorWebUpdate = AppUpdate(
            name = "MirrorWeb",
            packageName = "com.example.mirror",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/example")
        )

        val (directQueue, browserSkipped) = partitionInstallQueueCandidates(
            listOf(playUpdate, mirrorWebUpdate)
        )

        assertEquals(listOf(playUpdate), directQueue)
        assertEquals(listOf(mirrorWebUpdate), browserSkipped)
    }

    @Test
    fun appsFilterSupportsSearchAndQuickFilterChips() {
        val now = 1_700_000_000_000L
        val apps = listOf(
            AppInstalled(
                name = "Signal",
                packageName = "org.thoughtcrime.securesms",
                version = "7.0",
                versionCode = 700L,
                ignored = false,
                isSystemApp = false,
                lastUpdateTime = now - 1_000L
            ),
            AppInstalled(
                name = "System UI",
                packageName = "com.android.systemui",
                version = "16",
                versionCode = 36L,
                ignored = true,
                isSystemApp = true,
                lastUpdateTime = now - 2_000_000_000L
            )
        )

        assertEquals(1, filterInstalledApps(apps, "securesms", AppsQuickFilter.ALL, now).size)
        assertEquals(1, filterInstalledApps(apps, "", AppsQuickFilter.USER_ONLY, now).size)
        assertEquals("Signal", filterInstalledApps(apps, "", AppsQuickFilter.USER_ONLY, now).first().name)
        assertEquals(1, filterInstalledApps(apps, "", AppsQuickFilter.SYSTEM_ONLY, now).size)
        assertEquals("System UI", filterInstalledApps(apps, "", AppsQuickFilter.SYSTEM_ONLY, now).first().name)
        assertEquals(1, filterInstalledApps(apps, "", AppsQuickFilter.IGNORED_ONLY, now).size)
        assertEquals(1, filterInstalledApps(apps, "", AppsQuickFilter.RECENTLY_UPDATED, now).size)
    }

    @Test
    fun installOriginAndSignatureConflictDetectionIdentifiesSideloadAndFDroid() {
        assertEquals(InstallOrigin.PLAY_STORE, resolveInstallOrigin("com.android.vending", isSystemApp = false))
        assertEquals(InstallOrigin.FDROID, resolveInstallOrigin("org.fdroid.fdroid", isSystemApp = false))
        assertEquals(InstallOrigin.SIDELOAD, resolveInstallOrigin("com.google.android.packageinstaller", isSystemApp = false))
        assertEquals(InstallOrigin.SYSTEM, resolveInstallOrigin(null, isSystemApp = true))

        assertTrue(hasPotentialSignatureConflict(InstallOrigin.FDROID, PlaySource))
        assertTrue(hasPotentialSignatureConflict(InstallOrigin.SIDELOAD, PlaySource))
        assertFalse(hasPotentialSignatureConflict(InstallOrigin.PLAY_STORE, PlaySource))
    }

    @Test
    fun multiSourceEnrichmentAttachesAlternativeSourceEntries() {
        val playCandidate = AppUpdate(
            name = "App",
            packageName = "com.example.multi",
            version = "2.1.0",
            oldVersion = "2.0.0",
            versionCode = 210L,
            oldVersionCode = 200L,
            source = PlaySource,
            whatsNew = "<ul><li>Bug fixes</li></ul>",
            link = Link.Play { emptyList() }
        )
        val mirrorCandidate = AppUpdate(
            name = "App",
            packageName = "com.example.multi",
            version = "2.1.0",
            oldVersion = "2.0.0",
            versionCode = 210L,
            oldVersionCode = 200L,
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/example")
        )

        val enriched = enrichWithSourceAlternatives(
            primaryUpdates = listOf(playCandidate),
            allCandidates = listOf(playCandidate, mirrorCandidate)
        )

        assertEquals(1, enriched.size)
        assertEquals(2, enriched.single().alternativeUpdates.size)
        assertEquals("• Bug fixes", cleanChangelogText(playCandidate.whatsNew))
    }

    @Test
    fun installedAppsGridColumnCountUsesTwoInPortraitAndResponsiveCountOnWideScreens() {
        assertEquals(2, com.apkup.ui.component.installedAppsGridColumnCount(360f))
        assertEquals(2, com.apkup.ui.component.installedAppsGridColumnCount(411f))
        assertEquals(2, com.apkup.ui.component.installedAppsGridColumnCount(599f))
        assertEquals(3, com.apkup.ui.component.installedAppsGridColumnCount(600f))
        assertEquals(3, com.apkup.ui.component.installedAppsGridColumnCount(650f))
        assertEquals(5, com.apkup.ui.component.installedAppsGridColumnCount(840f))
    }

    @Test
    fun installLogDeduplicatesTerminalStatusEmissionsUntilNextBeginInstall() = runBlocking {
        val log = com.apkup.util.InstallLog()
        val emitted = CopyOnWriteArrayList<com.apkup.data.ui.AppInstallStatus>()
        val job = async(kotlinx.coroutines.Dispatchers.Unconfined) {
            log.status().collect {
                emitted += it
            }
        }
        log.beginInstall(42)
        log.emitStatus(com.apkup.data.ui.AppInstallStatus(success = false, id = 42, snack = true))
        log.emitStatus(com.apkup.data.ui.AppInstallStatus(success = false, id = 42, snack = true))
        assertEquals(1, emitted.size)

        // Starting a fresh attempt for the same id allows emitting again
        log.beginInstall(42)
        log.emitStatus(com.apkup.data.ui.AppInstallStatus(success = true, id = 42, snack = true))
        job.cancel()
        assertEquals(2, emitted.size)
        assertFalse(emitted[0].success)
        assertTrue(emitted[1].success)
    }

    @Test
    fun shizukuSetupStatusRequiresInstalledBinderAndPermission() {
        assertFalse(com.apkup.data.ui.ShizukuSetupStatus(false, false, false).isReady)
        assertFalse(com.apkup.data.ui.ShizukuSetupStatus(true, false, false).isReady)
        assertFalse(com.apkup.data.ui.ShizukuSetupStatus(true, true, false).isReady)
        assertTrue(com.apkup.data.ui.ShizukuSetupStatus(true, true, true).isReady)
    }

    @Test
    fun installQueuePromotesDirectInstallableAlternativeWhenPrimaryIsBrowserOnly() {
        val playAlt = AppUpdate(
            name = "PlayApp",
            packageName = "com.example.hybrid",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = PlaySource,
            link = Link.Play { emptyList() }
        )
        val mirrorPrimary = AppUpdate(
            name = "MirrorApp",
            packageName = "com.example.hybrid",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 20L,
            oldVersionCode = 10L,
            source = ApkMirrorSource,
            link = Link.Url("https://www.apkmirror.com/apk/example"),
            alternativeUpdates = listOf(playAlt)
        )

        val (directQueue, browserSkipped) = partitionInstallQueueCandidates(
            updates = listOf(mirrorPrimary),
            directApkMirrorEnabled = false
        )

        assertEquals(listOf(playAlt), directQueue)
        assertTrue(browserSkipped.isEmpty())
    }

    private data class VersionCandidate(
        val packageName: String,
        val versionCode: Long,
        val priority: Int = 0
    )

    private data class SourceCandidate(
        val packageName: String,
        val versionCode: Long,
        val source: Source
    )
}


