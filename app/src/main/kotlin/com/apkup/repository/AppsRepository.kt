package com.apkup.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.apkup.BuildConfig
import com.apkup.data.ui.AppInstalled
import com.apkup.prefs.Prefs
import com.apkup.transform.toAppInstalled
import kotlinx.coroutines.CancellationException


class AppsRepository(
	private val context: Context,
	private val prefs: Prefs
) {

	suspend fun getApps(): Result<List<AppInstalled>> = try {
		val startedAt = SystemClock.elapsedRealtime()
		val ignoredPackages = ignoredApps()
		val showSystemApps = prefs.showSystemApps.get()
		val apps = context.packageManager
			.getInstalledPackages(PackageManager.MATCH_ALL or PackageManager.GET_SIGNING_CERTIFICATES)
			.asSequence()
			.filter { includeSystemApp(it.applicationInfo?.flags ?: 0, showSystemApps) }
			.map { it.toAppInstalled(context, ignoredPackages) }
			.sortedWith(compareBy<AppInstalled> { it.ignored }.thenBy { it.name })
			.toList()
		if (BuildConfig.DEBUG) {
			Log.d(
				"AppsRepository",
				"Read ${apps.size} installed app(s) in ${SystemClock.elapsedRealtime() - startedAt} ms"
			)
		}
		Result.success(apps)
	} catch (cancellation: CancellationException) {
		throw cancellation
	} catch (exception: Exception) {
		Log.e("AppsRepository", "Could not read installed apps (${exception.javaClass.simpleName})")
		Result.failure(exception)
	}

	private fun ignoredApps() = prefs.ignoredApps.get()
}

internal fun Int.isSystemAppFlags(): Boolean =
	this and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

internal fun includeSystemApp(flags: Int, showSystemApps: Boolean): Boolean =
	showSystemApps || !flags.isSystemAppFlags()
