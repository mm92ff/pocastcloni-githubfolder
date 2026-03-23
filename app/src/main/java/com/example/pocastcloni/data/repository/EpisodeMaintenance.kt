package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity

internal fun selectEpisodesToPrune(
    episodes: List<EpisodeEntity>,
    keepCount: Int
): List<EpisodeEntity> {
    if (keepCount <= 0 || episodes.size <= keepCount) return emptyList()

    return episodes
        .sortedByDescending { it.pubDate?.time ?: 0L }
        .drop(keepCount)
        .filter(::isSafeToPrune)
}

internal fun isSafeToPrune(episode: EpisodeEntity): Boolean {
    return episode.isPlayed &&
        !episode.isFavorite &&
        episode.playbackPositionMs <= 0L &&
        episode.downloadStatus == DownloadStatus.NOT_DOWNLOADED
}

internal fun shouldResetDownloadState(
    status: DownloadStatus,
    downloadPath: String?,
    fileIsReadable: (String) -> Boolean
): Boolean {
    return when (status) {
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING
        -> true

        DownloadStatus.DOWNLOADED -> downloadPath.isNullOrBlank() || !fileIsReadable(downloadPath)

        DownloadStatus.NOT_DOWNLOADED,
        DownloadStatus.FAILED
        -> false
    }
}
