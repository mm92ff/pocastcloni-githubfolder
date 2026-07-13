package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.EpisodeWithPodcastLite
import com.example.pocastcloni.data.local.FavoriteOrderUpdate
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.data.manager.PodcastDownloader
import com.example.pocastcloni.data.remote.ItunesPodcastDto
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.data.worker.downloadStagingFiles
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.FeedUpdateFailure
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.model.classifyFeedFailure
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import com.example.pocastcloni.util.RetryingDataFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class PodcastRepositoryImpl
@Inject
constructor(
    private val podcastDao: PodcastDao,
    private val itunesSearchApi: ItunesSearchApi,
    private val dispatcherProvider: DispatcherProvider,
    private val downloader: PodcastDownloader,
    private val syncFeedUseCase: Provider<SyncFeedUseCase>,
    @ApplicationContext private val context: Context
) : PodcastRepository {
    private val updateSemaphore = Semaphore(MAX_FEED_UPDATE_FANOUT)

    // --- FLOWS ---
    override fun getAllPodcastsFlow(): Flow<List<Podcast>> {
        return RetryingDataFlow.bounded(
            podcastDao.getAllPodcastsFlow()
                .map { list ->
                    list.map { entity ->
                        entity.toDomain().copy(
                            hasNewEpisodes = entity.hasNewEpisodes,
                            isLatestEpisodePlayed = entity.isLatestEpisodePlayed
                        )
                    }
                }
        )
            .flowOn(dispatcherProvider.io)
    }

    override fun getEpisodesFlow(rssUrl: String): Flow<List<EpisodeEntity>> =
        RetryingDataFlow.bounded(podcastDao.getEpisodesFlow(rssUrl)).flowOn(dispatcherProvider.io)

    override fun getEpisodesPagedFlow(rssUrl: String): Flow<PagingData<EpisodeEntity>> =
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 40),
            pagingSourceFactory = { podcastDao.getEpisodesPagingSource(rssUrl) }
        ).flow.flowOn(dispatcherProvider.io)

    override fun getPodcastFlow(rssUrl: String): Flow<Podcast?> =
        RetryingDataFlow.bounded(podcastDao.getPodcastFlow(rssUrl).map { it?.toDomain() })
            .flowOn(dispatcherProvider.io)

    override fun getSubscribedUrlsFlow(): Flow<List<String>> =
        RetryingDataFlow.bounded(podcastDao.getSubscribedUrlsFlow()).flowOn(dispatcherProvider.io)

    override fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>> =
        RetryingDataFlow.bounded(
            podcastDao.getDownloadedEpisodes(
                listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
            )
        ).flowOn(dispatcherProvider.io)

    override fun getDownloadedEpisodesWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>> =
        RetryingDataFlow.bounded(
            podcastDao.getDownloadedEpisodesWithPodcastLiteFlow(
                listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
            )
        ).flowOn(dispatcherProvider.io)

    override fun getFavoriteEpisodes(): Flow<List<EpisodeEntity>> =
        RetryingDataFlow.bounded(podcastDao.getFavoriteEpisodes()).flowOn(dispatcherProvider.io)

    override fun getFavoriteEpisodesWithPodcastInfoFlow(): Flow<Map<EpisodeEntity, Podcast?>> =
        RetryingDataFlow.bounded(
            podcastDao.getFavoriteEpisodesWithPodcastLiteFlow()
                .map { list -> list.associate { row -> row.episode to row.toPodcastDomain() } }
        ).flowOn(dispatcherProvider.io)

    override fun isFavorite(episodeId: Long): Flow<Boolean> =
        RetryingDataFlow.bounded(podcastDao.isFavorite(episodeId)).flowOn(dispatcherProvider.io)

    override fun getPlaybackHistory(): Flow<List<EpisodeEntity>> =
        RetryingDataFlow.bounded(podcastDao.getPlaybackHistory()).flowOn(dispatcherProvider.io)

    override fun getPlaybackHistoryWithPodcastInfoFlow(): Flow<Map<EpisodeEntity, Podcast?>> =
        RetryingDataFlow.bounded(
            podcastDao.getPlaybackHistoryWithPodcastLiteFlow()
                .map { list -> list.associate { row -> row.episode to row.toPodcastDomain() } }
        ).flowOn(dispatcherProvider.io)

    override fun getEpisodesInProgress(): Flow<List<EpisodeWithPodcastLite>> =
        RetryingDataFlow.bounded(podcastDao.getEpisodesInProgressWithPodcastLiteFlow())
            .flowOn(dispatcherProvider.io)

    override fun getUnplayedCounts(): Flow<Map<String, Int>> =
        RetryingDataFlow.bounded(
            podcastDao.getUnplayedCountsFlow().map { list -> list.associate { it.rssUrl to it.count } }
        ).flowOn(dispatcherProvider.io)

    // --- PODCAST MANAGEMENT --- (unchanged)
    override suspend fun updateAllPodcasts(
        downloadLimit: Int,
        mode: FeedUpdateMode,
        forceFull: Boolean,
        feedUrls: Set<String>?
    ): PodcastUpdateSummary {
        return withContext(dispatcherProvider.io) {
            val urls =
                podcastDao.getAllPodcastUrls().let { subscribedUrls ->
                    if (feedUrls == null) subscribedUrls else subscribedUrls.filter(feedUrls::contains)
                }
            val outcomes =
                urls.chunked(MAX_FEED_UPDATE_FANOUT).flatMap { batch ->
                    batch.map { url ->
                        async {
                            updateSemaphore.withPermit {
                                try {
                                    syncFeedUseCase.get().invoke(url, downloadLimit, mode, null, forceFull)
                                    null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    Timber.w(error, "Failed to update a podcast feed")
                                    FeedUpdateFailure(url, classifyFeedFailure(error))
                                }
                            }
                        }
                    }.awaitAll()
                }
            val failures = outcomes.filterNotNull()
            PodcastUpdateSummary(
                totalCount = urls.size,
                successfulCount = urls.size - failures.size,
                failureCount = failures.size,
                failures = failures
            )
        }
    }

    override suspend fun addPodcast(
        url: String,
        downloadLimit: Int,
        mode: FeedUpdateMode,
        sortOrder: Long?,
        forceFull: Boolean,
        allowInsecureHttp: Boolean,
        allowLocalNetwork: Boolean
    ) {
        withContext(dispatcherProvider.io) {
            val normalizedUrl = url.trim()
            val existing = podcastDao.getPodcastByUrl(normalizedUrl)
            if (existing != null) {
                if (
                    (allowInsecureHttp && !existing.allowInsecureHttp) ||
                    (allowLocalNetwork && !existing.allowLocalNetwork)
                ) {
                    val effectiveAllowInsecureHttp = existing.allowInsecureHttp || allowInsecureHttp
                    val effectiveAllowLocalNetwork = existing.allowLocalNetwork || allowLocalNetwork
                    podcastDao.approvePodcastNetworkAccess(
                        rssUrl = normalizedUrl,
                        allowInsecureHttp = allowInsecureHttp,
                        allowLocalNetwork = allowLocalNetwork
                    )
                    syncFeedUseCase.get().invoke(
                        normalizedUrl,
                        downloadLimit,
                        mode,
                        existing.sortOrder,
                        forceFull,
                        allowInsecureHttp = effectiveAllowInsecureHttp,
                        allowLocalNetwork = effectiveAllowLocalNetwork
                    )
                }
                return@withContext
            }
            val orderToUse = sortOrder ?: ((podcastDao.getMaxSortOrder() ?: 0L) + 1L)
            syncFeedUseCase.get().invoke(
                normalizedUrl,
                downloadLimit,
                mode,
                orderToUse,
                forceFull,
                allowInsecureHttp,
                allowLocalNetwork
            )
        }
    }

    override suspend fun removePodcastByUrl(url: String) {
        withContext(dispatcherProvider.io) { podcastDao.deletePodcastAtomic(url) }
    }

    override suspend fun deletePodcast(podcast: Podcast) {
        removePodcastByUrl(podcast.rssUrl)
    }

    override suspend fun reorderPodcasts(list: List<Podcast>) {
        withContext(dispatcherProvider.io) {
            // FIX: Use partial update to change only sortOrder.
            // Prevents overwriting other fields (hasNewEpisodes, autoDownload) during parallel syncs.
            val updates =
                list.mapIndexed { index, item ->
                    PodcastSortUpdate(
                        rssUrl = item.rssUrl,
                        sortOrder = index.toLong()
                    )
                }
            podcastDao.updatePodcastSortOrders(updates)
        }
    }

    override suspend fun getPodcast(rssUrl: String): Podcast? =
        withContext(dispatcherProvider.io) { podcastDao.getPodcastByUrl(rssUrl)?.toDomain() }

    override suspend fun getEpisode(episodeId: Long): EpisodeEntity? =
        withContext(
            dispatcherProvider.io
        ) { podcastDao.getEpisodeById(episodeId) }

    override suspend fun resolveLegacyDownloadEpisode(guid: String): EpisodeEntity? =
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
            resolution.episode
        }

    // --- EPISODE ACTIONS ---

    // FIX: The notification dot is now updated as well!
    override suspend fun markEpisodePlayed(
        episodeId: Long,
        played: Boolean,
        datePlayed: Date?
    ) {
        withContext(dispatcherProvider.io) {
            val changedRows =
                if (played) {
                    podcastDao.markEpisodePlayedIfNeeded(episodeId, datePlayed ?: Date())
                } else {
                    podcastDao.markEpisodePlayed(episodeId, false, null)
                    1
                }
            if (changedRows > 0) updatePodcastNewFlagIfLatest(episodeId, played)
        }
    }

    override suspend fun toggleEpisodePlayed(episode: EpisodeEntity) {
        withContext(dispatcherProvider.io) {
            val newPlayed = !episode.isPlayed
            val date = if (newPlayed) Date() else null

            podcastDao.markEpisodePlayed(episode.episodeId, newPlayed, date)
            updatePodcastNewFlagIfLatest(episode.episodeId, newPlayed)
        }
    }

    // FIX: Helper function to avoid code duplication and ensure the dot is always updated
    private suspend fun updatePodcastNewFlagIfLatest(
        episodeId: Long,
        isPlayed: Boolean
    ) {
        // We need the RSS URL of the episode
        val episode = podcastDao.getEpisodeById(episodeId) ?: return
        val rssUrl = episode.podcastRssUrl

        if (rssUrl.isNotBlank()) {
            val latestGuid = podcastDao.getLatestEpisodeGuid(rssUrl)
            // If the changed episode is the LATEST one:
            if (latestGuid != null && latestGuid == episode.guid) {
                // If played -> no dot (hasNew = false)
                // If unplayed -> dot on (hasNew = true)
                podcastDao.updatePodcastNewFlag(rssUrl, hasNew = !isPlayed)
                podcastDao.updateLatestEpisodePlayedFlag(rssUrl, isPlayed)
            }
        }
    }

    override suspend fun savePlaybackProgress(
        episodeId: Long,
        positionMs: Long
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.updateEpisodeProgressOnly(episodeId, positionMs)
        }
    }

    override suspend fun markAllAsSeen() {
        withContext(dispatcherProvider.io) { podcastDao.markAllAsSeenAtomic() }
    }

    override suspend fun updatePodcastSettings(
        podcast: Podcast,
        autoDownloadEnabled: Boolean
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.updateAutoDownloadEnabled(podcast.rssUrl, autoDownloadEnabled)
        }
    }

    override suspend fun cleanupPlayedEpisodes() {
        withContext(dispatcherProvider.io) {
            val rows = podcastDao.getPlayedDownloadedEpisodePaths()
            podcastDao.bulkResetPlayedDownloadedEpisodes()
            rows.forEach { row ->
                val path = row.downloadPath ?: return@forEach
                runCatching {
                    if (path.startsWith("content://")) {
                        context.contentResolver.delete(path.toUri(), null, null)
                    } else {
                        java.io.File(path).delete()
                    }
                }
            }
        }
    }

    // --- SEARCH --- (unchanged)
    override suspend fun searchPodcasts(term: String): List<ItunesPodcastDto> {
        return withContext(dispatcherProvider.io) { itunesSearchApi.searchPodcasts(term).results }
    }

    override fun searchEpisodesFlow(query: String): Flow<List<EpisodeEntity>> {
        if (query.isBlank()) return flowOf(emptyList())
        val dbQuery = "*$query*"
        return RetryingDataFlow.bounded(podcastDao.searchEpisodes(dbQuery)).flowOn(dispatcherProvider.io)
    }

    // --- FAVORITES & HISTORY & SYNC (unchanged) ---
    override suspend fun setFavoriteStatus(
        episodeId: Long,
        isFavorite: Boolean,
        timestamp: Long?
    ) {
        withContext(dispatcherProvider.io) {
            val favoriteTimestamp = if (isFavorite) timestamp ?: System.currentTimeMillis() else null
            podcastDao.setFavoriteStatus(
                episodeId = episodeId,
                isFavorite = isFavorite,
                timestamp = favoriteTimestamp,
                favoriteAddedAt = favoriteTimestamp
            )
        }
    }

    override suspend fun clearHistory() {
        withContext(dispatcherProvider.io) { podcastDao.clearHistory() }
    }

    override suspend fun reorderFavorites(
        episodeIds: List<Long>,
        orderedAt: Long
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.updateFavoriteOrder(
                episodeIds.mapIndexed { index, episodeId ->
                    FavoriteOrderUpdate(
                        episodeId = episodeId,
                        favoriteTimestamp = orderedAt - index
                    )
                }
            )
        }
    }

    override suspend fun updateDownloadStatus(
        episodeId: Long,
        status: DownloadStatus,
        path: String?
    ) {
        withContext(dispatcherProvider.io) { podcastDao.updateDownloadStatus(episodeId, status, path) }
    }

    override suspend fun compareAndSetDownloadStatus(
        episodeId: Long,
        expectedStatuses: List<DownloadStatus>,
        status: DownloadStatus,
        path: String?
    ): Boolean =
        withContext(dispatcherProvider.io) {
            podcastDao.compareAndSetDownloadStatus(episodeId, expectedStatuses, status, path) == 1
        }

    override suspend fun compareAndSetDownloadStatusAndPath(
        episodeId: Long,
        expectedStatus: DownloadStatus,
        expectedPath: String?,
        status: DownloadStatus,
        path: String?
    ): Boolean =
        withContext(dispatcherProvider.io) {
            podcastDao.compareAndSetDownloadStatusAndPath(
                episodeId,
                expectedStatus,
                expectedPath,
                status,
                path
            ) == 1
        }

    override suspend fun getPodcastEntityByUrl(url: String): PodcastEntity? =
        withContext(dispatcherProvider.io) {
            podcastDao.getPodcastByUrl(url)
        }

    override suspend fun getMaxSortOrder(): Long? = withContext(dispatcherProvider.io) { podcastDao.getMaxSortOrder() }

    override suspend fun getEpisodesForSync(rssUrl: String): List<EpisodeEntity> =
        withContext(dispatcherProvider.io) {
            podcastDao.getEpisodesForPodcastSync(rssUrl)
        }

    override suspend fun isLatestEpisodePlayed(rssUrl: String): Boolean? =
        withContext(dispatcherProvider.io) {
            podcastDao.isLatestEpisodePlayed(rssUrl)
        }

    override suspend fun getLatestEpisodeGuid(rssUrl: String): String? =
        withContext(dispatcherProvider.io) {
            podcastDao.getLatestEpisodeGuid(rssUrl)
        }

    override suspend fun updatePodcastNewFlag(
        rssUrl: String,
        hasNew: Boolean
    ) = withContext(dispatcherProvider.io) {
        podcastDao.updatePodcastNewFlag(rssUrl, hasNew)
    }

    override suspend fun reconcileEpisodeStorage(
        activeDownloadEpisodeIds: Set<Long>,
        isDownloadWorkActive: suspend (episodeId: Long) -> Boolean
    ): Int {
        return withContext(dispatcherProvider.io) {
            var correctedEntries = 0
            val transientDownloads =
                podcastDao.getEpisodeDownloadStates(
                    listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
                )
            transientDownloads.forEach { row ->
                correctedEntries +=
                    reconcileTransientDownloadState(
                        initiallyActive = row.episodeId in activeDownloadEpisodeIds,
                        isWorkActive = { isDownloadWorkActive(row.episodeId) },
                        compareAndReset = {
                            podcastDao.compareAndSetDownloadStatus(
                                episodeId = row.episodeId,
                                expectedStatuses = listOf(row.downloadStatus),
                                status = DownloadStatus.NOT_DOWNLOADED,
                                path = null
                            ) == 1
                        },
                        deleteStaging = {
                            downloadStagingFiles(context.filesDir, row.episodeId).delete()
                        }
                    )
            }

            val brokenDownloads =
                podcastDao
                    .getEpisodeDownloadStates(listOf(DownloadStatus.DOWNLOADED))
                    .filter { row ->
                        shouldResetDownloadState(
                            status = row.downloadStatus,
                            downloadPath = row.downloadPath
                        ) { path ->
                            if (path.startsWith("content://")) {
                                isContentUriReadable(path)
                            } else {
                                File(path).let { it.exists() && it.isFile && it.canRead() }
                            }
                        }
                    }

            brokenDownloads.forEach { row ->
                correctedEntries +=
                    podcastDao.compareAndSetDownloadStatusAndPath(
                        episodeId = row.episodeId,
                        expectedStatus = DownloadStatus.DOWNLOADED,
                        expectedPath = row.downloadPath,
                        status = DownloadStatus.NOT_DOWNLOADED,
                        path = null
                    )
            }
            correctedEntries
        }
    }

    private fun isContentUriReadable(uriString: String): Boolean =
        try {
            context.contentResolver.openFileDescriptor(uriString.toUri(), "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }

    override suspend fun pruneLibrary(limitPerPodcast: Int) {
        withContext(dispatcherProvider.io) {
            val urls = podcastDao.getAllPodcastUrls()
            urls.forEach { url ->
                val episodesToPrune =
                    selectEpisodesToPrune(
                        episodes = podcastDao.getEpisodesForPodcastSync(url),
                        keepCount = limitPerPodcast
                    )
                if (episodesToPrune.isNotEmpty()) {
                    // Collect paths before deleting DB rows (defensive: isSafeToPrune already
                    // requires NOT_DOWNLOADED, so this list is normally empty).
                    val pathsToDelete = episodesToPrune
                        .filter { it.downloadStatus == DownloadStatus.DOWNLOADED }
                        .mapNotNull { it.downloadPath }

                    podcastDao.deleteEpisodes(episodesToPrune)

                    pathsToDelete.forEach { path ->
                        runCatching {
                            if (path.startsWith("content://")) {
                                context.contentResolver.delete(path.toUri(), null, null)
                            } else {
                                java.io.File(path).delete()
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun resetDatabase() {
        withContext(dispatcherProvider.io) { podcastDao.deleteAllPodcasts() }
    }

    private companion object {
        const val MAX_FEED_UPDATE_FANOUT = 4
    }
}
