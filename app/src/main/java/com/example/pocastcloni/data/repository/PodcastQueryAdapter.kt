package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.EpisodeWithPodcast
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastSearchResult
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.PodcastSearchPort
import com.example.pocastcloni.util.RetryingDataFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class PodcastQueryAdapter(
    private val podcastDao: PodcastDao,
    private val dispatcherProvider: DispatcherProvider,
    private val search: PodcastSearchPort
) : PodcastQueryPort {
    override fun getAllPodcastsFlow(): Flow<List<Podcast>> =
        RetryingDataFlow.bounded(
            podcastDao.getAllPodcastsWithCoverFlow().map { podcasts ->
                podcasts.map { row ->
                    row.toDomain().copy(
                        hasNewEpisodes = row.podcast.hasNewEpisodes,
                        isLatestEpisodePlayed = row.podcast.isLatestEpisodePlayed
                    )
                }
            }
        ).flowOn(dispatcherProvider.io)

    override fun getEpisodesFlow(rssUrl: String): Flow<List<Episode>> =
        RetryingDataFlow.bounded(
            podcastDao.getEpisodesFlow(rssUrl).map { episodes -> episodes.map(EpisodeEntity::toDomain) }
        ).flowOn(dispatcherProvider.io)

    override fun getPodcastFlow(rssUrl: String): Flow<Podcast?> =
        RetryingDataFlow.bounded(podcastDao.getPodcastWithCoverFlow(rssUrl).map { it?.toDomain() })
            .flowOn(dispatcherProvider.io)

    override fun getSubscribedUrlsFlow(): Flow<List<String>> =
        RetryingDataFlow.bounded(podcastDao.getSubscribedUrlsFlow()).flowOn(dispatcherProvider.io)

    override suspend fun getSubscribedUrls(): List<String> =
        withContext(dispatcherProvider.io) { podcastDao.getAllPodcastUrls() }

    override fun getDownloadedEpisodes(): Flow<List<Episode>> =
        RetryingDataFlow.bounded(
            podcastDao.getDownloadedEpisodes(
                listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
            ).map { episodes -> episodes.map(EpisodeEntity::toDomain) }
        ).flowOn(dispatcherProvider.io)

    override fun getDownloadedEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> =
        RetryingDataFlow.bounded(
            podcastDao.getDownloadedEpisodesWithPodcastLiteFlow(
                listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
            ).map { rows -> rows.map { it.toDomain() } }
        ).flowOn(dispatcherProvider.io)

    override fun getFavoriteEpisodes(): Flow<List<Episode>> =
        RetryingDataFlow.bounded(
            podcastDao.getFavoriteEpisodes().map { episodes -> episodes.map(EpisodeEntity::toDomain) }
        ).flowOn(dispatcherProvider.io)

    override fun getFavoriteEpisodesWithPodcastInfoFlow(): Flow<Map<Episode, Podcast?>> =
        RetryingDataFlow.bounded(
            podcastDao.getFavoriteEpisodesWithPodcastLiteFlow()
                .map { rows -> rows.associate { row -> row.episode.toDomain() to row.toPodcastDomain() } }
        ).flowOn(dispatcherProvider.io)

    override fun isFavorite(episodeId: Long): Flow<Boolean> =
        RetryingDataFlow.bounded(podcastDao.isFavorite(episodeId)).flowOn(dispatcherProvider.io)

    override fun getPlaybackHistory(): Flow<List<Episode>> =
        RetryingDataFlow.bounded(
            podcastDao.getPlaybackHistory().map { episodes -> episodes.map(EpisodeEntity::toDomain) }
        ).flowOn(dispatcherProvider.io)

    override fun getPlaybackHistoryWithPodcastInfoFlow(): Flow<Map<Episode, Podcast?>> =
        RetryingDataFlow.bounded(
            podcastDao.getPlaybackHistoryWithPodcastLiteFlow()
                .map { rows -> rows.associate { row -> row.episode.toDomain() to row.toPodcastDomain() } }
        ).flowOn(dispatcherProvider.io)

    override fun getEpisodesInProgress(): Flow<List<EpisodeWithPodcast>> =
        RetryingDataFlow.bounded(
            podcastDao.getEpisodesInProgressWithPodcastLiteFlow().map { rows -> rows.map { it.toDomain() } }
        ).flowOn(dispatcherProvider.io)

    override fun getUnplayedCounts(): Flow<Map<String, Int>> =
        RetryingDataFlow.bounded(
            podcastDao.getUnplayedCountsFlow().map { counts ->
                counts.associate { it.rssUrl to it.count }
            }
        ).flowOn(dispatcherProvider.io)

    override suspend fun getPodcast(rssUrl: String): Podcast? =
        withContext(dispatcherProvider.io) { podcastDao.getPodcastWithCoverByUrl(rssUrl)?.toDomain() }

    override suspend fun getEpisode(episodeId: Long): Episode? =
        withContext(dispatcherProvider.io) { podcastDao.getEpisodeById(episodeId)?.toDomain() }

    override suspend fun resolveLegacyDownloadEpisode(guid: String): Episode? =
        withContext(dispatcherProvider.io) {
            val resolution = resolveLegacyDownloadCandidates(podcastDao.getEpisodesByLegacyGuid(guid))
            resolution.transientEpisodeIdsToReset.forEach { episodeId ->
                podcastDao.compareAndSetDownloadStatus(
                    episodeId = episodeId,
                    expectedStatuses = listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING),
                    status = DownloadStatus.NOT_DOWNLOADED,
                    path = null
                )
            }
            resolution.episode?.toDomain()
        }

    override fun searchEpisodesFlow(query: String): Flow<List<Episode>> =
        search.searchEpisodesFlow(query)

    override suspend fun searchPodcasts(term: String): List<PodcastSearchResult> =
        search.searchPodcasts(term)
}
