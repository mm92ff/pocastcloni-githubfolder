package com.example.pocastcloni.data.local

import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.annotation.JsonProperty

// Definiert die Top-Level-Struktur der JSON-Datei.
data class BackupData(
    @JsonProperty(Constants.Backup.KEY_VERSION) val version: Int = Constants.Backup.BACKUP_VERSION,
    @JsonProperty(Constants.Backup.KEY_PODCASTS) val podcasts: List<BackupPodcast> = emptyList(),
    @JsonProperty(Constants.Backup.KEY_SETTINGS) val settings: UserSettings? = null,
    @JsonProperty("favorites") val favorites: List<BackupFavorite> = emptyList()
)

data class BackupPodcast(
    @JsonProperty(Constants.Backup.KEY_URL) val url: String = "",
    @JsonProperty(Constants.Backup.KEY_SORT_ORDER) val sortOrder: Long = 0,

    // Metadaten für Offline-Import-Resilienz
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("image_url") val imageUrl: String? = null,

    // NEU: Caching Header für Smart Updates (Wichtig für Traffic-Sparmaßnahmen nach Restore)
    @JsonProperty("last_modified") val lastModifiedHeader: String? = null,
    @JsonProperty("etag") val eTagHeader: String? = null
)

// Struktur für einen favorisierten Eintrag im Backup
data class BackupFavorite(
    @JsonProperty("podcast_url") val podcastUrl: String = "",
    @JsonProperty("episode_guid") val episodeGuid: String = "",
    @JsonProperty("timestamp") val timestamp: Long? = null
)