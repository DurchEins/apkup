package com.apkup.util

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private object ApkShortHashCache {
	const val SHORT_HASH_LENGTH = 8
	private const val BUFFER_SIZE = 16 * 1024
	val byPathAndTimestamp = ConcurrentHashMap<String, String>()
}

internal fun computeShortApkSha256(apkPath: String, lastUpdateTime: Long = 0L): String {
	if (apkPath.isBlank()) return ""
	val cacheKey = "$apkPath@$lastUpdateTime"
	ApkShortHashCache.byPathAndTimestamp[cacheKey]?.let { return it }

	val file = File(apkPath)
	if (!file.isFile || !file.canRead()) return ""

	val digest = runCatching {
		val md = MessageDigest.getInstance("SHA-256")
		val buffer = ByteArray(16 * 1024)
		file.inputStream().use { input ->
			while (true) {
				val read = input.read(buffer)
				if (read <= 0) break
				md.update(buffer, 0, read)
			}
		}
		md.digest()
			.joinToString(separator = "") { b -> "%02x".format(Locale.ROOT, b) }
			.take(ApkShortHashCache.SHORT_HASH_LENGTH)
	}.getOrDefault("")

	if (digest.isNotEmpty()) {
		ApkShortHashCache.byPathAndTimestamp[cacheKey] = digest
	}
	return digest
}

internal fun formatVersionWithShortHash(
	version: String,
	versionCode: Long = -1L,
	shortSha256: String = ""
): String {
	val cleanVersion = version.trim()
	val cleanHash = shortSha256.trim().lowercase(Locale.ROOT).take(ApkShortHashCache.SHORT_HASH_LENGTH)
	val base = buildString {
		if (cleanVersion.isNotEmpty()) append(cleanVersion)
		if (versionCode > 0L) {
			if (isNotEmpty()) append(' ')
			append('(').append(versionCode).append(')')
		}
	}
	return when {
		base.isNotEmpty() && cleanHash.isNotEmpty() -> "$base [$cleanHash]"
		base.isNotEmpty() -> base
		cleanHash.isNotEmpty() -> "[$cleanHash]"
		else -> ""
	}
}
