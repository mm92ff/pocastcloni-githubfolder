package com.example.pocastcloni.ui.history

import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.common.toDateBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryGroupingTest {

    private val zoneId = ZoneId.of("Europe/Zurich")
    private val nowMillis =
        LocalDateTime.of(2026, 6, 25, 12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `bucket maps today`() {
        assertEquals(
            DateBucket.TODAY,
            millisDaysAgo(0).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps yesterday`() {
        assertEquals(
            DateBucket.YESTERDAY,
            millisDaysAgo(1).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last week`() {
        assertEquals(
            DateBucket.LAST_WEEK,
            millisDaysAgo(7).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last month`() {
        assertEquals(
            DateBucket.LAST_MONTH,
            millisDaysAgo(30).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last two months`() {
        assertEquals(
            DateBucket.LAST_TWO_MONTHS,
            millisDaysAgo(60).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last five months`() {
        assertEquals(
            DateBucket.LAST_FIVE_MONTHS,
            millisDaysAgo(150).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last year`() {
        assertEquals(
            DateBucket.LAST_YEAR,
            millisDaysAgo(365).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps older and missing dates`() {
        assertEquals(
            DateBucket.OLDER,
            millisDaysAgo(366).toDateBucket(nowMillis, zoneId)
        )
        assertEquals(
            DateBucket.OLDER,
            (null as Long?).toDateBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps lower boundaries`() {
        val cases =
            listOf(
                2L to DateBucket.LAST_WEEK,
                8L to DateBucket.LAST_MONTH,
                31L to DateBucket.LAST_TWO_MONTHS,
                61L to DateBucket.LAST_FIVE_MONTHS,
                151L to DateBucket.LAST_YEAR
            )

        cases.forEach { (daysAgo, expectedBucket) ->
            assertEquals(
                "daysAgo=$daysAgo",
                expectedBucket,
                millisDaysAgo(daysAgo).toDateBucket(nowMillis, zoneId)
            )
        }
    }

    @Test
    fun `bucket uses local calendar days around midnight`() {
        val midnightNow =
            LocalDateTime.of(2026, 6, 25, 0, 30)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val earlierSameDay =
            LocalDateTime.of(2026, 6, 25, 0, 5)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        val previousCalendarDay =
            LocalDateTime.of(2026, 6, 24, 23, 30)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        assertEquals(
            DateBucket.TODAY,
            earlierSameDay.toDateBucket(midnightNow, zoneId)
        )
        assertEquals(
            DateBucket.YESTERDAY,
            previousCalendarDay.toDateBucket(midnightNow, zoneId)
        )
    }

    @Test
    fun `buildHistoryRows inserts one header per bucket and preserves item order`() {
        val today = historyItem(101L, "today", millisDaysAgo(0))
        val todaySecond = historyItem(102L, "today-second", millisDaysAgo(0))
        val yesterday = historyItem(103L, "yesterday", millisDaysAgo(1))
        val older = historyItem(104L, "older", millisDaysAgo(366))

        val rows = buildHistoryRows(
            items = listOf(today, todaySecond, yesterday, older),
            nowMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(
            listOf(
                HistoryListRow.SectionHeader(DateBucket.TODAY),
                HistoryListRow.EpisodeRow(today),
                HistoryListRow.EpisodeRow(todaySecond),
                HistoryListRow.SectionHeader(DateBucket.YESTERDAY),
                HistoryListRow.EpisodeRow(yesterday),
                HistoryListRow.SectionHeader(DateBucket.OLDER),
                HistoryListRow.EpisodeRow(older)
            ),
            rows
        )
    }

    @Test
    fun `history row keys are stable and do not collide`() {
        val item = historyItem(201L, "TODAY", millisDaysAgo(0))
        val header = HistoryListRow.SectionHeader(DateBucket.TODAY)
        val episode = HistoryListRow.EpisodeRow(item)

        assertEquals("section-TODAY", header.key)
        assertEquals("episode-201", episode.key)
        assertNotEquals(header.key, episode.key)
    }

    @Test
    fun `buildHistoryRows returns empty rows for empty history`() {
        assertEquals(
            emptyList<HistoryListRow>(),
            buildHistoryRows(emptyList(), nowMillis, zoneId)
        )
    }

    private fun millisDaysAgo(daysAgo: Long): Long =
        LocalDateTime.of(2026, 6, 25, 10, 0)
            .minusDays(daysAgo)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

    private fun historyItem(
        episodeId: Long,
        guid: String,
        datePlayedMs: Long?
    ): HistoryUiItem =
        HistoryUiItem(
            id = episodeId,
            episode =
            EpisodeDisplayModel(
                episodeId = episodeId,
                guid = guid,
                title = "Episode $guid",
                description = "",
                podcastRssUrl = "https://example.com/feed.xml",
                podcastTitle = null,
                podcastImageUrl = null,
                isFavorite = false,
                isPlayed = true,
                playbackPositionMs = 0L,
                durationMs = 0L,
                pubDateMs = null,
                datePlayedMs = datePlayedMs,
                favoriteAddedAtMs = null
            ),
            podcast = null
        )
}
