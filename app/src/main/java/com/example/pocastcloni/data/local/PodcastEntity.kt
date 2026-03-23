package com.example.pocastcloni.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pocastcloni.util.Constants
import java.util.Date

@Entity(tableName = Constants.Database.TABLE_PODCASTS)
data class PodcastEntity(
    @PrimaryKey val rssUrl: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val lastRefreshed: Date = Date(),
    val autoDownloadEnabled: Boolean = false,
    val sortOrder: Long = Constants.Database.DEFAULT_SORT_ORDER,
    // Denormalisierte Felder für schnellere UI-Queries
    val hasNewEpisodes: Boolean = false,
    val latestEpisodeGuid: String? = null,
    val latestEpisodePubDate: Date? = null,
    val isLatestEpisodePlayed: Boolean? = null,
    // Caching Header für Smart Updates
    val lastModifiedHeader: String? = null,
    val eTagHeader: String? = null
)
