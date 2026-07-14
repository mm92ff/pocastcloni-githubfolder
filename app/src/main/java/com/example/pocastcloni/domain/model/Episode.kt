package com.example.pocastcloni.domain.model

import java.util.Date

data class Episode(
    val guid: String,
    val podcastRssUrl: String,
    val title: String,
    val description: String,
    val pubDate: Date?,
    val link: String,
    val enclosureUrl: String,
    val type: String = "audio/mpeg",
    val fileSize: Long = 0,
    val isPlayed: Boolean = false,
    val playbackPositionMs: Long = 0,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadPath: String? = null,
    val isFavorite: Boolean = false,
    val datePlayed: Date? = null,
    val favoriteTimestamp: Long? = null,
    val favoriteAddedAt: Long? = null,
    val duration: Long = 0,
    val episodeId: Long = 0
)

data class EpisodeWithPodcast(
    val episode: Episode,
    val podcast: Podcast?
)
