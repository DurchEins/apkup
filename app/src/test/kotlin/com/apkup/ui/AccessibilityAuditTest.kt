package com.apkup.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.apkup.data.ui.cleanChangelogSpeechText
import com.apkup.data.ui.cleanChangelogText
import com.apkup.ui.theme.A11yDarkColors
import com.apkup.ui.theme.A11yDarkExtendedColors
import com.apkup.ui.theme.A11yLightColors
import com.apkup.ui.theme.A11yLightExtendedColors
import com.apkup.ui.theme.ApkUpExtendedColors
import com.apkup.ui.theme.DarkColors
import com.apkup.ui.theme.DarkExtendedColors
import com.apkup.ui.theme.LightColors
import com.apkup.ui.theme.LightExtendedColors
import com.apkup.util.SolarScheduleCalculator
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
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

    private fun verifyColorSchemeContrast(
        name: String,
        scheme: ColorScheme,
        ext: ApkUpExtendedColors,
        minTextRatio: Double = 4.5
    ) {
        assertContrast("$name onBackground/background", scheme.onBackground, scheme.background, minTextRatio)
        assertContrast("$name onSurface/surface", scheme.onSurface, scheme.surface, minTextRatio)
        assertContrast("$name onSurface/surfaceContainer", scheme.onSurface, scheme.surfaceContainer, minTextRatio)
        assertContrast("$name onSurfaceVariant/background", scheme.onSurfaceVariant, scheme.background, minTextRatio)
        assertContrast("$name onSurfaceVariant/surfaceContainer", scheme.onSurfaceVariant, scheme.surfaceContainer, minTextRatio)
        assertContrast("$name onSurfaceVariant/surfaceContainerHigh", scheme.onSurfaceVariant, scheme.surfaceContainerHigh, minTextRatio)
        assertContrast("$name primary/background", scheme.primary, scheme.background, minTextRatio)
        assertContrast("$name primary/surfaceContainer", scheme.primary, scheme.surfaceContainer, minTextRatio)
        assertContrast("$name primary/surfaceContainerHigh", scheme.primary, scheme.surfaceContainerHigh, minTextRatio)
        assertContrast("$name onPrimary/primary", scheme.onPrimary, scheme.primary, minTextRatio)
        assertContrast("$name onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, minTextRatio)
        assertContrast("$name onSecondary/secondary", scheme.onSecondary, scheme.secondary, minTextRatio)
        assertContrast("$name onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, minTextRatio)
        assertContrast("$name onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, minTextRatio)
        assertContrast("$name onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, minTextRatio)
        assertContrast("$name onError/error", scheme.onError, scheme.error, minTextRatio)
        assertContrast("$name onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer, minTextRatio)

        assertContrast("$name warning/background", ext.warning, scheme.background, minTextRatio)
        assertContrast("$name warning/surfaceContainerHigh", ext.warning, scheme.surfaceContainerHigh, minTextRatio)
        assertContrast("$name onWarning/warning", ext.onWarning, ext.warning, minTextRatio)
        assertContrast("$name onWarningContainer/warningContainer", ext.onWarningContainer, ext.warningContainer, minTextRatio)
        assertContrast("$name onSuccess/success", ext.onSuccess, ext.success, minTextRatio)
        assertContrast("$name onSuccessContainer/successContainer", ext.onSuccessContainer, ext.successContainer, minTextRatio)
        assertContrast("$name onInfo/info", ext.onInfo, ext.info, minTextRatio)
        assertContrast("$name onInfoContainer/infoContainer", ext.onInfoContainer, ext.infoContainer, minTextRatio)
    }

    @Test
    fun `Light and Dark Material 3 color schemes meet WCAG 2_2 AA text contrast`() {
        verifyColorSchemeContrast("Light", LightColors, LightExtendedColors, 4.5)
        verifyColorSchemeContrast("Dark", DarkColors, DarkExtendedColors, 4.5)
    }

    @Test
    fun `Accessibility Light and Dark schemes meet strict WCAG 2_2 AAA 7_0 text contrast and crisp UI borders`() {
        verifyColorSchemeContrast("A11yLight", A11yLightColors, A11yLightExtendedColors, 7.0)
        verifyColorSchemeContrast("A11yDark", A11yDarkColors, A11yDarkExtendedColors, 7.0)
        assertContrast("A11yLight outlineVariant/surfaceContainerLow", A11yLightColors.outlineVariant, A11yLightColors.surfaceContainerLow, 3.0)
        assertContrast("A11yDark outlineVariant/surfaceContainerLow", A11yDarkColors.outlineVariant, A11yDarkColors.surfaceContainerLow, 3.0)
    }

    @Test
    fun `SolarScheduleCalculator switches day and night offline without location permission`() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val winterWindow = SolarScheduleCalculator.calculateSolarWindow(
            date = LocalDate.of(2026, 11, 24),
            timeZone = berlin,
            epochMillis = Instant.parse("2026-11-24T12:00:00Z").toEpochMilli()
        )
        assertTrue("Expected Berlin winter sunrise 07:00-08:30, was ${winterWindow.sunriseMinutes}", winterWindow.sunriseMinutes in (7 * 60)..(8 * 60 + 30))
        assertTrue("Expected Berlin winter sunset 15:30-17:00, was ${winterWindow.sunsetMinutes}", winterWindow.sunsetMinutes in (15 * 60 + 30)..(17 * 60))

        // 11:00 UTC = 12:00 CET (daytime -> Light AAA), 19:00 UTC = 20:00 CET (night -> Dark AAA)
        val noonBerlin = Instant.parse("2026-11-24T11:00:00Z").toEpochMilli()
        val nightBerlin = Instant.parse("2026-11-24T19:00:00Z").toEpochMilli()
        assertFalse(SolarScheduleCalculator.isDarkAt(SolarScheduleCalculator.SUBMODE_AUTO_SUN, timeZone = berlin, epochMillis = noonBerlin))
        assertTrue(SolarScheduleCalculator.isDarkAt(SolarScheduleCalculator.SUBMODE_AUTO_SUN, timeZone = berlin, epochMillis = nightBerlin))

        // Manual AAA submodes ignore time of day
        assertFalse(SolarScheduleCalculator.isDarkAt(SolarScheduleCalculator.SUBMODE_LIGHT_AAA, timeZone = berlin, epochMillis = nightBerlin))
        assertTrue(SolarScheduleCalculator.isDarkAt(SolarScheduleCalculator.SUBMODE_DARK_AAA, timeZone = berlin, epochMillis = noonBerlin))

        // Custom sunrise/sunset override works cleanly
        assertTrue(
            SolarScheduleCalculator.isDarkAt(
                subMode = SolarScheduleCalculator.SUBMODE_AUTO_SUN,
                customSunriseMinutes = 13 * 60,
                customSunsetMinutes = 20 * 60,
                timeZone = berlin,
                epochMillis = noonBerlin
            )
        )
        assertEquals("07:45", SolarScheduleCalculator.formatMinutesHHmm(7 * 60 + 45))
        assertTrue(SolarScheduleCalculator.millisUntilNextTransition(timeZone = berlin, epochMillis = noonBerlin) > 1_000L)
    }

    @Test
    fun `Changelog cleaners strip HTML, Markdown syntax, long URLs, and raw hex hashes for visual and speech output`() {
        val raw = """
            <!-- hidden release note comment -->
            ## Highlights in [v2.0](https://github.com/example/app/releases/tag/v2.0)
            - **Faster startup** and `clean` UI
            SHA256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
            Full details at https://example.com/very/long/release/notes/url
        """.trimIndent()

        val visual = cleanChangelogText(raw)
        assertFalse(visual.contains("<!--"))
        assertFalse(visual.contains("##"))
        assertFalse(visual.contains("**"))
        assertFalse(visual.contains("`"))
        assertFalse(visual.contains("]("))
        assertTrue(visual.contains("Highlights in v2.0"))
        assertTrue(visual.contains("• Faster startup and clean UI"))

        val speech = cleanChangelogSpeechText(raw)
        assertFalse(speech.contains("https://"))
        assertFalse(speech.contains("0123456789abcdef0123456789abcdef"))
        assertTrue(speech.contains("Highlights in v2.0"))
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
            "a11y_deselect_item",
            "theme_a11y",
            "a11y_theme_mode_title",
            "a11y_submode_auto_sun",
            "a11y_submode_light",
            "a11y_submode_dark",
            "a11y_sun_schedule_info",
            "a11y_sun_custom_info",
            "a11y_sun_customize_btn",
            "a11y_sun_reset_btn",
            "dynamic_color_paused_a11y",
            "apps_grid_density_title",
            "apps_grid_density_large",
            "apps_grid_density_compact",
            "apps_grid_density_auto_a11y",
            "a11y_nav_badge_attention",
            "a11y_nav_badge_count",
            "onboarding_mode_title",
            "onboarding_mode_standard_title",
            "onboarding_mode_standard_desc",
            "onboarding_mode_a11y_title",
            "onboarding_mode_a11y_desc",
            "theme_mode_section_title",
            "a11y_accessible_ui_title",
            "a11y_accessible_ui_subtitle",
            "a11y_sun_sunrise_label",
            "a11y_sun_sunset_label",
            "a11y_lists_active_note",
            "a11y_action_expand",
            "a11y_action_collapse",
            "a11y_move_up",
            "a11y_move_down",
            "a11y_apps_list_active_hint",
            "a11y_apps_section_header",
            "a11y_options_action",
            "a11y_close_action",
            "a11y_updates_status_summary",
            "a11y_updates_more_actions",
            "a11y_updates_sources_more_singular",
            "a11y_updates_sources_more_plural",
            "a11y_updates_verified",
            "a11y_version_to",
            "updates_select_action",
            "a11y_selected_label"
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

    @Test
    fun accessibleTypographyEnforcesMinimumReadableSizes() {
        assertTrue(com.apkup.ui.theme.AccessibleTypography.bodySmall.fontSize.value >= 13f)
        assertTrue(com.apkup.ui.theme.AccessibleTypography.labelSmall.fontSize.value >= 13f)
        assertTrue(com.apkup.ui.theme.AccessibleTypography.labelMedium.fontSize.value >= 13f)
    }
}
