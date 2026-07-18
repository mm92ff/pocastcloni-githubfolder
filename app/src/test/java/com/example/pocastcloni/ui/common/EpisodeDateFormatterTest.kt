package com.example.pocastcloni.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class EpisodeDateFormatterTest {

    private val zoneId = ZoneId.of("Europe/Zurich")

    @Test
    fun `formatEpisodePublishDate uses German format locale`() {
        val epochMs =
            LocalDateTime.of(2026, 6, 25, 10, 30)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        assertEquals("25.06.2026", formatEpisodePublishDate(epochMs, Locale.GERMANY, zoneId))
    }

    @Test
    fun `formatEpisodePublishDate uses US format locale`() {
        val epochMs =
            LocalDateTime.of(2026, 6, 25, 10, 30)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        assertEquals("Jun 25, 2026", formatEpisodePublishDate(epochMs, Locale.US, zoneId))
    }

    @Test
    fun `formatEpisodePublishDateOrNull hides missing dates`() {
        assertNull(formatEpisodePublishDateOrNull(null, Locale.US, zoneId))
    }
}
