package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DeletePodcastUseCase
@Inject
constructor(
    private val repository: PodcastRepository
    // Optional: the downloader could be injected here if it exposes public methods
) {
    suspend operator fun invoke(podcast: Podcast) {
        // 1. Collect all downloads before deleting from the DB (paths would be lost afterwards).
        // getEpisodesForSync returns a plain list, which is convenient here.
        val episodes = repository.getEpisodesForSync(podcast.rssUrl)
        val filesToDelete =
            episodes
                .filter { it.downloadStatus == DownloadStatus.DOWNLOADED || it.downloadStatus == DownloadStatus.DOWNLOADING }
                .mapNotNull { it.downloadPath }

        // 2. Delete from the database (source of truth).
        // Throws on error -> files are kept -> safe!
        repository.deletePodcast(podcast)

        // 3. If DB was cleaned up successfully, delete the physical files
        filesToDelete.forEach { path ->
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }.onFailure { e ->
                Timber.w(e, "Failed to delete file after podcast removal: $path")
            }
        }
    }
}
