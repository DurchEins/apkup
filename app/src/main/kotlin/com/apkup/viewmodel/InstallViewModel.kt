package com.apkup.viewmodel

import android.util.Log
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
import com.apkup.util.InstallPart
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.aurora.gplayapi.data.models.PlayFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.IOException


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
            cancelInstall(it.id).join()
        }
    }.launchIn(viewModelScope)

    protected fun subscribeToInstallProgress(
        block: (AppInstallProgress) -> Unit
    ) = installLog.progress().onEach {
        block(it)
    }.launchIn(viewModelScope)

    protected suspend fun downloadAndInstall(
        id: Int,
        packageName: String,
        link: Link,
        appName: String = packageName
    ): Boolean = try {
        installLog.beginInstall(id)
        installLog.emitProgress(AppInstallProgress(id, 0L))
        when (link) {
            Link.Empty -> {
                Log.e("InstallViewModel", "downloadAndInstall: Empty link for $packageName")
                throw IOException("Unsupported empty download link for $packageName")
            }
            is Link.Play -> {
                val files = link.getInstallFiles()
                if (files.isEmpty()) {
                    throw IOException("Play Store returned no installable APK files for $packageName")
                }
                val sortedFiles = files.sortedWith(
                    compareByDescending<PlayFile> { it.type == PlayFile.Type.BASE }
                        .thenBy { it.name }
                )
                installLog.emitProgress(AppInstallProgress(id, 0L, sortedFiles.sumOf { it.size }))
                val parts = sortedFiles.mapIndexed { index, file ->
                    val partName = when {
                        file.type == PlayFile.Type.BASE -> "base.apk"
                        file.name.endsWith(".apk") -> file.name
                        file.name.isNotBlank() -> "${file.name}.apk"
                        else -> "split_$index.apk"
                    }
                    InstallPart(partName) {
                        downloader.downloadStream(file.url)
                            ?: throw IOException("Connection dropped while opening Play stream for ${file.name}")
                    }
                }
                installer.installParts(id, packageName, parts)
            }
            is Link.Url -> {
                if (link.link.isBlank()) {
                    throw IOException("Blank download URL for $packageName")
                }
                installLog.emitProgress(AppInstallProgress(id, 0L, link.size))
                val parts = listOf(
                    InstallPart("base.apk") {
                        downloader.downloadStream(link.link)
                            ?: throw IOException("Connection dropped while opening URL stream for $packageName")
                    }
                )
                installer.installParts(id, packageName, parts)
            }
        }
    } catch (cancellation: CancellationException) {
        installLog.emitProgress(AppInstallProgress(id, 0L))
        cancelInstall(id)
        throw cancellation
    } catch (error: Throwable) {
        Log.e("InstallViewModel", "Error in downloadAndInstall for $packageName (${error.message})", error)
        installLog.emitProgress(AppInstallProgress(id, 0L))
        // Route failure through installLog.emitStatus so duplicate snackbars are prevented
        if (!installLog.emitStatus(AppInstallStatus(false, id, snack = true))) {
            cancelInstall(id).join()
        }
        false
    }

    protected abstract fun sendInstallSnack(log: AppInstallStatus)
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
}
