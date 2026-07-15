package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.worker.DownloadPublicationGate
import com.example.pocastcloni.data.worker.DownloadPublicationRecord
import com.example.pocastcloni.data.worker.DownloadPublicationRecovery
import com.example.pocastcloni.data.worker.downloadStagingFiles
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reconciles durable episode state with app-owned download storage.
 *
 * Startup publication recovery and the later downloaded-path readability pass each run under
 * [DownloadPublicationGate]. Recovery may publish an unambiguous prepared target or compare-and-set
 * reset only the exact downloaded path it inspected. Queue and cancellation revalidation keeps its
 * separate coordinator and remains between those phases without holding either state lock.
 */
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
            recoverInterruptedPublications() +
                reconcileTransientDownloads(activeDownloadEpisodeIds, isDownloadWorkActive) +
                reconcileUnreadableDownloads()
        }

    private suspend fun recoverInterruptedPublications(): Int =
        DownloadPublicationGate.withLock {
            val downloadedRows =
                podcastDao.getEpisodeDownloadStates(listOf(DownloadStatus.DOWNLOADED))
            DownloadPublicationRecovery.from(context).recover(
                records = downloadedRows.mapNotNull { row ->
                    row.downloadPath?.let { path -> DownloadPublicationRecord(row.episodeId, path) }
                },
                resetDownload = { episodeId, expectedPath ->
                    podcastDao.compareAndSetDownloadStatusAndPath(
                        episodeId = episodeId,
                        expectedStatus = DownloadStatus.DOWNLOADED,
                        expectedPath = expectedPath,
                        status = DownloadStatus.NOT_DOWNLOADED,
                        path = null
                    ) == 1
                }
            )
        }

    private suspend fun reconcileTransientDownloads(
        activeDownloadEpisodeIds: Set<Long>,
        isDownloadWorkActive: suspend (episodeId: Long) -> Boolean
    ): Int =
        podcastDao
            .getEpisodeDownloadStates(listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
            .sumOf { row ->
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

    private suspend fun reconcileUnreadableDownloads(): Int =
        reconcileDownloadedReadability(
            loadDownloadedRows = {
                podcastDao.getEpisodeDownloadStates(listOf(DownloadStatus.DOWNLOADED))
            },
            fileIsReadable = ::isDownloadedPathReadable,
            compareAndReset = { row ->
                podcastDao.compareAndSetDownloadStatusAndPath(
                    episodeId = row.episodeId,
                    expectedStatus = DownloadStatus.DOWNLOADED,
                    expectedPath = row.downloadPath,
                    status = DownloadStatus.NOT_DOWNLOADED,
                    path = null
                ) == 1
            }
        )

    private fun isDownloadedPathReadable(path: String): Boolean =
        if (path.startsWith("content://")) {
            isContentUriReadable(path)
        } else {
            File(path).let { it.exists() && it.isFile && it.canRead() }
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
