package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.BackupFavorite
import com.example.pocastcloni.data.local.BackupEpisodeState
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

private const val SECONDS_PER_MINUTE = 60
private const val MILLIS_PER_SECOND = 1000

// --- EXISTING MAPPERS (leave unchanged) ---

fun PodcastEntity.toDomain(): Podcast {
    return Podcast(
        rssUrl = this.rssUrl,
        title = this.title,
        description = this.description,
        imageUrl = this.imageUrl,
        lastRefreshed = this.lastRefreshed,
        autoDownloadEnabled = this.autoDownloadEnabled,
        sortOrder = this.sortOrder,
        hasNewEpisodes = this.hasNewEpisodes,
        lastModifiedHeader = this.lastModifiedHeader,
        eTagHeader = this.eTagHeader,
        isLatestEpisodePlayed = this.isLatestEpisodePlayed,
        latestEpisodeDate = null,
        allowInsecureHttp = this.allowInsecureHttp,
        allowLocalNetwork = this.allowLocalNetwork
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
        allowLocalNetwork = this.allowLocalNetwork,
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
        autoDownloadEnabled = this.autoDownloadEnabled,
        allowInsecureHttp = this.allowInsecureHttp,
        allowLocalNetwork = this.allowLocalNetwork,
        title = this.title,
        description = this.description,
        imageUrl = this.imageUrl
    )
}

fun EpisodeEntity.toBackupFavorite(): BackupFavorite {
    return BackupFavorite(
        podcastUrl = this.podcastRssUrl,
        episodeGuid = this.guid,
        timestamp = this.favoriteAddedAt ?: this.favoriteTimestamp ?: 0L
    )
}

fun List<EpisodeEntity>.toBackupEpisodeStates(): List<BackupEpisodeState> {
    var nextFavoriteOrder = 0L
    return map { episode ->
        episode.toBackupEpisodeState(
            favoriteOrder = if (episode.isFavorite) nextFavoriteOrder++ else null
        )
    }
}

fun EpisodeEntity.toBackupEpisodeState(favoriteOrder: Long?): BackupEpisodeState =
    BackupEpisodeState(
        podcastUrl = podcastRssUrl,
        episodeGuid = guid,
        title = title,
        description = description,
        publishedAt = pubDate?.time,
        duration = duration,
        isFavorite = isFavorite,
        favoriteAddedAt = if (isFavorite) favoriteAddedAt ?: favoriteTimestamp ?: 0L else null,
        favoriteOrder = favoriteOrder,
        isPlayed = isPlayed,
        datePlayed = datePlayed?.time,
        playbackPositionMs = playbackPositionMs
    )

// --- NEW LOGIC FOR EPISODES & DATE PROTECTION ---

/**
 * Converts an RssItem (network) into an EpisodeEntity (database).
 * Includes protection against corrupt data (e.g. year 3000).
 */
fun RssItem.toEpisodeEntity(podcastUrl: String): EpisodeEntity {
    val cleanDate = sanitizeDate(this.pubDate)

    return EpisodeEntity(
        guid = stableEpisodeGuid(podcastUrl),
        podcastRssUrl = podcastUrl,
        title = this.title ?: "No Title",
        description = this.description ?: "",
        pubDate = cleanDate,
        link = this.link ?: "",
        enclosureUrl = this.enclosure?.url ?: "",
        type = this.enclosure?.type ?: "audio/mpeg",
        fileSize = this.enclosure?.length ?: 0L,
        duration = this.itunesDuration.durationMillis,
        // Defaults for new episodes
        isPlayed = false,
        playbackPositionMs = 0,
        downloadStatus = DownloadStatus.NOT_DOWNLOADED,
        isFavorite = false
    )
}

private fun RssItem.stableEpisodeGuid(podcastUrl: String): String {
    // Keep the v14 fallback order so the first post-upgrade sync reuses rows
    // whose legacy identity was their title instead of duplicating them.
    return guid ?: link ?: title ?: run {
        val stableFields = listOf(
            podcastUrl,
            enclosure?.url.orEmpty(),
            pubDate.orEmpty(),
            itunesDuration.orEmpty()
        )
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(stableFields.joinToString("\u001f").toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        "fallback:$digest"
    }
}

/**
 * Checks whether a date is valid.
 * If the date is > (now + 7 days), it is set to "now".
 */
private fun sanitizeDate(dateString: String?): Date {
    val parsedDate = dateString.parsedRssDate
    val now = System.currentTimeMillis()
    val threshold = now + Constants.Validation.MAX_FUTURE_DATE_THRESHOLD_MS

    return if (parsedDate == null || parsedDate.time > threshold) {
        // ERROR CASE: date is too far in the future (e.g. year 3000)
        // We correct it to "now" so that sorting is consistent.
        Date(now)
    } else {
        // Normal case
        parsedDate
    }
}

private val String?.parsedRssDate: Date?
    get() {
        if (isNullOrEmpty()) return null

        var parsedDate: Date? = null
        for (format in Constants.Parsing.DATE_FORMATS) {
            try {
                parsedDate = SimpleDateFormat(format, Locale.US).parse(this)
                if (parsedDate != null) break
            } catch (e: Exception) {
                // Format did not match, try the next one
            }
        }
        return parsedDate
    }

/** Converts an iTunes duration such as "01:20:30" or "4830" into milliseconds. */
private val String?.durationMillis: Long
    get() {
        if (isNullOrEmpty()) return 0L

        return try {
            if (contains(":")) {
                val parts = split(":")
                var seconds = 0L
                for (part in parts) {
                    seconds = seconds * SECONDS_PER_MINUTE + part.toLong()
                }
                seconds * MILLIS_PER_SECOND
            } else {
                // Could be seconds as a raw value
                toLong() * MILLIS_PER_SECOND
            }
        } catch (e: Exception) {
            0L
        }
    }
