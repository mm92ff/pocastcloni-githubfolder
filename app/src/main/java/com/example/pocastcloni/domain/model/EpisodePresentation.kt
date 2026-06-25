package com.example.pocastcloni.domain.model

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity

@Immutable
data class EpisodePresentation(
    val guid: String,
    val title: String,
    val link: String?,
    val description: String,
    val podcastRssUrl: String,
    val isFavorite: Boolean,
    val isPlayed: Boolean,
    val playbackPositionMs: Long,
    val durationMs: Long,
    val pubDateMs: Long?,
    val datePlayedMs: Long?,
    val favoriteAddedAtMs: Long?,
    val downloadStatus: DownloadStatus
) {
    companion object {
        fun from(entity: EpisodeEntity): EpisodePresentation =
            EpisodePresentation(
                guid = entity.guid,
                title = if (entity.title.isNotBlank()) entity.title else entity.link,
                link = entity.link,
                description = entity.description,
                podcastRssUrl = entity.podcastRssUrl,
                isFavorite = entity.isFavorite,
                isPlayed = entity.isPlayed,
                playbackPositionMs = entity.playbackPositionMs,
                durationMs = entity.duration,
                pubDateMs = entity.pubDate?.time,
                datePlayedMs = entity.datePlayed?.time,
                favoriteAddedAtMs = entity.favoriteAddedAt,
                downloadStatus = entity.downloadStatus
            )
    }
}
