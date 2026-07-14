package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.PodcastRemovalGateway
import com.example.pocastcloni.util.cancelEpisodeDownloadWork
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPodcastRemovalGateway
@Inject
constructor(
    @ApplicationContext private val context: Context
) : PodcastRemovalGateway {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun cancelActiveDownloads(episodes: List<Episode>) {
        episodes.filter { episode ->
            episode.downloadStatus == DownloadStatus.QUEUED ||
                episode.downloadStatus == DownloadStatus.DOWNLOADING
        }.forEach { episode ->
            workManager.cancelEpisodeDownloadWork(episode.episodeId, episode.guid)
        }
    }

    override suspend fun deleteDownloadedFiles(episodes: List<Episode>) {
        episodes.filter { episode ->
            episode.downloadStatus == DownloadStatus.DOWNLOADED ||
                episode.downloadStatus == DownloadStatus.DOWNLOADING ||
                episode.downloadStatus == DownloadStatus.QUEUED
        }.mapNotNull(Episode::downloadPath)
            .forEach { path ->
                runCatching {
                    if (path.startsWith("content://")) {
                        context.contentResolver.delete(path.toUri(), null, null)
                    } else {
                        File(path).delete()
                    }
                }.onFailure { error ->
                    Timber.w(error, "Failed to delete file after podcast removal: %s", path)
                }
            }
    }
}
