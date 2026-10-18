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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class MainViewModel(
	private val prefs: Prefs,
	private val installLog: InstallLog,
	workManager: WorkManager
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
