package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.EpisodeWithPodcastLite
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.data.manager.PodcastDownloader
import com.example.pocastcloni.data.remote.ItunesPodcastDto
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.PodcastUpdateSummary
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    private val updateSemaphore = Semaphore(4)

    // --- FLOWS --- (unchanged)
    override fun getAllPodcastsFlow(): Flow<List<Podcast>> {
        return podcastDao.getAllPodcastsFlow()
            .map { list ->
                list.map { entity ->
                    entity.toDomain().copy(
                        hasNewEpisodes = entity.hasNewEpisodes,
                        isLatestEpisodePlayed = entity.isLatestEpisodePlayed
                    )
                }
            }
            .catch { emit(emptyList()) }
            .flowOn(dispatcherProvider.io)
    }

    override fun getEpisodesFlow(rssUrl: String): Flow<List<EpisodeEntity>> =
        podcastDao.getEpisodesFlow(rssUrl).catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getEpisodesPagedFlow(rssUrl: String): Flow<PagingData<EpisodeEntity>> =
        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false, initialLoadSize = 40),
            pagingSourceFactory = { podcastDao.getEpisodesPagingSource(rssUrl) }
        ).flow.flowOn(dispatcherProvider.io)

    override fun getPodcastFlow(rssUrl: String): Flow<Podcast?> =
        podcastDao.getPodcastFlow(rssUrl).map { it?.toDomain() }.catch { emit(null) }.flowOn(dispatcherProvider.io)

    override fun getSubscribedUrlsFlow(): Flow<List<String>> =
        podcastDao.getSubscribedUrlsFlow().catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>> =
        podcastDao.getDownloadedEpisodes(listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED))
            .catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getDownloadedEpisodesWithPodcastLiteFlow(): Flow<List<EpisodeWithPodcastLite>> =
        podcastDao.getDownloadedEpisodesWithPodcastLiteFlow(
            listOf(DownloadStatus.DOWNLOADED, DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)
        )
            .catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getFavoriteEpisodes(): Flow<List<EpisodeEntity>> =
        podcastDao.getFavoriteEpisodes().catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getFavoriteEpisodesWithPodcastInfoFlow(): Flow<Map<EpisodeEntity, Podcast?>> =
        podcastDao.getFavoriteEpisodesWithPodcastLiteFlow()
            .map { list -> list.associate { row -> row.episode to row.toPodcastDomain() } }
            .catch { emit(emptyMap()) }.flowOn(dispatcherProvider.io)

    override fun isFavorite(guid: String): Flow<Boolean> =
        podcastDao.isFavorite(
            guid
        ).catch { emit(false) }.flowOn(dispatcherProvider.io)

    override fun getPlaybackHistory(): Flow<List<EpisodeEntity>> =
        podcastDao.getPlaybackHistory().catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getPlaybackHistoryWithPodcastInfoFlow(): Flow<Map<EpisodeEntity, Podcast?>> =
        podcastDao.getPlaybackHistoryWithPodcastLiteFlow()
            .map { list -> list.associate { row -> row.episode to row.toPodcastDomain() } }
            .catch { emit(emptyMap()) }.flowOn(dispatcherProvider.io)

    override fun getEpisodesInProgress(): Flow<List<EpisodeWithPodcastLite>> =
        podcastDao.getEpisodesInProgressWithPodcastLiteFlow().catch { emit(emptyList()) }.flowOn(dispatcherProvider.io)

    override fun getUnplayedCounts(): Flow<Map<String, Int>> =
        podcastDao.getUnplayedCountsFlow().map { list -> list.associate { it.rssUrl to it.count } }
            .catch { emit(emptyMap()) }.flowOn(dispatcherProvider.io)

    // --- PODCAST MANAGEMENT --- (unchanged)
    override suspend fun updateAllPodcasts(
        downloadLimit: Int,
        mode: FeedUpdateMode,
        forceFull: Boolean
    ): PodcastUpdateSummary {
        return withContext(dispatcherProvider.io) {
            val urls = podcastDao.getAllPodcastUrls()
            val results =
                urls.map { url ->
                    async {
                        updateSemaphore.withPermit {
                            try {
                                syncFeedUseCase.get().invoke(url, downloadLimit, mode, null, forceFull)
                                true
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                Timber.w(error, "Failed to update feed %s", url)
                                false
                            }
                        }
                    }
                }.awaitAll()
            val successfulCount = results.count { it }
            PodcastUpdateSummary(
                totalCount = urls.size,
                successfulCount = successfulCount,
                failureCount = urls.size - successfulCount
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
                    val approved = existing.copy(
                        allowInsecureHttp = existing.allowInsecureHttp || allowInsecureHttp,
                        allowLocalNetwork = existing.allowLocalNetwork || allowLocalNetwork
                    )
                    podcastDao.updatePodcast(approved)
                    syncFeedUseCase.get().invoke(
                        normalizedUrl,
                        downloadLimit,
                        mode,
                        existing.sortOrder,
                        forceFull,
                        allowInsecureHttp = approved.allowInsecureHttp,
                        allowLocalNetwork = approved.allowLocalNetwork
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

    override suspend fun getEpisode(guid: String): EpisodeEntity? =
        withContext(
            dispatcherProvider.io
        ) { podcastDao.getEpisodeByGuid(guid) }

    // --- EPISODE ACTIONS ---

    // FIX: The notification dot is now updated as well!
    override suspend fun markEpisodePlayed(
        guid: String,
        played: Boolean,
        datePlayed: Date?
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.markEpisodePlayed(guid, played, datePlayed)
            updatePodcastNewFlagIfLatest(guid, played)
        }
    }

    override suspend fun toggleEpisodePlayed(episode: EpisodeEntity) {
        withContext(dispatcherProvider.io) {
            val newPlayed = !episode.isPlayed
            val date = if (newPlayed) Date() else null

            podcastDao.markEpisodePlayed(episode.guid, newPlayed, date)
            updatePodcastNewFlagIfLatest(episode.guid, newPlayed)
        }
    }

    // FIX: Helper function to avoid code duplication and ensure the dot is always updated
    private suspend fun updatePodcastNewFlagIfLatest(
        guid: String,
        isPlayed: Boolean
    ) {
        // We need the RSS URL of the episode
        val episode = podcastDao.getEpisodeByGuid(guid) ?: return
        val rssUrl = episode.podcastRssUrl

        if (rssUrl.isNotBlank()) {
            val latestGuid = podcastDao.getLatestEpisodeGuid(rssUrl)
            // If the changed episode is the LATEST one:
            if (latestGuid != null && latestGuid == guid) {
                // If played -> no dot (hasNew = false)
                // If unplayed -> dot on (hasNew = true)
                podcastDao.updatePodcastNewFlag(rssUrl, hasNew = !isPlayed)
                podcastDao.updateLatestEpisodePlayedFlag(rssUrl, isPlayed)
            }
        }
    }

    override suspend fun savePlaybackProgress(
        guid: String,
        positionMs: Long
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.updateEpisodeProgressOnly(guid, positionMs)

            if (positionMs > 0) {
                // 1. Fetch duration (in seconds)
                val durationSeconds = podcastDao.getEpisodeDuration(guid) ?: 0L

                if (durationSeconds > 0) {
                    // 2. Convert to milliseconds for comparison
                    val durationMs = durationSeconds * 1000L

                    // 3. 95% logic
                    val thresholdMs = (durationMs * 0.95).toLong()

                    if (positionMs >= thresholdMs) {
                        Timber.d("Smart Completion: Marking $guid as played (pos=$positionMs, durMs=$durationMs)")

                        // FIX: Mark as played + dot update
                        podcastDao.markEpisodePlayed(guid, true, Date())
                        updatePodcastNewFlagIfLatest(guid, true)
                    }
                }
            }
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
        return podcastDao.searchEpisodes(dbQuery).catch { e ->
            Timber.e(e, "FTS Search failed")
            emit(emptyList())
        }.flowOn(dispatcherProvider.io)
    }

    // --- FAVORITES & HISTORY & SYNC (unchanged) ---
    override suspend fun setFavoriteStatus(
        guid: String,
        isFavorite: Boolean,
        timestamp: Long?
    ) {
        withContext(dispatcherProvider.io) {
            val favoriteTimestamp = if (isFavorite) timestamp ?: System.currentTimeMillis() else null
            podcastDao.setFavoriteStatus(
                guid = guid,
                isFavorite = isFavorite,
                timestamp = favoriteTimestamp,
                favoriteAddedAt = favoriteTimestamp
            )
        }
    }

    override suspend fun clearHistory() {
        withContext(dispatcherProvider.io) { podcastDao.clearHistory() }
    }

    override suspend fun reorderFavorites(episodes: List<EpisodeEntity>) {
        withContext(dispatcherProvider.io) { podcastDao.updateEpisodes(episodes) }
    }

    override suspend fun updateDownloadStatus(
        guid: String,
        status: DownloadStatus,
        path: String?
    ) {
        withContext(dispatcherProvider.io) { podcastDao.updateDownloadStatus(guid, status, path) }
    }

    override suspend fun getPodcastEntityByUrl(url: String): PodcastEntity? =
        withContext(dispatcherProvider.io) {
            podcastDao.getPodcastByUrl(url)
        }

    override suspend fun insertPodcastEntity(entity: PodcastEntity) =
        withContext(
            dispatcherProvider.io
        ) { podcastDao.insertPodcast(entity) }

    override suspend fun updatePodcastEntity(entity: PodcastEntity) =
        withContext(
            dispatcherProvider.io
        ) { podcastDao.updatePodcast(entity) }

    override suspend fun getMaxSortOrder(): Long? = withContext(dispatcherProvider.io) { podcastDao.getMaxSortOrder() }

    override suspend fun getEpisodesForSync(rssUrl: String): List<EpisodeEntity> =
        withContext(dispatcherProvider.io) {
            podcastDao.getEpisodesForPodcastSync(rssUrl)
        }

    override suspend fun insertEpisodes(episodes: List<EpisodeEntity>) =
        withContext(dispatcherProvider.io) {
            podcastDao.upsertEpisodesEfficient(episodes)
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

    override suspend fun reconcileEpisodeStorage(): Int {
        return withContext(dispatcherProvider.io) {
            var correctedEntries =
                podcastDao.bulkResetDownloadStates(
                    listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)
                )

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
                podcastDao.updateDownloadStatus(row.guid, DownloadStatus.NOT_DOWNLOADED, null)
            }

            correctedEntries += brokenDownloads.size
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
}
