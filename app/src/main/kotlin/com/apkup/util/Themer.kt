package com.apkup.util

import com.apkup.prefs.Prefs
import com.apkup.ui.theme.THEME_ACCESSIBILITY
import com.apkup.ui.theme.THEME_SYSTEM
import com.apkup.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Themer(private val prefs: Prefs) {

    private fun computeDark(nowMillis: Long = System.currentTimeMillis()): Boolean = isDarkTheme(
        theme = prefs.theme.get(),
        a11ySubMode = prefs.a11yThemeSubMode.get(),
        customSunriseMinutes = prefs.a11ySunriseMinutes.get(),
        customSunsetMinutes = prefs.a11ySunsetMinutes.get(),
        epochMillis = nowMillis
    )

    private val theme = MutableStateFlow(computeDark())
    private val dynamicColor = MutableStateFlow(prefs.dynamicColor.get())
    private val themeMode = MutableStateFlow(prefs.theme.get().coerceIn(THEME_SYSTEM, THEME_ACCESSIBILITY))
    private val highContrast = MutableStateFlow(prefs.theme.get() == THEME_ACCESSIBILITY)
    private val a11ySubMode = MutableStateFlow(prefs.a11yThemeSubMode.get())
    private val a11ySunriseMinutes = MutableStateFlow(prefs.a11ySunriseMinutes.get())
    private val a11ySunsetMinutes = MutableStateFlow(prefs.a11ySunsetMinutes.get())
    private val appsGridColumns = MutableStateFlow(prefs.appsGridColumns.get().coerceIn(2, 3))

    fun flow(): StateFlow<Boolean> = theme.asStateFlow()
    fun dynamicColorFlow(): StateFlow<Boolean> = dynamicColor.asStateFlow()
    fun themeModeFlow(): StateFlow<Int> = themeMode.asStateFlow()
    fun highContrastFlow(): StateFlow<Boolean> = highContrast.asStateFlow()
    fun a11ySubModeFlow(): StateFlow<Int> = a11ySubMode.asStateFlow()
    fun a11ySunriseMinutesFlow(): StateFlow<Int> = a11ySunriseMinutes.asStateFlow()
    fun a11ySunsetMinutesFlow(): StateFlow<Int> = a11ySunsetMinutes.asStateFlow()
    fun appsGridColumnsFlow(): StateFlow<Int> = appsGridColumns.asStateFlow()

    fun setTheme(v: Boolean) { theme.value = v }
    fun setDynamicColor(v: Boolean) { dynamicColor.value = v }

    fun refreshFromPrefs(nowMillis: Long = System.currentTimeMillis()) {
        val mode = prefs.theme.get().coerceIn(THEME_SYSTEM, THEME_ACCESSIBILITY)
        themeMode.value = mode
        highContrast.value = mode == THEME_ACCESSIBILITY
        a11ySubMode.value = prefs.a11yThemeSubMode.get()
        a11ySunriseMinutes.value = prefs.a11ySunriseMinutes.get()
        a11ySunsetMinutes.value = prefs.a11ySunsetMinutes.get()
        appsGridColumns.value = prefs.appsGridColumns.get().coerceIn(2, 3)
        dynamicColor.value = prefs.dynamicColor.get()
        theme.value = computeDark(nowMillis)
    }

    fun refreshSolarTheme(nowMillis: Long = System.currentTimeMillis()) {
        if (prefs.theme.get() == THEME_ACCESSIBILITY) {
            theme.value = computeDark(nowMillis)
        }
    }
}
