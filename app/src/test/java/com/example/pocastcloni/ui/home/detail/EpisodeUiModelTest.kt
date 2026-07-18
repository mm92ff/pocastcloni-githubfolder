package com.example.pocastcloni.ui.home.detail

import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.EpisodePresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class EpisodeUiModelTest {
    @Test
    fun `episode UI model caches raw values instead of localized copy`() {
        val episode = testEpisode()
        val initial = episode.toEpisodeUiModel("Podcast", null, 0f)
        val previousLocale = Locale.getDefault(Locale.Category.FORMAT)

        val remapped =
            try {
                Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY)
                episode.toEpisodeUiModelCached(initial, "Podcast", null, 0f)
            } finally {
                Locale.setDefault(Locale.Category.FORMAT, previousLocale)
            }

        assertSame(initial, remapped)
        assertEquals(1_719_311_400_000L, remapped.pubDateEpochMs)
        assertEquals(3_900_000L, remapped.durationMs)
        assertFalse(EpisodeUiModel::class.java.declaredFields.any { it.name == "date" || it.name == "duration" })
    }

    @Test
    fun `episode UI model preserves a missing publication date`() {
        val model = testEpisode().copy(pubDateMs = null).toEpisodeUiModel("Podcast", null, 0f)

        assertEquals(null, model.pubDateEpochMs)
    }

    private fun testEpisode() =
        EpisodePresentation(
            episodeId = 1L,
            guid = "episode-1",
            title = "Episode",
            link = "https://example.test/episode.mp3",
            description = "Description",
            podcastRssUrl = "https://example.test/feed.xml",
            isFavorite = false,
            isPlayed = false,
            playbackPositionMs = 0L,
            durationMs = 3_900_000L,
            pubDateMs = 1_719_311_400_000L,
            datePlayedMs = null,
            favoriteAddedAtMs = null,
            downloadStatus = DownloadStatus.NOT_DOWNLOADED
        )
}
