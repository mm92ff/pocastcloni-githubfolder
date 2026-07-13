package com.example.pocastcloni.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTickerLifecycleTest {
    private var nowMs = 0L
    private val lifecycle = PlaybackTickerLifecycle { nowMs }

    @Test
    fun `foreground play pause settles final interval and stops ticker`() {
        val started = lifecycle.reconcile(isPlayingReady = true, isForeground = true)
        assertEquals(500L, started.desiredIntervalMs)
        assertTrue(started.restartTicker)

        nowMs = 500L
        val tick = lifecycle.onTick(isForeground = true)
        assertEquals(500L, tick.elapsedPlayingMs)
        assertTrue(tick.syncUi)

        nowMs = 750L
        val paused = lifecycle.reconcile(isPlayingReady = false, isForeground = true)
        assertEquals(250L, paused.elapsedPlayingMs)
        assertNull(paused.desiredIntervalMs)
        assertTrue(paused.restartTicker)
        assertTrue(paused.flushPlayback)
    }

    @Test
    fun `foreground and background transitions preserve elapsed time and change interval once`() {
        lifecycle.reconcile(isPlayingReady = true, isForeground = true)
        nowMs = 200L

        val background = lifecycle.reconcile(isPlayingReady = true, isForeground = false)
        assertEquals(200L, background.elapsedPlayingMs)
        assertEquals(5_000L, background.desiredIntervalMs)
        assertTrue(background.restartTicker)
        assertFalse(background.syncUi)

        nowMs = 5_200L
        val tick = lifecycle.onTick(isForeground = false)
        assertEquals(5_000L, tick.elapsedPlayingMs)
        assertFalse(tick.syncUi)

        val repeated = lifecycle.reconcile(isPlayingReady = true, isForeground = false)
        assertFalse(repeated.restartTicker)
    }

    @Test
    fun `paused foreground return requests immediate ui synchronization`() {
        lifecycle.reconcile(isPlayingReady = false, isForeground = false)

        val foreground = lifecycle.reconcile(isPlayingReady = false, isForeground = true)

        assertNull(foreground.desiredIntervalMs)
        assertTrue(foreground.syncUi)
        assertFalse(foreground.restartTicker)
    }

    @Test
    fun `release settles active interval and lifecycle can start again`() {
        lifecycle.reconcile(isPlayingReady = true, isForeground = true)
        nowMs = 300L

        val released = lifecycle.release()
        assertEquals(300L, released.elapsedPlayingMs)
        assertTrue(released.flushPlayback)
        assertNull(released.desiredIntervalMs)

        val reconnected = lifecycle.reconcile(isPlayingReady = true, isForeground = true)
        assertEquals(500L, reconnected.desiredIntervalMs)
        assertTrue(reconnected.restartTicker)
    }
}
