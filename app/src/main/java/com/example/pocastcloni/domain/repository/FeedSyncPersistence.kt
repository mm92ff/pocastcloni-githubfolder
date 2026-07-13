package com.example.pocastcloni.domain.repository

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.PodcastFeedUpdate
import java.util.Date

interface FeedSyncPersistence {
    suspend fun persistFeedUpdate(
        update: PodcastFeedUpdate,
        newPodcast: PodcastEntity?,
        episodes: List<EpisodeEntity>
    ): Boolean

    suspend fun touchLastRefreshed(
        rssUrl: String,
        lastRefreshed: Date
    ): Boolean
}
