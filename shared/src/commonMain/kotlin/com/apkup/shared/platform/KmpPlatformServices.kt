package com.apkup.shared.platform

data class KmpSha256Result(
    val sha256Hex: String,
    val bytesHashed: Long,
    val elapsedMicros: Long
)

data class KmpVerifiedJarEntry(
    val entryJsonText: String,
    val signerSha256Hex: String,
    val elapsedMicros: Long
)

expect fun currentTimeMillis(): Long

expect fun nanoTime(): Long

expect fun computeFileSha256Hex(filePath: String): KmpSha256Result

expect suspend fun verifyAndExtractFDroidJarEntry(
    jarBytes: ByteArray,
    targetEntryName: String,
    expectedCertSha256: String,
    allowUnpinnedCert: Boolean
): KmpVerifiedJarEntry

expect fun constantTimeHexEquals(a: String, b: String): Boolean
