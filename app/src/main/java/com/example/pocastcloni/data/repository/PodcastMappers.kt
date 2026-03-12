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

// --- BESTEHENDE MAPPER (Unverändert lassen) ---

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
        isLatestEpisodePlayed = null,
        latestEpisodeDate = null
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
        timestamp = this.favoriteTimestamp ?: System.currentTimeMillis()
    )
}

// --- NEUE LOGIK FÜR EPISODEN & DATUMS-SCHUTZ ---

/**
 * Wandelt ein RssItem (Netzwerk) in eine EpisodeEntity (Datenbank) um.
 * Enthält Schutz gegen defekte Daten (z.B. Jahr 3000).
 */
fun RssItem.toEpisodeEntity(podcastUrl: String): EpisodeEntity {
    // 1. Datum parsen
    val rawDate = parseRssDate(this.pubDate)

    // 2. Datum "sanitizen" (Schutz vor Zukunfts-Daten)
    val cleanDate = sanitizeDate(rawDate)

    return EpisodeEntity(
        guid = this.guid ?: this.link ?: this.title ?: System.currentTimeMillis().toString(), // Fallback für GUID
        podcastRssUrl = podcastUrl,
        title = this.title ?: "Kein Titel",
        description = this.description ?: "",
        pubDate = cleanDate,
        link = this.link ?: "",
        enclosureUrl = this.enclosure?.url ?: "",
        type = this.enclosure?.type ?: "audio/mpeg",
        fileSize = this.enclosure?.length ?: 0L,
        duration = parseDuration(this.itunesDuration),
        // Defaults für neue Episoden
        isPlayed = false,
        playbackPositionMs = 0,
        downloadStatus = DownloadStatus.NOT_DOWNLOADED,
        isFavorite = false
    )
}

/**
 * Prüft, ob ein Datum gültig ist.
 * Wenn das Datum > (Jetzt + 7 Tage) ist, wird es auf "Jetzt" gesetzt.
 */
private fun sanitizeDate(date: Date?): Date {
    if (date == null) return Date() // Fallback auf Jetzt, wenn gar kein Datum da ist

    val now = System.currentTimeMillis()
    val threshold = now + Constants.Validation.MAX_FUTURE_DATE_THRESHOLD_MS

    return if (date.time > threshold) {
        // FEHLERFALL: Datum liegt zu weit in der Zukunft (z.B. Jahr 3000)
        // Wir korrigieren es auf "Jetzt", damit die Sortierung stimmt.
        Date(now)
    } else {
        // Normalfall
        date
    }
}

/**
 * Versucht das Datum mit verschiedenen Formaten zu parsen.
 */
private fun parseRssDate(dateString: String?): Date? {
    if (dateString.isNullOrEmpty()) return null

    for (format in Constants.Parsing.DATE_FORMATS) {
        try {
            // Locale.US ist wichtig für RSS (z.B. "Mon, 21 Jan...")
            val parser = SimpleDateFormat(format, Locale.US)
            return parser.parse(dateString)
        } catch (e: Exception) {
            // Format passte nicht, nächstes probieren
        }
    }
    return null
}

/**
 * Hilfsfunktion um iTunes Duration (z.B. "01:20:30" oder "4830") in Millisekunden zu wandeln
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
            // Könnte Sekunden als Rohwert sein
            durationStr.toLong() * 1000
        }
    } catch (e: Exception) {
        0L
    }
}