package com.apkup.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.apkup.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.Calendar
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// Launches a coroutine and executes it inside the mutex
fun CoroutineScope.launchWithMutex(
	mutex: Mutex,
	context: CoroutineContext = EmptyCoroutineContext,
	block: suspend CoroutineScope.() -> Unit
) = launch(context) {
	mutex.withLock {
		block()
	}
}

fun Boolean?.orFalse() = this ?: false

fun PackageInfo.name(context: Context) = applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""

fun Context.getAppIcon(packageName: String) = runCatching {
	packageManager.getApplicationIcon(packageName)
}.getOrElse {
	Log.e("getAppIcon", "App not found. Uninstalled most likely.")
	ContextCompat.getDrawable(this, R.drawable.ic_root)
}

fun Context.getAppName(packageName: String): String = runCatching {
	packageManager.getPackageInfo(packageName, 0).name(this)
}.getOrDefault("")

fun ByteArray.toSha1(): String = MessageDigest
	.getInstance("SHA-1")
	.digest(this)
	.joinToString(separator = "", transform = { "%02x".format(it) })

fun ByteArray.toSha256(): String = MessageDigest
	.getInstance("SHA-256")
	.digest(this)
	.joinToString(separator = "", transform = { "%02x".format(it) })

fun PackageInfo.getSignature(): ByteArray = runCatching {
	signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: ByteArray(0)
}.getOrDefault(ByteArray(0))

fun PackageInfo.getSignatureSha1(): String = getSignature().toSha1()

fun PackageInfo.getSignatureSha256(): String = getSignature().toSha256()

fun millisUntilHour(hour: Int): Long {
	val calendar = Calendar.getInstance()
	if (calendar.get(Calendar.HOUR_OF_DAY) >= hour) calendar.add(Calendar.HOUR, 24)
	calendar.set(Calendar.HOUR_OF_DAY, hour)
	calendar.set(Calendar.MINUTE, 0)
	return calendar.timeInMillis - System.currentTimeMillis()
}

fun Intent.getIntentExtra(): Intent? = when {
	Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
	else -> @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_INTENT)
}

fun Intent.getAppId() = runCatching {
	action?.split(".")?.get(1)?.toInt()
}.getOrNull()

fun PackageManager.isAndroidTv() = hasSystemFeature(PackageManager.FEATURE_LEANBACK)

fun randomUUID() = UUID.randomUUID().toString()

fun isDark() = Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun OkHttpClient.Builder.addUserAgentInterceptor(agent: String) = addNetworkInterceptor {
	it.proceed(it.request().newBuilder().header("User-Agent", agent).build())
}
