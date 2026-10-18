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
}
