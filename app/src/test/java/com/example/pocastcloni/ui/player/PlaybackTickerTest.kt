package com.example.pocastcloni.ui.player

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTickerTest {
    @Test
    fun `ticker waits for interval before first emission`() = runTest {
        PlaybackTicker().tick(500L).test {
            expectNoEvents()
            advanceTimeBy(499L)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(1L)
            runCurrent()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tick policy stops paused playback and slows background playback`() {
        assertNull(playbackTickIntervalMs(isPlayingReady = false, isForeground = true))
        assertEquals(500L, playbackTickIntervalMs(isPlayingReady = true, isForeground = true))
        assertEquals(5_000L, playbackTickIntervalMs(isPlayingReady = true, isForeground = false))
    }
}
