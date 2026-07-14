package com.example.pocastcloni.domain.usecase.episode

import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.EpisodeDownloadScheduler
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadEpisodeUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val downloadScheduler: EpisodeDownloadScheduler
) {
    val downloadProgressFlow: StateFlow<Map<Long, Float>> = downloadScheduler.downloadProgressFlow

    suspend operator fun invoke(episodeId: Long) {
        podcastQuery.getEpisode(episodeId)?.let { toggleDownload(it) }
    }

    private suspend fun toggleDownload(episode: Episode) {
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
