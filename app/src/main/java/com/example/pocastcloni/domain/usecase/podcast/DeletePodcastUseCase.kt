package com.example.pocastcloni.domain.usecase.podcast

import android.content.Context
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DeletePodcastUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    suspend operator fun invoke(podcast: Podcast) {
        // 1. Collect all downloads before deleting from the DB (paths would be lost afterwards).
        val episodes = repository.getEpisodesForSync(podcast.rssUrl)

        // Cancel pending/active WorkManager jobs before deleting DB rows so the
        // worker cannot write to already-deleted episode rows.
        episodes
            .filter {
                it.downloadStatus == DownloadStatus.QUEUED ||
                    it.downloadStatus == DownloadStatus.DOWNLOADING
            }
            .forEach { ep ->
                workManager.cancelUniqueWork("${Constants.DOWNLOAD_WORKER_UNIQUE_PREFIX}${ep.guid}")
            }

        val pathsToDelete =
            episodes
                .filter {
                    it.downloadStatus == DownloadStatus.DOWNLOADED ||
                        it.downloadStatus == DownloadStatus.DOWNLOADING ||
                        it.downloadStatus == DownloadStatus.QUEUED
                }
                .mapNotNull { it.downloadPath }

        // 2. Delete from the database (source of truth).
        // Throws on error -> files are kept -> safe!
        repository.deletePodcast(podcast)

        // 3. If DB was cleaned up successfully, delete the physical files
        pathsToDelete.forEach { path ->
            runCatching {
                if (path.startsWith("content://")) {
                    context.contentResolver.delete(path.toUri(), null, null)
                } else {
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
                Unit
            }.onFailure { e ->
                Timber.w(e, "Failed to delete file after podcast removal: $path")
            }
        }
    }
}
