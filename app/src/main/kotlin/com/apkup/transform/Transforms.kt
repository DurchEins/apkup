package com.apkup.transform

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.apkup.data.ui.AppInstalled
import com.apkup.repository.isSystemAppFlags
import com.apkup.util.getSignatureSha1
import com.apkup.util.getSignatureSha256
import com.apkup.util.name


import android.content.pm.PackageManager

fun PackageInfo.toAppInstalled(context: Context, ignored: List<String>): AppInstalled {
	val appInfo = applicationInfo ?: runCatching {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
		} else {
			@Suppress("DEPRECATION")
			context.packageManager.getApplicationInfo(packageName, 0)
		}
	}.getOrNull()
	val flags = appInfo?.flags ?: 0
	val sourceDir = appInfo?.sourceDir.orEmpty()
	val isSystem = flags.isSystemAppFlags() ||
		sourceDir.startsWith("/system/") ||
		sourceDir.startsWith("/system_ext/") ||
		sourceDir.startsWith("/vendor/") ||
		sourceDir.startsWith("/product/") ||
		sourceDir.startsWith("/apex/")
	return AppInstalled(
		name = name(context),
		packageName = packageName,
		version = versionName.orEmpty(),
		versionCode = longVersionCode,
		ignored = ignored.contains(packageName),
		signature = getSignatureSha1(),
		signatureSha256 = getSignatureSha256(),
		enabled = appInfo?.enabled != false,
		isSystemApp = isSystem,
		lastUpdateTime = lastUpdateTime,
		installerPackageName = context.resolveInstallerPackageName(packageName)
	)
}

internal fun Context.resolveInstallerPackageName(packageName: String): String = runCatching {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
		val info = packageManager.getInstallSourceInfo(packageName)
		info.installingPackageName
			?: info.initiatingPackageName
			?: info.originatingPackageName
			?: ""
	} else {
		@Suppress("DEPRECATION")
		packageManager.getInstallerPackageName(packageName).orEmpty()
	}
}.getOrDefault("")


