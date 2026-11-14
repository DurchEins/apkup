package com.apkup.repository

import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CachedUpdateSnapshot
import com.apkup.data.ui.CodebergSource
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.GitHubSource
import com.apkup.data.ui.GitLabSource
import com.apkup.data.ui.InstallOrigin
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.combineSourceResults
import com.apkup.data.ui.getPackageNames
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.data.ui.toAppUpdate
import com.apkup.data.ui.toCachedDto
import com.apkup.prefs.Prefs
import com.apkup.util.AppJournal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val playRepository: PlayRepository,
    private val izzyOnDroidRepository: IzzyOnDroidRepository,
    private val gitHubRepository: GitHubRepository,
    private val gitLabRepository: GitLabRepository,
    private val prefs: Prefs,
    private val repoMappingManager: RepoMappingManager? = null,
    private val codebergRepository: CodebergRepository? = null,
    private val customFDroidReposRepository: CustomFDroidReposRepository? = null,
    private val harvestBarrierTimeoutMs: Long = DEFAULT_HARVEST_BARRIER_TIMEOUT_MS
) {

    companion object {
        internal const val DEFAULT_HARVEST_BARRIER_TIMEOUT_MS = 12_000L
    }

    suspend fun getInstalledApps(): Result<List<AppInstalled>> = appsRepository.getApps()

    fun updates(installedAppsSnapshot: Result<List<AppInstalled>>? = null) = channelFlow<UpdateCheckResult<AppUpdate>> {
        val checkStartedAt = SystemClock.elapsedRealtime()
        val appLoadStartedAt = SystemClock.elapsedRealtime()
        val installedAppsResult = installedAppsSnapshot ?: appsRepository.getApps()
        val installedApps = installedAppsResult.getOrElse {
            Log.e("UpdatesRepository", "Could not read installed apps (${it.javaClass.simpleName})")
            send(UpdateCheckResult(emptyList(), successfulRequests = 0, failedRequests = 0, installedAppsFailed = true))
            return@channelFlow
        }
        val appLoadDuration = if (installedAppsSnapshot == null) {
            SystemClock.elapsedRealtime() - appLoadStartedAt
        } else null

        val appsToCheck = (
            installedApps.filter { !it.ignored && (prefs.showSystemApps.get() || !it.isSystemApp) } +
                buildWatchedAppPlaceholders(installedApps)
            )
        val installedOnlyToCheck = appsToCheck.filterNot { it.isWatchedOnly }
        val hasEnabledFDroidRepo = prefs.useIzzyOnDroid.get() && prefs.getFDroidRepos().any { it.enabled }
        val useCustomFDroid = prefs.useCustomFDroidRepos.get() && prefs.readCustomFDroidReposSafe().any { it.enabled }
        val useGitSources = prefs.useGitHub.get() || prefs.useGitLab.get() || prefs.useCodeberg.get()
        val izzyHarvestReady = if (hasEnabledFDroidRepo && useGitSources) CompletableDeferred<Unit>() else null

        val sourceChecks = mutableListOf<Pair<String, suspend (suspend (List<String>) -> Unit) -> SourceUpdateResult<AppUpdate>>>()
        if (prefs.useApkMirror.get()) {
            sourceChecks.add("APKMirror" to { onBatchSuccess -> apkMirrorRepository.updates(appsToCheck, onBatchSuccess).first() })
        }
        if (prefs.usePlay.get()) {
            sourceChecks.add("Google Play" to { onBatchSuccess ->
                val watchedPkgs = appsToCheck.filter { it.isWatchedOnly }.map { it.packageName }
                if (watchedPkgs.isNotEmpty()) onBatchSuccess(watchedPkgs)
                playRepository.updates(installedOnlyToCheck, onBatchSuccess).first()
            })
        }
        if (hasEnabledFDroidRepo) {
            sourceChecks.add("F-Droid / IzzyOnDroid" to { onBatchSuccess ->
                try {
                    izzyOnDroidRepository.updates(
                        apps = appsToCheck,
                        onBatchSuccess = onBatchSuccess,
                        onHarvestComplete = { izzyHarvestReady?.complete(Unit) }
                    ).first()
                } finally {
                    izzyHarvestReady?.complete(Unit)
                }
            })
        }
        if (useCustomFDroid && customFDroidReposRepository != null) {
            sourceChecks.add("Custom F-Droid" to { onBatchSuccess ->
                customFDroidReposRepository.updates(appsToCheck, onBatchSuccess).first()
            })
        }
        if (prefs.useGitHub.get()) {
            sourceChecks.add("GitHub" to { onBatchSuccess ->
                if (izzyHarvestReady != null) {
                    withTimeoutOrNull(harvestBarrierTimeoutMs) { izzyHarvestReady.await() }
                }
                gitHubRepository.updates(appsToCheck, onBatchSuccess).first()
            })
        }
        if (prefs.useGitLab.get()) {
            sourceChecks.add("GitLab" to { onBatchSuccess ->
                if (izzyHarvestReady != null) {
                    withTimeoutOrNull(harvestBarrierTimeoutMs) { izzyHarvestReady.await() }
                }
                gitLabRepository.updates(appsToCheck, onBatchSuccess).first()
            })
        }
        if (prefs.useCodeberg.get() && codebergRepository != null) {
            sourceChecks.add("Codeberg" to { onBatchSuccess ->
                if (izzyHarvestReady != null) {
                    withTimeoutOrNull(harvestBarrierTimeoutMs) { izzyHarvestReady.await() }
                }
                codebergRepository.updates(appsToCheck, onBatchSuccess).first()
            })
        }

        val sourceResults = mutableListOf<SourceUpdateResult<AppUpdate>>()
        val activeSourceLabels = sourceChecks.joinToString(", ") { it.first }
        if (BuildConfig.DEBUG) {
            Log.d("UpdatesRepository", "Starting update check across active sources ($activeSourceLabels) for ${appsToCheck.size} apps")
        }
        AppJournal.record("Updates", "Started update check (${appsToCheck.size} apps, sources: $activeSourceLabels)")
        if (sourceChecks.isEmpty()) {
            val emptyResult = UpdateCheckResult<AppUpdate>(emptyList(), successfulRequests = 0, failedRequests = 0)
            saveSnapshotToCache(emptyResult)
            send(emptyResult)
        } else coroutineScope {
            val packageNames = appsToCheck.getPackageNames()
            val progressTracker = UpdateCheckProgressTracker(packageNames, sourceChecks.map { it.first })
            val sourceResultsLock = Any()
            val prioritySelector: (AppUpdate) -> Int = { candidate ->
                computeUpdateSourcePriority(candidate, repoMappingManager)
            }
            fun currentResult(isChecking: Boolean): UpdateCheckResult<AppUpdate> = synchronized(sourceResultsLock) {
                val filteredResults = sourceResults.map { sourceResult ->
                    sourceResult.copy(
                        updates = sourceResult.updates.filter { it.isMeaningfulVersionUpgrade() }
                    )
                }
                val combined = combineSourceResults(
                    filteredResults,
                    packageName = AppUpdate::packageName,
                    versionCode = AppUpdate::versionCode,
                    sourcePriority = prioritySelector
                )
                val enrichedUpdates = enrichWithSourceAlternatives(
                    primaryUpdates = combined.updates,
                    allCandidates = filteredResults.flatMap { it.updates },
                    sourcePriority = prioritySelector
                )
                val totalApps = progressTracker.totalApps
                val checkedApps = progressTracker.checkedApps()
                val visibleCheckedApps = visibleCheckedApps(checkedApps, totalApps, isChecking)
                combined.copy(
                    updates = enrichedUpdates,
                    isChecking = isChecking,
                    checkedApps = visibleCheckedApps,
                    totalApps = totalApps.takeIf { it > 0 },
                    activeSourceName = if (isChecking) progressTracker.activeSourceName() else null
                )
            }

            if (progressTracker.totalApps > 0) send(currentResult(isChecking = true))
            val pending = sourceChecks.map { (sourceName, check) ->
                async {
                    val sourceStartedAt = SystemClock.elapsedRealtime()
                    val result = captureUpdateSourceCheck({
                        check { batch ->
                            progressTracker.markBatchSucceeded(sourceName, batch)
                            send(currentResult(isChecking = true))
                        }
                    }) { exception ->
                        val failure = when (exception) {
                            is java.net.SocketTimeoutException -> "timeout"
                            is retrofit2.HttpException -> "HTTP ${exception.code()}"
                            is java.io.IOException -> "network error"
                            else -> "processing error (${exception.javaClass.simpleName})"
                        }
                        Log.w("UpdatesRepository", "$sourceName update source failed: $failure")
                        AppJournal.record("Updates", "$sourceName failed: $failure")
                    }.copy(sourceName = sourceName)
                    result.failureSummary?.let { failure ->
                        AppJournal.record("Updates", "$sourceName failure details: $failure")
                    }
                    val elapsedMs = SystemClock.elapsedRealtime() - sourceStartedAt
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "UpdatesRepository",
                            "$sourceName completed: ${result.successfulRequests} request(s) succeeded, " +
                                "${result.failedRequests} failed, ${result.updates.size} update(s) returned in " +
                                "$elapsedMs ms"
                        )
                    }
                    AppJournal.record(
                        "Updates",
                        "$sourceName finished: ok=${result.successfulRequests}, fail=${result.failedRequests}, updates=${result.updates.size} (${elapsedMs}ms)"
                    )
                    sourceName to result
                }
            }.toMutableList()

            while (pending.isNotEmpty()) {
                val (completed, result) = awaitNextCompleted(pending)
                pending.remove(completed)
                progressTracker.markSourceFinished(result.first)
                synchronized(sourceResultsLock) { sourceResults += result.second }
                val isStillChecking = pending.isNotEmpty()
                val nextResult = currentResult(isChecking = isStillChecking)
                if (!isStillChecking && !nextResult.isCompleteFailure) {
                    saveSnapshotToCache(nextResult)
                }
                send(nextResult)
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                "UpdatesRepository",
                "Update check completed: ${installedApps.size} installed app(s), " +
                    "${sourceResults.size} source(s), app scan ${appLoadDuration?.let { "$it ms" } ?: "shared"}, " +
                    "total ${SystemClock.elapsedRealtime() - checkStartedAt} ms"
            )
        }
    }.flowOn(Dispatchers.IO)

    internal fun saveSnapshotToCache(
        result: UpdateCheckResult<AppUpdate>,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        runCatching {
            val snapshot = CachedUpdateSnapshot(
                timestampMillis = timestampMillis,
                updates = result.updates.map { it.toCachedDto() },
                hasSourceErrors = result.hasFailures,
                sourceErrors = result.sourceErrors,
                totalApps = result.totalApps
            )
            prefs.writeCachedSnapshot(snapshot)
        }.onFailure {
            Log.w("UpdatesRepository", "Could not persist update cache snapshot (${it.javaClass.simpleName})")
        }
    }

    internal fun restoreCachedResult(
        installedApps: List<AppInstalled>? = null
    ): Pair<Long, UpdateCheckResult<AppUpdate>>? {
        val snapshot = prefs.readCachedSnapshotSafe()
        if (snapshot.timestampMillis <= 0L) return null
        val installedByPackage = installedApps?.associateBy { it.packageName }
        val watchedByPackage = if (installedApps == null) {
            emptyMap()
        } else {
            buildWatchedAppPlaceholders(installedApps).associateBy { it.packageName }
        }
        val restoredUpdates = snapshot.updates
            .orEmpty()
            .map { dto ->
                dto.toAppUpdate { pkg, vCode, offer ->
                    playRepository.getInstallFilesForPackage(pkg, vCode, offer)
                }
            }
            .mapNotNull { update ->
                if (installedByPackage == null) {
                    update.takeIf { it.isMeaningfulVersionUpgrade() }
                } else {
                    val installed = installedByPackage[update.packageName]
                        ?: watchedByPackage[update.packageName]
                        ?: return@mapNotNull null
                    if (installed.ignored) return@mapNotNull null
                    val compatibleAlternatives = update.alternativeUpdates.map { alt ->
                        alt.copy(
                            name = installed.name.ifBlank { alt.name },
                            oldVersion = installed.version,
                            oldVersionCode = installed.versionCode,
                            installOrigin = installed.installOrigin,
                            installerPackageName = installed.installerPackageName
                        )
                    }.filter { it.isMeaningfulVersionUpgrade() && isCachedSourceCompatible(it, installed) }

                    val primaryRefreshed = update.copy(
                        name = installed.name.ifBlank { update.name },
                        oldVersion = installed.version,
                        oldVersionCode = installed.versionCode,
                        installOrigin = installed.installOrigin,
                        installerPackageName = installed.installerPackageName,
                        alternativeUpdates = if (compatibleAlternatives.size > 1) compatibleAlternatives else emptyList()
                    )

                    if (isCachedSourceCompatible(primaryRefreshed, installed)) {
                        primaryRefreshed.takeIf { it.isMeaningfulVersionUpgrade() }
                    } else {
                        val fallbackPrimary = compatibleAlternatives.firstOrNull() ?: return@mapNotNull null
                        fallbackPrimary.copy(
                            alternativeUpdates = if (compatibleAlternatives.size > 1) compatibleAlternatives else emptyList()
                        ).takeIf { it.isMeaningfulVersionUpgrade() }
                    }
                }
            }
        val checkResult = UpdateCheckResult(
            updates = restoredUpdates,
            successfulRequests = 1,
            failedRequests = if (snapshot.hasSourceErrors) 1 else 0,
            isChecking = false,
            checkedApps = snapshot.totalApps,
            totalApps = snapshot.totalApps,
            sourceErrors = snapshot.sourceErrors.orEmpty()
        )
        return snapshot.timestampMillis to checkResult
    }

    private fun buildWatchedAppPlaceholders(installedApps: List<AppInstalled>): List<AppInstalled> {
        val installedPackages = installedApps.mapTo(HashSet()) { it.packageName }
        return prefs.readWatchedAppsSafe()
            .filter { entry -> entry.packageName.isNotBlank() && entry.packageName !in installedPackages }
            .map { entry ->
                AppInstalled(
                    name = entry.appName.ifBlank { entry.packageName },
                    packageName = entry.packageName,
                    version = "",
                    versionCode = -1L,
                    signatureSha256 = "",
                    isWatchedOnly = true
                )
            }
    }

    private fun isCachedSourceCompatible(candidate: AppUpdate, installed: AppInstalled): Boolean {
        if (candidate.source != GitHubSource && candidate.source != GitLabSource && candidate.source != CodebergSource) {
            return true
        }
        val manager = repoMappingManager ?: return true
        val binding = (candidate.link as? Link.Url)?.link?.let { manager.parseRepoUrl(it) }
            ?: manager.getBinding(installed.packageName)
            ?: return true
        return !manager.isSignatureIncompatible(installed.packageName, binding, installed.signatureSha256)
    }
}

/**
 * Source deduplication priority (F-14):
 * 1. Explicit user custom repo binding (GitHub/GitLab/Codeberg) -> 100
 * 2. Matching installOrigin (Play->Play, F-Droid->F-Droid/Izzy, APKMirror->APKMirror, Sideload->GitHub/Codeberg/GitLab) -> 84..90
 * 3. Default tie-breaker priority -> Play (5) > GitHub (4) > Codeberg (3) > F-Droid/Izzy/CustomFDroid (2) > GitLab (1) > APKMirror (0)
 */
internal fun computeUpdateSourcePriority(
    update: AppUpdate,
    repoMappingManager: RepoMappingManager? = null
): Int {
    val customBinding = repoMappingManager?.getCustomBinding(update.packageName)
    if (customBinding != null) {
        val matchesCustomType = (customBinding.isGitHub && update.source == GitHubSource) ||
            (customBinding.isGitLab && update.source == GitLabSource) ||
            (customBinding.isCodeberg && update.source == CodebergSource)
        if (matchesCustomType) return 100
    }

    val isFDroidFamily = update.source == IzzyOnDroidSource ||
        update.source == FDroidSource ||
        update.source == CustomFDroidSource ||
        (update.source != PlaySource && update.source != GitHubSource && update.source != GitLabSource && update.source != CodebergSource && update.source != ApkMirrorSource)

    when (update.installOrigin) {
        InstallOrigin.PLAY_STORE -> if (update.source == PlaySource) return 90
        InstallOrigin.FDROID -> if (isFDroidFamily) return 90
        InstallOrigin.APKMIRROR -> if (update.source == ApkMirrorSource) return 90
        InstallOrigin.SIDELOAD -> {
            if (update.source == GitHubSource) return 86
            if (update.source == CodebergSource) return 85
            if (update.source == GitLabSource) return 85
            if (isFDroidFamily) return 84
        }
        else -> Unit
    }

    return defaultSourcePriority(update)
}

internal fun defaultSourcePriority(update: AppUpdate): Int = when (update.source) {
    PlaySource -> 5
    GitHubSource -> 4
    CodebergSource -> 3
    FDroidSource, IzzyOnDroidSource, CustomFDroidSource -> 2
    GitLabSource -> 1
    ApkMirrorSource -> 0
    else -> 2
}

internal fun enrichWithSourceAlternatives(
    primaryUpdates: List<AppUpdate>,
    allCandidates: List<AppUpdate>,
    sourcePriority: (AppUpdate) -> Int = ::defaultSourcePriority
): List<AppUpdate> {
    if (primaryUpdates.isEmpty()) return emptyList()
    val candidatesByPackage = allCandidates
        .filter { it.isMeaningfulVersionUpgrade() }
        .groupBy { it.packageName }

    return primaryUpdates.map { primary ->
        val distinctSources = candidatesByPackage[primary.packageName]
            .orEmpty()
            .groupBy { it.source.name }
            .values
            .map { sameSourceCandidates ->
                sameSourceCandidates.maxByOrNull { it.versionCode }!!
            }
            .sortedWith(
                compareByDescending<AppUpdate> { sourcePriority(it) }
                    .thenByDescending { it.versionCode }
            )
        if (distinctSources.size > 1) {
            primary.copy(alternativeUpdates = distinctSources)
        } else {
            primary
        }
    }
}

internal suspend fun <T> awaitNextCompleted(
    pending: Collection<Deferred<T>>
): Pair<Deferred<T>, T> = select {
    pending.forEach { deferred ->
        deferred.onAwait { result -> deferred to result }
    }
}
