package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.EpisodeWithPodcastLite
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- EXISTING MAPPERS (leave unchanged) ---

fun PodcastEntity.toDomain(): Podcast {
    return Podcast(
        rssUrl = this.rssUrl,
        title = this.title,
        description = this.description,
        imageUrl = this.imageUrl,
        lastRefreshed = this.lastRefreshed ?: Date(0),
        autoDownloadEnabled = this.autoDownloadEnabled,
        sortOrder = this.sortOrder,
        hasNewEpisodes = this.hasNewEpisodes,
        lastModifiedHeader = this.lastModifiedHeader,
        eTagHeader = this.eTagHeader,
        isLatestEpisodePlayed = this.isLatestEpisodePlayed,
        latestEpisodeDate = null,
        allowInsecureHttp = this.allowInsecureHttp
    )
}

fun Podcast.toEntity(): PodcastEntity {
    return PodcastEntity(
        rssUrl = this.rssUrl,
        title = this.title,
        description = this.description,
        imageUrl = this.imageUrl,
        lastRefreshed = this.lastRefreshed,
        autoDownloadEnabled = this.autoDownloadEnabled,
        sortOrder = this.sortOrder,
        allowInsecureHttp = this.allowInsecureHttp,
        hasNewEpisodes = this.hasNewEpisodes,
        lastModifiedHeader = this.lastModifiedHeader,
        eTagHeader = this.eTagHeader
    )
}

fun EpisodeWithPodcastLite.toPodcastDomain(): Podcast? {
    val lite = this.podcast ?: return null
    return Podcast(
        rssUrl = lite.rssUrl,
        title = lite.title,
        imageUrl = lite.imageUrl,
        description = "",
        lastRefreshed = Date(System.currentTimeMillis()),
        autoDownloadEnabled = false,
        sortOrder = 0,
        hasNewEpisodes = false,
        isLatestEpisodePlayed = null,
        lastModifiedHeader = null,
        eTagHeader = null,
        latestEpisodeDate = null
    )
}

fun PodcastEntity.toBackupPodcast(): BackupPodcast {
    return BackupPodcast(
        url = this.rssUrl,
        sortOrder = this.sortOrder,
        allowInsecureHttp = this.allowInsecureHttp,
        title = this.title,
        description = this.description,
        imageUrl = this.imageUrl,
        lastModifiedHeader = this.lastModifiedHeader,
        eTagHeader = this.eTagHeader
    )
}

fun EpisodeEntity.toBackupFavorite(): BackupFavorite {
    return BackupFavorite(
        podcastUrl = this.podcastRssUrl,
        episodeGuid = this.guid,
        timestamp = this.favoriteAddedAt ?: this.favoriteTimestamp ?: System.currentTimeMillis()
    )
}

// --- NEW LOGIC FOR EPISODES & DATE PROTECTION ---

/**
 * Converts an RssItem (network) into an EpisodeEntity (database).
 * Includes protection against corrupt data (e.g. year 3000).
 */
fun RssItem.toEpisodeEntity(podcastUrl: String): EpisodeEntity {
    // 1. Parse date
    val rawDate = parseRssDate(this.pubDate)

    // 2. Sanitize date (protection against future dates)
    val cleanDate = sanitizeDate(rawDate)

    return EpisodeEntity(
        guid = this.guid ?: this.link ?: this.title ?: System.currentTimeMillis().toString(), // Fallback for GUID
        podcastRssUrl = podcastUrl,
        title = this.title ?: "No Title",
        description = this.description ?: "",
        pubDate = cleanDate,
        link = this.link ?: "",
        enclosureUrl = this.enclosure?.url ?: "",
        type = this.enclosure?.type ?: "audio/mpeg",
        fileSize = this.enclosure?.length ?: 0L,
        duration = parseDuration(this.itunesDuration),
        // Defaults for new episodes
        isPlayed = false,
        playbackPositionMs = 0,
        downloadStatus = DownloadStatus.NOT_DOWNLOADED,
        isFavorite = false
    )
}

/**
 * Checks whether a date is valid.
 * If the date is > (now + 7 days), it is set to "now".
 */
private fun sanitizeDate(date: Date?): Date {
    if (date == null) return Date() // Fallback to now if no date is present at all

    val now = System.currentTimeMillis()
    val threshold = now + Constants.Validation.MAX_FUTURE_DATE_THRESHOLD_MS

    return if (date.time > threshold) {
        // ERROR CASE: date is too far in the future (e.g. year 3000)
        // We correct it to "now" so that sorting is consistent.
        Date(now)
    } else {
        // Normal case
        date
    }
}

/**
 * Tries to parse the date using various formats.
 */
private fun parseRssDate(dateString: String?): Date? {
    if (dateString.isNullOrEmpty()) return null

    for (format in Constants.Parsing.DATE_FORMATS) {
        try {
            // Locale.US is important for RSS (e.g. "Mon, 21 Jan...")
            val parser = SimpleDateFormat(format, Locale.US)
            return parser.parse(dateString)
        } catch (e: Exception) {
            // Format did not match, try the next one
        }
    }
    return null
}

/**
 * Helper function to convert iTunes duration (e.g. "01:20:30" or "4830") into milliseconds.
 */
private fun parseDuration(durationStr: String?): Long {
    if (durationStr.isNullOrEmpty()) return 0L

    return try {
        if (durationStr.contains(":")) {
            val parts = durationStr.split(":")
            var seconds = 0L
            for (part in parts) {
                seconds = seconds * 60 + part.toLong()
            }
            seconds * 1000
        } else {
            // Could be seconds as a raw value
            durationStr.toLong() * 1000
        }
    } catch (e: Exception) {
        0L
    }
}
