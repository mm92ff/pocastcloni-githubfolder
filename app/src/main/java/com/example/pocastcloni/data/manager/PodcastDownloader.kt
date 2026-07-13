package com.example.pocastcloni.data.manager

import android.content.Context
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.worker.cancelAndDeleteEpisodeDownload
import com.example.pocastcloni.data.worker.queueEpisodeDownload
import com.example.pocastcloni.domain.repository.PodcastRepository
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val podcastRepositoryProvider: Provider<PodcastRepository>
) {
    private val workManager = WorkManager.getInstance(context)

    suspend fun toggleDownload(episode: EpisodeEntity) {
        if (episode.downloadStatus == DownloadStatus.DOWNLOADED ||
            episode.downloadStatus == DownloadStatus.DOWNLOADING ||
            episode.downloadStatus == DownloadStatus.QUEUED
        ) {
            deleteDownload(episode)
        } else {
            startDownload(episode)
        }
    }

    private suspend fun startDownload(episode: EpisodeEntity) {
        queueEpisodeDownload(workManager, podcastRepositoryProvider.get(), episode)
    }

    private suspend fun deleteDownload(episode: EpisodeEntity) {
        cancelAndDeleteEpisodeDownload(
            context,
            workManager,
            podcastRepositoryProvider.get(),
            episode
        )
    }
}
