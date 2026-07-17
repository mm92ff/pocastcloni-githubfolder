package com.example.pocastcloni.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsSliderCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nonPresetValueIsDisplayedAndDescribedExactlyWithoutInitialCallback() {
        var persistedValue: Int? = null
        composeRule.setContent {
            MaterialTheme {
                SettingsSliderCard(
                    title = "Smart Stream",
                    value = 37,
                    valueRange = SMART_STREAM_SLIDER_RANGE,
                    steps = SMART_STREAM_SLIDER_STEPS,
                    onValueChangeFinished = { persistedValue = it },
                    valueDisplay = { Text("$it entries") },
                    valueMapping =
                    SettingsSliderValueMapping(
                        toSliderPosition = ::smartStreamItemLimitToSliderPosition,
                        toValue = ::smartStreamSliderPositionToItemLimit,
                        stateDescription = { "$it entries" }
                    )
                )
            }
        }

        composeRule.onNodeWithText("37 entries").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "37 entries")
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, persistedValue) }
    }
}
