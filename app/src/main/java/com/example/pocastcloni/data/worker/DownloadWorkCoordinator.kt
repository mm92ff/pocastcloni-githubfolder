package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.util.cancelEpisodeDownloadWork
import com.example.pocastcloni.util.cancelLegacyDownloadWork
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

@Suppress("TooGenericExceptionCaught")
internal suspend fun queueEpisodeDownload(
    workManager: WorkManager,
    commands: PodcastCommandPort,
    episode: Episode
): Boolean = DownloadWorkStateCoordinator.withLock {
    val previousStatus = episode.downloadStatus
    if (previousStatus != DownloadStatus.NOT_DOWNLOADED && previousStatus != DownloadStatus.FAILED) {
        return@withLock false
    }
    val queued =
        commands.compareAndSetDownloadStatus(
            episodeId = episode.episodeId,
            expectedStatuses = listOf(previousStatus),
            status = DownloadStatus.QUEUED,
            path = null
        )
    if (queued) {
        try {
            workManager.cancelLegacyDownloadWork(episode.guid)
            val workId = workManager.enqueueDownloadWork(episode.episodeId)
            DownloadWorkStateCoordinator.recordEnqueuedAttempt(episode.episodeId, workId)
        } catch (error: Throwable) {
            rollbackQueuedStatus(commands, episode, previousStatus)
            throw error
        }
    }
    queued
}

@Suppress("TooGenericExceptionCaught")
private suspend fun rollbackQueuedStatus(
    commands: PodcastCommandPort,
    episode: Episode,
    previousStatus: DownloadStatus
) {
    withContext(NonCancellable) {
        commands.compareAndSetDownloadStatus(
            episodeId = episode.episodeId,
            expectedStatuses = listOf(DownloadStatus.QUEUED),
            status = previousStatus,
            path = episode.downloadPath
        )
    }
}

internal suspend fun cancelAndDeleteEpisodeDownload(
    context: Context,
    workManager: WorkManager,
    query: PodcastQueryPort,
    commands: PodcastCommandPort,
    episode: Episode
) {
    DownloadWorkStateCoordinator.withLock {
        workManager.cancelEpisodeDownloadWork(episode.episodeId, episode.guid)
        DownloadWorkStateCoordinator.clearAttempt(episode.episodeId)
        withContext(NonCancellable) {
            val current = query.getEpisode(episode.episodeId)
            if (current != null) {
                val reset =
                    commands.compareAndSetDownloadStatusAndPath(
                        episodeId = current.episodeId,
                        expectedStatus = current.downloadStatus,
                        expectedPath = current.downloadPath,
                        status = DownloadStatus.NOT_DOWNLOADED,
                        path = null
                    )
                if (reset) current.downloadPath?.let { deleteDownloadTarget(context, it) }
            }
            downloadStagingFiles(context.filesDir, episode.episodeId).delete()
        }
    }
}

internal fun deleteDownloadTarget(
    context: Context,
    path: String
) {
    runCatching {
        if (path.startsWith("content://")) {
            context.contentResolver.delete(path.toUri(), null, null)
        } else {
            File(path).delete()
        }
    }
}
