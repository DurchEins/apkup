package com.apkup.repository

import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CachedUpdateSnapshot
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.UpdateCheckResult
import com.apkup.data.ui.combineSourceResults
import com.apkup.data.ui.getPackageNames
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.data.ui.toAppUpdate
import com.apkup.data.ui.toCachedDto
import com.apkup.prefs.Prefs
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select


class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val playRepository: PlayRepository,
    private val izzyOnDroidRepository: IzzyOnDroidRepository,
    private val prefs: Prefs
) {

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

        val appsToCheck = installedApps.filter { !it.ignored }
        val sourceChecks = mutableListOf<Pair<String, suspend (suspend (List<String>) -> Unit) -> SourceUpdateResult<AppUpdate>>>()
        if (prefs.useApkMirror.get()) sourceChecks.add("APKMirror" to { onBatchSuccess -> apkMirrorRepository.updates(appsToCheck, onBatchSuccess).first() })
        if (prefs.usePlay.get()) sourceChecks.add("Google Play" to { onBatchSuccess -> playRepository.updates(appsToCheck, onBatchSuccess).first() })
        if (prefs.useIzzyOnDroid.get()) sourceChecks.add("IzzyOnDroid" to { onBatchSuccess -> izzyOnDroidRepository.updates(appsToCheck, onBatchSuccess).first() })

        val sourceResults = mutableListOf<SourceUpdateResult<AppUpdate>>()
        Log.i("UpdatesRepository", "Starting update check across active sources (${sourceChecks.map { it.first }.joinToString(", ")}) for ${appsToCheck.size} apps")
        if (sourceChecks.isEmpty()) {
            val emptyResult = UpdateCheckResult<AppUpdate>(emptyList(), successfulRequests = 0, failedRequests = 0)
            saveSnapshotToCache(emptyResult)
            send(emptyResult)
        } else coroutineScope {
            // Count only nonblank, unique package IDs; these are exactly the IDs all sources query.
            val packageNames = appsToCheck.getPackageNames()
            val progressTracker = UpdateCheckProgressTracker(packageNames, sourceChecks.map { it.first })
            val sourceResultsLock = Any()
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
                    sourcePriority = { when (it.source) { PlaySource -> 2; IzzyOnDroidSource -> 1; else -> 0 } }
                )
                val enrichedUpdates = enrichWithSourceAlternatives(
                    primaryUpdates = combined.updates,
                    allCandidates = filteredResults.flatMap { it.updates }
                )
                val totalApps = progressTracker.totalApps
                val checkedApps = progressTracker.checkedApps()
                // Keep the active indicator below 100%; source result processing must also finish.
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
                        Log.e("UpdatesRepository", "$sourceName update source failed: $failure")
                    }.copy(sourceName = sourceName)
                    result.failureSummary?.let { failure ->
                        Log.e("UpdatesRepository", "$sourceName source failure details: $failure")
                    }
                    Log.i(
                        "UpdatesRepository",
                        "$sourceName completed: ${result.successfulRequests} request(s) succeeded, " +
                            "${result.failedRequests} failed, ${result.updates.size} update(s) returned in " +
                            "${SystemClock.elapsedRealtime() - sourceStartedAt} ms"
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
                // Partial empty results remain in Loading; an update from one source is visible
                // while the other source is still being checked.
                send(nextResult)
            }
        }
        Log.i(
            "UpdatesRepository",
            "Update check completed: ${installedApps.size} installed app(s), " +
                "${sourceResults.size} source(s), app scan ${appLoadDuration?.let { "${it} ms" } ?: "shared"}, " +
                "total ${SystemClock.elapsedRealtime() - checkStartedAt} ms"
        )
    }

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
            prefs.cachedUpdateSnapshot.put(snapshot)
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
        val restoredUpdates = snapshot.updates
            .map { dto ->
                dto.toAppUpdate { pkg, vCode, offer ->
                    playRepository.getInstallFilesForPackage(pkg, vCode, offer)
                }
            }
            .mapNotNull { update ->
                if (installedByPackage == null) {
                    update.takeIf { it.isMeaningfulVersionUpgrade() }
                } else {
                    val installed = installedByPackage[update.packageName] ?: return@mapNotNull null
                    if (installed.ignored) return@mapNotNull null
                    val refreshed = update.copy(
                        name = installed.name.ifBlank { update.name },
                        oldVersion = installed.version,
                        oldVersionCode = installed.versionCode,
                        installOrigin = installed.installOrigin,
                        installerPackageName = installed.installerPackageName,
                        alternativeUpdates = update.alternativeUpdates.map { alt ->
                            alt.copy(
                                name = installed.name.ifBlank { alt.name },
                                oldVersion = installed.version,
                                oldVersionCode = installed.versionCode,
                                installOrigin = installed.installOrigin,
                                installerPackageName = installed.installerPackageName
                            )
                        }.filter { it.isMeaningfulVersionUpgrade() }
                    )
                    refreshed.takeIf { it.isMeaningfulVersionUpgrade() }
                }
            }
        val checkResult = UpdateCheckResult(
            updates = restoredUpdates,
            successfulRequests = 1,
            failedRequests = if (snapshot.hasSourceErrors) 1 else 0,
            isChecking = false,
            checkedApps = snapshot.totalApps,
            totalApps = snapshot.totalApps,
            sourceErrors = snapshot.sourceErrors
        )
        return snapshot.timestampMillis to checkResult
    }

}

internal fun enrichWithSourceAlternatives(
    primaryUpdates: List<AppUpdate>,
    allCandidates: List<AppUpdate>
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
                compareByDescending<AppUpdate> { when (it.source) { PlaySource -> 2; IzzyOnDroidSource -> 1; else -> 0 } }
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
