package com.apkup.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogBottomSheetTest {

    @Test
    fun `resolveMilestonePrefix maps 3-part version to 2-part milestone asset prefix`() {
        assertEquals("1.39", resolveMilestonePrefix("1.39.3"))
        assertEquals("1.39", resolveMilestonePrefix("1.39"))
        assertEquals("100.1234", resolveMilestonePrefix("100.1234.5678"))
    }

    @Test
    fun `resolveBadgeType distinguishes major, feature, and patch versions including multi-digit segments`() {
        assertEquals(ChangelogBadgeType.PATCH, resolveBadgeType("1.39.5-Beta (150)"))
        assertEquals(ChangelogBadgeType.FEATURE, resolveBadgeType("1.39.0-Beta (148)"))
        assertEquals(ChangelogBadgeType.MAJOR, resolveBadgeType("2.0.0-Beta (200)"))
        assertEquals(ChangelogBadgeType.PATCH, resolveBadgeType("10.125.104-Beta (1480)"))
        assertEquals(ChangelogBadgeType.FEATURE, resolveBadgeType("10.125.0-Beta (1400)"))
        assertEquals(ChangelogBadgeType.FEATURE, resolveBadgeType("1.39.5", "Feature-Update"))
        assertEquals(ChangelogBadgeType.PATCH, resolveBadgeType("1.39.0", "Patch & Bugfix"))
    }

    @Test
    fun `parseChangelogDocument parses Google-style monthly sections, release headers, badges, and banner`() {
        val markdown = """
            # Was ist neu

            ## Oktober 2026

            ### 29. Oktober 2026 | 1.39.5-Beta (150) | Patch & Bugfix

            - **Neues Changelog im Monats-Design** Übersichtliche Monats-Gruppierung.
            - **Allgemeine Verbesserungen** Fehlerbehebungen und Leistungsverbesserungen.

            ### 26. Oktober 2026 | 1.39.0-Beta (148) | Feature-Update

            > [Banner] 24h-Download-Speicher | + Sofort-Installation

            - **Downloads bleiben 24 Stunden gespeichert** Heruntergeladene Updates bleiben erhalten.

            ## September 2026

            ### 18. September 2026 | 1.31.0-Beta (135) | Feature-Update

            - **Schwebende Navigationsleiste** Neues Dock-Design.
        """.trimIndent()

        val doc = parseChangelogDocument(markdown)
        assertEquals(2, doc.monthSections.size)

        val october = doc.monthSections[0]
        assertEquals("Oktober 2026", october.monthTitle)
        assertEquals(2, october.releases.size)

        val patchRelease = october.releases[0]
        assertEquals("29. Oktober 2026", patchRelease.date)
        assertEquals("1.39.5-Beta (150)", patchRelease.version)
        assertEquals("Patch & Bugfix", patchRelease.badgeLabel)
        assertEquals(ChangelogBadgeType.PATCH, patchRelease.badgeType)
        assertNull(patchRelease.banner)
        assertEquals(2, patchRelease.entries.size)
        assertEquals("Neues Changelog im Monats-Design", patchRelease.entries[0].title)
        assertEquals("Übersichtliche Monats-Gruppierung.", patchRelease.entries[0].description)

        val featureRelease = october.releases[1]
        assertEquals("26. Oktober 2026", featureRelease.date)
        assertEquals("1.39.0-Beta (148)", featureRelease.version)
        assertEquals("Feature-Update", featureRelease.badgeLabel)
        assertEquals(ChangelogBadgeType.FEATURE, featureRelease.badgeType)
        assertNotNull(featureRelease.banner)
        assertEquals("24h-Download-Speicher", featureRelease.banner?.title)
        assertEquals("+ Sofort-Installation", featureRelease.banner?.badgeText)
        assertEquals(1, featureRelease.entries.size)

        val september = doc.monthSections[1]
        assertEquals("September 2026", september.monthTitle)
        assertEquals(1, september.releases.size)
        assertEquals("18. September 2026", september.releases[0].date)
    }

    @Test
    fun `parseChangelogDocument splits legacy patch section and milestone feature items`() {
        val markdown = """
            # Was ist neu in Version 1.39.3
            
            ## 🛠️ Neu & behoben im aktuellen Patch (v1.39.3)
            
            - **Semantische 3-Zahlen-Versionierung** — Übersichtliches Versionsschema `v1.39.3-Beta (148)`.
            - **Live-Animation** — Direktes Feedback im Kachel-Button.
            
            ## ✨ Haupt-Features aus v1.39
            
            - **Ausklappbare Erweiterte Einstellungen** — Übersichtlich gebündelt im Setup-Tab.
            - **Offizielles ApkUp Troubleshooting-Wiki** — Direkte Hilfe bei Installationsfehlern.
        """.trimIndent()

        val doc = parseChangelogDocument(markdown)
        assertEquals("🛠️ Neu & behoben im aktuellen Patch (v1.39.3)", doc.patchTitle)
        assertEquals(2, doc.patchEntries.size)
        assertEquals("Semantische 3-Zahlen-Versionierung", doc.patchEntries[0].title)
        assertEquals("Übersichtliches Versionsschema v1.39.3-Beta (148).", doc.patchEntries[0].description)
        assertEquals("✨ Haupt-Features aus v1.39", doc.featureTitle)
        assertEquals(2, doc.featureEntries.size)
        assertEquals("Ausklappbare Erweiterte Einstellungen", doc.featureEntries[0].title)
        assertTrue(doc.monthSections.isNotEmpty())
    }

    @Test
    fun `parseChangelogDocument strips trailing title colons and inline markdown syntax`() {
        val markdown = """
            ## 🛠️ Neu & behoben im aktuellen Patch (v1.39.5)
            - ⚡ **Titel mit Doppelpunkt:** Beschreibung mit `Backticks` und *Kursivtext*.
        """.trimIndent()

        val doc = parseChangelogDocument(markdown)
        assertEquals(1, doc.patchEntries.size)
        assertEquals("Titel mit Doppelpunkt", doc.patchEntries[0].title)
        assertEquals("Beschreibung mit Backticks und Kursivtext.", doc.patchEntries[0].description)
    }

    @Test
    fun `production changelog assets parse into real monthly accordion sections with banners on every feature release`() {
        val assetDir = sequenceOf(
            File("src/main/assets/changelog"),
            File("app/src/main/assets/changelog")
        ).first { it.exists() }

        listOf("1.41_de.md", "1.41_en.md").forEach { fileName ->
            val raw = File(assetDir, fileName).readText(Charsets.UTF_8)
            val doc = parseChangelogDocument(raw, "1.41.13-Beta (168)")
            assertEquals("Expected 2 real month sections (November & October) in $fileName", 2, doc.monthSections.size)
            assertTrue("Expected at least 3 November releases in $fileName", doc.monthSections[0].releases.size >= 3)
            assertTrue("Expected at least 7 October releases in $fileName", doc.monthSections[1].releases.size >= 7)
            doc.monthSections.flatMap { it.releases }.forEach { release ->
                assertTrue(release.date.isNotBlank())
                assertTrue(release.version.isNotBlank())
                assertTrue(release.badgeLabel.isNotBlank())
                assertTrue(release.entries.isNotEmpty())
                if (release.badgeType == ChangelogBadgeType.FEATURE || release.badgeType == ChangelogBadgeType.MAJOR) {
                    assertNotNull("Expected visual highlight banner for feature release ${release.version} in $fileName", release.banner)
                }
                release.entries.forEach { entry ->
                    assertFalse("Title should not end with colon in $fileName: ${entry.title}", entry.title.endsWith(":"))
                    assertFalse("Description should not contain backticks in $fileName", entry.description.contains("`"))
                }
            }
        }
    }
}
