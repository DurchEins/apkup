package com.apkup.util

import com.apkup.data.ui.AppInstallProgress
import com.apkup.data.ui.AppInstallStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap


class InstallLog {

    private val status = MutableSharedFlow<AppInstallStatus>(100)
    private val progress = MutableSharedFlow<AppInstallProgress>(100)
    private val completedAttemptIds = ConcurrentHashMap.newKeySet<Int>()
    @Volatile var currentInstallId: Int = 0

    fun status() = status.asSharedFlow()
    fun progress() = progress.asSharedFlow()

    fun beginInstall(id: Int) {
        currentInstallId = id
        completedAttemptIds.remove(id)
    }

    fun cancelCurrentInstall() {
        val id = currentInstallId
        if (id != 0 && completedAttemptIds.add(id)) {
            status.tryEmit(AppInstallStatus(false, id, snack = true))
        }
    }

    fun emitStatus(newStatus: AppInstallStatus): Boolean {
        if (newStatus.id != 0 && !completedAttemptIds.add(newStatus.id)) {
            return false
        }
        return status.tryEmit(newStatus)
    }

    fun emitProgress(newProgress: AppInstallProgress) = progress.tryEmit(newProgress)

}
