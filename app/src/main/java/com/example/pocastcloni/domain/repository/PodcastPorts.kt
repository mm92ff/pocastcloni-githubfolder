package com.example.pocastcloni.domain.repository

import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.EpisodeWithPodcast
import com.example.pocastcloni.domain.model.FeedPodcastUpdate
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastSearchResult
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface PodcastCatalogQueryPort {
    fun getAllPodcastsFlow(): Flow<List<Podcast>>

    fun getPodcastFlow(rssUrl: String): Flow<Podcast?>

    fun getSubscribedUrlsFlow(): Flow<List<String>>

    suspend fun getSubscribedUrls(): List<String>

    suspend fun getPodcast(rssUrl: String): Podcast?
}

interface EpisodeCollectionQueryPort {
    fun getEpisodesFlow(rssUrl: String): Flow<List<Episode>>

    fun getDownloadedEpisodes(): Flow<List<Episode>>

    fun getDownloadedEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>>

    fun getFavoriteEpisodes(): Flow<List<Episode>>

    fun getFavoriteEpisodesWithPodcastInfoFlow(): Flow<Map<Episode, Podcast?>>

    fun isFavorite(episodeId: Long): Flow<Boolean>

    fun getPlaybackHistory(): Flow<List<Episode>>

    fun getPlaybackHistoryWithPodcastInfoFlow(): Flow<Map<Episode, Podcast?>>

    fun getEpisodesInProgress(): Flow<List<EpisodeWithPodcast>>

    fun getUnplayedCounts(): Flow<Map<String, Int>>
}

interface EpisodeLookupPort {
    suspend fun getEpisode(episodeId: Long): Episode?

    suspend fun resolveLegacyDownloadEpisode(guid: String): Episode?
}

interface PodcastSearchPort {
    fun searchEpisodesFlow(query: String): Flow<List<Episode>>

    suspend fun searchPodcasts(term: String): List<PodcastSearchResult>
}

interface PodcastQueryPort :
    PodcastCatalogQueryPort,
    EpisodeCollectionQueryPort,
    EpisodeLookupPort,
    PodcastSearchPort

interface PodcastCatalogCommandPort {
    suspend fun removePodcastByUrl(url: String)

    suspend fun deletePodcast(podcast: Podcast)

    /** Persists the complete podcast order from first to last as unique RSS URLs. */
    suspend fun reorderPodcasts(rssUrlsInOrder: List<String>)

    suspend fun markAllAsSeen()

    suspend fun updatePodcastSettings(
        podcast: Podcast,
        autoDownloadEnabled: Boolean
    )
}

interface EpisodePlaybackCommandPort {
    suspend fun markEpisodePlayed(
        episodeId: Long,
        played: Boolean,
        datePlayed: Date?
    )

    suspend fun toggleEpisodePlayed(episode: Episode)

    suspend fun savePlaybackProgress(
        episodeId: Long,
        positionMs: Long
    )
}

interface EpisodeLibraryCommandPort {
    suspend fun cleanupPlayedEpisodes()

    suspend fun setFavoriteStatus(
        episodeId: Long,
        isFavorite: Boolean,
        timestamp: Long?
    )

    suspend fun clearHistory()

    suspend fun reorderFavorites(
        episodeIds: List<Long>,
        orderedAt: Long
    )
}

interface EpisodeDownloadCommandPort {
    suspend fun updateDownloadStatus(
        episodeId: Long,
        status: DownloadStatus,
        path: String?
    )

    suspend fun compareAndSetDownloadStatus(
        episodeId: Long,
        expectedStatuses: List<DownloadStatus>,
        status: DownloadStatus,
        path: String?
    ): Boolean

    suspend fun compareAndSetDownloadStatusAndPath(
        episodeId: Long,
        expectedStatus: DownloadStatus,
        expectedPath: String?,
        status: DownloadStatus,
        path: String?
    ): Boolean
}

interface PodcastCommandPort :
    PodcastCatalogCommandPort,
    EpisodePlaybackCommandPort,
    EpisodeLibraryCommandPort,
    EpisodeDownloadCommandPort

interface FeedSyncStore {
    suspend fun getPodcastForSync(url: String): Podcast?

    suspend fun getMaxSortOrder(): Long?

    suspend fun getEpisodesForSync(rssUrl: String): List<Episode>

    suspend fun getLatestEpisodeGuid(rssUrl: String): String?

    suspend fun persistFeedUpdate(
        update: FeedPodcastUpdate,
        newPodcast: Podcast?,
        episodes: List<Episode>
    ): Boolean

    suspend fun touchLastRefreshed(
        rssUrl: String,
        lastRefreshed: Date
    ): Boolean

    suspend fun approvePodcastNetworkAccess(
        rssUrl: String,
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean
    )
}

interface LibraryMaintenancePort {
    suspend fun reconcileEpisodeStorage(
        activeDownloadEpisodeIds: Set<Long> = emptySet(),
        isDownloadWorkActive: suspend (episodeId: Long) -> Boolean = {
            it in activeDownloadEpisodeIds
        }
    ): Int

    suspend fun pruneLibrary(limitPerPodcast: Int)

    suspend fun resetDatabase()
}

interface FeedSyncRunner {
    @Suppress("LongParameterList")
    suspend fun sync(
        url: String,
        downloadLimit: Int,
        mode: FeedUpdateMode,
        sortOrder: Long? = null,
        forceFull: Boolean = false,
        allowInsecureHttp: Boolean = false,
        allowLocalNetwork: Boolean = false,
        feedItemLimit: Int = Constants.SecurityLimits.MAX_FEED_ITEMS
    )
}

interface FeedUpdateRunner {
    suspend fun updateAllPodcasts(
        downloadLimit: Int,
        mode: FeedUpdateMode,
        forceFull: Boolean,
        feedUrls: Set<String>? = null,
        feedItemLimit: Int = Constants.SecurityLimits.MAX_FEED_ITEMS
    ): PodcastUpdateSummary
}
