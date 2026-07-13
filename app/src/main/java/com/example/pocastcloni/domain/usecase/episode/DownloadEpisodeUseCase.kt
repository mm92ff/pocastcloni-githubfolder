package com.example.pocastcloni.domain.usecase.episode

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.worker.cancelAndDeleteEpisodeDownload
import com.example.pocastcloni.data.worker.queueEpisodeDownload
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.episodeIdFromDownloadWorkTag
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadEpisodeUseCase
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val podcastRepository: PodcastRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val workManager = WorkManager.getInstance(context)

    val downloadProgressFlow: StateFlow<Map<Long, Float>> =
        workManager.getWorkInfosByTagFlow(Constants.DOWNLOAD_WORKER_TAG)
            .map { workInfos ->
                workInfos.filter { it.state == WorkInfo.State.RUNNING }
                    .associate {
                        val episodeId =
                            it.tags.firstNotNullOfOrNull(::episodeIdFromDownloadWorkTag) ?: 0L
                        val progress = it.progress.getFloat("progress", 0f)
                        episodeId to progress
                    }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyMap()
            )

    suspend operator fun invoke(episodeId: Long) {
        podcastRepository.getEpisode(episodeId)?.let { toggleDownload(it) }
    }

    private suspend fun toggleDownload(episode: EpisodeEntity) {
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
        queueEpisodeDownload(workManager, podcastRepository, episode)
    }

    private suspend fun deleteDownload(episode: EpisodeEntity) {
        cancelAndDeleteEpisodeDownload(context, workManager, podcastRepository, episode)
    }
}
