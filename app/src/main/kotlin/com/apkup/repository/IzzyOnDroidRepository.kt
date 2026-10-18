package com.apkup.repository

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
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
    private val cacheDirectory: File? = null
) {
    companion object {
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

            val targetFile = getIndexJsonCacheFile()
            val hasValidLocalFile = targetFile.exists() && targetFile.length() > 0L
            val cachedOnDisk = if (hasValidLocalFile) loadFromDiskCache() else null

            // Sende ETag / If-Modified-Since NUR DANN an den Server, wenn die lokale targetFile tatsächlich existiert und nicht leer ist
            val meta = if (hasValidLocalFile && cachedOnDisk != null) loadMetadata() else Pair(null, null)
            val (etag, lastModified) = meta

            Log.i("IzzyOnDroidRepository", "Checking IzzyOnDroid repository (ETag: ${etag ?: "none"})")

            val requestBuilder = Request.Builder().url(INDEX_JAR_URL)
            if (!etag.isNullOrBlank()) {
                requestBuilder.header("If-None-Match", etag)
            }
            if (!lastModified.isNullOrBlank()) {
                requestBuilder.header("If-Modified-Since", lastModified)
            }

            try {
                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code == 304) {
                        val validFileAfter304 = targetFile.exists() && targetFile.length() > 0L
                        if (cachedOnDisk != null && validFileAfter304) {
                            Log.i("IzzyOnDroidRepository", "Index unchanged: HTTP 304")
                            Log.i("IzzyOnDroidRepository", "Parsed ${cachedOnDisk.apps.size} apps from IzzyOnDroid index")
                            inMemoryIndex = cachedOnDisk
                            inMemoryIndexTimestamp = System.currentTimeMillis()
                            return@withContext cachedOnDisk
                        } else {
                            Log.w("IzzyOnDroidRepository", "Server returned HTTP 304 but local index-v1.json is missing or corrupt. Forcing fresh download.")
                            val fresh = fetchFreshIndex()
                            inMemoryIndex = fresh
                            inMemoryIndexTimestamp = System.currentTimeMillis()
                            return@withContext fresh
                        }
                    }

                    if (response.isSuccessful) {
                        Log.i("IzzyOnDroidRepository", "Downloaded index-v1.jar: HTTP 200")
                        val body = response.body
                        val newEtag = response.header("ETag")
                        val newLastModified = response.header("Last-Modified")

                        val parsed = parseZipStreamAndSave(body.byteStream())
                        saveMetadata(newEtag, newLastModified)
                        Log.i("IzzyOnDroidRepository", "Parsed ${parsed.apps.size} apps from IzzyOnDroid index")
                        inMemoryIndex = parsed
                        inMemoryIndexTimestamp = System.currentTimeMillis()
                        return@withContext parsed
                    }

                    if (cachedOnDisk != null && targetFile.exists() && targetFile.length() > 0L) {
                        Log.w("IzzyOnDroidRepository", "HTTP ${response.code}: Falling back to cached disk index.")
                        Log.i("IzzyOnDroidRepository", "Parsed ${cachedOnDisk.apps.size} apps from IzzyOnDroid index")
                        inMemoryIndex = cachedOnDisk
                        inMemoryIndexTimestamp = System.currentTimeMillis()
                        return@withContext cachedOnDisk
                    }

                    throw IOException("HTTP ${response.code} when fetching IzzyOnDroid index")
                }
            } catch (networkError: Throwable) {
                if (cachedOnDisk != null && targetFile.exists() && targetFile.length() > 0L) {
                    Log.w("IzzyOnDroidRepository", "Network error (${networkError.message}): Falling back to cached disk index.")
                    Log.i("IzzyOnDroidRepository", "Parsed ${cachedOnDisk.apps.size} apps from IzzyOnDroid index")
                    inMemoryIndex = cachedOnDisk
                    inMemoryIndexTimestamp = System.currentTimeMillis()
                    return@withContext cachedOnDisk
                }
                throw networkError
            }
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
                saveMetadata(newEtag, newLastModified)
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

    private fun loadFromDiskCache(): IzzyIndexResponse? = runCatching {
        val file = getIndexJsonCacheFile()
        if (!file.exists() || file.length() <= 0L) return null
        FileInputStream(file).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, IzzyIndexResponse::class.java)
            }
        }
    }.getOrNull()

    private fun loadMetadata(): Pair<String?, String?> = runCatching {
        val file = getMetaCacheFile()
        if (!file.exists()) return Pair(null, null)
        val lines = file.readLines()
        val etag = lines.getOrNull(0)?.takeIf { it.isNotBlank() }
        val lastModified = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
        Pair(etag, lastModified)
    }.getOrDefault(Pair(null, null))

    private fun saveMetadata(etag: String?, lastModified: String?) {
        runCatching {
            val file = getMetaCacheFile()
            file.parentFile?.mkdirs()
            file.writeText("${etag.orEmpty()}\n${lastModified.orEmpty()}")
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
}
