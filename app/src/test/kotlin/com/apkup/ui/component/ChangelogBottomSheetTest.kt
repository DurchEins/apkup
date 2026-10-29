package com.apkup.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogBottomSheetTest {

    @Test
    fun `resolveMilestonePrefix maps 3-part version to 2-part milestone asset prefix`() {
        assertEquals("1.39", resolveMilestonePrefix("1.39.3"))
        assertEquals("1.39", resolveMilestonePrefix("1.39"))
        assertEquals("100.1234", resolveMilestonePrefix("100.1234.5678"))
    }

    @Test
    fun `parseChangelogDocument splits patch section and milestone feature items`() {
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
}
