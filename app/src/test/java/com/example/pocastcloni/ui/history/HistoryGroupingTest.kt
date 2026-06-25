package com.example.pocastcloni.ui.history

import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import org.junit.Assert.assertEquals
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
            HistoryTimeBucket.TODAY,
            millisDaysAgo(0).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps yesterday`() {
        assertEquals(
            HistoryTimeBucket.YESTERDAY,
            millisDaysAgo(1).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last week`() {
        assertEquals(
            HistoryTimeBucket.LAST_WEEK,
            millisDaysAgo(7).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last month`() {
        assertEquals(
            HistoryTimeBucket.LAST_MONTH,
            millisDaysAgo(30).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last two months`() {
        assertEquals(
            HistoryTimeBucket.LAST_TWO_MONTHS,
            millisDaysAgo(60).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last five months`() {
        assertEquals(
            HistoryTimeBucket.LAST_FIVE_MONTHS,
            millisDaysAgo(150).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps last year`() {
        assertEquals(
            HistoryTimeBucket.LAST_YEAR,
            millisDaysAgo(365).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `bucket maps older and missing dates`() {
        assertEquals(
            HistoryTimeBucket.OLDER,
            millisDaysAgo(366).toHistoryTimeBucket(nowMillis, zoneId)
        )
        assertEquals(
            HistoryTimeBucket.OLDER,
            (null as Long?).toHistoryTimeBucket(nowMillis, zoneId)
        )
    }

    @Test
    fun `buildHistoryRows inserts one header per bucket and preserves item order`() {
        val today = historyItem("today", millisDaysAgo(0))
        val todaySecond = historyItem("today-second", millisDaysAgo(0))
        val yesterday = historyItem("yesterday", millisDaysAgo(1))
        val older = historyItem("older", millisDaysAgo(366))

        val rows = buildHistoryRows(
            items = listOf(today, todaySecond, yesterday, older),
            nowMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(
            listOf(
                HistoryListRow.SectionHeader(HistoryTimeBucket.TODAY),
                HistoryListRow.EpisodeRow(today),
                HistoryListRow.EpisodeRow(todaySecond),
                HistoryListRow.SectionHeader(HistoryTimeBucket.YESTERDAY),
                HistoryListRow.EpisodeRow(yesterday),
                HistoryListRow.SectionHeader(HistoryTimeBucket.OLDER),
                HistoryListRow.EpisodeRow(older)
            ),
            rows
        )
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

    private fun historyItem(id: String, datePlayedMs: Long?): HistoryUiItem =
        HistoryUiItem(
            id = id,
            episode =
            EpisodeDisplayModel(
                guid = id,
                title = "Episode $id",
                description = "",
                podcastRssUrl = "https://example.com/feed.xml",
                podcastTitle = null,
                podcastImageUrl = null,
                isFavorite = false,
                isPlayed = true,
                playbackPositionMs = 0L,
                durationMs = 0L,
                pubDateMs = null,
                datePlayedMs = datePlayedMs
            ),
            podcast = null
        )
}
