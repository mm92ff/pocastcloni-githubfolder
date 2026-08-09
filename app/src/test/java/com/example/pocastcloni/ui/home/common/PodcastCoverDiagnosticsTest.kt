package com.example.pocastcloni.ui.home.common

import coil.decode.DataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PodcastCoverDiagnosticsTest {
    @Test
    fun `successful load reports source and monotonic duration without url`() {
        var nowNanos = 2_000_000L
        val events = mutableListOf<PodcastCoverLoadEvent>()
        val tracker =
            PodcastCoverLoadTracker(
                tag = PodcastCoverRequestTag(diagnosticId = "a1b2c3d4e5f6", pixelSize = 400),
                clockNanos = { nowNanos },
                reporter = events::add
            )

        tracker.start()
        nowNanos += 7_000_000L
        tracker.success(DataSource.DISK)

        assertEquals(
            PodcastCoverLoadEvent(
                diagnosticId = "a1b2c3d4e5f6",
                pixelSize = 400,
                dataSource = DataSource.DISK,
                outcome = PodcastCoverLoadOutcome.SUCCESS,
                durationMs = 7L
            ),
            events.single()
        )
        assertFalse(events.single().toString().contains("http"))
    }

    @Test
    fun `error reports only stable failure category`() {
        var nowNanos = 0L
        val events = mutableListOf<PodcastCoverLoadEvent>()
        val tracker = tracker(clockNanos = { nowNanos }, events = events)

        tracker.start()
        nowNanos = 3_000_000L
        tracker.error(IllegalStateException("https://secret.example/cover?token=private"))

        val event = events.single()
        assertEquals(PodcastCoverLoadOutcome.ERROR, event.outcome)
        assertEquals("IllegalStateException", event.failureCategory)
        assertFalse(event.toString().contains("secret.example"))
        assertFalse(event.toString().contains("private"))
    }

    @Test
    fun `terminal callback is reported only once`() {
        var nowNanos = 0L
        val events = mutableListOf<PodcastCoverLoadEvent>()
        val tracker = tracker(clockNanos = { nowNanos }, events = events)

        tracker.start()
        nowNanos = 1_000_000L
        tracker.cancel()
        tracker.error(IllegalArgumentException("late"))
        tracker.success(DataSource.NETWORK)

        assertEquals(1, events.size)
        assertEquals(PodcastCoverLoadOutcome.CANCELLED, events.single().outcome)
    }

    @Test
    fun `negative clock movement is clamped`() {
        var nowNanos = 5_000_000L
        val events = mutableListOf<PodcastCoverLoadEvent>()
        val tracker = tracker(clockNanos = { nowNanos }, events = events)

        tracker.start()
        nowNanos = 1_000_000L
        tracker.success(DataSource.MEMORY_CACHE)

        assertEquals(0L, events.single().durationMs)
    }

    private fun tracker(
        clockNanos: () -> Long,
        events: MutableList<PodcastCoverLoadEvent>
    ): PodcastCoverLoadTracker =
        PodcastCoverLoadTracker(
            tag = PodcastCoverRequestTag(diagnosticId = "0123456789ab", pixelSize = 300),
            clockNanos = clockNanos,
            reporter = events::add
        )
}
