package com.example.pocastcloni.data.manager

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.worker.DownloadWorker
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.cancelEpisodeDownloadWork
import com.example.pocastcloni.util.cancelLegacyDownloadWork
import com.example.pocastcloni.util.downloadWorkName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Responsible for managing downloads via WorkManager.
 * Encapsulates Android-specific WorkManager logic.
 */
@Singleton
class PodcastDownloader
@Inject
constructor(
    @ApplicationContext context: Context,
    private val podcastRepositoryProvider: Provider<PodcastRepository>
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun toggleDownload(episode: EpisodeEntity) {
        if (episode.downloadStatus == DownloadStatus.DOWNLOADED ||
            episode.downloadStatus == DownloadStatus.DOWNLOADING
        ) {
            deleteDownload(episode)
        } else {
            startDownload(episode)
        }
    }

    private suspend fun startDownload(episode: EpisodeEntity) {
        val podcastTitle = podcastRepositoryProvider.get().getPodcast(episode.podcastRssUrl)
            ?.title?.ifBlank { null } ?: "Unknown_Podcast"
        val episodeTitle = episode.title.ifBlank { null } ?: "Unknown_Episode"
        val fileName = "${podcastTitle}_${episodeTitle}${Constants.DOWNLOAD_FILE_EXTENSION}"

        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        Constants.DOWNLOAD_WORKER_EPISODE_ID to episode.episodeId,
                        Constants.DOWNLOAD_WORKER_URL to episode.enclosureUrl,
                        Constants.DOWNLOAD_WORKER_FILENAME to fileName
                    )
                )
                .build()

        workManager.cancelLegacyDownloadWork(episode.guid)
        workManager.enqueueUniqueWork(
            downloadWorkName(episode.episodeId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun deleteDownload(episode: EpisodeEntity) {
        workManager.cancelEpisodeDownloadWork(episode.episodeId, episode.guid)

        episode.downloadPath?.let { File(it).delete() }

        podcastRepositoryProvider.get().updateDownloadStatus(
            episode.episodeId,
            DownloadStatus.NOT_DOWNLOADED,
            null
        )
    }
}
