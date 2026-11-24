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
	if (installedVersionCode == -1L) return availableVersionCode > 0L && normalizeVersionString(availableVersion).isNotEmpty()
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

fun AppUpdate.isWatchedApp(): Boolean = oldVersionCode < 0L

private val htmlBreakRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val htmlClosingParagraphRegex = Regex("</p>", RegexOption.IGNORE_CASE)
private val htmlListStartRegex = Regex("<li[^>]*>", RegexOption.IGNORE_CASE)
private val htmlListEndRegex = Regex("</li>", RegexOption.IGNORE_CASE)
private val htmlCommentRegex = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
private val htmlTagRegex = Regex("<[^>]+>")
private val markdownHeadingRegex = Regex("^#{1,6}\\s*")
private val markdownListBulletRegex = Regex("^[-*+]\\s+")
private val markdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val markdownImageRegex = Regex("!\\[([^\\]]*)]\\([^)]+\\)")
private val markdownEmphasisRegex = Regex("(\\*\\*|__|~~|`{1,3})")
private val bareHexHashRegex = Regex("\\b[0-9a-fA-F]{32,64}\\b")
private val urlRegex = Regex("https?://\\S+")
private val repeatedSpaceRegex = Regex("[ \\t]{2,}")

internal fun cleanChangelogText(raw: String?): String {
	if (raw.isNullOrBlank()) return ""
	val unescaped = raw
		.replace(htmlCommentRegex, "")
		.replace(htmlBreakRegex, "\n")
		.replace(htmlClosingParagraphRegex, "\n")
		.replace(htmlListStartRegex, "• ")
		.replace(htmlListEndRegex, "\n")
		.replace(htmlTagRegex, "")
		.replace("&bull;", "•")
		.replace("&amp;", "&")
		.replace("&lt;", "<")
		.replace("&gt;", ">")
		.replace("&quot;", "\"")
		.replace("&#39;", "'")
		.replace("&nbsp;", " ")
		.replace(markdownImageRegex) { match -> match.groupValues[1] }
		.replace(markdownLinkRegex) { match -> match.groupValues[1] }
		.replace("```", "")
		.replace(markdownEmphasisRegex, "")
	return unescaped
		.lines()
		.mapNotNull { line ->
			val cleaned = line
				.trim()
				.replace(markdownHeadingRegex, "")
				.replace(markdownListBulletRegex, "• ")
				.replace(repeatedSpaceRegex, " ")
				.trim()
			when {
				cleaned.isEmpty() -> null
				bareHexHashRegex.matches(cleaned) -> null
				else -> cleaned
			}
		}
		.joinToString("\n")
}

internal fun cleanChangelogSpeechText(raw: String?): String {
	val clean = cleanChangelogText(raw)
	if (clean.isBlank()) return ""
	return clean
		.replace(bareHexHashRegex, "")
		.replace(urlRegex, "")
		.replace(repeatedSpaceRegex, " ")
		.lines()
		.map { it.trim().trimEnd(':', '-', '|') }
		.filter { it.isNotEmpty() }
		.joinToString(". ")
}

fun List<AppUpdate>.indexOf(id: Int) = indexOfFirst {
	it.id == id || it.alternativeUpdates.any { alt -> alt.id == id }
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
