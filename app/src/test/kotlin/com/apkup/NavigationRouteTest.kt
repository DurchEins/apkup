package com.apkup

import com.apkup.data.ui.resolveStartRoute
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
}
