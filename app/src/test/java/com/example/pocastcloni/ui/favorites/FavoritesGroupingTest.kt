package com.example.pocastcloni.ui.favorites

import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FavoritesGroupingTest {

    private val zoneId = ZoneId.of("Europe/Zurich")
    private val nowMillis =
        LocalDateTime.of(2026, 6, 25, 12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `buildFavoriteDateRows sorts by favorite added date and inserts headers`() {
        val yesterday = favoriteItem("yesterday", millisDaysAgo(1))
        val today = favoriteItem("today", millisDaysAgo(0))
        val older = favoriteItem("older", millisDaysAgo(366))

        val rows = buildFavoriteDateRows(
            items = listOf(older, yesterday, today),
            nowMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(
            listOf(
                FavoriteListRow.SectionHeader(DateBucket.TODAY),
                FavoriteListRow.EpisodeRow(today),
                FavoriteListRow.SectionHeader(DateBucket.YESTERDAY),
                FavoriteListRow.EpisodeRow(yesterday),
                FavoriteListRow.SectionHeader(DateBucket.OLDER),
                FavoriteListRow.EpisodeRow(older)
            ),
            rows
        )
    }

    @Test
    fun `buildFavoriteDateRows keeps headers before episodes when reversed`() {
        val today = favoriteItem("today", millisDaysAgo(0))
        val yesterday = favoriteItem("yesterday", millisDaysAgo(1))

        val rows = buildFavoriteDateRows(
            items = listOf(yesterday, today),
            nowMillis = nowMillis,
            zoneId = zoneId,
            reverseDisplayOrder = true
        )

        assertEquals(
            listOf(
                FavoriteListRow.SectionHeader(DateBucket.YESTERDAY),
                FavoriteListRow.EpisodeRow(yesterday),
                FavoriteListRow.SectionHeader(DateBucket.TODAY),
                FavoriteListRow.EpisodeRow(today)
            ),
            rows
        )
    }

    @Test
    fun `buildFavoriteDateRows puts missing added dates in older bucket`() {
        val missingDate = favoriteItem("missing-date", null)

        val rows = buildFavoriteDateRows(
            items = listOf(missingDate),
            nowMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(
            listOf(
                FavoriteListRow.SectionHeader(DateBucket.OLDER),
                FavoriteListRow.EpisodeRow(missingDate)
            ),
            rows
        )
    }

    private fun millisDaysAgo(daysAgo: Long): Long =
        LocalDateTime.of(2026, 6, 25, 10, 0)
            .minusDays(daysAgo)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

    private fun favoriteItem(id: String, favoriteAddedAtMs: Long?): FavoriteUiItem =
        FavoriteUiItem(
            id = id,
            episode =
            EpisodeDisplayModel(
                guid = id,
                title = "Episode $id",
                description = "",
                podcastRssUrl = "https://example.com/feed.xml",
                podcastTitle = null,
                podcastImageUrl = null,
                isFavorite = true,
                isPlayed = false,
                playbackPositionMs = 0L,
                durationMs = 0L,
                pubDateMs = null,
                datePlayedMs = null,
                favoriteAddedAtMs = favoriteAddedAtMs
            ),
            podcast = null
        )
}
