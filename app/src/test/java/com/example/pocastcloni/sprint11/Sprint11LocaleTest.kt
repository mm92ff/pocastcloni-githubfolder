package com.example.pocastcloni.sprint11

import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.data.repository.toEpisodeEntity
import com.example.pocastcloni.util.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale

class Sprint11LocaleTest {
    @Test
    fun `clock output uses the format locale rather than US`() {
        withFormatLocale(Locale.forLanguageTag("ar-EG")) {
            assertEquals("\u0660\u0661:\u0660\u0662", formatTime(62_000L))
        }
    }

    @Test
    fun `RSS English month parsing remains protocol locale independent`() {
        withFormatLocale(Locale.GERMANY) {
            val episode =
                RssItem(
                    guid = "locale-rss",
                    title = "Locale RSS",
                    pubDate = "Sun, 21 Jan 2024 10:00:00 +0000"
                ).toEpisodeEntity("https://example.test/feed.xml")

            assertEquals(Instant.parse("2024-01-21T10:00:00Z").toEpochMilli(), episode.pubDate?.time)
        }
    }

    private inline fun withFormatLocale(
        locale: Locale,
        block: () -> Unit
    ) {
        val previous = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, locale)
            block()
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previous)
        }
    }
}
