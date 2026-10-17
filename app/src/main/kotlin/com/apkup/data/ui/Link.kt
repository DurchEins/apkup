package com.apkup.data.ui

import com.aurora.gplayapi.data.models.PlayFile


sealed class Link {
    data object Empty: Link()
    data class Url(val link: String, val size: Long = 0L): Link()
    data class Play(val getInstallFiles: () -> List<PlayFile>): Link()
}
