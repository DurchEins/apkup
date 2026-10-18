package com.apkup.viewmodel

import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.Screen
import com.apkup.data.ui.resolveStartRoute
import com.apkup.prefs.Prefs
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.UpdatesNotification
import com.apkup.util.getAppId
import com.apkup.util.getIntentExtra
import com.apkup.util.orFalse
import com.apkup.worker.UpdatesWorker
import android.content.Context
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.data.selfupdate.SelfUpdateInfo
import com.apkup.data.selfupdate.SelfUpdateState
import com.apkup.data.snack.TextSnack
import com.apkup.repository.SelfUpdateRepository
import com.apkup.util.Downloader
import com.apkup.util.InstallPart
import com.apkup.util.SnackBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException


class MainViewModel(
	private val prefs: Prefs,
	private val installLog: InstallLog,
	workManager: WorkManager,
	private val selfUpdateRepository: SelfUpdateRepository? = null,
	private val downloader: Downloader? = null,
	private val sessionInstaller: SessionInstaller? = null,
	private val snackBar: SnackBar? = null
) : ViewModel() {
	val screens = listOf(Screen.Apps, Screen.Updates, Screen.Settings)

	init {
		if (prefs.enableBackgroundChecks.get()) UpdatesWorker.launch(workManager)
		val savedRoute = prefs.lastTab.get()
		val validRoute = resolveStartRoute(savedRoute, screens.map { it.route }.toSet())
		if (validRoute != savedRoute) prefs.lastTab.put(validRoute)
	}

	val isRefreshing = MutableStateFlow(false)
	private var hasInitializedColdStart = false
	private val _showChangelogSheet = MutableStateFlow(false)
	val showChangelogSheet: StateFlow<Boolean> = _showChangelogSheet.asStateFlow()

	private val _selfUpdateState = MutableStateFlow<SelfUpdateState>(SelfUpdateState.Idle)
	val selfUpdateState: StateFlow<SelfUpdateState> = _selfUpdateState.asStateFlow()

	fun dismissSelfUpdate() {
		_selfUpdateState.value = SelfUpdateState.Idle
	}

	fun checkForSelfUpdate(manual: Boolean = false, context: Context? = null) = viewModelScope.launch(Dispatchers.IO) {
		val repo = selfUpdateRepository ?: return@launch
		if (manual) {
			_selfUpdateState.value = SelfUpdateState.Checking
			context?.let { ctx ->
				snackBar?.snackBar(viewModelScope, TextSnack(ctx.getString(R.string.self_update_checking)))
			}
		}
		val result = repo.checkForUpdate()
		if (result.isSuccess) {
			val info = result.getOrNull()
			if (info != null) {
				_selfUpdateState.value = SelfUpdateState.Available(info)
			} else {
				_selfUpdateState.value = SelfUpdateState.Idle
				if (manual && context != null) {
					val msg = context.getString(R.string.self_update_latest, BuildConfig.VERSION_NAME)
					snackBar?.snackBar(viewModelScope, TextSnack(msg))
				}
			}
		} else {
			val error = result.exceptionOrNull()
			Log.e("MainViewModel", "Self update check failed", error)
			_selfUpdateState.value = SelfUpdateState.Idle
			if (manual && context != null) {
				snackBar?.snackBar(viewModelScope, TextSnack(context.getString(R.string.self_update_error)))
			}
		}
	}

	fun startSelfUpdate(info: SelfUpdateInfo, context: Context) = viewModelScope.launch(Dispatchers.IO) {
		val dl = downloader ?: return@launch
		val inst = sessionInstaller ?: return@launch
		val cacheDir = context.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_cache")
		val stagingDir = File(cacheDir, "self_update_staging").apply {
			deleteRecursively()
			mkdirs()
		}
		val targetFile = File(stagingDir, "ApkUp-${info.versionName}.apk")

		_selfUpdateState.value = SelfUpdateState.Downloading(info, 0)
		var downloadedBytes = 0L
		val totalBytes = info.apkSize

		try {
			val success = dl.downloadToFile(info.downloadUrl, targetFile) { chunk ->
				downloadedBytes += chunk
				val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
				_selfUpdateState.value = SelfUpdateState.Downloading(info, percent)
			}

			if (!success || targetFile.length() <= 0L) {
				throw IOException("Self-update APK download failed or resulted in empty file.")
			}

			_selfUpdateState.value = SelfUpdateState.Installing(info)
			val selfUpdateInstallId = "com.apkup.selfupdate".hashCode()
			val parts = listOf(InstallPart("base.apk") { targetFile.inputStream() })
			inst.installParts(selfUpdateInstallId, "com.apkup", parts)
			_selfUpdateState.value = SelfUpdateState.Idle
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Log.e("MainViewModel", "Failed to download/install self update", e)
			_selfUpdateState.value = SelfUpdateState.Idle
			snackBar?.snackBar(viewModelScope, TextSnack(context.getString(R.string.self_update_error)))
		}
	}

	fun checkChangelogPrompt(context: Context, currentVersionCode: Long) {
		val lastSeen = prefs.lastSeenVersionCode.get()
		val isFreshInstall = runCatching {
			val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
			packageInfo.firstInstallTime == packageInfo.lastUpdateTime
		}.getOrDefault(false)

		when {
			lastSeen == 0L && !isFreshInstall -> {
				_showChangelogSheet.value = true
			}
			lastSeen in 1 until currentVersionCode -> {
				_showChangelogSheet.value = true
			}
			lastSeen == 0L && isFreshInstall -> {
				prefs.lastSeenVersionCode.put(currentVersionCode)
			}
		}
	}

	fun dismissChangelogSheet(currentVersionCode: Long) {
		_showChangelogSheet.value = false
		prefs.lastSeenVersionCode.put(currentVersionCode)
	}

	fun initializeOnStart(
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel,
		isColdStart: Boolean = true
	) = viewModelScope.launch {
		if (hasInitializedColdStart) return@launch
		hasInitializedColdStart = true
		updatesViewModel.restoreCacheImmediately()
		val installedApps = appsViewModel.refreshAndGet(false)
		val hadCache = updatesViewModel.restoreCacheImmediately(installedApps)
		if (isColdStart || !hadCache) {
			updatesViewModel.refresh(load = true, installedAppsSnapshot = installedApps).join()
		}
		checkForSelfUpdate(manual = false)
	}

	fun refresh(
		appsViewModel: AppsViewModel,
		updatesViewModel: UpdatesViewModel
	) = viewModelScope.launch {
		isRefreshing.value = true
		updatesViewModel.beginRefresh()
		try {
			val installedApps = appsViewModel.refreshAndGet(false)
			updatesViewModel.refresh(false, installedApps).join()
		} finally {
			isRefreshing.value = false
		}
	}

	fun processIntent(
		intent: Intent,
		launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
		updatesViewModel: UpdatesViewModel,
		navController: NavController
	) {
		when {
			intent.action == UpdatesNotification.UpdateAction -> processUpdateIntent(navController, updatesViewModel)
			intent.action?.contains(SessionInstaller.INSTALL_ACTION).orFalse() -> processInstallIntent(intent, launcher)
			else -> {}
		}
	}

	fun navigateTo(navController: NavController, route: String) = navController.navigate(route) {
		popUpTo(navController.graph.findStartDestination().id) { saveState = true }
		launchSingleTop = true
		restoreState = true
		prefs.lastTab.put(route)
	}

	fun getLastRoute() = resolveStartRoute(
		prefs.lastTab.get(),
		screens.map { it.route }.toSet()
	)

	private fun processInstallIntent(
		intent: Intent,
		launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
	) = viewModelScope.launch(Dispatchers.IO) {
		when (intent.extras?.getInt(PackageInstaller.EXTRA_STATUS)) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				installLog.currentInstallId = intent.getAppId() ?: 0
				// Launch intent to confirm install
				intent.getIntentExtra()?.let {
					it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
					launcher.launch(it)
				}
			}
			PackageInstaller.STATUS_SUCCESS -> {
				intent.getAppId()?.let {
					installLog.emitStatus(AppInstallStatus(true, it))
				}
			}
			else -> {
				// We assume error and cancel the install
				intent.getAppId()?.let {
					installLog.emitStatus(AppInstallStatus(false, it))
				}
				val message = intent.extras?.getString(PackageInstaller.EXTRA_STATUS_MESSAGE)
				Log.e("MainViewModel", "Failed to install app: $message $intent")
			}
		}
	}

	private fun processUpdateIntent(
		navController: NavController,
		updatesViewModel: UpdatesViewModel
	) {
		navigateTo(navController, Screen.Updates.route)
		updatesViewModel.refresh()
	}

}
