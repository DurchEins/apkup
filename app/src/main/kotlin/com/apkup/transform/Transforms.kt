package com.apkup.transform

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import com.apkup.data.ui.AppInstalled
import com.apkup.repository.isSystemAppFlags
import com.apkup.util.getSignatureSha1
import com.apkup.util.getSignatureSha256
import com.apkup.util.name


fun PackageInfo.toAppInstalled(context: Context, ignored: List<String>) = AppInstalled(
	name = name(context),
	packageName = packageName,
	version = versionName.orEmpty(),
	versionCode = longVersionCode,
	ignored = ignored.contains(packageName),
	signature = getSignatureSha1(),
	signatureSha256 = getSignatureSha256(),
	enabled = applicationInfo?.enabled != false,
	isSystemApp = (applicationInfo?.flags ?: 0).isSystemAppFlags(),
	lastUpdateTime = lastUpdateTime,
	installerPackageName = context.resolveInstallerPackageName(packageName)
)

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


