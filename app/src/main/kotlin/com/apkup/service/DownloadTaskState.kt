package com.apkup.service

import com.apkup.data.ui.PlaySource
import com.apkup.data.ui.Source

data class DownloadTaskState(
    val id: Int,
    val packageName: String,
    val appName: String,
    val version: String = "",
    val source: Source = PlaySource,
    val progress: Long = 0L,
    val total: Long = 0L,
    val isInstalling: Boolean = false,
    val isQueued: Boolean = true,
    val isResolvingLink: Boolean = false,
    val isSaveOnly: Boolean = false
)
