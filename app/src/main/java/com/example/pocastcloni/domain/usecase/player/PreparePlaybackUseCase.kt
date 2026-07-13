package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.model.toPodcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.util.requireApprovedPodcastResource
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
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

class PreparePlaybackUseCase
@Inject
constructor(
    private val repository: PodcastRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val localNetworkAccessRegistry: LocalNetworkAccessRegistry
) {
    suspend operator fun invoke(episodeId: Long): PlayEpisodeResult {
        return withContext(dispatcherProvider.io) {
            val savedEpisode =
                repository.getEpisode(episodeId)
                    ?: throw IllegalStateException("Episode not available for ID $episodeId")

            // Load podcast info
            val podcastEntity = repository.getPodcastEntityByUrl(savedEpisode.podcastRssUrl)
            val podcast = podcastEntity?.toPodcast()

            // 2. Decision logic: local file vs. stream
            var finalUri = savedEpisode.enclosureUrl // Default: stream

            if (savedEpisode.downloadStatus == DownloadStatus.DOWNLOADED) {
                val localPath = savedEpisode.downloadPath

                if (!localPath.isNullOrBlank()) {
                    if (localPath.startsWith("content://")) {
                        // MediaStore URI (API 29+ public Downloads) — use directly
                        Timber.i("Playing OFFLINE (MediaStore): $localPath")
                        finalUri = localPath
                    } else {
                        val file = File(localPath)

                        // Strict check: does the file actually exist at the path stored in the database?
                        if (file.exists() && file.canRead()) {
                            Timber.i("Playing OFFLINE: ${file.absolutePath}")
                            finalUri = file.toURI().toString()
                        } else {
                            // DB says Downloaded, but file is missing -> fall back to stream
                            Timber.w("File missing despite DOWNLOADED status: $localPath. Fallback to stream.")
                            repository.updateDownloadStatus(savedEpisode.episodeId, DownloadStatus.NOT_DOWNLOADED, null)
                        }
                    }
                } else {
                    Timber.w("Download path missing in DB despite DOWNLOADED status. Fallback to stream.")
                    repository.updateDownloadStatus(savedEpisode.episodeId, DownloadStatus.NOT_DOWNLOADED, null)
                }
            } else {
                Timber.d("Episode not downloaded (Status: ${savedEpisode.downloadStatus}). Streaming: $finalUri")
            }

            if (finalUri == savedEpisode.enclosureUrl) {
                requireApprovedPodcastResource(
                    feedUrl = savedEpisode.podcastRssUrl,
                    resourceUrl = finalUri,
                    allowInsecureHttp = podcastEntity?.allowInsecureHttp == true,
                    allowLocalNetwork = podcastEntity?.allowLocalNetwork == true
                )
                if (podcastEntity?.allowLocalNetwork == true) {
                    localNetworkAccessRegistry.approveFeed(savedEpisode.podcastRssUrl)
                }
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
