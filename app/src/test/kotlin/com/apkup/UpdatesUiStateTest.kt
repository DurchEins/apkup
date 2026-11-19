package com.apkup

import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.UpdatesUiState
import com.apkup.data.ui.toUpdatesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.apkup.repository.UpdateCheckProgressTracker
import com.apkup.repository.visibleCheckedApps
import com.apkup.ui.screen.shouldShowDefaultPullToRefreshIndicator
import com.apkup.data.ui.Screen
import com.apkup.data.ui.updateCheckLoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class UpdatesUiStateTest {

    private val update = AppUpdate(
        name = "Sample",
        packageName = "example.sample",
        version = "2",
        oldVersion = "1",
        versionCode = 2,
        oldVersionCode = 1,
        source = PlaySource
    )

    @Test
    fun successfulCheckWithUpdatesShowsSuccessAndKeepsUpdates() {
        val state = UpdateCheckResult(listOf(update), 1, 0).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(listOf(update), hasSourceErrors = false), state)
    }

    @Test
    fun partialUpdatesStayVisibleWhileAnotherSourceIsChecking() {
        val state = UpdateCheckResult(
            updates = listOf(update),
            successfulRequests = 1,
            failedRequests = 0,
            isChecking = true
        ).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(listOf(update), hasSourceErrors = false, isChecking = true), state)
    }

    @Test
    fun emptyPartialResultKeepsProgressVisibleUntilEverySourceCompletes() {
        val state = UpdateCheckResult<AppUpdate>(
            updates = emptyList(),
            successfulRequests = 0,
            failedRequests = 1,
            isChecking = true,
            checkedApps = 7,
            totalApps = 20
        ).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(emptyList(), hasSourceErrors = true, isChecking = true, checkedApps = 7, totalApps = 20), state)
    }

    @Test
    fun anInFlightSourceFailureDoesNotEndTheOverallCheck() {
        val state = UpdateCheckResult<AppUpdate>(
            updates = emptyList(),
            successfulRequests = 0,
            failedRequests = 1,
            isChecking = true,
            checkedApps = 0,
            totalApps = 3
        ).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(emptyList(), hasSourceErrors = true, isChecking = true, checkedApps = 0, totalApps = 3), state)
    }

    @Test
    fun finalResultHidesProgressAfterAllSourcesComplete() {
        val state = UpdateCheckResult(listOf(update), 1, 0, isChecking = false).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(listOf(update), hasSourceErrors = false, isChecking = false), state)
    }

    @Test
    fun buttonAndPullRefreshUseTheSameLoadingState() {
        // The button and MainViewModel pull handler both call UpdatesViewModel.beginRefresh().
        val buttonRefreshState = updateCheckLoadingState()
        val pullRefreshState = updateCheckLoadingState()

        assertSame(buttonRefreshState, pullRefreshState)
        assertSame(UpdatesUiState.Loading, buttonRefreshState)
    }

    @Test
    fun updateScreenUsesItsHorizontalIndicatorInsteadOfDefaultPullSpinner() {
        assertEquals(false, shouldShowDefaultPullToRefreshIndicator(Screen.Updates.route))
        assertEquals(true, shouldShowDefaultPullToRefreshIndicator(Screen.Apps.route))
        assertEquals(true, shouldShowDefaultPullToRefreshIndicator(null))
    }

    @Test
    fun checkedAppProgressWaitsForEveryEnabledSourceAndDoesNotDoubleCountBatches() {
        val tracker = UpdateCheckProgressTracker(
            packageNames = listOf("a", "b", "c", "d"),
            sourceNames = listOf("APKMirror", "Google Play")
        )

        assertEquals(0, tracker.markBatchSucceeded("APKMirror", listOf("a", "b")))
        assertEquals(0, tracker.markBatchSucceeded("APKMirror", listOf("b", "c")))
        assertEquals(2, tracker.markBatchSucceeded("Google Play", listOf("a", "b")))
        assertEquals(3, tracker.markBatchSucceeded("Google Play", listOf("c", "d")))
        assertEquals(4, tracker.markBatchSucceeded("APKMirror", listOf("c", "d")))
        assertEquals(4, tracker.checkedApps())
        assertEquals(4, tracker.totalApps)
    }

    @Test
    fun failedOrUnknownBatchDoesNotCountAsChecked() {
        val tracker = UpdateCheckProgressTracker(listOf("a", "b"), listOf("APKMirror", "Google Play"))

        tracker.markBatchSucceeded("APKMirror", listOf("a", "b"))
        tracker.markBatchSucceeded("Google Play", listOf("a", "unknown"))

        assertEquals(1, tracker.checkedApps())
    }

    @Test
    fun earlyFinishedOrFailedSourceDoesNotFreezeRemainingSourceProgress() {
        val tracker = UpdateCheckProgressTracker(
            packageNames = listOf("a", "b", "c", "d"),
            sourceNames = listOf("APKMirror", "Google Play")
        )

        // Google Play fails early (e.g. HTTP 502 during auth) before completing any batch.
        assertEquals(0, tracker.markSourceFinished("Google Play"))

        // Subsequent APKMirror batches must advance checkedApps instead of staying stuck at 0.
        assertEquals(2, tracker.markBatchSucceeded("APKMirror", listOf("a", "b")))
        assertEquals(4, tracker.markBatchSucceeded("APKMirror", listOf("c", "d")))
        assertEquals(4, tracker.markSourceFinished("APKMirror"))
    }

    @Test
    fun parallelSourceBatchesAreCountedOncePerApp() = runBlocking {
        val packages = (1..100).map { "package.$it" }
        val tracker = UpdateCheckProgressTracker(packages, listOf("APKMirror", "Google Play"))

        listOf("APKMirror", "Google Play").map { source ->
            async(Dispatchers.Default) {
                packages.chunked(10).forEach { batch -> tracker.markBatchSucceeded(source, batch) }
                tracker.markBatchSucceeded(source, packages.take(10))
            }
        }.awaitAll()

        assertEquals(100, tracker.checkedApps())
        assertEquals(100, tracker.totalApps)
    }

    @Test
    fun progressCannotReachOneHundredPercentUntilCheckCompletes() {
        assertEquals(0, visibleCheckedApps(1, 1, isChecking = true))
        assertEquals(29, visibleCheckedApps(30, 30, isChecking = true))
        assertEquals(30, visibleCheckedApps(30, 30, isChecking = false))
        assertEquals(null, visibleCheckedApps(0, 0, isChecking = true))
        assertEquals(9, visibleCheckedApps(100, 10, isChecking = true))
    }

    @Test
    fun successfulEmptyCheckIsDifferentFromFailure() {
        val state = UpdateCheckResult<AppUpdate>(emptyList(), 2, 0).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(emptyList(), hasSourceErrors = false), state)
    }

    @Test
    fun partialCheckKeepsResultsAndMarksSourceWarning() {
        val state = UpdateCheckResult(listOf(update), 1, 1).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(listOf(update), hasSourceErrors = true), state)
    }

    @Test
    fun partialCheckWithoutUpdatesDoesNotClaimEverythingIsUpToDate() {
        val state = UpdateCheckResult<AppUpdate>(emptyList(), 1, 1).toUpdatesUiState()

        assertEquals(UpdatesUiState.Success(emptyList(), hasSourceErrors = true), state)
        assertTrue((state as UpdatesUiState.Success).hasSourceErrors)
    }

    @Test
    fun completeFailureShowsError() {
        assertSame(UpdatesUiState.Error, UpdateCheckResult<AppUpdate>(emptyList(), 0, 2).toUpdatesUiState())
    }

    @Test
    fun sourceErrorBannerExplicitlyNamesFailedAndSurvivingSources() {
        val playFailedBanner = com.apkup.ui.screen.formatSourceErrorBanner(
            sourceErrors = mapOf("Google Play" to "authentication: HTTP 502"),
            activeSources = listOf("APKMirror", "Google Play"),
            isGerman = true
        )
        assertEquals(
            "Google Play-Prüfung fehlgeschlagen (Auth-Fehler / HTTP 502). APKMirror-Ergebnisse werden angezeigt.",
            playFailedBanner
        )

        val mirrorFailedBanner = com.apkup.ui.screen.formatSourceErrorBanner(
            sourceErrors = mapOf("APKMirror" to "read timeout"),
            activeSources = listOf("APKMirror", "Google Play"),
            isGerman = true
        )
        assertEquals(
            "APKMirror-Prüfung fehlgeschlagen (Netzwerk-Timeout). Google Play-Ergebnisse werden angezeigt.",
            mirrorFailedBanner
        )
    }

    @Test
    fun refreshWithExistingUpdatesKeepsCardsVisibleInsteadOfEmptyingScreen() {
        val existing = UpdatesUiState.Success(
            updates = listOf(update),
            hasSourceErrors = false,
            isChecking = false,
            checkedApps = 10,
            totalApps = 10
        )

        val refreshingState = updateCheckLoadingState(existing)
        assertTrue(refreshingState is UpdatesUiState.Success)
        val success = refreshingState as UpdatesUiState.Success
        assertEquals(listOf(update), success.updates)
        assertTrue(success.isChecking)
    }

    @Test
    fun mergeRetainedUpdatesDuringCheckPreservesExistingUpdatesUntilCheckFinishes() {
        val cachedFirst = update
        val newlyDiscovered = update.copy(name = "Beta", packageName = "example.beta", id = 99)

        val whileCheckingEmpty = com.apkup.data.ui.mergeRetainedUpdatesDuringCheck(
            incomingUpdates = emptyList(),
            retainedUpdates = listOf(cachedFirst),
            isChecking = true
        )
        assertEquals(listOf(cachedFirst), whileCheckingEmpty)

        val whileCheckingPartial = com.apkup.data.ui.mergeRetainedUpdatesDuringCheck(
            incomingUpdates = listOf(newlyDiscovered),
            retainedUpdates = listOf(cachedFirst),
            isChecking = true
        )
        assertEquals(listOf(newlyDiscovered, cachedFirst), whileCheckingPartial)

        val whenFinished = com.apkup.data.ui.mergeRetainedUpdatesDuringCheck(
            incomingUpdates = listOf(newlyDiscovered),
            retainedUpdates = listOf(cachedFirst),
            isChecking = false
        )
        assertEquals(listOf(newlyDiscovered), whenFinished)
    }

    @Test
    fun resolveWikiUrlForSourceErrorDeepLinksToTroubleshootingSectionByReason() {
        val dnsUrl = com.apkup.ui.screen.resolveWikiUrlForSourceError("IzzyOnDroid", "UnknownHostException: dns lookup failed")
        assertTrue(dnsUrl.endsWith("#dns-resolution-failed"))

        val timeoutUrl = com.apkup.ui.screen.resolveWikiUrlForSourceError("APKMirror", "read timeout")
        assertTrue(timeoutUrl.endsWith("#connection-timeout"))

        val playAuthUrl = com.apkup.ui.screen.resolveWikiUrlForSourceError("Google Play", "authentication: HTTP 429")
        assertTrue(playAuthUrl.endsWith("#play-store-rate-limit"))

        val gitlabAuthUrl = com.apkup.ui.screen.resolveWikiUrlForSourceError("GitLab", "HTTP 401 Unauthorized")
        assertTrue(gitlabAuthUrl.endsWith("#gitlab-auth-error"))

        val serverErrorUrl = com.apkup.ui.screen.resolveWikiUrlForSourceError("IzzyOnDroid", "HTTP 502 Bad Gateway")
        assertTrue(serverErrorUrl.endsWith("#server-errors"))
    }

    @Test
    fun filterAndSortUpdatesSupportsSearchAndSortOrders() {
        val alpha = AppUpdate(
            name = "Zebra App",
            packageName = "org.example.zebra",
            version = "2.0",
            oldVersion = "1.0",
            versionCode = 2,
            oldVersionCode = 1,
            source = PlaySource,
            updatedOn = "10. Nov. 2026"
        )
        val beta = AppUpdate(
            name = "Alpha Reader",
            packageName = "io.github.reader",
            version = "3.0",
            oldVersion = "2.0",
            versionCode = 3,
            oldVersionCode = 2,
            source = com.apkup.data.ui.GitHubSource,
            updatedOn = "2026-11-13T18:00:00Z"
        )
        val gamma = AppUpdate(
            name = "Mid Tool",
            packageName = "org.apkmirror.tool",
            version = "1.5",
            oldVersion = "1.0",
            versionCode = 15,
            oldVersionCode = 10,
            source = com.apkup.data.ui.ApkMirrorSource,
            updatedOn = "Nov 12, 2026"
        )
        val all = listOf(alpha, beta, gamma)

        assertEquals(
            listOf("Alpha Reader", "Mid Tool", "Zebra App"),
            com.apkup.ui.screen.filterAndSortUpdates(all, null, "", com.apkup.ui.screen.UpdatesSortOrder.NAME_ASC).map { it.name }
        )
        assertEquals(
            listOf("Zebra App", "Mid Tool", "Alpha Reader"),
            com.apkup.ui.screen.filterAndSortUpdates(all, null, "", com.apkup.ui.screen.UpdatesSortOrder.NAME_DESC).map { it.name }
        )
        assertEquals(
            listOf("Mid Tool", "Alpha Reader", "Zebra App"),
            com.apkup.ui.screen.filterAndSortUpdates(all, null, "", com.apkup.ui.screen.UpdatesSortOrder.SOURCE).map { it.name }
        )
        assertEquals(
            listOf("Alpha Reader", "Mid Tool", "Zebra App"),
            com.apkup.ui.screen.filterAndSortUpdates(all, null, "", com.apkup.ui.screen.UpdatesSortOrder.RELEASE_DATE).map { it.name }
        )
        assertEquals(
            listOf("Zebra App"),
            com.apkup.ui.screen.filterAndSortUpdates(all, null, "zebra", com.apkup.ui.screen.UpdatesSortOrder.NAME_ASC).map { it.name }
        )
        assertEquals(
            listOf("Mid Tool"),
            com.apkup.ui.screen.filterAndSortUpdates(all, null, "apkmirror.tool", com.apkup.ui.screen.UpdatesSortOrder.NAME_ASC).map { it.name }
        )
        assertEquals(
            listOf("Zebra App"),
            com.apkup.ui.screen.filterAndSortUpdates(all, PlaySource.name, "", com.apkup.ui.screen.UpdatesSortOrder.NAME_DESC).map { it.name }
        )
        val betaWithPlayAlt = beta.copy(
            alternativeUpdates = listOf(beta.copy(source = PlaySource, updatedOn = "2026-11-11"))
        )
        assertEquals(
            listOf("Zebra App", "Alpha Reader"),
            com.apkup.ui.screen.filterAndSortUpdates(
                listOf(alpha, betaWithPlayAlt, gamma),
                PlaySource.name,
                "",
                com.apkup.ui.screen.UpdatesSortOrder.NAME_DESC
            ).map { it.name }
        )
        assertEquals(
            listOf("Alpha Reader"),
            com.apkup.ui.screen.filterAndSortUpdates(
                listOf(alpha, betaWithPlayAlt, gamma),
                PlaySource.name,
                "alpha",
                com.apkup.ui.screen.UpdatesSortOrder.RELEASE_DATE
            ).map { it.name }
        )
    }

    @Test
    fun `filterAndSortInstalledApps supports search quick filter and all sort orders`() {
        val playNew = com.apkup.data.ui.AppInstalled(
            name = "Alpha Notes",
            packageName = "com.example.alpha",
            version = "2.0",
            versionCode = 20,
            lastUpdateTime = 3_000L,
            installerPackageName = "com.android.vending"
        )
        val sideloadOld = com.apkup.data.ui.AppInstalled(
            name = "Zebra Utility",
            packageName = "org.example.zebra",
            version = "1.0",
            versionCode = 10,
            lastUpdateTime = 1_000L,
            installerPackageName = "com.apkup"
        )
        val fdroidMid = com.apkup.data.ui.AppInstalled(
            name = "Beta Reader",
            packageName = "org.fdroid.beta",
            version = "1.5",
            versionCode = 15,
            lastUpdateTime = 2_000L,
            installerPackageName = "org.fdroid.fdroid"
        )
        val apps = listOf(sideloadOld, playNew, fdroidMid)

        assertEquals(
            listOf("Alpha Notes", "Beta Reader", "Zebra Utility"),
            com.apkup.data.ui.filterAndSortInstalledApps(
                apps, "", com.apkup.data.ui.AppsQuickFilter.ALL, com.apkup.data.ui.AppsSortOrder.NAME_ASC
            ).map { it.name }
        )
        assertEquals(
            listOf("Zebra Utility", "Beta Reader", "Alpha Notes"),
            com.apkup.data.ui.filterAndSortInstalledApps(
                apps, "", com.apkup.data.ui.AppsQuickFilter.ALL, com.apkup.data.ui.AppsSortOrder.NAME_DESC
            ).map { it.name }
        )
        assertEquals(
            listOf("Alpha Notes", "Beta Reader", "Zebra Utility"),
            com.apkup.data.ui.filterAndSortInstalledApps(
                apps, "", com.apkup.data.ui.AppsQuickFilter.ALL, com.apkup.data.ui.AppsSortOrder.SOURCE
            ).map { it.name }
        )
        assertEquals(
            listOf("Alpha Notes", "Beta Reader", "Zebra Utility"),
            com.apkup.data.ui.filterAndSortInstalledApps(
                apps, "", com.apkup.data.ui.AppsQuickFilter.ALL, com.apkup.data.ui.AppsSortOrder.LAST_UPDATED
            ).map { it.name }
        )
        assertEquals(
            listOf("Beta Reader"),
            com.apkup.data.ui.filterAndSortInstalledApps(
                apps, "fdroid.beta", com.apkup.data.ui.AppsQuickFilter.ALL, com.apkup.data.ui.AppsSortOrder.NAME_DESC
            ).map { it.name }
        )
    }

    @Test
    fun `source error banner excludes failed F-Droid repos from surviving sources`() {
        val bannerDe = com.apkup.ui.screen.formatSourceErrorBanner(
            sourceErrors = mapOf("F-Droid / IzzyOnDroid" to "Network error"),
            activeSources = listOf("APKMirror", "Google Play", "IzzyOnDroid", "NewPipe Repo", "My Custom Repo", "GitHub"),
            isGerman = true,
            fdroidRepoNames = setOf("IzzyOnDroid", "NewPipe Repo", "My Custom Repo")
        )
        assertTrue(bannerDe.contains("F-Droid / IzzyOnDroid-Prüfung fehlgeschlagen"))
        assertTrue(bannerDe.contains("APKMirror & Google Play & GitHub-Ergebnisse werden angezeigt."))
        assertTrue(!bannerDe.contains("IzzyOnDroid-Ergebnisse"))
        assertTrue(!bannerDe.contains("NewPipe Repo"))
        assertTrue(!bannerDe.contains("My Custom Repo"))

        val partialEmpty = com.apkup.ui.screen.formatPartialEmptyMessage(
            sourceErrors = mapOf("F-Droid / IzzyOnDroid" to "Network error"),
            activeSources = listOf("IzzyOnDroid", "F-Droid"),
            isGerman = true
        )
        assertTrue(partialEmpty.contains("Keine erreichbare Quelle hat Updates gemeldet."))
    }
}
