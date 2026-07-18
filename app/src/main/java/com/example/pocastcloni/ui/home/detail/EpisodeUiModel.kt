package com.example.pocastcloni.ui.home.detail

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.domain.model.EpisodePresentation
import com.example.pocastcloni.domain.model.EpisodeWithPodcastInfo

@Immutable
enum class DownloadStatusUiModel {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

@Immutable
data class EpisodeUiModel(
    val episodeId: Long,
    val guid: String,
    val podcastUrl: String,
    val title: String,
    val podcastTitle: String,
    val imageUrl: String?,
    val downloadStatus: DownloadStatusUiModel,
    val downloadProgress: Float,
    val isPlayed: Boolean,
    val isFavorite: Boolean,
    val positionMs: Long,
    val description: String?,
    val podcastImageUrl: String?,
    val pubDateEpochMs: Long?,
    val durationMs: Long
)

private fun EpisodePresentation.toDownloadStatusUiModel(): DownloadStatusUiModel {
    return when (this.downloadStatus) {
        DownloadStatus.DOWNLOADED -> DownloadStatusUiModel.DOWNLOADED
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DownloadStatusUiModel.DOWNLOADING
        else -> DownloadStatusUiModel.NOT_DOWNLOADED
    }
}

fun EpisodePresentation.toEpisodeUiModelCached(
    previous: EpisodeUiModel?,
    podcastName: String,
    podcastImageUrl: String?,
    downloadProgress: Float
): EpisodeUiModel {
    val epochMs = this.pubDateMs
    val durationMs = this.durationMs
    val downloadStatusUiModel = this.toDownloadStatusUiModel()

    val currentProgress = downloadProgress

    if (previous != null) {
        val identityUnchanged =
            previous.episodeId == this.episodeId &&
                previous.guid == this.guid &&
                previous.podcastUrl == this.podcastRssUrl
        val contentUnchanged =
            previous.title == this.title &&
                previous.description == this.description &&
                previous.podcastTitle == podcastName
        val mediaUnchanged =
            previous.podcastImageUrl == podcastImageUrl &&
                previous.imageUrl == podcastImageUrl &&
                previous.pubDateEpochMs == epochMs &&
                previous.durationMs == durationMs

        if (identityUnchanged && contentUnchanged && mediaUnchanged) {
            val dynamicUnchanged =
                previous.downloadStatus == downloadStatusUiModel &&
                    previous.downloadProgress == currentProgress &&
                    previous.isPlayed == this.isPlayed &&
                    previous.isFavorite == this.isFavorite &&
                    previous.positionMs == this.playbackPositionMs

            if (dynamicUnchanged) return previous

            return previous.copy(
                downloadStatus = downloadStatusUiModel,
                downloadProgress = currentProgress,
                isPlayed = this.isPlayed,
                isFavorite = this.isFavorite,
                positionMs = this.playbackPositionMs
            )
        }
    }

    return createEpisodeUiModel(
        podcastName = podcastName,
        podcastImageUrl = podcastImageUrl,
        downloadProgress = currentProgress,
        downloadStatus = downloadStatusUiModel
    )
}

private fun EpisodePresentation.createEpisodeUiModel(
    podcastName: String,
    podcastImageUrl: String?,
    downloadProgress: Float,
    downloadStatus: DownloadStatusUiModel
): EpisodeUiModel {
    return EpisodeUiModel(
        episodeId = this.episodeId,
        guid = this.guid,
        podcastUrl = this.podcastRssUrl,
        title = this.title,
        podcastTitle = podcastName,
        imageUrl = podcastImageUrl,
        downloadStatus = downloadStatus,
        downloadProgress = downloadProgress,
        isPlayed = this.isPlayed,
        isFavorite = this.isFavorite,
        positionMs = this.playbackPositionMs,
        description = this.description,
        podcastImageUrl = podcastImageUrl,
        pubDateEpochMs = pubDateMs,
        durationMs = durationMs
    )
}

fun EpisodePresentation.toEpisodeUiModel(
    podcastName: String,
    podcastImageUrl: String?,
    downloadProgress: Float
): EpisodeUiModel =
    toEpisodeUiModelCached(
        previous = null,
        podcastName = podcastName,
        podcastImageUrl = podcastImageUrl,
        downloadProgress = downloadProgress
    )

fun EpisodeWithPodcastInfo.toEpisodeUiModel(
    downloadProgress: Float,
    previous: EpisodeUiModel? = null
): EpisodeUiModel =
    episode.toEpisodeUiModelCached(
        previous = previous,
        podcastName = podcast?.title ?: "",
        podcastImageUrl = podcast?.imageUrl,
        downloadProgress = downloadProgress
    )
