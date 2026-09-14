package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.pocastcloni.data.local.FavoriteOrderUpdate
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastSortUpdate
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

internal class PodcastCommandAdapter(
    private val podcastDao: PodcastDao,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context
) : PodcastCommandPort {
    override suspend fun removePodcastByUrl(url: String) {
        withContext(dispatcherProvider.io) { podcastDao.deletePodcastAtomic(url) }
    }

    override suspend fun deletePodcast(podcast: Podcast) {
        removePodcastByUrl(podcast.rssUrl)
    }

    override suspend fun reorderPodcasts(rssUrlsInOrder: List<String>) {
        withContext(dispatcherProvider.io) {
            val updates =
                rssUrlsInOrder.mapIndexed { index, rssUrl ->
                    PodcastSortUpdate(
                        rssUrl = rssUrl,
                        sortOrder = index.toLong()
                    )
                }
            podcastDao.updatePodcastSortOrders(updates)
        }
    }

    override suspend fun markEpisodePlayed(
        episodeId: Long,
        played: Boolean,
        datePlayed: Date?
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.markEpisodePlayedAndReconcileBadge(
                episodeId = episodeId,
                isPlayed = played,
                datePlayed = if (played) datePlayed ?: Date() else null
            )
        }
    }

    override suspend fun toggleEpisodePlayed(episode: Episode) {
        withContext(dispatcherProvider.io) {
            podcastDao.toggleEpisodePlayedAndReconcileBadge(
                episodeId = episode.episodeId,
                datePlayed = Date()
            )
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
        withContext(dispatcherProvider.io) { podcastDao.markAllAsSeen() }
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
                        File(path).delete()
                    }
                }
            }
        }
    }

    override suspend fun updatePodcastSettings(
        podcast: Podcast,
        autoDownloadEnabled: Boolean
    ) {
        withContext(dispatcherProvider.io) {
            podcastDao.updateAutoDownloadEnabled(podcast.rssUrl, autoDownloadEnabled)
        }
    }

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
                favoriteTimestamp = favoriteTimestamp,
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
        withContext(dispatcherProvider.io) {
            podcastDao.updateDownloadStatus(episodeId, status, path)
        }
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
}
