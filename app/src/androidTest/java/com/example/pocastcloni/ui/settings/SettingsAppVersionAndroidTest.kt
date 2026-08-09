package com.example.pocastcloni.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.BuildConfig
import com.example.pocastcloni.R
import org.junit.Rule
import org.junit.Test

class SettingsAppVersionAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appInformationDisplaysCompiledVersionNameAndCode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sectionTitle = context.getString(R.string.settings_section_app_information)
        val versionLabel = context.getString(R.string.settings_app_version)
        val versionValue =
            context.getString(
                R.string.settings_app_version_value,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
            )

        composeRule.setContent {
            MaterialTheme {
                AppInformationSection()
            }
        }

        composeRule.onNodeWithText(sectionTitle).assertIsDisplayed()
        composeRule.onNodeWithText(versionLabel).assertIsDisplayed()
        composeRule.onNodeWithText(versionValue).assertIsDisplayed()
    }
}
