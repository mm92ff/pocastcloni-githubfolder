package com.example.pocastcloni.ui.home.feed

import androidx.compose.ui.unit.dp
import com.example.pocastcloni.ui.player.MiniPlayerLayoutDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBottomPaddingTest {
    @Test
    fun `hidden player leaves no bottom padding regardless of progress height`() {
        listOf(0, 7, 100).forEach { progressBarHeight ->
            assertEquals(0.dp, homeGridBottomPadding(false, progressBarHeight))
        }
    }

    @Test
    fun `visible player keeps collapsed height plus twenty four dp`() {
        val progressBarHeight = 7

        assertEquals(
            MiniPlayerLayoutDefaults.collapsedHeight(progressBarHeight.dp) + 24.dp,
            homeGridBottomPadding(true, progressBarHeight)
        )
    }
}
