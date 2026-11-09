package com.apkup.repository

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.fdroid.FDroidV2Entry
import com.apkup.data.fdroid.FDroidV2Index
import com.apkup.data.fdroid.toIzzyIndexResponse
import com.apkup.data.izzy.IzzyIndexResponse
import com.apkup.data.izzy.IzzyPackageInfo
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.IzzyOnDroidSource
import com.apkup.data.ui.Link
import com.apkup.data.ui.SourceUpdateResult
import com.apkup.data.ui.isMeaningfulVersionUpgrade
import com.apkup.data.ui.isVerifiableVersionUpgrade
import com.apkup.prefs.Prefs
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
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class IzzyOnDroidRepository(
    private val context: Context? = null,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val prefs: Prefs? = null,
    private val repoMappingManager: RepoMappingManager? = null,
    private val cacheDirectory: File? = null
) {
    companion object {
        const val ENTRY_JSON_URL = "https://apt.izzysoft.de/fdroid/repo/entry.json"
        const val INDEX_V2_JSON_URL = "https://apt.izzysoft.de/fdroid/repo/index-v2.json"
        const val INDEX_JAR_URL = "https://apt.izzysoft.de/fdroid/repo/index-v1.jar"
        const val REPO_BASE_URL = "https://apt.izzysoft.de/fdroid/repo/"
        internal const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        private const val BATCH_NOTIFY_SIZE = 25
    }

    private val mutex = Mutex()
    private var inMemoryIndex: IzzyIndexResponse? = null
    private var inMemoryIndexTimestamp: Long = 0L

    private fun getCacheDir(): File {
        val dir = cacheDirectory
            ?: context?.cacheDir?.let { File(it, "izzy_cache") }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "izzy_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getIndexV2JsonCacheFile(): File {
        val file = File(getCacheDir(), "index-v2.json")
        file.parentFile?.mkdirs()
        return file
    }

    private fun getIndexJsonCacheFile(): File {
        val file = File(getCacheDir(), "index-v1.json")
        file.parentFile?.mkdirs()
        return file
    }

    private fun getMetaCacheFile(): File {
        val file = File(getCacheDir(), "index.meta")
        file.parentFile?.mkdirs()
        return file
    }

    fun updates(
        apps: List<AppInstalled>,
        onBatchSuccess: suspend (List<String>) -> Unit = {}
    ): Flow<SourceUpdateResult<AppUpdate>> = flow {
        val startedAt = SystemClock.elapsedRealtime()
        val index = getOrFetchIndex()

        repoMappingManager?.let { manager ->
            index.apps.forEach { appInfo ->
                val sourceUrl = appInfo.source
                if (!sourceUrl.isNullOrBlank()) {
                    manager.registerHarvestedRepo(appInfo.packageName, sourceUrl)
                }
            }
        }

        val appsMap = index.apps.associateBy { it.packageName }
        val updates = mutableListOf<AppUpdate>()

        apps.chunked(BATCH_NOTIFY_SIZE).forEach { batch ->
            for (app in batch) {
                val packageList = index.packages[app.packageName].orEmpty()
                if (packageList.isNotEmpty()) {
                    val latestRepoPkg = packageList.maxByOrNull { it.versionCode } ?: packageList.first()
                    if (app.versionCode >= latestRepoPkg.versionCode) {
                        Log.d(
                            "IzzyOnDroidRepository",
                            "Package ${app.packageName} found in Izzy, but current version ${app.versionCode} >= repo version ${latestRepoPkg.versionCode}"
                        )
                    } else {
                        val installedSigner = normalizeSha256(app.signatureSha256)
                        val repoSigner = normalizeSha256(latestRepoPkg.signer)
                        val signatureMatches = isSignatureMatching(app.signatureSha256, latestRepoPkg.signer)
                        if (!signatureMatches) {
                            Log.w(
                                "IzzyOnDroidRepository",
                                "Package ${app.packageName} update available in Izzy, but signature mismatch! Local: $installedSigner vs Repo: $repoSigner"
                            )
                        }
                    }
                }

                val candidate = findBestPackage(app, packageList)
                if (candidate != null) {
                    Log.i(
                        "IzzyOnDroidRepository",
                        "Valid Izzy update found for ${app.packageName}: v${candidate.versionName} (${candidate.versionCode})"
                    )
                    val appInfo = appsMap[app.packageName]
                    val displayName = appInfo?.name?.takeIf { it.isNotBlank() } ?: app.name
                    val whatsNew = appInfo?.summary.orEmpty()
                    val downloadUrl = "$REPO_BASE_URL${candidate.apkName}"
                    val update = AppUpdate(
                        name = displayName,
                        packageName = app.packageName,
                        version = candidate.versionName,
                        oldVersion = app.version,
                        versionCode = candidate.versionCode,
                        oldVersionCode = app.versionCode,
                        source = IzzyOnDroidSource,
                        link = Link.Url(link = downloadUrl, size = candidate.size),
                        whatsNew = whatsNew,
                        totalSize = candidate.size,
                        installOrigin = app.installOrigin,
                        installerPackageName = app.installerPackageName
                    )
                    if (update.isMeaningfulVersionUpgrade()) {
                        updates.add(update)
                    }
                }
            }
            onBatchSuccess(batch.map { it.packageName })
        }

        Log.i("IzzyOnDroidRepository", "Found ${updates.size} eligible updates matching package & certificate SHA-256")

        emit(
            SourceUpdateResult(
                updates = updates,
                successfulRequests = apps.size,
                failedRequests = 0,
                failureSummary = null
            )
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.e("IzzyOnDroidRepository", "IzzyOnDroid update check failed", error)
        val failureSummary = when (error) {
            is java.net.SocketTimeoutException -> "timeout"
            is java.net.UnknownHostException -> "DNS lookup failed"
            is IOException -> error.message ?: "network error"
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

    internal suspend fun getOrFetchIndex(): IzzyIndexResponse = mutex.withLock {
        withContext(Dispatchers.IO) {
            inMemoryIndex?.let { return@withContext it }

            // 1. Try F-Droid Index V2 (modern entry.json + index-v2.json)
            try {
                val v2Result = fetchOrGetV2Index()
                if (v2Result != null) {
                    inMemoryIndex = v2Result
                    inMemoryIndexTimestamp = System.currentTimeMillis()
                    return@withContext v2Result
                }
            } catch (v2Error: Throwable) {
                if (v2Error is CancellationException) throw v2Error
                Log.w("IzzyOnDroidRepository", "F-Droid V2 fetch failed, trying V1 fallback: ${v2Error.message}")
            }

            // 2. Fallback to F-Droid Index V1 (index-v1.jar)
            try {
                val v1Result = fetchOrGetV1Index()
                inMemoryIndex = v1Result
                inMemoryIndexTimestamp = System.currentTimeMillis()
                return@withContext v1Result
            } catch (v1Error: Throwable) {
                if (v1Error is CancellationException) throw v1Error
                val cached = loadFromDiskCache()
                if (cached != null) {
                    Log.w("IzzyOnDroidRepository", "Network error (${v1Error.message}): Falling back to cached disk index.")
                    inMemoryIndex = cached
                    inMemoryIndexTimestamp = System.currentTimeMillis()
                    return@withContext cached
                }
                throw v1Error
            }
        }
    }

    private fun fetchOrGetV2Index(): IzzyIndexResponse? {
        val targetV2File = getIndexV2JsonCacheFile()
        val hasValidLocalFile = targetV2File.exists() && targetV2File.length() > 0L
        val cachedOnDisk = if (hasValidLocalFile) loadFromDiskCacheV2() else null
        val meta = if (hasValidLocalFile && cachedOnDisk != null) loadMetadata() else IndexMetadata()

        val requestBuilder = Request.Builder().url(ENTRY_JSON_URL)
        if (meta.format == "v2" && !meta.etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", meta.etag)
        }
        if (meta.format == "v2" && !meta.lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", meta.lastModified)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304) {
                if (cachedOnDisk != null && targetV2File.exists() && targetV2File.length() > 0L) {
                    Log.i("IzzyOnDroidRepository", "F-Droid V2 entry unchanged: HTTP 304")
                    Log.i("IzzyOnDroidRepository", "Parsed ${cachedOnDisk.apps.size} apps from IzzyOnDroid V2 index")
                    return cachedOnDisk
                } else {
                    Log.w("IzzyOnDroidRepository", "Server returned HTTP 304 for entry.json but local index-v2.json is missing. Forcing fresh download.")
                    return fetchFreshV2Index()
                }
            }

            if (response.isSuccessful) {
                val newEtag = response.header("ETag")
                val newLastModified = response.header("Last-Modified")
                val entry = runCatching {
                    gson.fromJson(response.body.charStream(), FDroidV2Entry::class.java)
                }.getOrNull() ?: return null

                val expectedSha = entry.index?.sha256.orEmpty()
                val indexUrl = if (!entry.index?.name.isNullOrBlank()) {
                    val rel = entry.index.name.removePrefix("/")
                    "$REPO_BASE_URL$rel"
                } else {
                    INDEX_V2_JSON_URL
                }

                if (cachedOnDisk != null && expectedSha.isNotBlank() && expectedSha.equals(meta.indexSha256, ignoreCase = true)) {
                    Log.i("IzzyOnDroidRepository", "F-Droid V2 index unchanged (matching SHA-256: $expectedSha)")
                    saveMetadata(IndexMetadata(newEtag, newLastModified, expectedSha, "v2"))
                    return cachedOnDisk
                }

                return downloadAndSaveV2Index(indexUrl, expectedSha, newEtag, newLastModified)
            }

            if (response.code == 404 || response.code == 403) {
                Log.w("IzzyOnDroidRepository", "F-Droid V2 entry.json not available (HTTP ${response.code}). Falling back to V1.")
                return null
            }

            if (cachedOnDisk != null && targetV2File.exists() && targetV2File.length() > 0L) {
                Log.w("IzzyOnDroidRepository", "HTTP ${response.code} fetching entry.json: Falling back to cached V2 disk index.")
                return cachedOnDisk
            }

            return null
        }
    }

    private fun fetchFreshV2Index(): IzzyIndexResponse? {
        val entryRequest = Request.Builder().url(ENTRY_JSON_URL).build()
        okHttpClient.newCall(entryRequest).execute().use { response ->
            if (!response.isSuccessful) return null
            val newEtag = response.header("ETag")
            val newLastModified = response.header("Last-Modified")
            val entry = runCatching {
                gson.fromJson(response.body.charStream(), FDroidV2Entry::class.java)
            }.getOrNull() ?: return null

            val expectedSha = entry.index?.sha256.orEmpty()
            val indexUrl = if (!entry.index?.name.isNullOrBlank()) {
                val rel = entry.index.name.removePrefix("/")
                "$REPO_BASE_URL$rel"
            } else {
                INDEX_V2_JSON_URL
            }
            return downloadAndSaveV2Index(indexUrl, expectedSha, newEtag, newLastModified)
        }
    }

    private fun downloadAndSaveV2Index(
        url: String,
        expectedSha256: String,
        entryEtag: String?,
        entryLastModified: String?
    ): IzzyIndexResponse {
        val targetFile = getIndexV2JsonCacheFile()
        val tempFile = File(getCacheDir(), "index-v2.json.tmp")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { indexResponse ->
                if (!indexResponse.isSuccessful) {
                    throw IOException("HTTP ${indexResponse.code} when downloading index-v2.json from $url")
                }
                FileOutputStream(tempFile).use { out ->
                    indexResponse.body.byteStream().copyTo(out)
                }
            }

            if (expectedSha256.isNotBlank()) {
                val actualSha256 = calculateFileSha256(tempFile)
                if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                    throw IOException("Index SHA-256 mismatch for index-v2.json (expected: $expectedSha256, actual: $actualSha256)")
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            val sizeKb = targetFile.length() / 1024
            Log.i("IzzyOnDroidRepository", "Successfully downloaded index-v2.json ($sizeKb KB)")

            val parsed = FileInputStream(targetFile).use { fileIn ->
                InputStreamReader(fileIn, Charsets.UTF_8).use { reader ->
                    val v2 = gson.fromJson(reader, FDroidV2Index::class.java)
                        ?: throw IOException("Empty or invalid index-v2.json")
                    v2.toIzzyIndexResponse()
                }
            }
            saveMetadata(IndexMetadata(entryEtag, entryLastModified, expectedSha256, "v2"))
            Log.i("IzzyOnDroidRepository", "Parsed ${parsed.apps.size} apps from IzzyOnDroid V2 index")
            return parsed
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun fetchOrGetV1Index(): IzzyIndexResponse {
        val targetFile = getIndexJsonCacheFile()
        val hasValidLocalFile = targetFile.exists() && targetFile.length() > 0L
        val cachedOnDisk = if (hasValidLocalFile) loadFromDiskCacheV1() else null
        val meta = if (hasValidLocalFile && cachedOnDisk != null) loadMetadata() else IndexMetadata()

        Log.i("IzzyOnDroidRepository", "Checking IzzyOnDroid V1 repository (ETag: ${meta.etag ?: "none"})")

        val requestBuilder = Request.Builder().url(INDEX_JAR_URL)
        if (meta.format == "v1" && !meta.etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", meta.etag)
        }
        if (meta.format == "v1" && !meta.lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", meta.lastModified)
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 304) {
                if (cachedOnDisk != null && targetFile.exists() && targetFile.length() > 0L) {
                    Log.i("IzzyOnDroidRepository", "V1 index unchanged: HTTP 304")
                    Log.i("IzzyOnDroidRepository", "Parsed ${cachedOnDisk.apps.size} apps from IzzyOnDroid V1 index")
                    return cachedOnDisk
                } else {
                    Log.w("IzzyOnDroidRepository", "Server returned HTTP 304 but local index-v1.json is missing or corrupt. Forcing fresh download.")
                    return fetchFreshIndex()
                }
            }

            if (response.isSuccessful) {
                Log.i("IzzyOnDroidRepository", "Downloaded index-v1.jar: HTTP 200")
                val newEtag = response.header("ETag")
                val newLastModified = response.header("Last-Modified")

                val parsed = parseZipStreamAndSave(response.body.byteStream())
                saveMetadata(IndexMetadata(newEtag, newLastModified, null, "v1"))
                Log.i("IzzyOnDroidRepository", "Parsed ${parsed.apps.size} apps from IzzyOnDroid V1 index")
                return parsed
            }

            if (cachedOnDisk != null && targetFile.exists() && targetFile.length() > 0L) {
                Log.w("IzzyOnDroidRepository", "HTTP ${response.code}: Falling back to cached disk index.")
                Log.i("IzzyOnDroidRepository", "Parsed ${cachedOnDisk.apps.size} apps from IzzyOnDroid index")
                return cachedOnDisk
            }

            throw IOException("HTTP ${response.code} when fetching IzzyOnDroid index")
        }
    }

    private fun fetchFreshIndex(): IzzyIndexResponse {
        val request = Request.Builder().url(INDEX_JAR_URL).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.i("IzzyOnDroidRepository", "Downloaded index-v1.jar: HTTP 200")
                val newEtag = response.header("ETag")
                val newLastModified = response.header("Last-Modified")

                val parsed = parseZipStreamAndSave(response.body.byteStream())
                saveMetadata(IndexMetadata(newEtag, newLastModified, null, "v1"))
                Log.i("IzzyOnDroidRepository", "Parsed ${parsed.apps.size} apps from IzzyOnDroid index")
                return parsed
            }
            throw IOException("HTTP ${response.code} when fetching fresh IzzyOnDroid index")
        }
    }

    internal fun parseZipStreamAndSave(inputStream: java.io.InputStream): IzzyIndexResponse {
        val targetFile = getIndexJsonCacheFile()
        val cacheDir = getCacheDir()
        val tempFile = File(cacheDir, "index-v1.json.tmp")
        tempFile.parentFile?.mkdirs()
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "index-v1.json") {
                        FileOutputStream(tempFile).use { out ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                            }
                        }

                        targetFile.parentFile?.mkdirs()
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }

                        val sizeKb = targetFile.length() / 1024
                        Log.i("IzzyOnDroidRepository", "Successfully extracted index-v1.json ($sizeKb KB)")

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
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun loadFromDiskCacheV2(): IzzyIndexResponse? = runCatching {
        val file = getIndexV2JsonCacheFile()
        if (!file.exists() || file.length() <= 0L) return null
        FileInputStream(file).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                val v2 = gson.fromJson(reader, FDroidV2Index::class.java)
                v2?.toIzzyIndexResponse()
            }
        }
    }.getOrNull()

    private fun loadFromDiskCacheV1(): IzzyIndexResponse? = runCatching {
        val file = getIndexJsonCacheFile()
        if (!file.exists() || file.length() <= 0L) return null
        FileInputStream(file).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, IzzyIndexResponse::class.java)
            }
        }
    }.getOrNull()

    private fun loadFromDiskCache(): IzzyIndexResponse? {
        val v2 = loadFromDiskCacheV2()
        if (v2 != null) return v2
        return loadFromDiskCacheV1()
    }

    internal data class IndexMetadata(
        val etag: String? = null,
        val lastModified: String? = null,
        val indexSha256: String? = null,
        val format: String = "v2"
    )

    private fun loadMetadata(): IndexMetadata = runCatching {
        val file = getMetaCacheFile()
        if (!file.exists()) return IndexMetadata()
        val lines = file.readLines()
        IndexMetadata(
            etag = lines.getOrNull(0)?.takeIf { it.isNotBlank() },
            lastModified = lines.getOrNull(1)?.takeIf { it.isNotBlank() },
            indexSha256 = lines.getOrNull(2)?.takeIf { it.isNotBlank() },
            format = lines.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "v1"
        )
    }.getOrDefault(IndexMetadata())

    private fun saveMetadata(metadata: IndexMetadata) {
        runCatching {
            val file = getMetaCacheFile()
            file.parentFile?.mkdirs()
            file.writeText("${metadata.etag.orEmpty()}\n${metadata.lastModified.orEmpty()}\n${metadata.indexSha256.orEmpty()}\n${metadata.format}")
        }
    }

    internal fun findBestPackage(
        app: AppInstalled,
        packages: List<IzzyPackageInfo>,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray(),
        currentSdk: Int = Build.VERSION.SDK_INT
    ): IzzyPackageInfo? {
        val compatible = packages.filter { pkg ->
            pkg.versionCode > app.versionCode &&
                isVerifiableVersionUpgrade(app.version, pkg.versionName, app.versionCode, pkg.versionCode) &&
                isSignatureMatching(app.signatureSha256, pkg.signer) &&
                isAbiCompatible(pkg.nativecode, supportedAbis) &&
                isSdkCompatible(pkg.minSdkVersion, currentSdk)
        }
        return compatible.maxByOrNull { it.versionCode }
    }

    fun normalizeSha256(hash: String?): String =
        hash.orEmpty().replace(":", "").replace(" ", "").trim().lowercase()

    internal fun isSignatureMatching(installedSha256: String?, izzySigner: String?): Boolean {
        val installedSigner = normalizeSha256(installedSha256)
        val repoSigner = normalizeSha256(izzySigner)
        if (installedSigner == EMPTY_SHA256 || repoSigner == EMPTY_SHA256) return false
        return installedSigner.isNotEmpty() && installedSigner == repoSigner
    }

    internal fun isAbiCompatible(
        nativeCode: List<String>?,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
    ): Boolean {
        if (nativeCode.isNullOrEmpty()) return true
        if (supportedAbis.isEmpty()) return true
        return nativeCode.any { it in supportedAbis }
    }

    internal fun isSdkCompatible(
        minSdkVersion: Int,
        currentSdk: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (minSdkVersion <= 0) return true
        return currentSdk >= minSdkVersion
    }

    private fun calculateFileSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
