package com.apkup.repository

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUpdateRepositoryTest {

    private val repo = SelfUpdateRepository(OkHttpClient(), Gson())

    @Test
    fun `parseVersion strips build number suffix from preRelease and supports 3-part versions`() {
        val parsed = repo.parseVersion("v1.39.3-Beta (148)")
        assertEquals(1, parsed.major)
        assertEquals(39, parsed.minor)
        assertEquals(3, parsed.patch)
        assertEquals("Beta", parsed.preRelease)
        assertEquals(148, parsed.buildNumber)
    }

    @Test
    fun `parseVersion supports 3 to 4 digit version segments and build numbers`() {
        val parsed = repo.parseVersion("v100.1234.5678-Beta (12345)")
        assertEquals(100, parsed.major)
        assertEquals(1234, parsed.minor)
        assertEquals(5678, parsed.patch)
        assertEquals("Beta", parsed.preRelease)
        assertEquals(12345, parsed.buildNumber)
    }

    @Test
    fun `isNewerVersion compares patch and 3 to 4 digit numbers accurately`() {
        assertTrue(repo.isNewerVersion("1.39.2-Beta (147)", "v1.39.3-Beta (148)"))
        assertFalse(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.3-Beta (148)"))
        assertFalse(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.2-Beta (147)"))
        assertTrue(repo.isNewerVersion("1.39.3-Beta (148)", "v1.39.10-Beta (155)"))
        assertTrue(repo.isNewerVersion("1.999.999-Beta (9999)", "1.1000.0-Beta (10000)"))
    }

    @Test
    fun `isNewerVersion uses buildNumber as tie-breaker when semantic version and preRelease match`() {
        assertTrue(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.3-Beta (149)"))
        assertFalse(repo.isNewerVersion("1.39.3-Beta (148)", "1.39.3-Beta"))
    }
}
