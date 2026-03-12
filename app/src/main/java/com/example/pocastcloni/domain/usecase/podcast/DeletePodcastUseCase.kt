package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class DeletePodcastUseCase @Inject constructor(
    private val repository: PodcastRepository
    // Optional: Hier könnte man den Downloader injecten, falls er public Methoden hat
) {
    suspend operator fun invoke(podcast: Podcast) {
        // 1. Sammle alle Downloads, bevor wir die DB löschen (da wir sonst die Pfade verlieren)
        // Wir nutzen getEpisodesForSync, da es eine einfache Liste zurückgibt.
        val episodes = repository.getEpisodesForSync(podcast.rssUrl)
        val filesToDelete = episodes
            .filter { it.downloadStatus == DownloadStatus.DOWNLOADED || it.downloadStatus == DownloadStatus.DOWNLOADING }
            .mapNotNull { it.downloadPath }

        // 2. Lösche aus der Datenbank (Source of Truth)
        // Wirft Exception bei Fehler -> Files bleiben erhalten -> Sicher!
        repository.deletePodcast(podcast)

        // 3. Wenn DB erfolgreich bereinigt, lösche physische Dateien
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