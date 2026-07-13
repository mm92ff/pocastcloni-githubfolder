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
        val yesterday = favoriteItem(101L, "yesterday", millisDaysAgo(1))
        val today = favoriteItem(102L, "today", millisDaysAgo(0))
        val older = favoriteItem(103L, "older", millisDaysAgo(366))

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
        val today = favoriteItem(201L, "today", millisDaysAgo(0))
        val yesterday = favoriteItem(202L, "yesterday", millisDaysAgo(1))

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
        val missingDate = favoriteItem(301L, "missing-date", null)

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

    @Test
    fun `buildFavoriteDateRows uses the full date bucket sequence`() {
        val items =
            listOf(
                favoriteItem(401L, "today", millisDaysAgo(0)),
                favoriteItem(402L, "yesterday", millisDaysAgo(1)),
                favoriteItem(403L, "last-week", millisDaysAgo(7)),
                favoriteItem(404L, "last-month", millisDaysAgo(30)),
                favoriteItem(405L, "last-two-months", millisDaysAgo(60)),
                favoriteItem(406L, "last-five-months", millisDaysAgo(150)),
                favoriteItem(407L, "last-year", millisDaysAgo(365)),
                favoriteItem(408L, "older", millisDaysAgo(366))
            )

        val rows = buildFavoriteDateRows(
            items = items.shuffled(),
            nowMillis = nowMillis,
            zoneId = zoneId
        )

        assertEquals(
            listOf(
                DateBucket.TODAY,
                DateBucket.YESTERDAY,
                DateBucket.LAST_WEEK,
                DateBucket.LAST_MONTH,
                DateBucket.LAST_TWO_MONTHS,
                DateBucket.LAST_FIVE_MONTHS,
                DateBucket.LAST_YEAR,
                DateBucket.OLDER
            ),
            rows.filterIsInstance<FavoriteListRow.SectionHeader>().map { it.bucket }
        )
    }

    private fun millisDaysAgo(daysAgo: Long): Long =
        LocalDateTime.of(2026, 6, 25, 10, 0)
            .minusDays(daysAgo)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

    private fun favoriteItem(
        episodeId: Long,
        guid: String,
        favoriteAddedAtMs: Long?
    ): FavoriteUiItem =
        FavoriteUiItem(
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
