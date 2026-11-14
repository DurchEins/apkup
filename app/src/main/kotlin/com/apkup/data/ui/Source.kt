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
