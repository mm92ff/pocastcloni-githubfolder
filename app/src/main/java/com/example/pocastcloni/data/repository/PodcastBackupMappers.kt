package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.BackupEpisodeState
import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity

fun PodcastEntity.toBackupPodcast(): BackupPodcast =
    BackupPodcast(
        url = rssUrl,
        sortOrder = sortOrder,
        autoDownloadEnabled = autoDownloadEnabled,
        allowInsecureHttp = allowInsecureHttp,
        allowLocalNetwork = allowLocalNetwork,
        title = title,
        description = description,
        imageUrl = imageUrl
    )

fun EpisodeEntity.toBackupFavorite(): BackupFavorite =
    BackupFavorite(
        podcastUrl = podcastRssUrl,
        episodeGuid = guid,
        timestamp = favoriteAddedAt ?: favoriteTimestamp ?: 0L
    )

fun List<EpisodeEntity>.toBackupEpisodeStates(): List<BackupEpisodeState> {
    var nextFavoriteOrder = 0L
    return map { episode ->
        episode.toBackupEpisodeState(
            favoriteOrder = if (episode.isFavorite) nextFavoriteOrder++ else null
        )
    }
}

fun EpisodeEntity.toBackupEpisodeState(favoriteOrder: Long?): BackupEpisodeState =
    BackupEpisodeState(
        podcastUrl = podcastRssUrl,
        episodeGuid = guid,
        title = title,
        description = description,
        publishedAt = pubDate?.time,
        duration = duration,
        isFavorite = isFavorite,
        favoriteAddedAt = if (isFavorite) favoriteAddedAt ?: favoriteTimestamp ?: 0L else null,
        favoriteOrder = favoriteOrder,
        isPlayed = isPlayed,
        datePlayed = datePlayed?.time,
        playbackPositionMs = playbackPositionMs
    )
