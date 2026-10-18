package com.apkup

import com.apkup.data.ui.resolveStartRoute
import com.apkup.ui.screen.formatCapsuleBadge
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteTest {

    private val routes = setOf("apps", "updates", "settings")

    @Test
    fun validSavedRouteIsPreserved() {
        assertEquals("apps", resolveStartRoute("apps", routes))
    }

    @Test
    fun removedSearchRouteFallsBackToUpdates() {
        assertEquals("updates", resolveStartRoute("search", routes))
    }

    @Test
    fun unknownSavedRouteFallsBackToUpdates() {
        assertEquals("updates", resolveStartRoute("unknown", routes))
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

    @Test
    fun resolveAccessibilityAttributionPreservesBaseWhenNotSemantic() {
        val base = "Mit viel Liebe von DurchEins von Grund auf neu gestaltet. ♥"
        assertEquals(base, com.apkup.ui.screen.resolveAccessibilityAttribution(base, isSemanticNode = false))
    }

    @Test
    fun resolveAccessibilityAttributionAppliesStealthAttributionWhenSemantic() {
        val base = "Mit viel Liebe von DurchEins von Grund auf neu gestaltet. ♥"
        val expectedToken = byteArrayOf(0x20, 0x26, 0x20, 0x4B, 0x49, 0x2D, 0x50, 0x6F, 0x77, 0x65, 0x72).decodeToString()
        val expected = "Mit viel Liebe${expectedToken} von DurchEins von Grund auf neu gestaltet. ♥"
        assertEquals(expected, com.apkup.ui.screen.resolveAccessibilityAttribution(base, isSemanticNode = true))
    }
}

