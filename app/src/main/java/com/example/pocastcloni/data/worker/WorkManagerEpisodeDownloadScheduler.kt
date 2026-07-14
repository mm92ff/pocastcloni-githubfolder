package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.EpisodeDownloadScheduler
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
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
class WorkManagerEpisodeDownloadScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val podcastQuery: PodcastQueryPort,
    private val podcastCommands: PodcastCommandPort,
    @ApplicationScope applicationScope: CoroutineScope
) : EpisodeDownloadScheduler {
    private val workManager = WorkManager.getInstance(context)

    override val downloadProgressFlow: StateFlow<Map<Long, Float>> =
        workManager.getWorkInfosByTagFlow(Constants.DOWNLOAD_WORKER_TAG)
            .map { workInfos ->
                workInfos.filter { it.state == WorkInfo.State.RUNNING }
                    .associate { workInfo ->
                        val episodeId =
                            workInfo.tags.firstNotNullOfOrNull(::episodeIdFromDownloadWorkTag) ?: 0L
                        episodeId to workInfo.progress.getFloat(PROGRESS_KEY, 0f)
                    }
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(DOWNLOAD_PROGRESS_STOP_TIMEOUT_MS),
                initialValue = emptyMap()
            )

    override suspend fun queue(episode: Episode) {
        queueEpisodeDownload(workManager, podcastCommands, episode)
    }

    override suspend fun cancelAndDelete(episode: Episode) {
        cancelAndDeleteEpisodeDownload(
            context = context,
            workManager = workManager,
            query = podcastQuery,
            commands = podcastCommands,
            episode = episode
        )
    }

    private companion object {
        const val DOWNLOAD_PROGRESS_STOP_TIMEOUT_MS = 5_000L
        const val PROGRESS_KEY = "progress"
    }
}
