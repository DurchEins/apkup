package com.apkup.data.fdroid

import com.apkup.data.izzy.IzzyAppInfo
import com.apkup.data.izzy.IzzyIndexResponse
import com.apkup.data.izzy.IzzyPackageInfo
import com.apkup.data.izzy.IzzyRepoInfo
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Cryptographic JAR signature verifier (`entry.jar` / `index-v1.jar`) and low-memory streaming
 * `JsonReader` index parser for F-Droid V2 & V1 repositories (`F-02`, `F-23b`, `CRIT-02`, `HIGH-12`).
 */
object FDroidIndexEngine {

    private const val MAX_JAR_ENTRIES = 64
    private const val MAX_ENTRY_JAR_BYTES = 4 * 1024 * 1024L // 4 MB cap for entry.jar
    private const val MAX_ENTRY_JSON_BYTES = 2 * 1024 * 1024L // 2 MB cap for entry.json
    private const val MAX_INDEX_JSON_BYTES = 256L * 1024L * 1024L // 256 MB cap for index-v1/v2.json

    data class VerifiedJarPayload<T>(
        val payload: T,
        val signerSha256Fingerprint: String
    )

    /**
     * Copies a JAR stream to a bounded temp file and verifies [targetEntryName] via `JarFile(..., true)`.
     * Using `JarFile` instead of `JarInputStream` is required because F-Droid servers (e.g. IzzyOnDroid)
     * often place `entry.json` before `META-INF/MANIFEST.MF` inside `entry.jar`, which causes sequential
     * `JarInputStream` to report `entry.certificates == null` despite valid cryptographic JAR signatures.
     */
    private inline fun verifyAndReadSignedJarEntry(
        jarStream: InputStream,
        jarName: String,
        targetEntryName: String,
        expectedCertSha256: String,
        allowUnpinnedCert: Boolean,
        maxJarBytes: Long,
        maxTargetBytes: Long,
        tempJarFile: File,
        crossinline consumeTarget: (InputStream) -> Unit
    ): String {
        val expectedNorm = FDroidRepoConfig.normalizeFingerprint(expectedCertSha256)
        if (!FDroidRepoConfig.isValidSha256Fingerprint(expectedNorm) && !(allowUnpinnedCert && expectedNorm.isEmpty())) {
            throw SecurityException("Invalid expected F-Droid repository fingerprint")
        }

        try {
            val buffer = ByteArray(16_384)
            var jarTotal = 0L
            FileOutputStream(tempJarFile).use { out ->
                var r = jarStream.read(buffer)
                while (r != -1) {
                    jarTotal += r
                    if (jarTotal > maxJarBytes) {
                        throw IOException("$jarName exceeds maximum allowed size ($maxJarBytes bytes)")
                    }
                    out.write(buffer, 0, r)
                    r = jarStream.read(buffer)
                }
                out.flush()
            }

            var verifiedFp: String? = null
            JarFile(tempJarFile, true).use { jarFile ->
                var entryCount = 0
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    entryCount++
                    if (entryCount > MAX_JAR_ENTRIES) {
                        throw SecurityException("Too many entries inside $jarName ($entryCount)")
                    }
                    if (entry.name == targetEntryName) {
                        if (verifiedFp != null) {
                            throw SecurityException("Duplicate $targetEntryName inside $jarName")
                        }
                        jarFile.getInputStream(entry).use { entryIn ->
                            consumeTarget(entryIn)
                        }
                        val fp = extractEntryCertSha256(entry)
                            ?: throw SecurityException("$jarName entry '$targetEntryName' is unsigned")
                        if (expectedNorm.isNotEmpty() && !constantTimeEquals(fp, expectedNorm)) {
                            throw SecurityException(
                                "F-Droid $jarName certificate mismatch: expected $expectedNorm, got $fp"
                            )
                        }
                        verifiedFp = fp
                    } else {
                        // Drain remaining entries completely so JarVerifier validates all manifest digests
                        jarFile.getInputStream(entry).use { auxIn ->
                            var auxTotal = 0L
                            var r = auxIn.read(buffer)
                            while (r != -1) {
                                auxTotal += r
                                if (auxTotal > MAX_ENTRY_JSON_BYTES) {
                                    throw IOException("Unexpectedly large auxiliary entry in $jarName: ${entry.name}")
                                }
                                r = auxIn.read(buffer)
                            }
                        }
                    }
                }
            }

            return verifiedFp ?: throw IOException("$targetEntryName not found inside $jarName")
        } finally {
            runCatching { if (tempJarFile.exists()) tempJarFile.delete() }
        }
    }

    /**
     * Extracts `entry.json` from a signed `entry.jar` stream, validates all JAR entry cryptographic
     * digests via `JarFile`, and verifies the signing X.509 certificate's SHA-256 fingerprint
     * against [expectedCertSha256] (or allows unpinned TOFU capture when [allowUnpinnedCert] is true).
     */
    fun verifyAndExtractEntryJson(
        jarStream: InputStream,
        expectedCertSha256: String,
        gson: Gson,
        allowUnpinnedCert: Boolean = false,
        tempDir: File? = null
    ): VerifiedJarPayload<FDroidV2Entry> {
        val dir = tempDir?.apply { mkdirs() }
        val tempJar = File.createTempFile("fdroid_entry_", ".jar", dir)
        var extractedBytes: ByteArray? = null

        val verifiedCertFp = verifyAndReadSignedJarEntry(
            jarStream = jarStream,
            jarName = "entry.jar",
            targetEntryName = "entry.json",
            expectedCertSha256 = expectedCertSha256,
            allowUnpinnedCert = allowUnpinnedCert,
            maxJarBytes = MAX_ENTRY_JAR_BYTES,
            maxTargetBytes = MAX_ENTRY_JSON_BYTES,
            tempJarFile = tempJar
        ) { entryIn ->
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            var r = entryIn.read(buf)
            while (r != -1) {
                total += r
                if (total > MAX_ENTRY_JSON_BYTES) {
                    throw IOException("entry.json exceeds maximum allowed size ($MAX_ENTRY_JSON_BYTES bytes)")
                }
                baos.write(buf, 0, r)
                r = entryIn.read(buf)
            }
            extractedBytes = baos.toByteArray()
        }

        val jsonBytes = extractedBytes ?: throw IOException("entry.json not found inside entry.jar")
        val parsed = InputStreamReader(jsonBytes.inputStream(), Charsets.UTF_8).use { reader ->
            gson.fromJson(reader, FDroidV2Entry::class.java)
        } ?: throw IOException("Malformed entry.json inside verified entry.jar")

        return VerifiedJarPayload(parsed, verifiedCertFp)
    }

    /**
     * Extracts `index-v1.json` from a signed `index-v1.jar` stream to [targetFile] atomically,
     * verifying the JAR cryptographic signature against [expectedCertSha256] BEFORE committing [targetFile].
     */
    fun verifyAndExtractV1IndexJarToFile(
        jarStream: InputStream,
        expectedCertSha256: String,
        targetFile: File,
        allowUnpinnedCert: Boolean = false
    ): String {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val tempJar = File(targetFile.parentFile, "${targetFile.name}.jar.tmp")
        if (tempFile.exists()) tempFile.delete()
        if (tempJar.exists()) tempJar.delete()

        try {
            val verifiedFp = verifyAndReadSignedJarEntry(
                jarStream = jarStream,
                jarName = "index-v1.jar",
                targetEntryName = "index-v1.json",
                expectedCertSha256 = expectedCertSha256,
                allowUnpinnedCert = allowUnpinnedCert,
                maxJarBytes = MAX_INDEX_JSON_BYTES,
                maxTargetBytes = MAX_INDEX_JSON_BYTES,
                tempJarFile = tempJar
            ) { entryIn ->
                val buffer = ByteArray(16_384)
                var total = 0L
                FileOutputStream(tempFile).use { out ->
                    var r = entryIn.read(buffer)
                    while (r != -1) {
                        total += r
                        if (total > MAX_INDEX_JSON_BYTES) {
                            throw IOException("index-v1.json exceeds maximum allowed size")
                        }
                        out.write(buffer, 0, r)
                        r = entryIn.read(buffer)
                    }
                    out.flush()
                }
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return verifiedFp
        } catch (t: Throwable) {
            runCatching { tempFile.delete() }
            throw t
        }
    }

    /**
     * Streams `index-v2.json` via Gson [JsonReader], materializing ONLY packages present in
     * [installedPackages] (when non-null) so 90+ MB repositories like official `f-droid.org` parse
     * in `< 2 MB` heap without `OutOfMemoryError` (`HIGH-12`).
     */
    fun streamParseV2IndexFile(
        file: File,
        installedPackages: Set<String>?,
        gson: Gson,
        preferredLocale: Locale = Locale.getDefault()
    ): IzzyIndexResponse {
        FileInputStream(file).buffered(32_768).use { fileIn ->
            InputStreamReader(fileIn, Charsets.UTF_8).use { isr ->
                JsonReader(isr).use { reader ->
                    var repoMeta: FDroidV2RepoMetadata? = null
                    val matchedPackages = HashMap<String, FDroidV2PackageEntry>()

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "repo" -> {
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                } else {
                                    repoMeta = gson.fromJson(reader, FDroidV2RepoMetadata::class.java)
                                }
                            }
                            "packages" -> {
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                } else {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val pkgName = reader.nextName()
                                        if (installedPackages != null && pkgName !in installedPackages) {
                                            reader.skipValue()
                                        } else if (reader.peek() == JsonToken.NULL) {
                                            reader.nextNull()
                                        } else {
                                            val entry: FDroidV2PackageEntry? =
                                                gson.fromJson(reader, FDroidV2PackageEntry::class.java)
                                            if (entry != null) {
                                                matchedPackages[pkgName] = entry
                                            }
                                        }
                                    }
                                    reader.endObject()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    return FDroidV2Index(
                        repo = repoMeta,
                        packages = matchedPackages
                    ).toIzzyIndexResponse(preferredLocale)
                }
            }
        }
    }

    /**
     * Streams `index-v1.json` via Gson [JsonReader], filtering only [installedPackages] when non-null.
     */
    fun streamParseV1IndexFile(
        file: File,
        installedPackages: Set<String>?,
        gson: Gson
    ): IzzyIndexResponse {
        val pkgListType = object : TypeToken<List<IzzyPackageInfo>>() {}.type
        FileInputStream(file).buffered(32_768).use { fileIn ->
            InputStreamReader(fileIn, Charsets.UTF_8).use { isr ->
                JsonReader(isr).use { reader ->
                    var repoInfo: IzzyRepoInfo? = null
                    val apps = ArrayList<IzzyAppInfo>()
                    val packages = HashMap<String, List<IzzyPackageInfo>>()

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "repo" -> {
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                } else {
                                    repoInfo = gson.fromJson(reader, IzzyRepoInfo::class.java)
                                }
                            }
                            "apps" -> {
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                } else {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        val app: IzzyAppInfo? = gson.fromJson(reader, IzzyAppInfo::class.java)
                                        if (app != null && app.packageName.isNotBlank()) {
                                            if (installedPackages == null || app.packageName in installedPackages) {
                                                apps.add(app)
                                            }
                                        }
                                    }
                                    reader.endArray()
                                }
                            }
                            "packages" -> {
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                } else {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val pkgName = reader.nextName()
                                        if (installedPackages != null && pkgName !in installedPackages) {
                                            reader.skipValue()
                                        } else if (reader.peek() == JsonToken.NULL) {
                                            reader.nextNull()
                                        } else {
                                            val list: List<IzzyPackageInfo>? = gson.fromJson(reader, pkgListType)
                                            if (!list.isNullOrEmpty()) {
                                                packages[pkgName] = list
                                            }
                                        }
                                    }
                                    reader.endObject()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()

                    return IzzyIndexResponse(
                        repo = repoInfo,
                        apps = apps,
                        packages = packages
                    )
                }
            }
        }
    }

    /**
     * Streams `index-v2.json` alongside an RFC 7396 JSON Merge Patch [diffFile] into [outputFile].
     * Untouched entries in `packages` are copied token-by-token via [copyJsonValue] in O(1) memory.
     */
    fun streamApplyV2DiffPatch(
        baseIndexFile: File,
        diffFile: File,
        outputFile: File,
        gson: Gson
    ) {
        val diffRootElement = FileInputStream(diffFile).buffered(16_384).use { fin ->
            InputStreamReader(fin, Charsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader)
            }
        }
        if (diffRootElement == null || !diffRootElement.isJsonObject) {
            throw IOException("Invalid F-Droid V2 diff JSON root")
        }
        val diffRoot = diffRootElement.asJsonObject
        outputFile.parentFile?.mkdirs()

        FileInputStream(baseIndexFile).buffered(32_768).use { fin ->
            InputStreamReader(fin, Charsets.UTF_8).use { isr ->
                JsonReader(isr).use { reader ->
                    FileOutputStream(outputFile).use { fout ->
                        BufferedWriter(OutputStreamWriter(fout, Charsets.UTF_8), 32_768).use { bw ->
                            JsonWriter(bw).use { writer ->
                                val handledTopKeys = HashSet<String>()
                                reader.beginObject()
                                writer.beginObject()
                                while (reader.hasNext()) {
                                    val key = reader.nextName()
                                    handledTopKeys.add(key)
                                    val patchVal = diffRoot.get(key)
                                    when {
                                        patchVal == null -> {
                                            writer.name(key)
                                            copyJsonValue(reader, writer)
                                        }
                                        patchVal.isJsonNull -> {
                                            reader.skipValue()
                                        }
                                        key == "packages" && patchVal.isJsonObject && reader.peek() == JsonToken.BEGIN_OBJECT -> {
                                            val diffPackages = patchVal.asJsonObject
                                            val handledPackages = HashSet<String>()
                                            writer.name("packages")
                                            writer.beginObject()
                                            reader.beginObject()
                                            while (reader.hasNext()) {
                                                val pkgName = reader.nextName()
                                                handledPackages.add(pkgName)
                                                val pkgPatch = diffPackages.get(pkgName)
                                                when {
                                                    pkgPatch == null -> {
                                                        writer.name(pkgName)
                                                        copyJsonValue(reader, writer)
                                                    }
                                                    pkgPatch.isJsonNull -> {
                                                        reader.skipValue()
                                                    }
                                                    else -> {
                                                        val basePkg = JsonParser.parseReader(reader)
                                                        val mergedPkg = applyJsonMergePatch(basePkg, pkgPatch)
                                                        if (mergedPkg != null) {
                                                            writer.name(pkgName)
                                                            gson.toJson(mergedPkg, writer)
                                                        }
                                                    }
                                                }
                                            }
                                            reader.endObject()
                                            for ((pkgName, pkgPatch) in diffPackages.entrySet()) {
                                                if (pkgName !in handledPackages && !pkgPatch.isJsonNull) {
                                                    val cleanNew = applyJsonMergePatch(null, pkgPatch)
                                                    if (cleanNew != null) {
                                                        writer.name(pkgName)
                                                        gson.toJson(cleanNew, writer)
                                                    }
                                                }
                                            }
                                            writer.endObject()
                                        }
                                        else -> {
                                            val baseVal = JsonParser.parseReader(reader)
                                            val merged = applyJsonMergePatch(baseVal, patchVal)
                                            if (merged != null) {
                                                writer.name(key)
                                                gson.toJson(merged, writer)
                                            }
                                        }
                                    }
                                }
                                reader.endObject()

                                for ((key, patchVal) in diffRoot.entrySet()) {
                                    if (key !in handledTopKeys && !patchVal.isJsonNull) {
                                        val cleanNew = applyJsonMergePatch(null, patchVal)
                                        if (cleanNew != null) {
                                            writer.name(key)
                                            gson.toJson(cleanNew, writer)
                                        }
                                    }
                                }
                                writer.endObject()
                                writer.flush()
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun applyJsonMergePatch(target: JsonElement?, patch: JsonElement): JsonElement? {
        if (patch.isJsonNull) return null
        if (!patch.isJsonObject) return patch
        val patchObj = patch.asJsonObject
        val result = JsonObject()
        val targetObj = if (target != null && target.isJsonObject) target.asJsonObject else null
        if (targetObj != null) {
            for ((key, value) in targetObj.entrySet()) {
                if (!patchObj.has(key)) {
                    result.add(key, value)
                }
            }
        }
        for ((key, patchValue) in patchObj.entrySet()) {
            if (patchValue.isJsonNull) continue
            val existingValue = targetObj?.get(key)
            val merged = applyJsonMergePatch(existingValue, patchValue)
            if (merged != null) {
                result.add(key, merged)
            }
        }
        return result
    }

    private fun copyJsonValue(reader: JsonReader, writer: JsonWriter) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                writer.beginObject()
                while (reader.hasNext()) {
                    writer.name(reader.nextName())
                    copyJsonValue(reader, writer)
                }
                reader.endObject()
                writer.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                writer.beginArray()
                while (reader.hasNext()) {
                    copyJsonValue(reader, writer)
                }
                reader.endArray()
                writer.endArray()
            }
            JsonToken.STRING -> writer.value(reader.nextString())
            JsonToken.NUMBER -> writer.jsonValue(reader.nextString())
            JsonToken.BOOLEAN -> writer.value(reader.nextBoolean())
            JsonToken.NULL -> {
                reader.nextNull()
                writer.nullValue()
            }
            else -> reader.skipValue()
        }
    }

    private fun extractEntryCertSha256(entry: JarEntry): String? {
        val certs: Array<Certificate> = entry.certificates ?: return null
        val leaf = certs.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        return digest.joinToString("") { "%02X".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(
            a.uppercase(Locale.ROOT).toByteArray(Charsets.US_ASCII),
            b.uppercase(Locale.ROOT).toByteArray(Charsets.US_ASCII)
        )
}
