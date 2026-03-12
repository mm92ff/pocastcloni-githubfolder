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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadEpisodeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val podcastRepository: PodcastRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val workManager = WorkManager.getInstance(context)

    val downloadProgressFlow: StateFlow<Map<String, Float>> =
        workManager.getWorkInfosByTagFlow(Constants.DOWNLOAD_WORKER_TAG)
            .map { workInfos ->
                workInfos.filter { it.state == WorkInfo.State.RUNNING }
                    .associate {
                        val guid = it.tags.firstOrNull { t -> t.startsWith(Constants.DOWNLOAD_WORKER_UNIQUE_PREFIX) }
                            ?.removePrefix(Constants.DOWNLOAD_WORKER_UNIQUE_PREFIX) ?: ""
                        val progress = it.progress.getFloat("progress", 0f)
                        guid to progress
                    }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyMap()
            )


    suspend operator fun invoke(guid: String) {
        podcastRepository.getEpisode(guid)?.let { toggleDownload(it) }
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
        podcastRepository.updateDownloadStatus(episode.guid, DownloadStatus.QUEUED, null)

        val fileName = "${episode.guid.hashCode()}${Constants.DOWNLOAD_FILE_EXTENSION}"
        val uniqueWorkName = "${Constants.DOWNLOAD_WORKER_UNIQUE_PREFIX}${episode.guid}"

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    Constants.DOWNLOAD_WORKER_GUID to episode.guid,
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
        workManager.cancelUniqueWork("${Constants.DOWNLOAD_WORKER_UNIQUE_PREFIX}${episode.guid}")
        episode.downloadPath?.let { File(it).delete() }
        podcastRepository.updateDownloadStatus(episode.guid, DownloadStatus.NOT_DOWNLOADED, null)
    }
}