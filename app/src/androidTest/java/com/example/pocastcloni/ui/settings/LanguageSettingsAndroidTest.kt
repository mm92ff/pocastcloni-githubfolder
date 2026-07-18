package com.example.pocastcloni.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pocastcloni.ui.locale.SupportedAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LanguageSettingsAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languageRowExposesCurrentSelectionAndOpensSingleChoiceDialog() {
        composeRule.setContent {
            MaterialTheme {
                AppLanguageSettingsContent(
                    currentLanguage = SupportedAppLanguage.ENGLISH,
                    onLanguageSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("App language")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("English").assertIsDisplayed()
        composeRule.onNodeWithText("App language").performClick()

        composeRule.onNodeWithText("Choose app language").assertIsDisplayed()
        composeRule.onNode(optionMatcher("System default", selected = false)).assertIsNotSelected()
        composeRule.onNode(optionMatcher("English", selected = true)).assertIsSelected()
        composeRule.onNode(optionMatcher("Deutsch", selected = false)).assertIsNotSelected()
    }

    @Test
    fun dismissingDialogDoesNotEmitSelection() {
        val selections = mutableListOf<SupportedAppLanguage>()
        composeRule.setContent {
            MaterialTheme {
                AppLanguageSettingsContent(
                    currentLanguage = SupportedAppLanguage.ENGLISH,
                    onLanguageSelected = selections::add
                )
            }
        }

        composeRule.onNodeWithText("App language").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertDialogIsClosed()
        composeRule.runOnIdle { assertEquals(emptyList<SupportedAppLanguage>(), selections) }
    }

    @Test
    fun selectingOptionEmitsOneIntentAndClosesDialog() {
        val selections = mutableListOf<SupportedAppLanguage>()
        composeRule.setContent {
            MaterialTheme {
                AppLanguageSettingsContent(
                    currentLanguage = SupportedAppLanguage.ENGLISH,
                    onLanguageSelected = selections::add
                )
            }
        }

        composeRule.onNodeWithText("App language").performClick()
        composeRule.onNode(optionMatcher("Deutsch", selected = false)).performClick()

        assertDialogIsClosed()
        composeRule.runOnIdle {
            assertEquals(listOf(SupportedAppLanguage.GERMAN), selections)
        }
    }

    @Test
    fun selectingCurrentOptionEmitsOnlyOneIdempotentIntent() {
        val selections = mutableListOf<SupportedAppLanguage>()
        composeRule.setContent {
            MaterialTheme {
                AppLanguageSettingsContent(
                    currentLanguage = SupportedAppLanguage.ENGLISH,
                    onLanguageSelected = selections::add
                )
            }
        }

        composeRule.onNodeWithText("App language").performClick()
        composeRule.onNode(optionMatcher("English", selected = true)).performClick()

        assertDialogIsClosed()
        composeRule.runOnIdle {
            assertEquals(listOf(SupportedAppLanguage.ENGLISH), selections)
        }
    }

    private fun assertDialogIsClosed() {
        assertTrue(
            composeRule.onAllNodesWithText("Choose app language")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    private fun optionMatcher(
        label: String,
        selected: Boolean
    ): SemanticsMatcher =
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected)
}
