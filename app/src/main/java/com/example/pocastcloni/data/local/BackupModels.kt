package com.example.pocastcloni.data.local

import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.annotation.JsonProperty

// Defines the top-level structure of the JSON file.
data class BackupData(
    @JsonProperty(Constants.Backup.KEY_VERSION) val version: Int = Constants.Backup.BACKUP_VERSION,
    @JsonProperty(Constants.Backup.KEY_PODCASTS) val podcasts: List<BackupPodcast> = emptyList(),
    @JsonProperty(Constants.Backup.KEY_SETTINGS) val settings: UserSettings? = null,
    @JsonProperty("favorites") val favorites: List<BackupFavorite> = emptyList()
)

data class BackupPodcast(
    @JsonProperty(Constants.Backup.KEY_URL) val url: String = "",
    @JsonProperty(Constants.Backup.KEY_SORT_ORDER) val sortOrder: Long = 0,
    @JsonProperty("allow_insecure_http") val allowInsecureHttp: Boolean = false,
    // Metadata for offline import resilience
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("image_url") val imageUrl: String? = null,
    // NEW: Caching headers for smart updates (important for traffic savings after restore)
    @JsonProperty("last_modified") val lastModifiedHeader: String? = null,
    @JsonProperty("etag") val eTagHeader: String? = null
)

// Structure for a favorited entry in the backup
data class BackupFavorite(
    @JsonProperty("podcast_url") val podcastUrl: String = "",
    @JsonProperty("episode_guid") val episodeGuid: String = "",
    @JsonProperty("timestamp") val timestamp: Long? = null
)
