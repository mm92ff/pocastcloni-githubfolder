package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity

internal data class LegacyDownloadResolution(
    val episode: EpisodeEntity?,
    val transientEpisodeIdsToReset: List<Long>
)

internal fun resolveLegacyDownloadCandidates(matches: List<EpisodeEntity>): LegacyDownloadResolution {
    matches.singleOrNull()?.let { episode ->
        return LegacyDownloadResolution(episode, emptyList())
    }
    return LegacyDownloadResolution(
        episode = null,
        transientEpisodeIdsToReset = matches
            .filter { it.downloadStatus == DownloadStatus.QUEUED || it.downloadStatus == DownloadStatus.DOWNLOADING }
            .map { it.episodeId }
    )
}
