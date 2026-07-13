package com.example.pocastcloni.data.repository

import androidx.room.withTransaction
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.PodcastFeedUpdate
import com.example.pocastcloni.domain.repository.FeedSyncPersistence
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFeedSyncPersistence
@Inject
constructor(
    private val database: AppDatabase,
    private val podcastDao: PodcastDao
) : FeedSyncPersistence {
    override suspend fun persistFeedUpdate(
        update: PodcastFeedUpdate,
        newPodcast: PodcastEntity?,
        episodes: List<EpisodeEntity>
    ): Boolean {
        require(newPodcast == null || newPodcast.rssUrl == update.rssUrl)
        require(episodes.all { it.podcastRssUrl == update.rssUrl })

        database.withTransaction {
            if (newPodcast == null) {
                check(podcastDao.updatePodcastFromFeed(update) == 1) {
                    "Feed update referenced a missing podcast"
                }
            } else {
                podcastDao.insertPodcast(newPodcast.copy(hasNewEpisodes = false))
            }
            val insertResults = podcastDao.upsertEpisodesEfficient(episodes)
            if (insertResults.any { it != ON_CONFLICT_IGNORED }) {
                check(podcastDao.markPodcastHasNewEpisodes(update.rssUrl) == 1) {
                    "Feed update could not mark its inserted episodes as new"
                }
            }
        }
        return readAutoDownloadEnabled(update.rssUrl)
    }

    override suspend fun touchLastRefreshed(
        rssUrl: String,
        lastRefreshed: Date
    ): Boolean {
        database.withTransaction {
            check(podcastDao.updatePodcastLastRefreshed(rssUrl, lastRefreshed) == 1) {
                "Feed refresh referenced a missing podcast"
            }
        }
        return readAutoDownloadEnabled(rssUrl)
    }

    private suspend fun readAutoDownloadEnabled(rssUrl: String): Boolean =
        checkNotNull(podcastDao.getPodcastAutoDownloadEnabled(rssUrl)) {
            "Feed persistence could not read the committed podcast"
        }

    private companion object {
        const val ON_CONFLICT_IGNORED = -1L
    }
}
