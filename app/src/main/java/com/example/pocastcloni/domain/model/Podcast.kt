package com.example.pocastcloni.domain.model

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.data.local.PodcastEntity
import java.util.Date

@Immutable
data class Podcast(
    val rssUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val lastRefreshed: Date,
    val autoDownloadEnabled: Boolean,
    val sortOrder: Long,
    val hasNewEpisodes: Boolean,
    val lastModifiedHeader: String?,
    val eTagHeader: String?,
    val latestEpisodeDate: Date?,
    val isLatestEpisodePlayed: Boolean?,
    val allowInsecureHttp: Boolean = false
) {
    companion object {
        const val DEFAULT_SORT_ORDER = -1L
    }
}

fun PodcastEntity.toPodcast(): Podcast {
    return Podcast(
        rssUrl = rssUrl,
        title = title,
        description = description,
        imageUrl = imageUrl,
        lastRefreshed = lastRefreshed,
        autoDownloadEnabled = autoDownloadEnabled,
        sortOrder = sortOrder,
        hasNewEpisodes = hasNewEpisodes,
        lastModifiedHeader = lastModifiedHeader,
        eTagHeader = eTagHeader,
        latestEpisodeDate = null, // This conversion is for a single entity, which doesn't have the joined data
        isLatestEpisodePlayed = null,
        allowInsecureHttp = allowInsecureHttp
    )
}
