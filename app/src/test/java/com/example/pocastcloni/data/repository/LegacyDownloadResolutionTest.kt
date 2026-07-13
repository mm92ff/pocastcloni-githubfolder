package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LegacyDownloadResolutionTest {
    @Test
    fun uniqueLegacyGuidResolvesToItsInternalId() {
        val episode = episode(11L, DownloadStatus.QUEUED)

        val resolution = resolveLegacyDownloadCandidates(listOf(episode))

        assertSame(episode, resolution.episode)
        assertEquals(emptyList<Long>(), resolution.transientEpisodeIdsToReset)
    }

    @Test
    fun ambiguousLegacyGuidIsRejectedAndOnlyTransientRowsAreReset() {
        val resolution = resolveLegacyDownloadCandidates(
            listOf(
                episode(11L, DownloadStatus.QUEUED),
                episode(22L, DownloadStatus.DOWNLOADING),
                episode(33L, DownloadStatus.DOWNLOADED)
            )
        )

        assertNull(resolution.episode)
        assertEquals(listOf(11L, 22L), resolution.transientEpisodeIdsToReset)
    }

    private fun episode(
        episodeId: Long,
        status: DownloadStatus
    ) = EpisodeEntity(
        guid = "legacy-guid",
        podcastRssUrl = "https://example.com/$episodeId.xml",
        title = "Episode $episodeId",
        description = "",
        pubDate = null,
        link = "",
        enclosureUrl = "https://example.com/$episodeId.mp3",
        downloadStatus = status,
        episodeId = episodeId
    )
}
