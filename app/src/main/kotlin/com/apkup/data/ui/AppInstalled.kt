package com.apkup.data.ui

enum class InstallOrigin {
	PLAY_STORE,
	FDROID,
	APKMIRROR,
	REVANCED,
	SIDELOAD,
	SYSTEM
}

data class AppInstalled(
	val name: String,
	val packageName: String,
	val version: String,
	val versionCode: Long,
	val ignored: Boolean = false,
	val signature: String = "",
	val signatureSha256: String = "",
	val enabled: Boolean = true,
	val isSystemApp: Boolean = false,
	val lastUpdateTime: Long = 0L,
	val installerPackageName: String = ""
) {
	val installOrigin: InstallOrigin
		get() = resolveInstallOrigin(installerPackageName, isSystemApp)
}

internal fun resolveInstallOrigin(
	installerPackageName: String?,
	isSystemApp: Boolean = false
): InstallOrigin {
	val pkg = installerPackageName?.trim()?.lowercase().orEmpty()
	return when {
		pkg == "com.android.vending" || pkg == "com.google.android.feedback" -> InstallOrigin.PLAY_STORE
		pkg in FDROID_INSTALLERS || pkg.contains("fdroid") || pkg.contains("droidify") -> InstallOrigin.FDROID
		pkg == "com.apkmirror.helper.prod" || pkg.contains("apkmirror") -> InstallOrigin.APKMIRROR
		pkg.contains("revanced") || pkg.contains("vanced") -> InstallOrigin.REVANCED
		pkg.isNotEmpty() -> InstallOrigin.SIDELOAD
		isSystemApp -> InstallOrigin.SYSTEM
		else -> InstallOrigin.SIDELOAD
	}
}

internal fun hasPotentialSignatureConflict(
	installOrigin: InstallOrigin,
	updateSource: Source
): Boolean = when (installOrigin) {
	InstallOrigin.FDROID, InstallOrigin.REVANCED -> true
	InstallOrigin.SIDELOAD -> updateSource == PlaySource
	else -> false
}

private val FDROID_INSTALLERS = setOf(
	"org.fdroid.fdroid",
	"org.fdroid.basic",
	"org.fdroid.fdroid.privileged",
	"com.looker.droidify",
	"com.machiav3lli.fdroid",
	"eu.bubu1.fdroidclassic",
	"nya.kitsunyan.foxydroid"
)

fun List<AppInstalled>.getApp(packageName: String) = find { packageName == it.packageName }

fun List<AppInstalled>.getVersionCode(packageName: String) = getApp(packageName)
	?.versionCode
	?: 0L

fun List<AppInstalled>.getVersion(packageName: String) = getApp(packageName)
	?.version
	?: ""

fun List<AppInstalled>.getSignature(packageName: String) = getApp(packageName)
	?.signature
	.orEmpty()

fun List<AppInstalled>.getPackageNames() = asSequence()
	.filterNot { it.ignored }
	.map { it.packageName }
	.filter { it.isNotBlank() }
	.distinct()
	.toList()
