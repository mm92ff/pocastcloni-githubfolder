package com.example.pocastcloni.ui.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.pocastcloni.R
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.Constants
import javax.inject.Inject

class MediaStateMapper
@Inject
constructor() {
    fun mapToMediaItem(
        episode: EpisodeEntity,
        podcast: PodcastEntity?,
        playUri: String
    ): MediaItem {
        val artworkUri = podcast?.imageUrl?.takeIf { it.isNotBlank() }?.toUri()
        return MediaItem.Builder()
            .setMediaId(episode.guid)
            .setUri(playUri.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(podcast?.title ?: Constants.EMPTY_STRING)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()
    }

    fun mapToUiState(
        controller: MediaController?,
        episode: EpisodeEntity?,
        podcast: PodcastEntity?,
        currentState: PlayerUiState
    ): PlayerUiState {
        if (controller == null) return currentState

        val meta = controller.mediaMetadata
        val currentGuid = controller.currentMediaItem?.mediaId

        val title = episode?.title ?: meta.title?.toString() ?: Constants.EMPTY_STRING
        val subtitle = podcast?.title ?: meta.artist?.toString() ?: Constants.EMPTY_STRING
        val cover =
            podcast?.imageUrl?.takeIf { it.isNotBlank() }
                ?: meta.artworkUri?.toString()
                ?: currentState.coverUrl

        return currentState.copy(
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            currentEpisodeGuid = currentGuid,
            currentEpisodeTitle = title,
            currentEpisodeSubtitle = subtitle,
            coverUrl = cover,
            currentPodcastUrl = episode?.podcastRssUrl ?: currentState.currentPodcastUrl
        )
    }

    /**
     * Maps technical ExoPlayer errors to user-readable UI text.
     */
    fun mapError(error: PlaybackException?): UiText? {
        if (error == null) return null

        return when (error.errorCode) {
            // Network errors
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            ->
                UiText.StringResource(R.string.error_no_internet)

            // File errors (e.g. download deleted)
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                UiText.StringResource(R.string.error_file_deleted)

            // Decoder / format errors
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
            ->
                UiText.StringResource(R.string.error_decoder)

            // Fallback for everything else
            else -> UiText.StringResource(R.string.error_generic)
        }
    }
}
