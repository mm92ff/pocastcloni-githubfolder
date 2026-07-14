package com.example.pocastcloni.domain.usecase.player

import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.DownloadStatus
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.LocalNetworkApprovalPort
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.util.requireApprovedPodcastResource
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class PlayEpisodeResult(
    val episode: Episode,
    val startPosition: Long,
    val podcast: Podcast?,
    val playUri: String
)

class PlaybackUnavailableException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

class PreparePlaybackUseCase
@Inject
constructor(
    private val podcastQuery: PodcastQueryPort,
    private val podcastCommands: PodcastCommandPort,
    private val dispatcherProvider: DispatcherProvider,
    private val localNetworkApproval: LocalNetworkApprovalPort
) {
    suspend operator fun invoke(episodeId: Long): PlayEpisodeResult {
        return withContext(dispatcherProvider.io) {
            val savedEpisode =
                podcastQuery.getEpisode(episodeId)
                    ?: throw IllegalStateException("Episode not available for ID $episodeId")

            // Load podcast info
            val podcast = podcastQuery.getPodcast(savedEpisode.podcastRssUrl)

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
                            podcastCommands.updateDownloadStatus(
                                savedEpisode.episodeId,
                                DownloadStatus.NOT_DOWNLOADED,
                                null
                            )
                        }
                    }
                } else {
                    Timber.w("Download path missing in DB despite DOWNLOADED status. Fallback to stream.")
                    podcastCommands.updateDownloadStatus(
                        savedEpisode.episodeId,
                        DownloadStatus.NOT_DOWNLOADED,
                        null
                    )
                }
            } else {
                Timber.d("Episode not downloaded (Status: ${savedEpisode.downloadStatus}). Streaming: $finalUri")
            }

            if (finalUri == savedEpisode.enclosureUrl) {
                validateStreamingUri(savedEpisode, podcast, finalUri)
                if (podcast?.allowLocalNetwork == true) {
                    localNetworkApproval.approveFeed(savedEpisode.podcastRssUrl)
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

    private fun validateStreamingUri(
        episode: Episode,
        podcast: Podcast?,
        streamingUri: String
    ) {
        if (streamingUri.isBlank()) {
            throw PlaybackUnavailableException("Episode has no playable media URL")
        }
        try {
            requireApprovedPodcastResource(
                feedUrl = episode.podcastRssUrl,
                resourceUrl = streamingUri,
                allowInsecureHttp = podcast?.allowInsecureHttp == true,
                allowLocalNetwork = podcast?.allowLocalNetwork == true
            )
        } catch (error: IllegalArgumentException) {
            throw PlaybackUnavailableException("Episode network access is not approved", error)
        }
    }
}
