package com.example.pocastcloni.domain.usecase.episode

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.worker.DownloadWorker
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.cancelEpisodeDownloadWork
import com.example.pocastcloni.util.cancelLegacyDownloadWork
import com.example.pocastcloni.util.downloadWorkName
import com.example.pocastcloni.util.episodeIdFromDownloadWorkTag
import com.example.pocastcloni.util.requireApprovedPodcastResource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.core.net.toUri
import java.io.File
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
        val podcast = podcastRepository.getPodcastEntityByUrl(episode.podcastRssUrl)
        requireApprovedPodcastResource(
            feedUrl = episode.podcastRssUrl,
            resourceUrl = episode.enclosureUrl,
            allowInsecureHttp = podcast?.allowInsecureHttp == true,
            allowLocalNetwork = podcast?.allowLocalNetwork == true
        )
        podcastRepository.updateDownloadStatus(episode.episodeId, DownloadStatus.QUEUED, null)

        val podcastTitle = podcastRepository.getPodcast(episode.podcastRssUrl)
            ?.title?.ifBlank { null } ?: "Unknown_Podcast"
        val episodeTitle = episode.title.ifBlank { null } ?: "Unknown_Episode"
        val fileName = "${podcastTitle}_${episodeTitle}${Constants.DOWNLOAD_FILE_EXTENSION}"
        val uniqueWorkName = downloadWorkName(episode.episodeId)

        workManager.cancelLegacyDownloadWork(episode.guid)

        val request =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        Constants.DOWNLOAD_WORKER_EPISODE_ID to episode.episodeId,
                        Constants.DOWNLOAD_WORKER_URL to episode.enclosureUrl,
                        Constants.DOWNLOAD_WORKER_FILENAME to fileName
                    )
                )
                .addTag(Constants.DOWNLOAD_WORKER_TAG)
                .addTag(uniqueWorkName) // Tagging with unique name to easily find guid
                .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun deleteDownload(episode: EpisodeEntity) {
        workManager.cancelEpisodeDownloadWork(episode.episodeId, episode.guid)
        episode.downloadPath?.let { path ->
            runCatching {
                if (path.startsWith("content://")) {
                    context.contentResolver.delete(path.toUri(), null, null)
                } else {
                    File(path).delete()
                }
            }
        }
        podcastRepository.updateDownloadStatus(episode.episodeId, DownloadStatus.NOT_DOWNLOADED, null)
    }
}
