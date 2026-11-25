package com.apkup.shared.platform

import com.apkup.shared.engine.KmpTelemetryRegistry
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Locale
import java.util.jar.JarFile

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun nanoTime(): Long = System.nanoTime()

actual fun constantTimeHexEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(
        a.lowercase(Locale.ROOT).toByteArray(Charsets.US_ASCII),
        b.lowercase(Locale.ROOT).toByteArray(Charsets.US_ASCII)
    )

actual fun computeFileSha256Hex(filePath: String): KmpSha256Result {
    val startNs = System.nanoTime()
    val file = File(filePath)
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    file.inputStream().buffered().use { input ->
        while (true) {
            val r = input.read(buffer)
            if (r <= 0) break
            digest.update(buffer, 0, r)
            total += r
        }
    }
    val hex = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    val elapsedMicros = ((System.nanoTime() - startNs) / 1000L).coerceAtLeast(1L)
    KmpTelemetryRegistry.recordSha256(elapsedMicros, total)
    return KmpSha256Result(
        sha256Hex = hex,
        bytesHashed = total,
        elapsedMicros = elapsedMicros
    )
}

actual suspend fun verifyAndExtractFDroidJarEntry(
    jarBytes: ByteArray,
    targetEntryName: String,
    expectedCertSha256: String,
    allowUnpinnedCert: Boolean
): KmpVerifiedJarEntry {
    val startNs = System.nanoTime()
    val expectedNorm = expectedCertSha256.trim().replace(":", "").uppercase(Locale.ROOT)
    val isHex64 = expectedNorm.length == 64 && expectedNorm.all { it in '0'..'9' || it in 'A'..'F' }
    if (!isHex64 && !(allowUnpinnedCert && expectedNorm.isEmpty())) {
        throw SecurityException("Invalid expected F-Droid repository fingerprint")
    }

    val tempJar = File.createTempFile("kmp_fdroid_", ".jar")
    try {
        tempJar.writeBytes(jarBytes)
        var verifiedFp: String? = null
        var extractedText: String? = null
        val buf = ByteArray(16_384)

        JarFile(tempJar, true).use { jar ->
            var entryCount = 0
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                entryCount++
                if (entryCount > 64) {
                    throw SecurityException("Too many entries inside JAR ($entryCount)")
                }
                if (entry.name == targetEntryName) {
                    if (verifiedFp != null) {
                        throw SecurityException("Duplicate $targetEntryName inside JAR")
                    }
                    extractedText = jar.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                    val certs: Array<Certificate> = entry.certificates
                        ?: throw SecurityException("JAR entry '$targetEntryName' is unsigned")
                    val leaf = certs.firstOrNull()
                        ?: throw SecurityException("JAR entry '$targetEntryName' has no signer certificate")
                    val fp = MessageDigest.getInstance("SHA-256")
                        .digest(leaf.encoded)
                        .joinToString("") { "%02X".format(Locale.ROOT, it) }
                    if (expectedNorm.isNotEmpty() && !constantTimeHexEquals(fp, expectedNorm)) {
                        throw SecurityException("F-Droid JAR certificate mismatch: expected $expectedNorm, got $fp")
                    }
                    verifiedFp = fp
                } else {
                    jar.getInputStream(entry).use { aux ->
                        while (aux.read(buf) != -1) {
                            // Drain so JarVerifier validates all digests
                        }
                    }
                }
            }
        }

        val text = extractedText ?: throw IOException("$targetEntryName not found inside JAR")
        val fp = verifiedFp ?: throw SecurityException("Unsigned $targetEntryName")
        val elapsedMicros = ((System.nanoTime() - startNs) / 1000L).coerceAtLeast(1L)
        return KmpVerifiedJarEntry(
            entryJsonText = text,
            signerSha256Hex = fp,
            elapsedMicros = elapsedMicros
        )
    } finally {
        runCatching { if (tempJar.exists()) tempJar.delete() }
    }
}
