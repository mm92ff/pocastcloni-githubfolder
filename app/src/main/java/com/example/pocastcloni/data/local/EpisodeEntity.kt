package com.example.pocastcloni.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.pocastcloni.util.Constants
import java.util.Date

@Entity(
    tableName = Constants.Database.TABLE_EPISODES,
    foreignKeys = [
        ForeignKey(
            entity = PodcastEntity::class,
            parentColumns = ["rssUrl"],
            childColumns = ["podcastRssUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["podcastRssUrl"]),
        Index(value = ["podcastRssUrl", "pubDate"]),
        Index(value = ["downloadStatus", "pubDate"]),
        Index(value = ["isFavorite", "favoriteTimestamp"]),
        Index(value = ["isPlayed", "datePlayed"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey val guid: String,
    val podcastRssUrl: String,
    val title: String,
    val description: String,
    val pubDate: Date?,
    val link: String,
    // RSS enclosure (Audio-Datei)
    val enclosureUrl: String,
    val type: String = "audio/mpeg",
    val fileSize: Long = 0,
    // Status Felder
    val isPlayed: Boolean = false,
    val playbackPositionMs: Long = 0,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadPath: String? = null,
    val isFavorite: Boolean = false,
    val datePlayed: Date? = null,
    val favoriteTimestamp: Long? = null,
    val duration: Long = 0
)
