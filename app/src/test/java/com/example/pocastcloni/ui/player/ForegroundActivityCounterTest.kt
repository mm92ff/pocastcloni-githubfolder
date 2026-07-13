package com.example.pocastcloni.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundActivityCounterTest {
    @Test
    fun `foreground remains true until final started activity stops`() {
        val counter = ForegroundActivityCounter()

        assertTrue(counter.onActivityStarted())
        assertTrue(counter.onActivityStarted())
        assertTrue(counter.onActivityStopped())
        assertFalse(counter.onActivityStopped())
    }

    @Test
    fun `extra stop cannot make counter negative`() {
        val counter = ForegroundActivityCounter()

        assertFalse(counter.onActivityStopped())
        assertTrue(counter.onActivityStarted())
    }
}
