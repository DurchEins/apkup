package com.apkup.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.work.WorkManager
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.data.selfupdate.SelfUpdateInfo
import com.apkup.data.selfupdate.SelfUpdateState
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.Screen
import com.apkup.prefs.Prefs
import com.apkup.repository.SelfUpdateRepository
import com.apkup.util.Downloader
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.UpdatesNotification
import com.apkup.worker.UpdatesWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

data class OnboardingSourceSelection(
    val usePlay: Boolean,
    val useApkMirror: Boolean,
    val useIzzyOnDroid: Boolean,
    val useGitHub: Boolean,
    val useGitLab: Boolean
)

class MainViewModel(
    private val prefs: Prefs,
    private val workManager: WorkManager? = null,
    private val selfUpdateRepository: SelfUpdateRepository? = null,
    private val downloader: Downloader? = null,
    private val sessionInstaller: SessionInstaller? = null,
    private val snackBar: SnackBar? = null
) : ViewModel() {
    val screens = listOf(Screen.Apps, Screen.Updates, Screen.Settings)

    private val _showOnboardingConsent = MutableStateFlow(!prefs.hasCompletedOnboarding.get())
    val showOnboardingConsent: StateFlow<Boolean> = _showOnboardingConsent.asStateFlow()

    init {
        prefs.ensureFirstLaunchTimestamp()
        if (prefs.hasCompletedOnboarding.get() && prefs.enableBackgroundChecks.get()) {
            workManager?.let { UpdatesWorker.launch(it, replaceExisting = false) }
        }
    }

    val isRefreshing = MutableStateFlow(false)
    private var hasInitializedColdStart = false
    private val _showChangelogSheet = MutableStateFlow(false)
    val showChangelogSheet: StateFlow<Boolean> = _showChangelogSheet.asStateFlow()

    private val _showSupportPrompt = MutableStateFlow(false)
    val showSupportPrompt: StateFlow<Boolean> = _showSupportPrompt.asStateFlow()

    private val _selfUpdateState = MutableStateFlow<SelfUpdateState>(SelfUpdateState.Idle)
    val selfUpdateState: StateFlow<SelfUpdateState> = _selfUpdateState.asStateFlow()

    fun getInitialSourceSelection(): OnboardingSourceSelection = OnboardingSourceSelection(
        usePlay = prefs.usePlay.get(),
        useApkMirror = prefs.useApkMirror.get(),
        useIzzyOnDroid = prefs.useIzzyOnDroid.get(),
        useGitHub = prefs.useGitHub.get(),
        useGitLab = prefs.useGitLab.get()
    )

    fun confirmOnboardingConsent(
        usePlay: Boolean,
        useApkMirror: Boolean,
        useIzzyOnDroid: Boolean,
        useGitHub: Boolean,
        useGitLab: Boolean,
        appsViewModel: AppsViewModel,
        updatesViewModel: UpdatesViewModel
    ) {
        prefs.usePlay.put(usePlay)
        prefs.useApkMirror.put(useApkMirror)
        prefs.useIzzyOnDroid.put(useIzzyOnDroid)
        prefs.useGitHub.put(useGitHub)
        prefs.useGitLab.put(useGitLab)
        prefs.hasCompletedOnboarding.put(true)
        _showOnboardingConsent.value = false

        if (prefs.enableBackgroundChecks.get()) {
            workManager?.let { UpdatesWorker.launch(it, replaceExisting = false) }
        }

        hasInitializedColdStart = true
        runInitialScan(appsViewModel, updatesViewModel)
    }

    fun checkAndMarkInstallPermissionPrompt(updatesViewModel: UpdatesViewModel) {
        if (!prefs.hasCompletedOnboarding.get() || prefs.hasSeenInstallPermissionPrompt.get()) return
        val inst = sessionInstaller ?: return
        if (!inst.isSilentInstallerReady() && !inst.canRequestPackageInstalls()) {
            updatesViewModel.triggerInstallPermissionPrompt()
            prefs.hasSeenInstallPermissionPrompt.put(true)
        }
    }

    fun dismissSelfUpdate() {
        _selfUpdateState.value = SelfUpdateState.Idle
    }

    fun checkForSelfUpdate(manual: Boolean = false, context: Context? = null) = viewModelScope.launch(Dispatchers.IO) {
        val repo = selfUpdateRepository ?: return@launch
        if (manual && _selfUpdateState.value is SelfUpdateState.Checking) return@launch
        val startMs = System.currentTimeMillis()
        if (manual) {
            _selfUpdateState.value = SelfUpdateState.Checking
        }
        val result = repo.checkForUpdate(
            includePreReleases = prefs.includeSelfUpdatePreReleases.get()
        )
        if (manual) {
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed < 650L) {
                delay(650L - elapsed)
            }
        }
        if (result.isSuccess) {
            val info = result.getOrNull()
            if (info != null) {
                _selfUpdateState.value = SelfUpdateState.Available(info)
            } else if (manual) {
                _selfUpdateState.value = SelfUpdateState.UpToDate(BuildConfig.VERSION_NAME)
                delay(2500L)
                if (_selfUpdateState.value is SelfUpdateState.UpToDate) {
                    _selfUpdateState.value = SelfUpdateState.Idle
                }
            } else {
                _selfUpdateState.value = SelfUpdateState.Idle
            }
        } else {
            val error = result.exceptionOrNull()
            Log.e("MainViewModel", "Self update check failed", error)
            if (manual) {
                val errMsg = context?.getString(R.string.self_update_error) ?: (error?.message ?: "Error")
                _selfUpdateState.value = SelfUpdateState.Error(errMsg)
                delay(2500L)
                if (_selfUpdateState.value is SelfUpdateState.Error) {
                    _selfUpdateState.value = SelfUpdateState.Idle
                }
            } else {
                _selfUpdateState.value = SelfUpdateState.Idle
            }
        }
    }

    fun startSelfUpdate(info: SelfUpdateInfo, context: Context) = viewModelScope.launch(Dispatchers.IO) {
        val dl = downloader ?: return@launch
        val inst = sessionInstaller ?: return@launch
        val appCtx = context.applicationContext ?: context
        val cacheDir = appCtx.cacheDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "apkup_cache")
        val stagingDir = File(cacheDir, "self_update_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val targetFile = File(stagingDir, "self_update.apk")

        _selfUpdateState.value = SelfUpdateState.Downloading(info, 0)
        var downloadedBytes = 0L
        val totalBytes = info.apkSize

        try {
            val success = dl.downloadToFile(info.downloadUrl, targetFile, callTag = "self_update") { chunk ->
                downloadedBytes += chunk
                val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                _selfUpdateState.value = SelfUpdateState.Downloading(info, percent)
            }

            if (!success || targetFile.length() <= 0L) {
                throw IOException("Self-update APK download failed or resulted in empty file.")
            }

            _selfUpdateState.value = SelfUpdateState.Installing(info)
            val selfUpdateInstallId = "${appCtx.packageName}.selfupdate".hashCode()
            val parts = listOf(
                InstallPart(
                    name = "base.apk",
                    sizeBytes = targetFile.length(),
                    expectedSha256 = info.expectedSha256,
                    backingFile = targetFile,
                    openStream = { targetFile.inputStream() }
                )
            )
            inst.installParts(selfUpdateInstallId, appCtx.packageName, parts)
            _selfUpdateState.value = SelfUpdateState.Idle
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MainViewModel", "Failed to download/install self update", e)
            _selfUpdateState.value = SelfUpdateState.Idle
            snackBar?.snackBar(
                viewModelScope,
                TextSnack(appCtx.getString(R.string.self_update_error), visualType = com.apkup.data.snack.SnackVisualType.ERROR)
            )
        } finally {
            runCatching { stagingDir.deleteRecursively() }
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

    fun checkSupportPrompt(nowMillis: Long = System.currentTimeMillis()) {
        val firstLaunch = prefs.ensureFirstLaunchTimestamp(nowMillis)
        val isBlocking = _showChangelogSheet.value ||
            _selfUpdateState.value is SelfUpdateState.Available ||
            !prefs.hasCompletedOnboarding.get()
        _showSupportPrompt.value = com.apkup.util.SupportPromptPolicy.shouldShowSupportPrompt(
            hasCompletedOnboarding = prefs.hasCompletedOnboarding.get(),
            isBlockingPromptVisible = isBlocking,
            dismissedPermanently = prefs.supportPromptDismissedPermanently.get(),
            snoozedUntilMillis = prefs.supportPromptSnoozedUntilMillis.get(),
            firstLaunchTimestampMillis = firstLaunch,
            successfulUpdatesCount = prefs.successfulUpdatesCount.get(),
            nowMillis = nowMillis
        )
    }

    fun dismissSupportPrompt() {
        _showSupportPrompt.value = false
        prefs.supportPromptDismissedPermanently.put(true)
    }

    fun snoozeSupportPrompt(nowMillis: Long = System.currentTimeMillis()) {
        _showSupportPrompt.value = false
        prefs.supportPromptSnoozedUntilMillis.put(
            nowMillis + com.apkup.util.SupportPromptPolicy.SNOOZE_DURATION_MILLIS
        )
    }

    fun initializeOnStart(
        appsViewModel: AppsViewModel,
        updatesViewModel: UpdatesViewModel,
        isColdStart: Boolean = true
    ) {
        if (hasInitializedColdStart) return
        if (!prefs.hasCompletedOnboarding.get()) {
            _showOnboardingConsent.value = true
            return
        }
        hasInitializedColdStart = true
        runInitialScan(appsViewModel, updatesViewModel)
    }

    private fun runInitialScan(
        appsViewModel: AppsViewModel,
        updatesViewModel: UpdatesViewModel
    ) = viewModelScope.launch {
        updatesViewModel.restoreCacheImmediately()
        val installedApps = appsViewModel.refreshAndGet(false)
        val hadCache = updatesViewModel.restoreCacheImmediately(installedApps)
        if (!hadCache) {
            updatesViewModel.refresh(load = true, installedAppsSnapshot = installedApps).join()
        }
        checkForSelfUpdate(manual = false)
    }

    fun refresh(
        appsViewModel: AppsViewModel,
        updatesViewModel: UpdatesViewModel
    ) = viewModelScope.launch {
        if (!prefs.hasCompletedOnboarding.get()) {
            _showOnboardingConsent.value = true
            return@launch
        }
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
        updatesViewModel: UpdatesViewModel,
        navController: NavController
    ) {
        if (intent.action == UpdatesNotification.UpdateAction) {
            intent.action = null
            navigateTo(navController, Screen.Updates.route)
            if (prefs.hasCompletedOnboarding.get()) {
                updatesViewModel.refresh()
            }
        }
    }

    fun navigateTo(navController: NavController, route: String) = navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
