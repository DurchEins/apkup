package com.apkup.data.ui

import com.apkup.R

data class Source(
    val name: String,
    val resourceId: Int
)

val ApkMirrorSource = Source("ApkMirror", R.drawable.ic_apkmirror)
val PlaySource = Source("Play", R.drawable.ic_play)
val IzzyOnDroidSource = Source("IzzyOnDroid", R.drawable.ic_izzy)
val FDroidSource = Source("F-Droid", R.drawable.ic_izzy)
val GitHubSource = Source("GitHub", R.drawable.ic_github)
val GitLabSource = Source("GitLab", R.drawable.ic_gitlab)
val CodebergSource = Source("Codeberg", R.drawable.ic_codeberg)
val CustomFDroidSource = Source("Custom F-Droid", R.drawable.ic_izzy)

object SourcePriorityKeys {
    const val PLAY = "Play"
    const val GITHUB = "GitHub"
    const val CODEBERG = "Codeberg"
    const val FDROID = "FDroid"
    const val CUSTOM_FDROID = "CustomFDroid"
    const val GITLAB = "GitLab"
    const val APKMIRROR = "ApkMirror"

    val DEFAULT_ORDER: List<String> = listOf(
        PLAY,
        GITHUB,
        CODEBERG,
        FDROID,
        CUSTOM_FDROID,
        GITLAB,
        APKMIRROR
    )

    fun fromSource(source: Source): String = when (source) {
        PlaySource -> PLAY
        GitHubSource -> GITHUB
        CodebergSource -> CODEBERG
        FDroidSource, IzzyOnDroidSource -> FDROID
        CustomFDroidSource -> CUSTOM_FDROID
        GitLabSource -> GITLAB
        ApkMirrorSource -> APKMIRROR
        else -> CUSTOM_FDROID
    }

    fun iconForKey(key: String): Int = when (key) {
        PLAY -> R.drawable.ic_play
        GITHUB -> R.drawable.ic_github
        CODEBERG -> R.drawable.ic_codeberg
        FDROID, CUSTOM_FDROID -> R.drawable.ic_izzy
        GITLAB -> R.drawable.ic_gitlab
        APKMIRROR -> R.drawable.ic_apkmirror
        else -> R.drawable.ic_izzy
    }

    fun normalizeOrder(raw: List<String>?): List<String> {
        val valid = raw.orEmpty().filter { it in DEFAULT_ORDER }.distinct()
        return valid + DEFAULT_ORDER.filterNot { it in valid }
    }
}
