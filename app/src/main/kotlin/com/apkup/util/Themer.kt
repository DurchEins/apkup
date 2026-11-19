package com.apkup.util

import com.apkup.prefs.Prefs
import com.apkup.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class Themer(prefs: Prefs) {

    private val theme = MutableStateFlow(isDarkTheme(prefs.theme.get()))
    private val dynamicColor = MutableStateFlow(prefs.dynamicColor.get())

    fun flow() = theme
    fun dynamicColorFlow() = dynamicColor

    fun setTheme(v: Boolean) { theme.value = v }
    fun setDynamicColor(v: Boolean) { dynamicColor.value = v }

}
