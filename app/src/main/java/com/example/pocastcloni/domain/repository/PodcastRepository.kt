package com.example.pocastcloni.domain.repository

import androidx.paging.PagingData
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.EpisodeWithPodcastLite
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.ItunesPodcastDto
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import kotlinx.coroutines.flow.Flow
import java.util.Date

interface PodcastRepository {
    // --- FLOWS ---
    fun getAllPodcastsFlow(): Flow<List<Podcast>>

    fun getEpisodesFlow(rssUrl: String): Flow<List<EpisodeEntity>>

    fun getEpisodesPagedFlow(rssUrl: String): Flow<PagingData<EpisodeEntity>>

    fun getPodcastFlow(rssUrl: String): Flow<Podcast?>

    fun getSubscribedUrlsFlow(): Flow<List<String>>

    fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>>

    fun getDownloadedEpisodesWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>>

    fun getFavoriteEpisodes(): Flow<List<EpisodeEntity>>

    fun getFavoriteEpisodesWithPodcastInfoFlow(): Flow<Map<EpisodeEntity, Podcast?>>

    fun isFavorite(episodeId: Long): Flow<Boolean>

    fun getPlaybackHistory(): Flow<List<EpisodeEntity>>

    fun getPlaybackHistoryWithPodcastInfoFlow(): Flow<Map<EpisodeEntity, Podcast?>>

    // These were missing from the interface but were present in the impl
    fun getEpisodesInProgress(): Flow<List<EpisodeWithPodcastLite>>

    fun getUnplayedCounts(): Flow<Map<String, Int>>

    // --- PODCAST MANAGEMENT ---
    suspend fun updateAllPodcasts(
        downloadLimit: Int,
        mode: FeedUpdateMode,
        forceFull: Boolean
    ): PodcastUpdateSummary

    suspend fun addPodcast(
        url: String,
        downloadLimit: Int,
        mode: FeedUpdateMode,
        sortOrder: Long? = null,
        forceFull: Boolean = false,
        allowInsecureHttp: Boolean = false,
        allowLocalNetwork: Boolean = false
    )

    suspend fun removePodcastByUrl(url: String)

    suspend fun deletePodcast(podcast: Podcast)

    suspend fun reorderPodcasts(list: List<Podcast>)

    suspend fun getPodcast(rssUrl: String): Podcast?

    suspend fun getEpisode(episodeId: Long): EpisodeEntity?

    suspend fun resolveLegacyDownloadEpisode(guid: String): EpisodeEntity?

    // --- EPISODE ACTIONS ---
    suspend fun markEpisodePlayed(
        episodeId: Long,
        played: Boolean,
        datePlayed: Date?
    )

    suspend fun toggleEpisodePlayed(episode: EpisodeEntity)

    suspend fun savePlaybackProgress(
        episodeId: Long,
        positionMs: Long
    )

    suspend fun markAllAsSeen()

    suspend fun cleanupPlayedEpisodes()

    suspend fun updatePodcastSettings(
        podcast: Podcast,
        autoDownloadEnabled: Boolean
    )

    // --- SEARCH ---
    suspend fun searchPodcasts(term: String): List<ItunesPodcastDto>

    // FTS search added
    fun searchEpisodesFlow(query: String): Flow<List<EpisodeEntity>>

    // --- FAVORITES & HISTORY ---
    suspend fun setFavoriteStatus(
        episodeId: Long,
        isFavorite: Boolean,
        timestamp: Long?
    )

    suspend fun clearHistory()

    suspend fun reorderFavorites(episodes: List<EpisodeEntity>)

    // --- DOWNLOAD STATUS ---
    suspend fun updateDownloadStatus(
        episodeId: Long,
        status: DownloadStatus,
        path: String?
    )

    // --- SYNC SUPPORT ---
    suspend fun getPodcastEntityByUrl(url: String): PodcastEntity?

    suspend fun insertPodcastEntity(entity: PodcastEntity)

    suspend fun updatePodcastEntity(entity: PodcastEntity)

    suspend fun getMaxSortOrder(): Long?

    suspend fun getEpisodesForSync(rssUrl: String): List<EpisodeEntity>

    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    suspend fun isLatestEpisodePlayed(rssUrl: String): Boolean?

    suspend fun getLatestEpisodeGuid(rssUrl: String): String?

    suspend fun updatePodcastNewFlag(
        rssUrl: String,
        hasNew: Boolean
    )

    // --- MAINTENANCE ---
    suspend fun reconcileEpisodeStorage(): Int

    suspend fun pruneLibrary(limitPerPodcast: Int)

    suspend fun resetDatabase()
}
