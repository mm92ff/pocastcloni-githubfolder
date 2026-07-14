package com.example.pocastcloni.data.manager

import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.EpisodeDownloadScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Responsible for managing downloads via WorkManager.
 * Encapsulates Android-specific WorkManager logic.
 */
@Singleton
class PodcastDownloader
@Inject
constructor(
    private val downloadScheduler: EpisodeDownloadScheduler
) {
    suspend fun toggleDownload(episode: Episode) {
        if (episode.downloadStatus == DownloadStatus.DOWNLOADED ||
            episode.downloadStatus == DownloadStatus.DOWNLOADING ||
            episode.downloadStatus == DownloadStatus.QUEUED
        ) {
            deleteDownload(episode)
        } else {
            startDownload(episode)
        }
    }

    private suspend fun startDownload(episode: Episode) {
        downloadScheduler.queue(episode)
    }

    private suspend fun deleteDownload(episode: Episode) {
        downloadScheduler.cancelAndDelete(episode)
    }
}
