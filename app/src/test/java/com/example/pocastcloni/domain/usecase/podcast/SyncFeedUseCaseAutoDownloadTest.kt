package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class SyncFeedUseCaseAutoDownloadTest {
    @Test
    fun zeroLimit_disablesAutoDownloadSelection() {
        val episodes = listOf(episode(guid = "episode-1", pubDateMs = 3_000))

        val result = selectEpisodesForAutoDownload(episodes, downloadLimit = 0)

        assertEquals(emptyList<EpisodeEntity>(), result)
    }

    @Test
    fun selectsOnlyNewestEpisodesWithinConfiguredLimit() {
        val episodes = listOf(
            episode(guid = "episode-1", pubDateMs = 1_000),
            episode(guid = "episode-2", pubDateMs = 3_000),
            episode(guid = "episode-3", pubDateMs = 2_000),
            episode(guid = "episode-4", pubDateMs = 4_000)
        )

        val result = selectEpisodesForAutoDownload(episodes, downloadLimit = 2)

        assertEquals(listOf("episode-4", "episode-2"), result.map { it.guid })
    }

    @Test
    fun skipsEpisodesThatAreAlreadyHandledOrMissingAudio() {
        val episodes = listOf(
            episode(guid = "downloaded", pubDateMs = 5_000, status = DownloadStatus.DOWNLOADED),
            episode(guid = "queued", pubDateMs = 4_000, status = DownloadStatus.QUEUED),
            episode(guid = "blank", pubDateMs = 3_000, enclosureUrl = ""),
            episode(guid = "candidate", pubDateMs = 2_000),
            episode(guid = "older", pubDateMs = 1_000)
        )

        val result = selectEpisodesForAutoDownload(episodes, downloadLimit = 4)

        assertEquals(listOf("candidate"), result.map { it.guid })
    }

    private fun episode(
        guid: String,
        pubDateMs: Long,
        status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
        enclosureUrl: String = "https://example.com/$guid.mp3"
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = "https://example.com/feed.xml",
        title = guid,
        description = guid,
        pubDate = Date(pubDateMs),
        link = "https://example.com/$guid",
        enclosureUrl = enclosureUrl,
        downloadStatus = status
    )
}
