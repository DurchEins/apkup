package com.apkup

import com.apkup.data.ui.Screen
import com.apkup.ui.screen.formatCapsuleBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteTest {

    @Test
    fun startDestinationAlwaysResolvesToUpdatesRoute() {
        assertEquals("updates", Screen.Updates.route)
        assertEquals("apps", Screen.Apps.route)
        assertEquals("settings", Screen.Settings.route)
    }

    @Test
    fun formatCapsuleBadgeHandlesCounts() {
        assertEquals("", formatCapsuleBadge(""))
        assertEquals("0", formatCapsuleBadge("0"))
        assertEquals("12", formatCapsuleBadge("12"))
        assertEquals("99", formatCapsuleBadge("99"))
        assertEquals("99+", formatCapsuleBadge("100"))
        assertEquals("99+", formatCapsuleBadge("104"))
        assertEquals("99+", formatCapsuleBadge("999"))
    }

    @Test
    fun resolveChangelogLanguageSuffixMapsCorrectly() {
        assertEquals("de", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.GERMAN))
        assertEquals("en", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.ENGLISH))
        assertEquals("es", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.forLanguageTag("es")))
        assertEquals("fr", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.FRENCH))
        assertEquals("pt", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.forLanguageTag("pt")))
        assertEquals("it", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.ITALIAN))
        assertEquals("ru", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.forLanguageTag("ru")))
        assertEquals("zh-rCN", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.CHINESE))
        assertEquals("ja", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.JAPANESE))
        assertEquals("en", com.apkup.ui.component.resolveChangelogLanguageSuffix(java.util.Locale.KOREAN))
    }
}

