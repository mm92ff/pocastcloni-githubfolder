package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class EpisodeMaintenanceTest {
    @Test
    fun selectEpisodesToPrune_onlyReturnsOlderPlayedEpisodesThatAreSafeToDelete() {
        val episodes =
            listOf(
                episode("latest-unplayed", pubDateMs = 6_000, isPlayed = false),
                episode("latest-favorite", pubDateMs = 5_000, isPlayed = true, isFavorite = true),
                episode("played-safe", pubDateMs = 4_000, isPlayed = true),
                episode("downloaded", pubDateMs = 3_000, isPlayed = true, downloadStatus = DownloadStatus.DOWNLOADED),
                episode("in-progress", pubDateMs = 2_000, isPlayed = true, playbackPositionMs = 42_000),
                episode("played-safe-2", pubDateMs = 1_000, isPlayed = true)
            )

        val result = selectEpisodesToPrune(episodes, keepCount = 2)

        assertEquals(listOf("played-safe", "played-safe-2"), result.map { it.guid })
    }

    @Test
    fun shouldResetDownloadState_resetsTransientAndBrokenDownloadedStates() {
        assertTrue(shouldResetDownloadState(DownloadStatus.QUEUED, null) { false })
        assertTrue(shouldResetDownloadState(DownloadStatus.DOWNLOADING, null) { false })
        assertTrue(shouldResetDownloadState(DownloadStatus.DOWNLOADED, null) { false })
        assertTrue(shouldResetDownloadState(DownloadStatus.DOWNLOADED, "missing.mp3") { false })
        assertFalse(shouldResetDownloadState(DownloadStatus.DOWNLOADED, "ok.mp3") { true })
        assertFalse(shouldResetDownloadState(DownloadStatus.NOT_DOWNLOADED, null) { false })
    }

    private fun episode(
        guid: String,
        pubDateMs: Long,
        isPlayed: Boolean,
        isFavorite: Boolean = false,
        playbackPositionMs: Long = 0L,
        downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED
    ) = EpisodeEntity(
        guid = guid,
        podcastRssUrl = "https://example.com/feed.xml",
        title = guid,
        description = guid,
        pubDate = Date(pubDateMs),
        link = "https://example.com/$guid",
        enclosureUrl = "https://example.com/$guid.mp3",
        isPlayed = isPlayed,
        isFavorite = isFavorite,
        playbackPositionMs = playbackPositionMs,
        downloadStatus = downloadStatus
    )
}
