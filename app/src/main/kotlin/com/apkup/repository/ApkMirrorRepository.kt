package com.apkup.repository

import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkup.data.apkmirror.AppExistsRequest
import com.apkup.data.apkmirror.AppExistsResponseApk
import com.apkup.data.apkmirror.AppExistsResponseData
import com.apkup.data.apkmirror.toAppUpdate
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.filterNewerCandidates
import com.apkup.data.ui.getPackageNames
import com.apkup.data.ui.isCompatibleFormFactor
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.data.ui.matchesSignature
import com.apkup.data.ui.supportsArchitecture
import com.apkup.data.ui.supportsMinApi
import com.apkup.BuildConfig
import com.apkup.service.ApkMirrorService
import com.apkup.util.isAndroidTv
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentLinkedQueue


internal const val APKMIRROR_REQUEST_BATCH_SIZE = 25
internal const val APKMIRROR_REQUEST_CONCURRENCY = 8
internal const val APKMIRROR_WORKER_STAGGER_MS = 30L

class ApkMirrorRepository(
    private val service: ApkMirrorService,
    packageManager: PackageManager
) {

    private val arch = when {
        Build.SUPPORTED_ABIS.contains("x86") -> "x86"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "arm"
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm"
        else -> "arm"
    }

    private val isAndroidTV = packageManager.isAndroidTv()
    private val api = Build.VERSION.SDK_INT

    suspend fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ) = flow<SourceUpdateResult<AppUpdate>> {
        val packageNames = apps.getPackageNames()
        val failureSummaries = ConcurrentLinkedQueue<String>()
        val batchCount = (packageNames.size + APKMIRROR_REQUEST_BATCH_SIZE - 1) / APKMIRROR_REQUEST_BATCH_SIZE
        Log.i(
            "ApkMirrorRepository",
            "Checking ${packageNames.size} installed apps in $batchCount request batches " +
                "(up to $APKMIRROR_REQUEST_CONCURRENCY concurrent)"
        )
        val result = requestConcurrentBatches(
            items = packageNames,
            batchSize = APKMIRROR_REQUEST_BATCH_SIZE,
            concurrency = APKMIRROR_REQUEST_CONCURRENCY,
            workerStaggerMs = APKMIRROR_WORKER_STAGGER_MS,
            request = { batch ->
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val response = service.appExists(AppExistsRequest(batch, buildIgnoreList())).data
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "ApkMirrorRepository",
                            "Batch with ${batch.size} package IDs succeeded in " +
                                "${SystemClock.elapsedRealtime() - startedAt} ms; ${response.size} responses"
                        )
                    }
                    response
                } catch (exception: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "ApkMirrorRepository",
                            "Batch with ${batch.size} package IDs failed in " +
                                "${SystemClock.elapsedRealtime() - startedAt} ms " +
                                "(${describeApkMirrorFailure(exception)})"
                        )
                    }
                    throw exception
                }
            },
            stopSchedulingAfterFailure = { exception ->
                exception is java.net.UnknownHostException ||
                    (exception is retrofit2.HttpException && exception.code() == 429)
            },
            onFailure = { exception ->
                val failure = describeApkMirrorFailure(exception)
                failureSummaries.add(failure)
                Log.e("ApkMirrorRepository", "Update batch failed: $failure")
            },
            onBatchSuccess = onBatchSuccess
        )
        val processingStartedAt = SystemClock.elapsedRealtime()
        val updates = parseUpdates(result.responses, apps)
        if (BuildConfig.DEBUG) {
            Log.d(
                "ApkMirrorRepository",
                "Processed ${result.responses.size} response(s) into ${updates.size} update(s) in " +
                    "${SystemClock.elapsedRealtime() - processingStartedAt} ms; " +
                    "${result.successfulRequests} batch(es) succeeded, ${result.failedRequests} failed"
            )
        }
        emit(
            SourceUpdateResult(
                updates,
                result.successfulRequests,
                result.failedRequests,
                failureSummaries.peek()
            )
        )
    }

    private fun parseUpdates(updates: List<AppExistsResponseData>, apps: List<AppInstalled>): List<AppUpdate> {
        val installedByPackage = apps.associateBy { it.packageName }
        val installedVersions = apps.associate { it.packageName to it.versionCode }
        val candidates = updates.asSequence()
            .filter { it.exists == true }
            .flatMap { data -> data.apks.asSequence().map { data to it } }
        val newerCandidates = filterNewerCandidates(
            candidates.toList(),
            installedVersions,
            packageName = { it.first.pname },
            versionCode = { it.second.versionCode }
        )
        val results = newerCandidates
            .asSequence()
            .filter { (data, apk) -> matchesSignature(apk.signaturesSha1, installedByPackage[data.pname]?.signature) }
            .filter { (_, apk) -> filterArch(apk) }
            .filter { (_, apk) -> filterMinApi(apk) }
            .filter { (_, apk) -> isCompatibleFormFactor(apk.capabilities, isAndroidTV) }
            .groupBy { it.first.pname }
            .mapNotNull { (packageName, packageCandidates) ->
                val installedApp = installedByPackage[packageName] ?: return@mapNotNull null
                packageCandidates
                    .map { (data, apk) -> apk.toAppUpdate(installedApp, data.release) }
                    .filter { it.isMeaningfulVersionUpgrade() }
                    .maxByOrNull { it.versionCode }
            }
        Log.i(
            "ApkMirrorRepository",
            "Received ${updates.size} app responses; ${results.size} updates passed version and compatibility filters"
        )
        return results
    }

    private fun filterArch(app: AppExistsResponseApk) =
        supportsArchitecture(app.arches, Build.SUPPORTED_ABIS.toList(), arch)

    private fun filterMinApi(apk: AppExistsResponseApk) = supportsMinApi(apk.minapi, api)

    private fun buildIgnoreList() = listOf("alpha", "beta")

}

internal fun describeApkMirrorFailure(exception: Throwable): String = when {
    exception is java.net.SocketTimeoutException -> {
        val message = generateSequence(exception as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        when {
            message.contains("connect", ignoreCase = true) -> "connect timeout"
            message.contains("read", ignoreCase = true) -> "read timeout"
            message.contains("write", ignoreCase = true) -> "write timeout"
            else -> "socket timeout (phase not reported by HTTP client)"
        }
    }
    exception is java.net.UnknownHostException -> "DNS lookup failed"
    exception is retrofit2.HttpException -> "HTTP ${exception.code()}"
    exception is java.io.IOException -> "network error (${exception.javaClass.simpleName})"
    else -> "response processing error (${exception.javaClass.simpleName})"
}
