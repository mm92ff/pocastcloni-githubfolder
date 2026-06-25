package com.example.pocastcloni.domain.usecase.favorite

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.domain.repository.PodcastRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderFavoritesUseCaseTest {

    private val repository: PodcastRepository = mockk()
    private val useCase = ReorderFavoritesUseCase(repository)

    @Test
    fun `reorder updates manual timestamps without changing favorite added dates`() = runTest {
        val first = episode(guid = "first", favoriteAddedAt = 100L)
        val second = episode(guid = "second", favoriteAddedAt = 50L)
        val capturedEpisodes = slot<List<EpisodeEntity>>()

        coEvery { repository.getEpisode("first") } returns first
        coEvery { repository.getEpisode("second") } returns second
        coEvery { repository.reorderFavorites(capture(capturedEpisodes)) } just runs

        useCase(listOf("second", "first"))

        coVerify { repository.reorderFavorites(any()) }
        assertEquals(listOf("second", "first"), capturedEpisodes.captured.map { it.guid })
        assertEquals(listOf(50L, 100L), capturedEpisodes.captured.map { it.favoriteAddedAt })
    }

    private fun episode(
        guid: String,
        favoriteAddedAt: Long
    ): EpisodeEntity =
        EpisodeEntity(
            guid = guid,
            podcastRssUrl = "https://example.com/feed.xml",
            title = "Episode $guid",
            description = "",
            pubDate = null,
            link = "",
            enclosureUrl = "",
            type = "audio/mpeg",
            fileSize = 0L,
            isPlayed = false,
            playbackPositionMs = 0L,
            downloadStatus = DownloadStatus.NOT_DOWNLOADED,
            downloadPath = null,
            isFavorite = true,
            datePlayed = null,
            favoriteTimestamp = favoriteAddedAt,
            favoriteAddedAt = favoriteAddedAt,
            duration = 0L
        )
}
