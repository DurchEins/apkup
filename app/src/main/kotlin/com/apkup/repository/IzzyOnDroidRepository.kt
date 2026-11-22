package com.apkup.repository

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkup.R
import com.apkup.data.fdroid.FDroidIndexEngine
import com.apkup.data.fdroid.FDroidRepoConfig
import com.apkup.data.fdroid.FDroidV2IndexFileRef
import com.apkup.data.izzy.IzzyIndexResponse
import com.apkup.data.izzy.IzzyPackageInfo
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.FDroidSource
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.Source
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.canonicalizeAbi
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.data.ui.isVerifiableVersionUpgrade
import com.apkup.prefs.Prefs
import com.apkup.util.ApkIntegrityVerifier
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Multi-Repository F-Droid V2/V1 Update Engine (`F-02`, `F-23b`, `CRIT-02`, `HIGH-12`).
 *
 * Supports IzzyOnDroid (default enabled), Official F-Droid, microG, Guardian Project, and
 * user-defined custom F-Droid repositories with mandatory `entry.jar` / `index-v1.jar` X.509
 * signature verification, anti-rollback timestamp protection, streaming `JsonReader` parsing,
 * 5-minute TTL, and mandatory per-APK SHA-256 verification.
 */
class IzzyOnDroidRepository(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs? = null,
    private val repoMappingManager: RepoMappingManager? = null,
    private val cacheDirectory: File? = null,
    private val repoBaseUrl: String? = null,
    private val targetSource: Source? = null,
    private val repoDisplayName: String = "",
    private val repoFingerprint: String = "",
    private val onTofuFingerprintVerified: ((String) -> Unit)? = null
) {
    companion object {
        const val ENTRY_JAR_URL = "https://apt.izzysoft.de/fdroid/repo/entry.jar"
        const val ENTRY_JSON_URL = "https://apt.izzysoft.de/fdroid/repo/entry.json"
        const val INDEX_V2_JSON_URL = "https://apt.izzysoft.de/fdroid/repo/index-v2.json"
        const val INDEX_JAR_URL = "https://apt.izzysoft.de/fdroid/repo/index-v1.jar"
        const val REPO_BASE_URL = "https://apt.izzysoft.de/fdroid/repo/"
        internal const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        internal const val IN_MEMORY_CACHE_TTL_MS = 5L * 60L * 1_000L // 5 minutes (HIGH-12)
        private const val MAX_INDEX_DOWNLOAD_BYTES = 256L * 1024L * 1024L // 256 MB hard cap
        private const val BATCH_NOTIFY_SIZE = 25
    }

    private data class CachedRepoIndex(
        val index: IzzyIndexResponse,
        val fetchedAtMillis: Long,
        val coveredPackages: Set<String>?
    )

    private val mutex = Mutex()
    private val inMemoryRepoCache = ConcurrentHashMap<String, CachedRepoIndex>()

    private val pinnedTofuFingerprints = ConcurrentHashMap<String, String>()

    fun invalidateMemoryCache() {
        inMemoryRepoCache.clear()
    }

    private fun getRootCacheDir(): File {
        val dir = cacheDirectory
            ?: context?.cacheDir?.let { File(it, "fdroid_cache") }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "fdroid_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getRepoCacheDir(repoId: String): File {
        val safeId = repoId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "default" }
        val dir = if (cacheDirectory != null && (repoId == FDroidRepoConfig.ID_IZZYONDROID || repoBaseUrl != null)) {
            cacheDirectory
        } else {
            File(getRootCacheDir(), safeId)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun resolveSourceForRepo(repo: FDroidRepoConfig): Source = targetSource ?: when (repo.id) {
        FDroidRepoConfig.ID_IZZYONDROID -> IzzyOnDroidSource
        FDroidRepoConfig.ID_FDROID_OFFICIAL -> FDroidSource
        else -> Source(repo.name.ifBlank { "F-Droid" }, R.drawable.ic_izzy)
    }

    internal fun resolveActiveRepos(): List<FDroidRepoConfig> {
        if (repoBaseUrl != null) {
            val cleanUrl = FDroidRepoConfig.normalizeRepoBaseUrl(repoBaseUrl)
                ?: throw SecurityException("Only valid HTTPS F-Droid repository URLs are allowed")
            val presetMatch = FDroidRepoConfig.defaultPresets().firstOrNull {
                it.normalizedUrl.equals(cleanUrl, ignoreCase = true)
            }
            val normExplicitFp = FDroidRepoConfig.normalizeFingerprint(repoFingerprint)
            val singleId = presetMatch?.id
                ?: ("custom_" + cleanUrl.lowercase(Locale.ROOT).hashCode().toUInt().toString(16))
            val pinnedForRepo = pinnedTofuFingerprints[singleId].orEmpty()
            val effectiveFp = when {
                presetMatch != null -> {
                    if (normExplicitFp.isNotEmpty() && !FDroidRepoConfig.isValidSha256Fingerprint(normExplicitFp)) {
                        throw SecurityException("Invalid SHA-256 fingerprint for F-Droid repository")
                    }
                    presetMatch.normalizedFingerprint
                }
                normExplicitFp.isEmpty() -> pinnedForRepo
                FDroidRepoConfig.isValidSha256Fingerprint(normExplicitFp) -> normExplicitFp
                else -> throw SecurityException("Invalid SHA-256 fingerprint for custom F-Droid repository")
            }
            return listOf(
                FDroidRepoConfig(
                    id = singleId,
                    name = repoDisplayName.ifBlank { presetMatch?.name ?: cleanUrl },
                    url = cleanUrl,
                    fingerprint = effectiveFp,
                    enabled = true,
                    isBuiltIn = presetMatch != null,
                    isTofuPinned = presetMatch == null && normExplicitFp.isEmpty() && pinnedForRepo.isNotEmpty()
                )
            )
        }
        if (prefs != null) {
            if (!prefs.useIzzyOnDroid.get()) {
                return emptyList()
            }
            return prefs.readFDroidReposSafe().filter { it.enabled }
        }
        return listOf(FDroidRepoConfig.defaultPresets().first())
    }

    fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {},
        onHarvestComplete: (() -> Unit)? = null
    ): Flow<SourceUpdateResult<AppUpdate>> {
        var harvestNotified = false
        fun notifyHarvestOnce() {
            if (!harvestNotified) {
                harvestNotified = true
                onHarvestComplete?.invoke()
            }
        }

        return flow {
            val startedAt = SystemClock.elapsedRealtime()
            val enabledRepos = resolveActiveRepos()

            if (enabledRepos.isEmpty() || apps.isEmpty()) {
                notifyHarvestOnce()
                emit(SourceUpdateResult(emptyList(), successfulRequests = 0, failedRequests = 0))
                return@flow
            }

            val installedPackageSet = apps.map { it.packageName }.filter { it.isNotBlank() }.toSet()
            val repoIndexes = ArrayList<Pair<FDroidRepoConfig, IzzyIndexResponse>>(enabledRepos.size)
            val repoFailures = ArrayList<String>()
            var succeededRepos = 0

            for (repoConfig in enabledRepos) {
                try {
                    val idx = getOrFetchRepoIndex(repoConfig, installedPackageSet)
                    repoIndexes.add(repoConfig to idx)
                    succeededRepos++

                    // Signature-gated passive harvesting restricted to installed apps & trusted hosts (F-04a)
                    repoMappingManager?.let { manager ->
                        val appsMap = idx.apps.associateBy { it.packageName }
                        for (installedApp in apps) {
                            if (installedApp.isWatchedOnly || installedApp.versionCode < 0L) continue
                            val appInfo = appsMap[installedApp.packageName] ?: continue
                            val sourceUrl = appInfo.source?.takeIf { it.isNotBlank() } ?: continue
                            val packageList = idx.packages[installedApp.packageName].orEmpty()
                            val repoSigners = packageList.map { normalizeSha256(it.signer) }
                                .filter { it.isNotEmpty() && it != EMPTY_SHA256 }
                            val installedSigner = normalizeSha256(installedApp.signatureSha256)
                            val parsedBinding = manager.parseRepoUrl(sourceUrl)

                            if (repoSigners.isNotEmpty() && installedSigner.isNotEmpty() && installedSigner != EMPTY_SHA256) {
                                if (packageList.any { isSignatureMatching(installedApp.signatureSha256, it.signer) }) {
                                    manager.registerHarvestedRepo(installedApp.packageName, sourceUrl)
                                    if (parsedBinding != null && manager.isSignatureIncompatible(installedApp.packageName, parsedBinding, installedSigner)) {
                                        manager.clearSignatureIncompatible(installedApp.packageName)
                                    }
                                } else {
                                    manager.removeHarvestedRepo(installedApp.packageName)
                                    if (parsedBinding != null) {
                                        manager.markSignatureIncompatible(installedApp.packageName, parsedBinding, installedSigner)
                                    }
                                }
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (error: Throwable) {
                    Log.e("IzzyOnDroidRepository", "Failed to fetch F-Droid repo '${repoConfig.name}': ${error.message}")
                    val summary = when (error) {
                        is SecurityException -> "${repoConfig.name}: signature verification failed"
                        is java.net.SocketTimeoutException -> "${repoConfig.name}: timeout"
                        is java.net.UnknownHostException -> "${repoConfig.name}: DNS lookup failed"
                        is IOException -> "${repoConfig.name}: network error"
                        else -> "${repoConfig.name}: ${error.javaClass.simpleName}"
                    }
                    repoFailures.add(summary)
                }
            }
            notifyHarvestOnce()

            if (repoIndexes.isEmpty() && repoFailures.isNotEmpty()) {
                emit(
                    SourceUpdateResult(
                        updates = emptyList(),
                        successfulRequests = 0,
                        failedRequests = repoFailures.size,
                        failureSummary = repoFailures.first()
                    )
                )
                return@flow
            }

            val updates = mutableListOf<AppUpdate>()

            apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
                for (app in batch) {
                    val perRepoCandidates = ArrayList<AppUpdate>(repoIndexes.size)
                    for ((repoConfig, index) in repoIndexes) {
                        val packageList = index.packages[app.packageName].orEmpty()
                        if (packageList.isEmpty()) continue

                        val candidate = findBestPackage(
                            app = app,
                            packages = packageList,
                            requireApkHash = true
                        ) ?: continue

                        val normalizedApkSha256 = ApkIntegrityVerifier.normalizeSha256Hex(candidate.hash)
                            ?: continue
                        val safeApkRelPath = candidate.apkName.trim().removePrefix("/")
                        if (safeApkRelPath.isEmpty() || safeApkRelPath.contains("..")) continue

                        val baseUrl = repoConfig.normalizedUrl.trimEnd('/')
                        val downloadUrl = "$baseUrl/$safeApkRelPath"
                        if (!downloadUrl.startsWith("https://", ignoreCase = true)) continue

                        val appInfo = index.apps.firstOrNull { it.packageName == app.packageName }
                        val displayName = appInfo?.name?.takeIf { it.isNotBlank() } ?: app.name
                        val whatsNew = appInfo?.summary.orEmpty()

                        val update = AppUpdate(
                            name = displayName,
                            packageName = app.packageName,
                            version = candidate.versionName,
                            oldVersion = app.version,
                            versionCode = candidate.versionCode,
                            oldVersionCode = app.versionCode,
                            source = resolveSourceForRepo(repoConfig),
                            link = Link.Url(
                                link = downloadUrl,
                                size = candidate.size,
                                expectedSha256 = normalizedApkSha256
                            ),
                            whatsNew = whatsNew,
                            totalSize = candidate.size,
                            installOrigin = app.installOrigin,
                            installerPackageName = app.installerPackageName
                        )
                        if (update.isMeaningfulVersionUpgrade()) {
                            perRepoCandidates.add(update)
                        }
                    }

                    if (perRepoCandidates.isNotEmpty()) {
                        val sorted = perRepoCandidates.sortedByDescending { it.versionCode }
                        val best = sorted.first()
                        val merged = if (sorted.size > 1) {
                            best.copy(alternativeUpdates = sorted)
                        } else {
                            best
                        }
                        updates.add(merged)
                    }
                }
                onBatchSuccess(batch.map { it.packageName })
            }

            Log.i(
                "IzzyOnDroidRepository",
                "Found ${updates.size} verified F-Droid/Izzy update(s) across ${repoIndexes.size} repo(s) in ${SystemClock.elapsedRealtime() - startedAt} ms"
            )

            emit(
                SourceUpdateResult(
                    updates = updates,
                    successfulRequests = succeededRepos.coerceAtLeast(if (repoFailures.isEmpty()) 1 else 0),
                    failedRequests = repoFailures.size,
                    failureSummary = if (repoFailures.isNotEmpty() && updates.isEmpty()) repoFailures.first() else null
                )
            )
        }.catch { error ->
            if (error is CancellationException) throw error
            notifyHarvestOnce()
            Log.e("IzzyOnDroidRepository", "F-Droid/IzzyOnDroid update check failed", error)
            val failureSummary = when (error) {
                is SecurityException -> "signature verification failed"
                is java.net.SocketTimeoutException -> "timeout"
                is java.net.UnknownHostException -> "DNS lookup failed"
                is IOException -> "network error"
                else -> "processing error (${error.javaClass.simpleName})"
            }
            emit(
                SourceUpdateResult(
                    updates = emptyList(),
                    successfulRequests = 0,
                    failedRequests = 1,
                    failureSummary = failureSummary
                )
            )
        }
    }

    internal suspend fun getOrFetchRepoIndex(
        repo: FDroidRepoConfig = FDroidRepoConfig.defaultPresets().first(),
        installedPackages: Set<String>? = null
    ): IzzyIndexResponse = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val mem = inMemoryRepoCache[repo.id]
            if (mem != null &&
                now - mem.fetchedAtMillis <= IN_MEMORY_CACHE_TTL_MS &&
                (mem.coveredPackages == null || (installedPackages != null && mem.coveredPackages.containsAll(installedPackages)))
            ) {
                return@withContext mem.index
            }

            val tofuFp = pinnedTofuFingerprints[repo.id]
            val effectiveRepo = if (repo.normalizedFingerprint.isEmpty() && !tofuFp.isNullOrEmpty()) {
                repo.copy(fingerprint = tofuFp, isTofuPinned = true)
            } else {
                repo
            }

            // 1. Try F-Droid Index V2 (`entry.jar` cryptographic signature + `index-v2.json` SHA-256)
            var securityAbort: SecurityException? = null
            try {
                val v2Result = fetchOrGetVerifiedV2Index(effectiveRepo, installedPackages)
                if (v2Result != null) {
                    inMemoryRepoCache[effectiveRepo.id] = CachedRepoIndex(v2Result, System.currentTimeMillis(), installedPackages)
                    return@withContext v2Result
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (sec: SecurityException) {
                // Fail-Closed: NEVER fall back to V1 or stale disk cache on a cryptographic signature / rollback attack!
                securityAbort = sec
            } catch (v2Error: Throwable) {
                Log.w("IzzyOnDroidRepository", "F-Droid V2 fetch failed for ${effectiveRepo.name}, trying V1 signed JAR: ${v2Error.javaClass.simpleName}")
            }

            if (securityAbort != null) {
                throw securityAbort
            }

            // 2. Fallback to F-Droid Index V1 (`index-v1.jar` cryptographic signature)
            try {
                val v1Result = fetchOrGetVerifiedV1Index(effectiveRepo, installedPackages)
                inMemoryRepoCache[effectiveRepo.id] = CachedRepoIndex(v1Result, System.currentTimeMillis(), installedPackages)
                return@withContext v1Result
            } catch (ce: CancellationException) {
                throw ce
            } catch (sec: SecurityException) {
                throw sec
            } catch (v1Error: Throwable) {
                val cached = loadFromDiskCache(effectiveRepo, installedPackages)
                if (cached != null) {
                    Log.w("IzzyOnDroidRepository", "Network error for ${effectiveRepo.name}; using previously verified disk index.")
                    inMemoryRepoCache[effectiveRepo.id] = CachedRepoIndex(cached, System.currentTimeMillis(), installedPackages)
                    return@withContext cached
                }
                throw v1Error
            }
        }
    }

    private fun fetchOrGetVerifiedV2Index(
        repo: FDroidRepoConfig,
        installedPackages: Set<String>?
    ): IzzyIndexResponse? {
        val repoDir = getRepoCacheDir(repo.id)
        val targetV2File = File(repoDir, "index-v2.json")
        val meta = loadMetadata(repoDir)
        val hasValidLocalFile = targetV2File.isFile && targetV2File.length() > 0L

        val baseUrl = repo.normalizedUrl.trimEnd('/')
        val entryJarUrl = "$baseUrl/entry.jar"

        val requestBuilder = Request.Builder().url(entryJarUrl)
        if (meta.format == "v2" && hasValidLocalFile && !meta.etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", meta.etag)
        }
        if (meta.format == "v2" && hasValidLocalFile && !meta.lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", meta.lastModified)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && hasValidLocalFile && !meta.indexSha256.isNullOrBlank()) {
                return FDroidIndexEngine.streamParseV2IndexFile(targetV2File, installedPackages, gson)
            }
            if (response.code == 404 || response.code == 403) {
                return null
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} fetching entry.jar for ${repo.name}")
            }

            val newEtag = response.header("ETag")
            val newLastModified = response.header("Last-Modified")

            val verifiedJar = FDroidIndexEngine.verifyAndExtractEntryJson(
                jarStream = response.body.byteStream(),
                expectedCertSha256 = repo.normalizedFingerprint,
                gson = gson,
                allowUnpinnedCert = (repoBaseUrl != null || !repo.isBuiltIn) && repo.normalizedFingerprint.isEmpty(),
                tempDir = repoDir
            )
            val verifiedEntry = verifiedJar.payload
            val verifiedSignerFp = verifiedJar.signerSha256Fingerprint

            enforceAntiRollbackTimestamp(repo, verifiedEntry.timestamp)

            val indexRef = verifiedEntry.index
                ?: throw SecurityException("Verified entry.json for ${repo.name} is missing 'index' reference")
            val expectedIndexSha = ApkIntegrityVerifier.normalizeSha256Hex(indexRef.sha256)
                ?: throw SecurityException("Verified entry.json for ${repo.name} has invalid SHA-256 for index-v2.json")

            val relPath = indexRef.name.trim().removePrefix("/").ifBlank { "index-v2.json" }
            if (relPath.contains("..")) {
                throw SecurityException("Invalid index path in entry.json: $relPath")
            }
            val indexUrl = "$baseUrl/$relPath"

            val matchesServerSha = expectedIndexSha.equals(meta.serverIndexSha256 ?: meta.indexSha256, ignoreCase = true)
            val matchesTimestamp = verifiedEntry.timestamp > 0L && verifiedEntry.timestamp == meta.timestamp
            if (hasValidLocalFile && !meta.indexSha256.isNullOrBlank() && (matchesServerSha || matchesTimestamp)) {
                val diskSha = ApkIntegrityVerifier.computeFileSha256(targetV2File)
                if (diskSha.equals(meta.indexSha256, ignoreCase = true)) {
                    saveMetadata(
                        repoDir,
                        IndexMetadata(
                            etag = newEtag,
                            lastModified = newLastModified,
                            indexSha256 = diskSha,
                            format = "v2",
                            serverIndexSha256 = expectedIndexSha,
                            timestamp = verifiedEntry.timestamp.takeIf { it > 0L } ?: meta.timestamp
                        )
                    )
                    val parsedDisk = FDroidIndexEngine.streamParseV2IndexFile(targetV2File, installedPackages, gson)
                    recordTofuPinIfUnpinned(repo, verifiedSignerFp)
                    return parsedDisk
                }
            }

            // P-18 Part 1: Try incremental F-Droid V2 diff when our local index-v2.json is verified & its timestamp is in entry.diffs
            val baseTimestamp = meta.timestamp
            val diffRef = if (hasValidLocalFile &&
                meta.format == "v2" &&
                baseTimestamp > 0L &&
                verifiedEntry.timestamp > baseTimestamp &&
                !meta.indexSha256.isNullOrBlank()
            ) {
                verifiedEntry.diffs?.get(baseTimestamp.toString())
            } else {
                null
            }
            if (diffRef != null) {
                val patched = tryApplyVerifiedV2Diff(
                    repo = repo,
                    repoDir = repoDir,
                    baseUrl = baseUrl,
                    targetV2File = targetV2File,
                    expectedLocalSha = meta.indexSha256!!,
                    diffRef = diffRef,
                    newTimestamp = verifiedEntry.timestamp,
                    expectedServerIndexSha = expectedIndexSha,
                    entryEtag = newEtag,
                    entryLastModified = newLastModified,
                    installedPackages = installedPackages
                )
                if (patched != null) {
                    recordTofuPinIfUnpinned(repo, verifiedSignerFp)
                    return patched
                }
            }

            val parsed = downloadVerifyAndParseV2Index(
                repo = repo,
                repoDir = repoDir,
                url = indexUrl,
                expectedSha256 = expectedIndexSha,
                entryEtag = newEtag,
                entryLastModified = newLastModified,
                entryTimestamp = verifiedEntry.timestamp,
                installedPackages = installedPackages
            )
            recordTofuPinIfUnpinned(repo, verifiedSignerFp)
            return parsed
        }
    }

    private fun tryApplyVerifiedV2Diff(
        repo: FDroidRepoConfig,
        repoDir: File,
        baseUrl: String,
        targetV2File: File,
        expectedLocalSha: String,
        diffRef: FDroidV2IndexFileRef,
        newTimestamp: Long,
        expectedServerIndexSha: String,
        entryEtag: String?,
        entryLastModified: String?,
        installedPackages: Set<String>?
    ): IzzyIndexResponse? {
        val diffRelPath = diffRef.name.trim().removePrefix("/")
        if (diffRelPath.isEmpty() || diffRelPath.contains("..")) return null
        val expectedDiffSha = ApkIntegrityVerifier.normalizeSha256Hex(diffRef.sha256) ?: return null
        val diffUrl = "$baseUrl/$diffRelPath"
        if (!diffUrl.startsWith("https://", ignoreCase = true)) return null

        val diskSha = ApkIntegrityVerifier.computeFileSha256(targetV2File)
        if (!diskSha.equals(expectedLocalSha, ignoreCase = true)) {
            return null
        }

        val diffTempFile = File(repoDir, "diff-v2.json.tmp")
        val patchedTempFile = File(repoDir, "index-v2.patched.tmp")
        if (diffTempFile.exists()) diffTempFile.delete()
        if (patchedTempFile.exists()) patchedTempFile.delete()

        return try {
            val req = Request.Builder().url(diffUrl).build()
            okHttpClient.newCall(req).execute().use { diffResponse ->
                if (!diffResponse.isSuccessful) {
                    Log.w("IzzyOnDroidRepository", "HTTP ${diffResponse.code} fetching F-Droid V2 diff for ${repo.name}, falling back to full index")
                    return null
                }
                var totalBytes = 0L
                val buffer = ByteArray(16_384)
                FileOutputStream(diffTempFile).use { out ->
                    diffResponse.body.byteStream().use { input ->
                        var r = input.read(buffer)
                        while (r != -1) {
                            totalBytes += r
                            if (totalBytes > MAX_INDEX_DOWNLOAD_BYTES) {
                                throw IOException("F-Droid V2 diff exceeds maximum size cap")
                            }
                            out.write(buffer, 0, r)
                            r = input.read(buffer)
                        }
                    }
                    out.flush()
                }
            }

            // Fail-Closed: cryptographic SHA-256 verification of diff payload against signed entry.json
            ApkIntegrityVerifier.verifyFileSha256(diffTempFile, expectedDiffSha)

            FDroidIndexEngine.streamApplyV2DiffPatch(
                baseIndexFile = targetV2File,
                diffFile = diffTempFile,
                outputFile = patchedTempFile,
                gson = gson
            )

            val parsed = FDroidIndexEngine.streamParseV2IndexFile(patchedTempFile, installedPackages, gson)
            parsed.repo?.timestamp?.takeIf { it > 0L }?.let { enforceAntiRollbackTimestamp(repo, it) }

            val newLocalSha = ApkIntegrityVerifier.computeFileSha256(patchedTempFile)
            if (targetV2File.exists()) targetV2File.delete()
            if (!patchedTempFile.renameTo(targetV2File)) {
                patchedTempFile.copyTo(targetV2File, overwrite = true)
                patchedTempFile.delete()
            }

            saveMetadata(
                repoDir,
                IndexMetadata(
                    etag = entryEtag,
                    lastModified = entryLastModified,
                    indexSha256 = newLocalSha,
                    format = "v2",
                    serverIndexSha256 = expectedServerIndexSha,
                    timestamp = newTimestamp
                )
            )
            Log.i("IzzyOnDroidRepository", "Applied incremental F-Droid V2 diff (${diffTempFile.length()} bytes) for ${repo.name}")
            parsed
        } catch (sec: SecurityException) {
            throw sec
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.w("IzzyOnDroidRepository", "F-Droid V2 diff patch failed for ${repo.name}, falling back to full index: ${t.javaClass.simpleName}")
            null
        } finally {
            if (diffTempFile.exists()) diffTempFile.delete()
            if (patchedTempFile.exists()) patchedTempFile.delete()
        }
    }

    private fun downloadVerifyAndParseV2Index(
        repo: FDroidRepoConfig,
        repoDir: File,
        url: String,
        expectedSha256: String,
        entryEtag: String?,
        entryLastModified: String?,
        entryTimestamp: Long = 0L,
        installedPackages: Set<String>?
    ): IzzyIndexResponse {
        val targetFile = File(repoDir, "index-v2.json")
        val tempFile = File(repoDir, "index-v2.json.tmp")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { indexResponse ->
                if (!indexResponse.isSuccessful) {
                    throw IOException("HTTP ${indexResponse.code} downloading index-v2.json for ${repo.name}")
                }
                var totalBytes = 0L
                val buffer = ByteArray(16_384)
                FileOutputStream(tempFile).use { out ->
                    indexResponse.body.byteStream().use { input ->
                        var r = input.read(buffer)
                        while (r != -1) {
                            totalBytes += r
                            if (totalBytes > MAX_INDEX_DOWNLOAD_BYTES) {
                                throw IOException("index-v2.json exceeds maximum size cap ($MAX_INDEX_DOWNLOAD_BYTES bytes)")
                            }
                            out.write(buffer, 0, r)
                            r = input.read(buffer)
                        }
                    }
                    out.flush()
                }
            }

            // Mandatory cryptographic SHA-256 verification against signed entry.json!
            ApkIntegrityVerifier.verifyFileSha256(tempFile, expectedSha256)

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val parsed = FDroidIndexEngine.streamParseV2IndexFile(targetFile, installedPackages, gson)
            val resolvedTs = parsed.repo?.timestamp?.takeIf { it > 0L } ?: entryTimestamp
            resolvedTs.takeIf { it > 0L }?.let { enforceAntiRollbackTimestamp(repo, it) }
            saveMetadata(
                repoDir,
                IndexMetadata(
                    etag = entryEtag,
                    lastModified = entryLastModified,
                    indexSha256 = expectedSha256,
                    format = "v2",
                    serverIndexSha256 = expectedSha256,
                    timestamp = entryTimestamp.takeIf { it > 0L } ?: resolvedTs
                )
            )
            return parsed
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun fetchOrGetVerifiedV1Index(
        repo: FDroidRepoConfig,
        installedPackages: Set<String>?
    ): IzzyIndexResponse {
        val repoDir = getRepoCacheDir(repo.id)
        val targetFile = File(repoDir, "index-v1.json")
        val meta = loadMetadata(repoDir)
        val hasValidLocalFile = targetFile.isFile && targetFile.length() > 0L

        val baseUrl = repo.normalizedUrl.trimEnd('/')
        val jarUrl = "$baseUrl/index-v1.jar"

        val requestBuilder = Request.Builder().url(jarUrl)
        if (meta.format == "v1" && hasValidLocalFile && !meta.etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", meta.etag)
        }
        if (meta.format == "v1" && hasValidLocalFile && !meta.lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", meta.lastModified)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304 && hasValidLocalFile) {
                return FDroidIndexEngine.streamParseV1IndexFile(targetFile, installedPackages, gson)
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} fetching index-v1.jar for ${repo.name}")
            }

            val newEtag = response.header("ETag")
            val newLastModified = response.header("Last-Modified")

            val verifiedSignerFp = FDroidIndexEngine.verifyAndExtractV1IndexJarToFile(
                jarStream = response.body.byteStream(),
                expectedCertSha256 = repo.normalizedFingerprint,
                targetFile = targetFile,
                allowUnpinnedCert = (repoBaseUrl != null || !repo.isBuiltIn) && repo.normalizedFingerprint.isEmpty()
            )

            val parsed = FDroidIndexEngine.streamParseV1IndexFile(targetFile, installedPackages, gson)
            parsed.repo?.timestamp?.takeIf { it > 0L }?.let { enforceAntiRollbackTimestamp(repo, it) }
            val sha = ApkIntegrityVerifier.computeFileSha256(targetFile)
            saveMetadata(repoDir, IndexMetadata(newEtag, newLastModified, sha, "v1"))
            recordTofuPinIfUnpinned(repo, verifiedSignerFp)
            return parsed
        }
    }

    private fun recordTofuPinIfUnpinned(repo: FDroidRepoConfig, verifiedSignerFp: String) {
        if (repo.normalizedFingerprint.isNotEmpty()) return
        val cleanFp = FDroidRepoConfig.normalizeFingerprint(verifiedSignerFp)
        if (!FDroidRepoConfig.isValidSha256Fingerprint(cleanFp)) return
        pinnedTofuFingerprints[repo.id] = cleanFp
        if (!repo.isBuiltIn) {
            prefs?.pinFDroidRepoTofu(repo.id, cleanFp)
        }
        onTofuFingerprintVerified?.invoke(cleanFp)
    }

    private fun enforceAntiRollbackTimestamp(repo: FDroidRepoConfig, candidateTimestamp: Long) {
        if (candidateTimestamp <= 0L) return
        val p = prefs ?: return
        val previous = p.getFDroidRepoTimestamp(repo.id)
        if (previous > 0L && candidateTimestamp < previous) {
            throw SecurityException(
                "Blocked F-Droid replay/rollback attack for ${repo.name}: timestamp $candidateTimestamp < $previous"
            )
        }
        p.recordFDroidRepoTimestamp(repo.id, candidateTimestamp)
    }

    /**
     * Kept for unit-test atomic extraction verification (`IzzyOnDroidTest`).
     * Production network paths use [FDroidIndexEngine.verifyAndExtractV1IndexJarToFile].
     */
    internal fun parseZipStreamAndSave(inputStream: InputStream): IzzyIndexResponse {
        val repoDir = getRepoCacheDir(FDroidRepoConfig.ID_IZZYONDROID)
        val targetFile = File(repoDir, "index-v1.json")
        val tempFile = File(repoDir, "index-v1.json.tmp")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        try {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "index-v1.json") {
                        FileOutputStream(tempFile).use { out ->
                            val buffer = ByteArray(16_384)
                            var bytesRead: Int
                            var total = 0L
                            while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                total += bytesRead
                                if (total > MAX_INDEX_DOWNLOAD_BYTES) {
                                    throw IOException("index-v1.json exceeds maximum size cap")
                                }
                                out.write(buffer, 0, bytesRead)
                            }
                        }

                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists()) targetFile.delete()
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }

                        return FileInputStream(targetFile).use { fileIn ->
                            InputStreamReader(fileIn, Charsets.UTF_8).use { reader ->
                                gson.fromJson(reader, IzzyIndexResponse::class.java)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            throw IOException("index-v1.json not found inside index-v1.jar")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun loadFromDiskCache(
        repo: FDroidRepoConfig,
        installedPackages: Set<String>?
    ): IzzyIndexResponse? {
        val repoDir = getRepoCacheDir(repo.id)
        val meta = loadMetadata(repoDir)
        val v2File = File(repoDir, "index-v2.json")
        if (v2File.isFile && v2File.length() > 0L) {
            if (!meta.indexSha256.isNullOrBlank()) {
                val verified = runCatching {
                    ApkIntegrityVerifier.verifyFileSha256(v2File, meta.indexSha256)
                    FDroidIndexEngine.streamParseV2IndexFile(v2File, installedPackages, gson)
                }.getOrNull()
                if (verified != null) return verified
            } else if (cacheDirectory != null) {
                val seeded = runCatching {
                    FDroidIndexEngine.streamParseV2IndexFile(v2File, installedPackages, gson)
                }.getOrNull()
                if (seeded != null) return seeded
            }
        }
        val v1File = File(repoDir, "index-v1.json")
        if (v1File.isFile && v1File.length() > 0L) {
            if (!meta.indexSha256.isNullOrBlank()) {
                val verified = runCatching {
                    ApkIntegrityVerifier.verifyFileSha256(v1File, meta.indexSha256)
                    FDroidIndexEngine.streamParseV1IndexFile(v1File, installedPackages, gson)
                }.getOrNull()
                if (verified != null) return verified
            } else if (cacheDirectory != null) {
                val seeded = runCatching {
                    FDroidIndexEngine.streamParseV1IndexFile(v1File, installedPackages, gson)
                }.getOrNull()
                if (seeded != null) return seeded
            }
        }
        return null
    }

    internal data class IndexMetadata(
        val etag: String? = null,
        val lastModified: String? = null,
        val indexSha256: String? = null,
        val format: String = "v2",
        val serverIndexSha256: String? = null,
        val timestamp: Long = 0L
    )

    private fun loadMetadata(repoDir: File): IndexMetadata = runCatching {
        val file = File(repoDir, "index.meta")
        if (!file.exists()) return IndexMetadata()
        val lines = file.readLines()
        val indexSha = lines.getOrNull(2)?.takeIf { it.isNotBlank() }
        IndexMetadata(
            etag = lines.getOrNull(0)?.takeIf { it.isNotBlank() },
            lastModified = lines.getOrNull(1)?.takeIf { it.isNotBlank() },
            indexSha256 = indexSha,
            format = lines.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "v1",
            serverIndexSha256 = lines.getOrNull(4)?.takeIf { it.isNotBlank() } ?: indexSha,
            timestamp = lines.getOrNull(5)?.toLongOrNull() ?: 0L
        )
    }.getOrDefault(IndexMetadata())

    private fun saveMetadata(repoDir: File, metadata: IndexMetadata) {
        runCatching {
            val file = File(repoDir, "index.meta")
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    append(metadata.etag.orEmpty()).append('\n')
                    append(metadata.lastModified.orEmpty()).append('\n')
                    append(metadata.indexSha256.orEmpty()).append('\n')
                    append(metadata.format).append('\n')
                    append(metadata.serverIndexSha256.orEmpty()).append('\n')
                    append(metadata.timestamp.takeIf { it > 0L }?.toString().orEmpty())
                }
            )
        }
    }

    internal fun findBestPackage(
        app: AppInstalled,
        packages: List<IzzyPackageInfo>,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray(),
        currentSdk: Int = Build.VERSION.SDK_INT,
        requireApkHash: Boolean = false
    ): IzzyPackageInfo? {
        val isWatched = app.isWatchedOnly || app.versionCode < 0L
        val compatible = packages.filter { pkg ->
            pkg.versionCode > app.versionCode &&
                isVerifiableVersionUpgrade(app.version, pkg.versionName, app.versionCode, pkg.versionCode) &&
                (isWatched || isSignatureMatching(app.signatureSha256, pkg.signer)) &&
                (!requireApkHash || ApkIntegrityVerifier.normalizeSha256Hex(pkg.hash) != null) &&
                isAbiCompatible(pkg.nativecode, supportedAbis) &&
                isSdkCompatible(pkg.minSdkVersion, currentSdk)
        }
        return compatible.maxByOrNull { it.versionCode }
    }

    fun normalizeSha256(hash: String?): String =
        hash.orEmpty().replace(":", "").replace(" ", "").trim().lowercase(Locale.ROOT)

    internal fun isSignatureMatching(installedSha256: String?, izzySigner: String?): Boolean {
        val installedSigner = normalizeSha256(installedSha256)
        val repoSigner = normalizeSha256(izzySigner)
        if (installedSigner == EMPTY_SHA256 || repoSigner == EMPTY_SHA256) return false
        if (repoSigner.isEmpty() || installedSigner.isEmpty()) return false
        // Support comma-separated multi-signer SHA-256 sets from Extensions.getSignatureSha256()
        val installedSet = installedSigner.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return if (installedSet.size <= 1) {
            installedSigner == repoSigner
        } else {
            repoSigner in installedSet
        }
    }

    internal fun isAbiCompatible(
        nativeCode: List<String>?,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
    ): Boolean {
        if (nativeCode.isNullOrEmpty()) return true
        if (supportedAbis.isEmpty()) return true
        val canonicalSupported = supportedAbis.map { canonicalizeAbi(it) }.toSet()
        return nativeCode.any { raw ->
            val canon = canonicalizeAbi(raw)
            canon in canonicalSupported
        }
    }

    internal fun isSdkCompatible(
        minSdkVersion: Int,
        currentSdk: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (minSdkVersion <= 0) return true
        return currentSdk >= minSdkVersion
    }
}
