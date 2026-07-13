package com.example.pocastcloni.data.local

import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

// Defines the top-level structure of the JSON file.
data class BackupData(
    @JsonProperty(Constants.Backup.KEY_VERSION) val version: Int = Constants.Backup.BACKUP_VERSION,
    @JsonProperty(Constants.Backup.KEY_PODCASTS) val podcasts: List<BackupPodcast> = emptyList(),
    @JsonProperty(Constants.Backup.KEY_SETTINGS) val settings: UserSettings? = null,
    @get:JsonIgnore
    val settingsFieldPresence: BackupSettingsFieldPresence? = null,
    @JsonProperty("favorites") val favorites: List<BackupFavorite> = emptyList()
)

data class BackupSettingsFieldPresence(
    val fields: Set<String>,
    val indicatorFields: Set<String> = emptySet()
)

internal fun BackupData.settingsForRestore(current: UserSettings): UserSettings? {
    val imported = settings ?: return null
    val presence = settingsFieldPresence ?: return imported
    val fields = presence.fields

    return current.copy(
        theme = fields.importedValue("theme", imported.theme, current.theme),
        appColor = fields.importedValue("appColor", imported.appColor, current.appColor),
        colorStrength = fields.importedValue("colorStrength", imported.colorStrength, current.colorStrength),
        bufferMode = fields.importedValue("bufferMode", imported.bufferMode, current.bufferMode),
        layoutMode = fields.importedValue("layoutMode", imported.layoutMode, current.layoutMode),
        gridSize = fields.importedValue("gridSize", imported.gridSize, current.gridSize),
        showGridTitles = fields.importedValue("showGridTitles", imported.showGridTitles, current.showGridTitles),
        confirmDelete = fields.importedValue("confirmDelete", imported.confirmDelete, current.confirmDelete),
        progressBarHeight = fields.importedValue(
            "progressBarHeight",
            imported.progressBarHeight,
            current.progressBarHeight
        ),
        navBarHeight = fields.importedValue("navBarHeight", imported.navBarHeight, current.navBarHeight),
        showMiniPlayerTimeOverlay = fields.importedValue(
            "showMiniPlayerTimeOverlay",
            imported.showMiniPlayerTimeOverlay,
            current.showMiniPlayerTimeOverlay
        ),
        transparentMiniPlayer = fields.importedValue(
            "transparentMiniPlayer",
            imported.transparentMiniPlayer,
            current.transparentMiniPlayer
        ),
        transparentBottomBar = fields.importedValue(
            "transparentBottomBar",
            imported.transparentBottomBar,
            current.transparentBottomBar
        ),
        oneHandedMode = fields.importedValue("oneHandedMode", imported.oneHandedMode, current.oneHandedMode),
        bottomBarCleanModeEnabled = fields.importedValue(
            "bottomBarCleanModeEnabled",
            imported.bottomBarCleanModeEnabled,
            current.bottomBarCleanModeEnabled
        ),
        bottomBarAutoHideEnabled = fields.importedValue(
            "bottomBarAutoHideEnabled",
            imported.bottomBarAutoHideEnabled,
            current.bottomBarAutoHideEnabled
        ),
        bottomBarAutoHideDelaySeconds = fields.importedValue(
            "bottomBarAutoHideDelaySeconds",
            imported.bottomBarAutoHideDelaySeconds,
            current.bottomBarAutoHideDelaySeconds
        ),
        gradientBackgroundEnabled = fields.importedValue(
            "gradientBackgroundEnabled",
            imported.gradientBackgroundEnabled,
            current.gradientBackgroundEnabled
        ),
        gradientBackgroundStrength = fields.importedValue(
            "gradientBackgroundStrength",
            imported.gradientBackgroundStrength,
            current.gradientBackgroundStrength
        ),
        gradientBackgroundDirection = fields.importedValue(
            "gradientBackgroundDirection",
            imported.gradientBackgroundDirection,
            current.gradientBackgroundDirection
        ),
        transparentSearchCards = fields.importedValue(
            "transparentSearchCards",
            imported.transparentSearchCards,
            current.transparentSearchCards
        ),
        transparentPodcastCards = fields.importedValue(
            "transparentPodcastCards",
            imported.transparentPodcastCards,
            current.transparentPodcastCards
        ),
        transparentEpisodeRows = fields.importedValue(
            "transparentEpisodeRows",
            imported.transparentEpisodeRows,
            current.transparentEpisodeRows
        ),
        autoDownloadLimit = fields.importedValue(
            "autoDownloadLimit",
            imported.autoDownloadLimit,
            current.autoDownloadLimit
        ),
        autoRefreshOnStart = fields.importedValue(
            "autoRefreshOnStart",
            imported.autoRefreshOnStart,
            current.autoRefreshOnStart
        ),
        backgroundCheckEnabled = fields.importedValue(
            "backgroundCheckEnabled",
            imported.backgroundCheckEnabled,
            current.backgroundCheckEnabled
        ),
        backgroundCheckInterval = fields.importedValue(
            "backgroundCheckInterval",
            imported.backgroundCheckInterval,
            current.backgroundCheckInterval
        ),
        markPlayedDurationSeconds = fields.importedValue(
            "markPlayedDurationSeconds",
            imported.markPlayedDurationSeconds,
            current.markPlayedDurationSeconds
        ),
        feedUpdateMode = fields.importedValue(
            "feedUpdateMode",
            imported.feedUpdateMode,
            current.feedUpdateMode
        ),
        indicator = imported.indicator.mergePresent(current.indicator, presence.indicatorFields),
        saveToDownloadsFolder = fields.importedValue(
            "saveToDownloadsFolder",
            imported.saveToDownloadsFolder,
            current.saveToDownloadsFolder
        ),
        autoCleanupEnabled = fields.importedValue(
            "autoCleanupEnabled",
            imported.autoCleanupEnabled,
            current.autoCleanupEnabled
        ),
        cleanupKeepLimit = fields.importedValue(
            "cleanupKeepLimit",
            imported.cleanupKeepLimit,
            current.cleanupKeepLimit
        ),
        cleanupIntervalHours = fields.importedValue(
            "cleanupIntervalHours",
            imported.cleanupIntervalHours,
            current.cleanupIntervalHours
        )
    )
}

private fun IndicatorSettings.mergePresent(
    current: IndicatorSettings,
    fields: Set<String>
): IndicatorSettings = current.copy(
    colorArgb = fields.importedValue("colorArgb", colorArgb, current.colorArgb),
    size = fields.importedValue("size", size, current.size),
    borderWidth = fields.importedValue("borderWidth", borderWidth, current.borderWidth),
    xOffset = fields.importedValue("xOffset", xOffset, current.xOffset),
    yOffset = fields.importedValue("yOffset", yOffset, current.yOffset)
)

private fun <T> Set<String>.importedValue(
    field: String,
    imported: T,
    current: T
): T = if (field in this) imported else current

data class BackupPodcast(
    @JsonProperty(Constants.Backup.KEY_URL) val url: String = "",
    @JsonProperty(Constants.Backup.KEY_SORT_ORDER) val sortOrder: Long = 0,
    @JsonProperty("allow_insecure_http") val allowInsecureHttp: Boolean = false,
    @JsonProperty("allow_local_network") val allowLocalNetwork: Boolean = false,
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
