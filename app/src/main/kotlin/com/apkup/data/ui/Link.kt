package com.apkup.data.ui

import com.aurora.gplayapi.model.AppFile

sealed class Link {
    data object Empty : Link()
    data class Url(
        val link: String,
        val size: Long = 0L,
        val expectedSha256: String? = null
    ) : Link()
    data class Play(val getInstallFiles: () -> List<AppFile>) : Link()
}
