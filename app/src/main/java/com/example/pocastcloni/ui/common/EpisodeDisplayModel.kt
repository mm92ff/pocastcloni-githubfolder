package com.example.pocastcloni.ui.common

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.Podcast

@Immutable
data class EpisodeDisplayModel(
    val guid: String,
    val title: String,
    val description: String,
    val podcastRssUrl: String,
    val podcastTitle: String?,
    val podcastImageUrl: String?,
    val isFavorite: Boolean,
    val isPlayed: Boolean,
    val playbackPositionMs: Long,
    val durationMs: Long,
    val pubDateMs: Long?,
    val datePlayedMs: Long?
) {
    companion object {
        fun from(
            presentation: EpisodePresentation,
            podcast: Podcast?,
            descriptionOverride: String? = null
        ): EpisodeDisplayModel =
            EpisodeDisplayModel(
                guid = presentation.guid,
                title = if (presentation.title.isNotBlank()) presentation.title else presentation.link.orEmpty(),
                description = descriptionOverride ?: presentation.description,
                podcastRssUrl = presentation.podcastRssUrl,
                podcastTitle = podcast?.title,
                podcastImageUrl = podcast?.imageUrl,
                isFavorite = presentation.isFavorite,
                isPlayed = presentation.isPlayed,
                playbackPositionMs = presentation.playbackPositionMs,
                durationMs = presentation.durationMs,
                pubDateMs = presentation.pubDateMs,
                datePlayedMs = presentation.datePlayedMs
            )
    }
}
