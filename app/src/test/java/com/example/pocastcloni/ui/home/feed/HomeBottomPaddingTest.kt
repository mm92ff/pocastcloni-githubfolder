package com.example.pocastcloni.ui.home.feed

import androidx.compose.ui.unit.dp
import com.example.pocastcloni.ui.player.MiniPlayerLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBottomPaddingTest {
    @Test
    fun `hidden player leaves no bottom padding regardless of progress height`() {
        listOf(0, 7, 100).forEach { progressBarHeight ->
            assertEquals(0.dp, homeGridBottomPadding(false, progressBarHeight, 0))
        }
    }

    @Test
    fun `hidden player uses every configurable bottom spacing`() {
        listOf(0, 4, 8, 12, 16, 20, 24).forEach { spacing ->
            assertEquals(spacing.dp, homeGridBottomPadding(false, 7, spacing))
        }
    }

    @Test
    fun `visible player keeps collapsed height plus twenty four dp`() {
        val progressBarHeight = 7

        assertEquals(
            MiniPlayerLayoutDefaults.collapsedHeight(progressBarHeight.dp) + 24.dp,
            homeGridBottomPadding(true, progressBarHeight, 0)
        )
        assertEquals(
            MiniPlayerLayoutDefaults.collapsedHeight(progressBarHeight.dp) + 24.dp,
            homeGridBottomPadding(true, progressBarHeight, 24)
        )
    }
}
