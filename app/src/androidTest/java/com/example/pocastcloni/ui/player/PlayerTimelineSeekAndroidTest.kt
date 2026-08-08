package com.example.pocastcloni.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerTimelineSeekAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tapUsesCompleteSeekLifecycleWithOneTarget() {
        val events = mutableListOf<String>()
        setTimelineContent(
            onSeekStart = { events += "start" },
            onSeek = { events += "seek:$it" },
            onSeekEnd = { events += "end" }
        )

        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(listOf("start", "seek:600000", "end"), events)
        }
    }

    @Test
    fun accessibilitySeekUsesTheSameLifecycle() {
        val events = mutableListOf<String>()
        setTimelineContent(
            onSeekStart = { events += "start" },
            onSeek = { events += "seek:$it" },
            onSeekEnd = { events += "end" }
        )

        composeRule.onNodeWithTag(TIMELINE_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                action(600_000f)
            }

        composeRule.runOnIdle {
            assertEquals(listOf("start", "seek:600000", "end"), events)
        }
    }

    @Test
    fun dragSendsOneFinalTargetInsideTheSeekLifecycle() {
        val events = mutableListOf<String>()
        setTimelineContent(
            onSeekStart = { events += "start" },
            onSeek = { events += "seek:$it" },
            onSeekEnd = { events += "end" }
        )

        composeRule.onNodeWithTag(TIMELINE_TAG).performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertEquals(3, events.size)
            assertEquals("start", events.first())
            assertEquals("end", events.last())
            val targetPositionMs = events[1].substringAfter("seek:").toLong()
            assertTrue(targetPositionMs > 600_000L)
        }
    }

    @Test
    fun nonSeekableTimelineRejectsPointerAndAccessibilityRequests() {
        val events = mutableListOf<String>()
        setTimelineContent(
            isSeekable = false,
            onSeekStart = { events += "start" },
            onSeek = { events += "seek:$it" },
            onSeekEnd = { events += "end" }
        )

        val node = composeRule.onNodeWithTag(TIMELINE_TAG).assertIsNotEnabled()
        val semanticsNode = node.fetchSemanticsNode()
        val accepted =
            semanticsNode.config[SemanticsActions.SetProgress].action?.invoke(600_000f) ?: false
        node.performTouchInput { click() }

        composeRule.runOnIdle {
            assertFalse(accepted)
            assertEquals(emptyList<String>(), events)
        }
    }

    private fun setTimelineContent(
        isSeekable: Boolean = true,
        onSeekStart: () -> Unit,
        onSeek: (Long) -> Unit,
        onSeekEnd: () -> Unit
    ) {
        composeRule.setContent {
            MaterialTheme {
                CustomProgressBar(
                    currentPositionMs = 120_000L,
                    bufferedPositionMs = 700_000L,
                    durationMs = 1_200_000L,
                    height = 4.dp,
                    color = Color.Red,
                    onSeek = onSeek,
                    isSeekable = isSeekable,
                    modifier = Modifier.testTag(TIMELINE_TAG),
                    onSeekStart = onSeekStart,
                    onSeekEnd = onSeekEnd
                )
            }
        }
    }

    private companion object {
        private const val TIMELINE_TAG = "player-timeline"
    }
}
