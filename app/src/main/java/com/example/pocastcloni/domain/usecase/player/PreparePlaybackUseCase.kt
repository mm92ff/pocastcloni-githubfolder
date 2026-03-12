package com.example.pocastcloni.domain.usecase.player

import androidx.core.net.toUri
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.toPodcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class PlayEpisodeResult(
    val episode: EpisodeEntity,
    val startPosition: Long,
    val podcast: Podcast?,
    val playUri: String
)

class PreparePlaybackUseCase @Inject constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(guid: String): PlayEpisodeResult {
        return withContext(dispatcherProvider.io) {
            val savedEpisode = repository.getEpisode(guid)
                ?: throw IllegalStateException("Episode not available for GUID $guid")

            // Podcast Infos laden
            val podcastEntity = repository.getPodcastEntityByUrl(savedEpisode.podcastRssUrl)
            val podcast = podcastEntity?.toPodcast()

            // 2. Entscheidungslogik: Lokal vs. Stream
            var finalUri = savedEpisode.enclosureUrl // Default: Stream

            if (savedEpisode.downloadStatus == DownloadStatus.DOWNLOADED) {
                val localPath = savedEpisode.downloadPath

                if (!localPath.isNullOrBlank()) {
                    val file = File(localPath)

                    // Strenge Prüfung: Existiert die Datei unter dem Datenbank-Pfad wirklich?
                    if (file.exists() && file.canRead()) {
                        Timber.i("Playing OFFLINE: ${file.absolutePath}")
                        finalUri = file.toUri().toString()
                    } else {
                        // DB sagt Downloaded, aber Datei fehlt -> Fallback auf Stream
                        Timber.w("File missing despite DOWNLOADED status: $localPath. Fallback to stream.")
                        repository.updateDownloadStatus(savedEpisode.guid, DownloadStatus.NOT_DOWNLOADED, null)
                    }
                } else {
                    Timber.w("Download path missing in DB despite DOWNLOADED status. Fallback to stream.")
                    repository.updateDownloadStatus(savedEpisode.guid, DownloadStatus.NOT_DOWNLOADED, null)
                }
            } else {
                Timber.d("Episode not downloaded (Status: ${savedEpisode.downloadStatus}). Streaming: $finalUri")
            }

            PlayEpisodeResult(
                episode = savedEpisode,
                startPosition = savedEpisode.playbackPositionMs,
                podcast = podcast,
                playUri = finalUri
            )
        }
    }
}
