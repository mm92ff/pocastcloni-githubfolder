package com.example.pocastcloni.ui.home.detail

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Immutable
enum class DownloadStatusUiModel {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

@Immutable
data class EpisodeUiModel(
    val guid: String,
    val podcastUrl: String,
    val title: String,
    val podcastTitle: String,
    val date: String,
    val duration: String,
    val imageUrl: String?,
    val downloadStatus: DownloadStatusUiModel,
    val downloadProgress: Float,
    val isPlayed: Boolean,
    val isFavorite: Boolean,
    val positionMs: Long,
    val description: String?,
    val podcastImageUrl: String?,
    val pubDateEpochMs: Long = 0L,
    val durationSeconds: Long = 0L,
)

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) - TimeUnit.HOURS.toMinutes(hours)
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm".format(minutes)
}

private fun formatDate(epochMs: Long): String {
    val instant = Instant.ofEpochMilli(epochMs)
    val zonedDateTime = instant.atZone(ZoneId.systemDefault())
    return dateFormatter.format(zonedDateTime)
}

private fun EpisodeEntity.toDownloadStatusUiModel(): DownloadStatusUiModel {
    return when (this.downloadStatus) {
        DownloadStatus.DOWNLOADED -> DownloadStatusUiModel.DOWNLOADED
        DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DownloadStatusUiModel.DOWNLOADING
        else -> DownloadStatusUiModel.NOT_DOWNLOADED
    }
}

fun EpisodeEntity.toEpisodeUiModelCached(
    previous: EpisodeUiModel?,
    podcastName: String,
    podcastImageUrl: String?,
    downloadProgress: Float // <--- NEUER PARAMETER
): EpisodeUiModel {
    val epochMs = this.pubDate?.time ?: 0L
    val durationMs = this.duration // In EpisodeEntity als Millisekunden gespeichert
    val downloadStatusUiModel = this.toDownloadStatusUiModel()

    // Hier nutzen wir den übergebenen Wert (vom ViewModel/Worker), statt 0f hardcodiert.
    val currentProgress = downloadProgress

    if (previous != null &&
        previous.guid == this.guid &&
        previous.podcastUrl == this.podcastRssUrl &&
        previous.title == this.title &&
        previous.description == this.description &&
        previous.podcastTitle == podcastName &&
        previous.podcastImageUrl == podcastImageUrl &&
        previous.imageUrl == podcastImageUrl &&
        previous.pubDateEpochMs == epochMs &&
        previous.durationSeconds == durationMs // Vergleiche Millisekunden
    ) {
        val dynamicUnchanged =
            previous.downloadStatus == downloadStatusUiModel &&
                    previous.downloadProgress == currentProgress && // Vergleich mit aktuellem Wert
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

    return EpisodeUiModel(
        guid = this.guid,
        podcastUrl = this.podcastRssUrl,
        title = this.title,
        podcastTitle = podcastName,
        date = formatDate(epochMs),
        duration = formatDuration(durationMs),
        imageUrl = podcastImageUrl,
        downloadStatus = downloadStatusUiModel,
        downloadProgress = currentProgress,
        isPlayed = this.isPlayed,
        isFavorite = this.isFavorite,
        positionMs = this.playbackPositionMs,
        description = this.description,
        podcastImageUrl = podcastImageUrl,
        pubDateEpochMs = epochMs,
        durationSeconds = durationMs // Wir speichern hier Millisekunden, der Name 'durationSeconds' im UI Model ist etwas irreführend, aber wir lassen ihn zur Konsistenz vorerst so.
    )
}

// Auch die einfache Helper-Funktion muss den Parameter jetzt annehmen und weiterreichen
fun EpisodeEntity.toEpisodeUiModel(
    podcastName: String,
    podcastImageUrl: String?,
    downloadProgress: Float // <--- NEUER PARAMETER
): EpisodeUiModel = toEpisodeUiModelCached(
    previous = null,
    podcastName = podcastName,
    podcastImageUrl = podcastImageUrl,
    downloadProgress = downloadProgress // <--- Weitergabe
)