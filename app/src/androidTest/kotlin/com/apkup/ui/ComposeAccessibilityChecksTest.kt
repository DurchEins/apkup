package com.apkup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apkup.R
import com.apkup.ui.component.SegmentedButtonSetting
import com.apkup.ui.component.SettingsSection
import com.apkup.ui.component.SwitchSetting
import com.apkup.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose Accessibility Test Framework (ATF) verification running directly on device.
 */
@RunWith(AndroidJUnit4::class)
class ComposeAccessibilityChecksTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsComponents_passComposeAccessibilityChecks_inAaaHighContrastLightAndDark() {
        composeTestRule.enableAccessibilityChecks()

        composeTestRule.setContent {
            var selectedTheme by remember { mutableIntStateOf(3) }
            var dynamicColorEnabled by remember { mutableStateOf(true) }
            var sectionExpanded by remember { mutableStateOf(true) }
            val darkMode = selectedTheme == 1

            AppTheme(darkTheme = darkMode, dynamicColor = false, highContrast = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsSection(
                            title = "Appearance & Layout",
                            expanded = sectionExpanded,
                            onExpandedChange = { sectionExpanded = it }
                        ) {
                            SegmentedButtonSetting(
                                text = "Theme",
                                options = listOf("System", "Dark", "Light", "Accessibility"),
                                getValue = { selectedTheme },
                                setValue = { selectedTheme = it },
                                icon = R.drawable.ic_theme
                            )
                            SwitchSetting(
                                getValue = { dynamicColorEnabled },
                                setValue = { dynamicColorEnabled = it },
                                text = "Material You Dynamic Color",
                                subtitle = "Paused while Accessibility AAA theme is active",
                                icon = R.drawable.ic_theme
                            )
                            if (sectionExpanded) {
                                Text(text = "Offline solar switch: Light 07:45 – 16:15")
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Accessibility").assertIsSelected()
        composeTestRule.onNodeWithText("Dark").performClick()
        composeTestRule.onNodeWithText("Dark").assertIsSelected()
        composeTestRule.onNodeWithText("Material You Dynamic Color").performClick()
        composeTestRule.onNodeWithText("Appearance & Layout").performClick()
    }
}
