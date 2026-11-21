package com.apkup.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

sealed class ApkSignatureCheckResult {
	data object Compatible : ApkSignatureCheckResult()
	data object Indeterminate : ApkSignatureCheckResult()
	data class Mismatch(
		val installedSignersSha256: Set<String>,
		val archiveSignersSha256: Set<String>
	) : ApkSignatureCheckResult()
}

internal data class ParsedPackageSigningInfo(
	val packageName: String?,
	val currentSigners: Set<String>,
	val historySigners: Set<String>,
	val hasMultipleSigners: Boolean = false
)

object ApkSignatureVerifier {

	internal const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

	internal fun normalizeHash(hash: String?): String =
		hash.orEmpty().replace(":", "").replace(" ", "").trim().lowercase()

	internal fun areSignerSetsCompatible(
		installedCurrentSigners: Set<String>,
		installedHistorySigners: Set<String>,
		archiveCurrentSigners: Set<String>,
		archiveHistorySigners: Set<String>,
		installedHasMultipleSigners: Boolean = false,
		archiveHasMultipleSigners: Boolean = false
	): ApkSignatureCheckResult {
		val cleanInstalledCurrent = installedCurrentSigners.map(::normalizeHash)
			.filter { it.isNotEmpty() && it != EMPTY_SHA256 }
			.toSet()
		val cleanInstalledHistory = installedHistorySigners.map(::normalizeHash)
			.filter { it.isNotEmpty() && it != EMPTY_SHA256 }
			.toSet()
		val cleanArchiveCurrent = archiveCurrentSigners.map(::normalizeHash)
			.filter { it.isNotEmpty() && it != EMPTY_SHA256 }
			.toSet()
		val cleanArchiveHistory = archiveHistorySigners.map(::normalizeHash)
			.filter { it.isNotEmpty() && it != EMPTY_SHA256 }
			.toSet()

		val allInstalled = cleanInstalledCurrent + cleanInstalledHistory
		val allArchive = cleanArchiveCurrent + cleanArchiveHistory

		if (cleanInstalledCurrent.isEmpty() || cleanArchiveCurrent.isEmpty()) {
			return ApkSignatureCheckResult.Indeterminate
		}

		if (installedHasMultipleSigners || archiveHasMultipleSigners) {
			return if (cleanInstalledCurrent == cleanArchiveCurrent) {
				ApkSignatureCheckResult.Compatible
			} else {
				ApkSignatureCheckResult.Mismatch(allInstalled, allArchive)
			}
		}

		if (cleanInstalledCurrent == cleanArchiveCurrent) {
			return ApkSignatureCheckResult.Compatible
		}

		// Directional APK Signature Scheme v3 key-rotation check:
		// Only allow forward rotation where the installed app's active signer is present in the
		// candidate archive's v3 rotation lineage; never allow rollback to a retired historical key.
		if (cleanInstalledCurrent.size == 1 && cleanArchiveCurrent.size == 1) {
			val installedCert = cleanInstalledCurrent.single()
			if (installedCert in cleanArchiveHistory) {
				return ApkSignatureCheckResult.Compatible
			}
		}

		return ApkSignatureCheckResult.Mismatch(allInstalled, allArchive)
	}

	fun verifyApkFilesAgainstInstalled(
		context: Context,
		packageName: String,
		apkFiles: List<File>
	): ApkSignatureCheckResult = runCatching {
		if (apkFiles.isEmpty() || packageName.isBlank()) {
			return ApkSignatureCheckResult.Indeterminate
		}
		val pm = context.packageManager
		val orderedFiles = apkFiles
			.filter { it.exists() && it.isFile }
			.sortedByDescending { it.name.equals("base.apk", ignoreCase = true) }
		if (orderedFiles.isEmpty()) {
			return ApkSignatureCheckResult.Indeterminate
		}

		val installedParsed = getInstalledSigningPackageInfo(pm, packageName)?.toParsedSigningInfo()
		val archiveParsed = orderedFiles.mapNotNull { apkFile ->
			getArchiveSigningPackageInfo(pm, apkFile.absolutePath)?.toParsedSigningInfo()
		}

		verifyParsedSigningInfos(
			packageName = packageName,
			installed = installedParsed,
			archives = archiveParsed
		)
	}.getOrDefault(ApkSignatureCheckResult.Indeterminate)

	internal fun verifyParsedSigningInfos(
		packageName: String,
		installed: ParsedPackageSigningInfo?,
		archives: List<ParsedPackageSigningInfo>
	): ApkSignatureCheckResult {
		if (packageName.isBlank()) {
			return ApkSignatureCheckResult.Indeterminate
		}

		val instAll = installed?.let {
			(it.currentSigners + it.historySigners)
				.map(::normalizeHash)
				.filter { h -> h.isNotEmpty() && h != EMPTY_SHA256 }
				.toSet()
		}.orEmpty()

		var anchorArchiveSigners: Set<String>? = null
		for (archive in archives) {
			if (archive.packageName != packageName) {
				return ApkSignatureCheckResult.Mismatch(instAll, emptySet())
			}
			val cleanArchCurrent = archive.currentSigners
				.map(::normalizeHash)
				.filter { it.isNotEmpty() && it != EMPTY_SHA256 }
				.toSet()
			if (cleanArchCurrent.isEmpty()) {
				return ApkSignatureCheckResult.Indeterminate
			}
			if (anchorArchiveSigners == null) {
				anchorArchiveSigners = cleanArchCurrent
			} else if (cleanArchCurrent != anchorArchiveSigners) {
				return ApkSignatureCheckResult.Mismatch(instAll, cleanArchCurrent)
			}
		}

		if (installed == null) {
			return ApkSignatureCheckResult.Compatible // App not installed yet -> no update signature conflict
		}

		if (archives.isEmpty()) {
			return ApkSignatureCheckResult.Indeterminate
		}

		for (archive in archives) {
			val check = areSignerSetsCompatible(
				installedCurrentSigners = installed.currentSigners,
				installedHistorySigners = installed.historySigners,
				archiveCurrentSigners = archive.currentSigners,
				archiveHistorySigners = archive.historySigners,
				installedHasMultipleSigners = installed.hasMultipleSigners,
				archiveHasMultipleSigners = archive.hasMultipleSigners
			)
			if (check !is ApkSignatureCheckResult.Compatible) {
				return check
			}
		}

		return ApkSignatureCheckResult.Compatible
	}

	private fun PackageInfo.toParsedSigningInfo(): ParsedPackageSigningInfo {
		val (current, history, multi) = extractSignerSets(this)
		return ParsedPackageSigningInfo(
			packageName = packageName,
			currentSigners = current,
			historySigners = history,
			hasMultipleSigners = multi
		)
	}

	fun getInstalledPrimarySha256(context: Context, packageName: String): String? = runCatching {
		val info = getInstalledSigningPackageInfo(context.packageManager, packageName) ?: return null
		info.getSignatureSha256().let(::normalizeHash).takeIf { it.isNotEmpty() && it != EMPTY_SHA256 }
	}.getOrNull()

	private fun getInstalledSigningPackageInfo(pm: PackageManager, packageName: String): PackageInfo? =
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				pm.getPackageInfo(
					packageName,
					PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
				)
			} else {
				@Suppress("DEPRECATION")
				pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
			}
		} catch (_: PackageManager.NameNotFoundException) {
			null
		}

	private fun getArchiveSigningPackageInfo(pm: PackageManager, archivePath: String): PackageInfo? =
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			pm.getPackageArchiveInfo(
				archivePath,
				PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
			)
		} else {
			@Suppress("DEPRECATION")
			pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
		}

	private fun extractSignerSets(info: PackageInfo): Triple<Set<String>, Set<String>, Boolean> {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			val signing = info.signingInfo
			if (signing != null) {
				val current = signing.apkContentsSigners
					?.map { it.toByteArray().toSha256() }
					?.toSet()
					.orEmpty()
				val history = if (signing.hasMultipleSigners()) {
					emptySet()
				} else {
					signing.signingCertificateHistory
						?.map { it.toByteArray().toSha256() }
						?.toSet()
						.orEmpty()
				}
				return Triple(current, history, signing.hasMultipleSigners())
			}
		}
		@Suppress("DEPRECATION")
		val legacy = info.signatures
			?.map { it.toByteArray().toSha256() }
			?.toSet()
			.orEmpty()
		return Triple(legacy, emptySet(), legacy.size > 1)
	}
}
