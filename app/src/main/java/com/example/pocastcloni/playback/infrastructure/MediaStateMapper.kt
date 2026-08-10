package com.example.pocastcloni.playback.infrastructure

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.playback.api.PlayerUiState
import com.example.pocastcloni.util.Constants
import javax.inject.Inject

class MediaStateMapper
@Inject
constructor() {
    fun mapToMediaItem(
        episode: Episode,
        podcast: Podcast?,
        playUri: String,
        artworkData: ByteArray? = null
    ): MediaItem {
        val metadata =
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(podcast?.title ?: Constants.EMPTY_STRING)
        artworkData?.let { bytes ->
            metadata.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(episode.episodeId.toString())
            .setUri(playUri.toUri())
            .setMediaMetadata(
                metadata.build()
            )
            .build()
    }

    fun mapToUiState(
        controller: MediaController?,
        episode: Episode?,
        podcast: Podcast?,
        currentState: PlayerUiState
    ): PlayerUiState {
        if (controller == null) return currentState

        val meta = controller.mediaMetadata
        val currentEpisodeId = controller.currentMediaItem?.mediaId?.toLongOrNull()

        val title = episode?.title ?: meta.title?.toString() ?: Constants.EMPTY_STRING
        val subtitle = podcast?.title ?: meta.artist?.toString() ?: Constants.EMPTY_STRING
        val cover =
            podcast?.imageUrl?.takeIf { it.isNotBlank() }
                ?: meta.artworkUri?.toString()
                ?: currentState.coverUrl

        return currentState.copy(
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            currentEpisodeId = currentEpisodeId,
            currentEpisodeTitle = title,
            currentEpisodeSubtitle = subtitle,
            coverUrl = cover,
            coverFileName = podcast?.coverFileName,
            coverRevision = podcast?.coverRevision ?: 0L,
            currentPodcastUrl = episode?.podcastRssUrl ?: currentState.currentPodcastUrl
        )
    }

    /**
     * Maps technical ExoPlayer errors to user-readable UI text.
     */
    fun mapError(error: PlaybackException?): Int? {
        if (error == null) return null

        return when (error.errorCode) {
            // Network errors
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ->
                R.string.error_no_internet

            // File errors (e.g. download deleted)
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                R.string.error_file_deleted

            // Decoder / format errors
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
            ->
                R.string.error_decoder

            // Fallback for everything else
            else -> R.string.error_generic
        }
    }
}
