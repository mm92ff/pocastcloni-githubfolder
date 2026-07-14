package com.example.pocastcloni.data.repository

import androidx.room.withTransaction
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastFeedUpdate
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.FeedPodcastUpdate
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.FeedSyncStore
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists feed metadata and episodes in one transaction, then reads auto-download state
 * after commit so callers schedule work from the committed podcast state.
 */
@Singleton
class RoomFeedSyncPersistence
@Inject
constructor(
    private val database: AppDatabase,
    private val podcastDao: PodcastDao
) : FeedSyncStore {
    override suspend fun getPodcastForSync(url: String): Podcast? =
        podcastDao.getPodcastByUrl(url)?.toDomain()

    override suspend fun getMaxSortOrder(): Long? = podcastDao.getMaxSortOrder()

    override suspend fun getEpisodesForSync(rssUrl: String): List<Episode> =
        podcastDao.getEpisodesForPodcastSync(rssUrl).map { it.toDomain() }

    override suspend fun getLatestEpisodeGuid(rssUrl: String): String? =
        podcastDao.getLatestEpisodeGuid(rssUrl)

    override suspend fun persistFeedUpdate(
        update: FeedPodcastUpdate,
        newPodcast: Podcast?,
        episodes: List<Episode>
    ): Boolean {
        require(newPodcast == null || newPodcast.rssUrl == update.rssUrl)
        require(episodes.all { it.podcastRssUrl == update.rssUrl })

        val entityUpdate =
            PodcastFeedUpdate(
                rssUrl = update.rssUrl,
                title = update.title,
                description = update.description,
                imageUrl = update.imageUrl,
                lastRefreshed = update.lastRefreshed,
                lastModifiedHeader = update.lastModifiedHeader,
                eTagHeader = update.eTagHeader
            )

        database.withTransaction {
            if (newPodcast == null) {
                check(podcastDao.updatePodcastFromFeed(entityUpdate) == 1) {
                    "Feed update referenced a missing podcast"
                }
            } else {
                podcastDao.insertPodcast(newPodcast.copy(hasNewEpisodes = false).toEntity())
            }
            val insertResults = podcastDao.upsertEpisodesEfficient(episodes.map { it.toEntity() })
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

    override suspend fun approvePodcastNetworkAccess(
        rssUrl: String,
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean
    ) {
        podcastDao.approvePodcastNetworkAccess(
            rssUrl = rssUrl,
            allowInsecureHttp = allowInsecureHttp,
            allowLocalNetwork = allowLocalNetwork
        )
    }

    private suspend fun readAutoDownloadEnabled(rssUrl: String): Boolean =
        checkNotNull(podcastDao.getPodcastAutoDownloadEnabled(rssUrl)) {
            "Feed persistence could not read the committed podcast"
        }

    private companion object {
        const val ON_CONFLICT_IGNORED = -1L
    }
}
