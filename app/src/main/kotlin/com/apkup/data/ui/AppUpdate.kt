package com.apkup.data.ui

data class AppUpdate(
	val name: String,
	val packageName: String,
	val version: String,
	val oldVersion: String,
	val versionCode: Long,
	val oldVersionCode: Long,
	val source: Source,
	val link: Link = Link.Empty,
	val whatsNew: String = "",
	val isInstalling: Boolean = false,
	val total: Long = 0L,
	val progress: Long = 0L,
	val id: Int = "${source.name}.$packageName.$versionCode.$version".hashCode(),
	val isQueued: Boolean = false,
	val totalSize: Long = 0L,
	val updatedOn: String = "",
	val offerType: Int = 1,
	val installOrigin: InstallOrigin = InstallOrigin.SYSTEM,
	val installerPackageName: String = "",
	val alternativeUpdates: List<AppUpdate> = emptyList()
) {
	val isDirectInstallable: Boolean
		get() = isDirectInstallable(directApkMirrorEnabled = false)

	val isBrowserOnlyUpdate: Boolean
		get() = isBrowserOnlyUpdate(directApkMirrorEnabled = false)

	fun isDirectInstallable(directApkMirrorEnabled: Boolean): Boolean =
		link is Link.Play || (link is Link.Url && (source != ApkMirrorSource || directApkMirrorEnabled))

	fun isBrowserOnlyUpdate(directApkMirrorEnabled: Boolean): Boolean =
		source == ApkMirrorSource && link is Link.Url && !directApkMirrorEnabled
}

internal fun normalizeVersionString(version: String): String =
	version.trim()
		.removePrefix("v")
		.removePrefix("V")
		.trim()

internal fun isVerifiableVersionUpgrade(
	installedVersion: String,
	availableVersion: String,
	installedVersionCode: Long,
	availableVersionCode: Long
): Boolean {
	if (availableVersionCode <= 0L || installedVersionCode <= 0L) return false
	if (availableVersionCode <= installedVersionCode) return false
	val normalizedAvailable = normalizeVersionString(availableVersion)
	if (normalizedAvailable.isEmpty()) return false
	val normalizedInstalled = normalizeVersionString(installedVersion)
	if (normalizedInstalled.isNotEmpty() && normalizedInstalled.equals(normalizedAvailable, ignoreCase = true)) {
		return false
	}
	return true
}

fun AppUpdate.isMeaningfulVersionUpgrade(): Boolean = isVerifiableVersionUpgrade(
	installedVersion = oldVersion,
	availableVersion = version,
	installedVersionCode = oldVersionCode,
	availableVersionCode = versionCode
)

internal fun cleanChangelogText(raw: String?): String {
	if (raw.isNullOrBlank()) return ""
	return raw
		.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
		.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
		.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
		.replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
		.replace(Regex("<[^>]+>"), "")
		.replace("&bull;", "•")
		.replace("&amp;", "&")
		.replace("&lt;", "<")
		.replace("&gt;", ">")
		.replace("&quot;", "\"")
		.replace("&#39;", "'")
		.replace("&nbsp;", " ")
		.lines()
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.joinToString("\n")
}

fun List<AppUpdate>.indexOf(id: Int) = indexOfFirst { it.id == id }

fun MutableList<AppUpdate>.setIsInstalling(id: Int, b: Boolean): List<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) {
		this[index] = this[index].copy(isInstalling = b, isQueued = if (b) false else this[index].isQueued)
	}
	return this
}

fun MutableList<AppUpdate>.setIsQueued(id: Int, queued: Boolean): MutableList<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) {
		this[index] = this[index].copy(isQueued = queued)
	}
	return this
}

fun MutableList<AppUpdate>.removeId(id: Int): List<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) this.removeAt(index)
	return this
}

fun MutableList<AppUpdate>.removePackageName(packageName: String): List<AppUpdate> {
	removeAll { it.packageName == packageName }
	return this
}

fun MutableList<AppUpdate>.setProgress(progress: AppInstallProgress): MutableList<AppUpdate> {
	val index = this.indexOf(progress.id)
	if (index != -1) {
		progress.progress?.let { this[index] = this[index].copy(progress = it) }
		progress.total?.let { this[index] = this[index].copy(total = it) }
	}
	return this
}
