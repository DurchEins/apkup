package com.apkup.viewmodel

import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkup.R
import com.apkup.data.snack.TextSnack
import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.Link
import com.apkup.prefs.Prefs
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


abstract class InstallViewModel(
    private val downloader: Downloader,
    private val installer: SessionInstaller,
    private val snackBar: SnackBar,
    private val stringer: Stringer,
    private val installLog: InstallLog,
    private val prefs: Prefs? = null
): ViewModel() {

    open fun isApkMirrorBlocked(): Boolean = false

    open fun install(update: AppUpdate, uriHandler: UriHandler) {
        when (update.source) {
            ApkMirrorSource -> {
                val url = (update.link as? Link.Url)?.link.orEmpty()
                val directEnabled = prefs?.directApkMirrorDownloads?.get() ?: false
                if (directEnabled && !isApkMirrorBlocked()) {
                    downloadAndInstall(update)
                } else {
                    if (directEnabled && isApkMirrorBlocked()) {
                        snackBar.snackBar(
                            viewModelScope,
                            TextSnack(stringer.get(R.string.apkmirror_challenge_fallback))
                        )
                    }
                    if (url.isNotBlank()) {
                        uriHandler.openUri(url)
                    }
                }
            }
            else -> downloadAndInstall(update)
        }
    }

    protected fun subscribeToInstallStatus() = installLog.status().onEach {
        sendInstallSnack(it)
        if (it.success) {
            finishInstall(it.id).join()
        } else {
            installLog.emitProgress(AppInstallProgress(it.id, 0L))
            onInstallFailed(it.id).join()
        }
    }.launchIn(viewModelScope)

    protected open fun onInstallFailed(id: Int): Job = cancelInstall(id)

    protected fun subscribeToInstallProgress(
        block: (AppInstallProgress) -> Unit
    ) = installLog.progress().onEach {
        block(it)
    }.launchIn(viewModelScope)

    protected abstract fun sendInstallSnack(log: AppInstallStatus)
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
}
