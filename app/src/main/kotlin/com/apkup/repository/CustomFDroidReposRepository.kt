package com.apkup.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.CustomFDroidSource
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.prefs.Prefs
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Queries user-configured third-party F-Droid repositories (official F-Droid,
 * Mullvad, NewPipe, Guardian Project, GrapheneOS, etc.) by delegating index
 * parsing and matching to parameterized [IzzyOnDroidRepository] instances.
 */
class CustomFDroidReposRepository(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs,
    @Suppress("unused", "UNUSED_PARAMETER")
    private val repoMappingManager: RepoMappingManager? = null,
    private val baseCacheDirectory: File? = null
) {
    private val repoClients = ConcurrentHashMap<String, IzzyOnDroidRepository>()

    private fun clientFor(baseUrl: String, name: String, fingerprint: String): IzzyOnDroidRepository {
        val key = "${baseUrl.trim().lowercase()}|${name.trim()}|${fingerprint.trim()}"
        return repoClients.getOrPut(key) {
            val cacheRoot = baseCacheDirectory
                ?: context?.cacheDir?.let { File(it, "fdroid_custom_repos") }
                ?: File(System.getProperty("java.io.tmpdir") ?: ".", "fdroid_custom_repos")
            val dirHash = baseUrl.trim().lowercase().hashCode().toUInt().toString(16)
            IzzyOnDroidRepository(
                context = context,
                okHttpClient = okHttpClient,
                gson = gson,
                prefs = prefs,
                repoMappingManager = null,
                cacheDirectory = File(cacheRoot, "repo_$dirHash"),
                repoBaseUrl = baseUrl,
                targetSource = CustomFDroidSource,
                repoDisplayName = name,
                repoFingerprint = fingerprint,
                onTofuFingerprintVerified = { verifiedFp ->
                    prefs.pinCustomFDroidRepoTofu(baseUrl, verifiedFp)
                }
            )
        }
    }

    @Suppress("SENSELESS_COMPARISON")
    fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ): Flow<SourceUpdateResult<AppUpdate>> = flow {
        val startedAt = SystemClock.elapsedRealtime()
        val alreadyCheckedUrls = if (prefs.useIzzyOnDroid.get()) {
            prefs.readFDroidReposSafe()
                .filter { it.enabled }
                .mapNotNull { FDroidRepoConfig.normalizeRepoBaseUrl(it.url)?.lowercase() }
                .toSet()
        } else {
            emptySet()
        }
        val activeRepos = prefs.readCustomFDroidReposSafe()
            .filter { repo ->
                val safeBaseUrl = (repo.baseUrl as String?).orEmpty()
                repo.enabled &&
                    safeBaseUrl.isNotBlank() &&
                    (FDroidRepoConfig.normalizeRepoBaseUrl(repo.normalizedBaseUrl())?.lowercase() !in alreadyCheckedUrls)
            }
        if (activeRepos.isEmpty()) {
            onBatchSuccess(apps.map { it.packageName })
            emit(SourceUpdateResult(updates = emptyList(), successfulRequests = apps.size, failedRequests = 0))
            return@flow
        }

        val aggregatedUpdates = mutableListOf<AppUpdate>()
        var totalSuccessful = 0
        var totalFailed = 0
        val errors = mutableListOf<String>()

        for (repo in activeRepos) {
            val normalizedUrl = repo.normalizedBaseUrl()
            val repoLabel = (repo.name as String?).orEmpty().ifBlank { normalizedUrl }
            val safeFingerprint = (repo.fingerprint as String?).orEmpty()
            try {
                val client = clientFor(normalizedUrl, repoLabel, safeFingerprint)
                val result = client.updates(apps = apps, onBatchSuccess = {}).first()
                aggregatedUpdates += result.updates
                totalSuccessful += result.successfulRequests
                totalFailed += result.failedRequests
                result.failureSummary?.let { errors += "$repoLabel: $it" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                totalFailed++
                errors += "$repoLabel: ${e.message ?: e.javaClass.simpleName}"
                Log.w("CustomFDroidReposRepo", "Repo $repoLabel ($normalizedUrl) failed: ${e.message}")
            }
        }

        onBatchSuccess(apps.map { it.packageName })
        Log.i(
            "CustomFDroidReposRepo",
            "Checked ${activeRepos.size} custom F-Droid repo(s), found ${aggregatedUpdates.size} update(s) in ${SystemClock.elapsedRealtime() - startedAt} ms"
        )

        emit(
            SourceUpdateResult(
                updates = aggregatedUpdates,
                successfulRequests = if (aggregatedUpdates.isNotEmpty() || totalSuccessful > 0) totalSuccessful.coerceAtLeast(1) else 0,
                failedRequests = totalFailed,
                failureSummary = errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
        )
    }
}
