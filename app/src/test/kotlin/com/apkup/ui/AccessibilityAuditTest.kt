package com.apkup.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.apkup.ui.theme.ApkUpExtendedColors
import com.apkup.ui.theme.DarkColors
import com.apkup.ui.theme.DarkExtendedColors
import com.apkup.ui.theme.LightColors
import com.apkup.ui.theme.LightExtendedColors
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated WCAG 2.2 AA contrast & 9-locale accessibility string resource verification.
 */
class AccessibilityAuditTest {

    private fun linearChannel(component: Float): Double {
        val c = component.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearChannel(color.red) +
            0.7152 * linearChannel(color.green) +
            0.0722 * linearChannel(color.blue)

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, minRatio: Double = 4.5) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$label contrast ${"%.2f".format(ratio)}:1 is below WCAG AA $minRatio:1",
            ratio >= minRatio
        )
    }

    private fun verifyColorSchemeContrast(name: String, scheme: ColorScheme, ext: ApkUpExtendedColors) {
        assertContrast("$name onBackground/background", scheme.onBackground, scheme.background)
        assertContrast("$name onSurface/surface", scheme.onSurface, scheme.surface)
        assertContrast("$name onSurface/surfaceContainer", scheme.onSurface, scheme.surfaceContainer)
        assertContrast("$name onSurfaceVariant/background", scheme.onSurfaceVariant, scheme.background)
        assertContrast("$name onSurfaceVariant/surfaceContainer", scheme.onSurfaceVariant, scheme.surfaceContainer)
        assertContrast("$name onSurfaceVariant/surfaceContainerHigh", scheme.onSurfaceVariant, scheme.surfaceContainerHigh)
        assertContrast("$name primary/background", scheme.primary, scheme.background)
        assertContrast("$name primary/surfaceContainer", scheme.primary, scheme.surfaceContainer)
        assertContrast("$name primary/surfaceContainerHigh", scheme.primary, scheme.surfaceContainerHigh)
        assertContrast("$name onPrimary/primary", scheme.onPrimary, scheme.primary)
        assertContrast("$name onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertContrast("$name onSecondary/secondary", scheme.onSecondary, scheme.secondary)
        assertContrast("$name onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
        assertContrast("$name onTertiary/tertiary", scheme.onTertiary, scheme.tertiary)
        assertContrast("$name onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer)
        assertContrast("$name onError/error", scheme.onError, scheme.error)
        assertContrast("$name onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer)

        assertContrast("$name warning/background", ext.warning, scheme.background)
        assertContrast("$name warning/surfaceContainerHigh", ext.warning, scheme.surfaceContainerHigh)
        assertContrast("$name onWarning/warning", ext.onWarning, ext.warning)
        assertContrast("$name onWarningContainer/warningContainer", ext.onWarningContainer, ext.warningContainer)
        assertContrast("$name onSuccess/success", ext.onSuccess, ext.success)
        assertContrast("$name onSuccessContainer/successContainer", ext.onSuccessContainer, ext.successContainer)
        assertContrast("$name onInfo/info", ext.onInfo, ext.info)
        assertContrast("$name onInfoContainer/infoContainer", ext.onInfoContainer, ext.infoContainer)
    }

    @Test
    fun `Light and Dark Material 3 color schemes meet WCAG 2_2 AA text contrast`() {
        verifyColorSchemeContrast("Light", LightColors, LightExtendedColors)
        verifyColorSchemeContrast("Dark", DarkColors, DarkExtendedColors)
    }

    @Test
    fun `All 9 Android locale string files parse cleanly and define every accessibility label`() {
        val localeDirs = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-pt",
            "values-ru",
            "values-zh-rCN"
        )
        val requiredKeys = listOf(
            "updates_more_actions_cd",
            "a11y_state_expanded",
            "a11y_state_collapsed",
            "a11y_clear_search",
            "a11y_close_search",
            "a11y_select_item",
            "a11y_deselect_item"
        )
        val resRoot = sequenceOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("Could not locate Android res directory")

        val factory = DocumentBuilderFactory.newInstance()
        for (dirName in localeDirs) {
            val stringsFile = File(resRoot, "$dirName/strings.xml")
            assertTrue("Missing $dirName/strings.xml", stringsFile.isFile)
            val doc = factory.newDocumentBuilder().parse(stringsFile)
            val nodes = doc.getElementsByTagName("string")
            val valuesByName = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i)
                val key = el.attributes?.getNamedItem("name")?.nodeValue ?: continue
                valuesByName[key] = el.textContent.orEmpty().trim()
            }
            for (key in requiredKeys) {
                val value = valuesByName[key]
                assertTrue("Missing key '$key' in $dirName/strings.xml", !value.isNullOrBlank())
                assertFalse(
                    "Key '$key' in $dirName/strings.xml still contains generic placeholder",
                    value.equals("Icon", ignoreCase = true) || value.equals("TODO", ignoreCase = true)
                )
            }
        }
    }
}
