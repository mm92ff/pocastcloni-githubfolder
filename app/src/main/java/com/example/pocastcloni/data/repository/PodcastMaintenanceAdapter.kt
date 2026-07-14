package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.worker.downloadStagingFiles
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import kotlinx.coroutines.withContext
import java.io.File

internal class PodcastMaintenanceAdapter(
    private val podcastDao: PodcastDao,
    private val dispatcherProvider: DispatcherProvider,
    private val context: Context
) : LibraryMaintenancePort {
    override suspend fun reconcileEpisodeStorage(
        activeDownloadEpisodeIds: Set<Long>,
        isDownloadWorkActive: suspend (episodeId: Long) -> Boolean
    ): Int =
        withContext(dispatcherProvider.io) {
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

    private fun isContentUriReadable(uriString: String): Boolean =
        runCatching {
            context.contentResolver.openFileDescriptor(uriString.toUri(), "r")?.use { true } ?: false
        }.getOrDefault(false)

    override suspend fun pruneLibrary(limitPerPodcast: Int) {
        withContext(dispatcherProvider.io) {
            podcastDao.getAllPodcastUrls().forEach { url ->
                val episodesToPrune =
                    selectEpisodesToPrune(
                        episodes = podcastDao.getEpisodesForPodcastSync(url),
                        keepCount = limitPerPodcast
                    )
                if (episodesToPrune.isNotEmpty()) {
                    val pathsToDelete =
                        episodesToPrune
                            .filter { it.downloadStatus == DownloadStatus.DOWNLOADED }
                            .mapNotNull { it.downloadPath }

                    podcastDao.deleteEpisodes(episodesToPrune)
                    pathsToDelete.forEach { path ->
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
        }
    }

    override suspend fun resetDatabase() {
        withContext(dispatcherProvider.io) { podcastDao.deleteAllPodcasts() }
    }
}
