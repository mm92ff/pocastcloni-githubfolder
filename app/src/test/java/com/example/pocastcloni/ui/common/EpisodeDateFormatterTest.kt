package com.example.pocastcloni.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EpisodeDateFormatterTest {

    private val zoneId = ZoneId.of("Europe/Zurich")

    @Test
    fun `formatEpisodePublishDate formats compact calendar date`() {
        val epochMs =
            LocalDateTime.of(2026, 6, 25, 10, 30)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        assertEquals("25.06.2026", formatEpisodePublishDate(epochMs, zoneId))
    }

    @Test
    fun `formatEpisodePublishDateOrNull hides missing dates`() {
        assertNull(formatEpisodePublishDateOrNull(null, zoneId))
    }
}
