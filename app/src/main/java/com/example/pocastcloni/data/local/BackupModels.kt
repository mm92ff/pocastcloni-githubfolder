package com.example.pocastcloni.data.local

import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

// Defines the top-level structure of the JSON file.
data class BackupData(
    @JsonProperty(Constants.Backup.KEY_VERSION) val version: Int = Constants.Backup.BACKUP_VERSION,
    @JsonProperty(Constants.Backup.KEY_PODCASTS) val podcasts: List<BackupPodcast> = emptyList(),
    @JsonProperty(Constants.Backup.KEY_SETTINGS) val settings: UserSettings? = null,
    @get:JsonIgnore
    val settingsFieldPresence: BackupSettingsFieldPresence? = null,
    @JsonProperty("favorites") val favorites: List<BackupFavorite> = emptyList(),
    @param:JsonAlias("episode_states")
    @JsonProperty(Constants.Backup.KEY_EPISODE_STATES)
    val episodeStates: List<BackupEpisodeState> = emptyList()
)

data class BackupRoomSnapshot(
    val podcasts: List<PodcastEntity>,
    val episodeStates: List<EpisodeEntity>
)

data class BackupSettingsFieldPresence(
    val fields: Set<String>,
    val indicatorFields: Set<String> = emptySet()
)

internal fun BackupData.settingsForRestore(current: UserSettings): UserSettings? =
    when (val imported = settings) {
        null -> null
        else ->
            settingsFieldPresence?.let { presence ->
                val fields = presence.fields
                val coreAppearance = imported.mergeCoreAppearance(current, fields)
                val surfaces = imported.mergeSurfaceSettings(coreAppearance, fields)
                val gradientsAndCards = imported.mergeGradientAndCardSettings(surfaces, fields)
                val syncAndData = imported.mergeSyncAndDataSettings(gradientsAndCards, fields)
                syncAndData.copy(
                    indicator = imported.indicator.mergePresent(current.indicator, presence.indicatorFields)
                )
            } ?: imported
    }

private fun UserSettings.mergeCoreAppearance(
    current: UserSettings,
    fields: Set<String>
): UserSettings = current.copy(
    theme = fields.importedValue("theme", theme, current.theme),
    appColor = fields.importedValue("appColor", appColor, current.appColor),
    colorStrength = fields.importedValue("colorStrength", colorStrength, current.colorStrength),
    bufferMode = fields.importedValue("bufferMode", bufferMode, current.bufferMode),
    layoutMode = fields.importedValue("layoutMode", layoutMode, current.layoutMode),
    gridSize = fields.importedValue("gridSize", gridSize, current.gridSize),
    showGridTitles = fields.importedValue("showGridTitles", showGridTitles, current.showGridTitles),
    confirmDelete = fields.importedValue("confirmDelete", confirmDelete, current.confirmDelete),
    progressBarHeight = fields.importedValue("progressBarHeight", progressBarHeight, current.progressBarHeight),
    navBarHeight = fields.importedValue("navBarHeight", navBarHeight, current.navBarHeight)
)

private fun UserSettings.mergeSurfaceSettings(
    current: UserSettings,
    fields: Set<String>
): UserSettings = current.copy(
    showMiniPlayerTimeOverlay = fields.importedValue(
        "showMiniPlayerTimeOverlay",
        showMiniPlayerTimeOverlay,
        current.showMiniPlayerTimeOverlay
    ),
    transparentMiniPlayer = fields.importedValue(
        "transparentMiniPlayer",
        transparentMiniPlayer,
        current.transparentMiniPlayer
    ),
    transparentBottomBar = fields.importedValue(
        "transparentBottomBar",
        transparentBottomBar,
        current.transparentBottomBar
    ),
    oneHandedMode = fields.importedValue("oneHandedMode", oneHandedMode, current.oneHandedMode),
    bottomBarCleanModeEnabled = fields.importedValue(
        "bottomBarCleanModeEnabled",
        bottomBarCleanModeEnabled,
        current.bottomBarCleanModeEnabled
    ),
    bottomBarAutoHideEnabled = fields.importedValue(
        "bottomBarAutoHideEnabled",
        bottomBarAutoHideEnabled,
        current.bottomBarAutoHideEnabled
    ),
    bottomBarAutoHideDelaySeconds = fields.importedValue(
        "bottomBarAutoHideDelaySeconds",
        bottomBarAutoHideDelaySeconds,
        current.bottomBarAutoHideDelaySeconds
    )
)

private fun UserSettings.mergeGradientAndCardSettings(
    current: UserSettings,
    fields: Set<String>
): UserSettings = current.copy(
    gradientBackgroundEnabled = fields.importedValue(
        "gradientBackgroundEnabled",
        gradientBackgroundEnabled,
        current.gradientBackgroundEnabled
    ),
    gradientBackgroundStrength = fields.importedValue(
        "gradientBackgroundStrength",
        gradientBackgroundStrength,
        current.gradientBackgroundStrength
    ),
    gradientBackgroundDirection = fields.importedValue(
        "gradientBackgroundDirection",
        gradientBackgroundDirection,
        current.gradientBackgroundDirection
    ),
    transparentSearchCards = fields.importedValue(
        "transparentSearchCards",
        transparentSearchCards,
        current.transparentSearchCards
    ),
    transparentPodcastCards = fields.importedValue(
        "transparentPodcastCards",
        transparentPodcastCards,
        current.transparentPodcastCards
    ),
    transparentEpisodeRows = fields.importedValue(
        "transparentEpisodeRows",
        transparentEpisodeRows,
        current.transparentEpisodeRows
    )
)

private fun UserSettings.mergeSyncAndDataSettings(
    current: UserSettings,
    fields: Set<String>
): UserSettings = current.copy(
    autoDownloadLimit = fields.importedValue("autoDownloadLimit", autoDownloadLimit, current.autoDownloadLimit),
    autoRefreshOnStart = fields.importedValue("autoRefreshOnStart", autoRefreshOnStart, current.autoRefreshOnStart),
    backgroundCheckEnabled = fields.importedValue(
        "backgroundCheckEnabled",
        backgroundCheckEnabled,
        current.backgroundCheckEnabled
    ),
    backgroundCheckInterval = fields.importedValue(
        "backgroundCheckInterval",
        backgroundCheckInterval,
        current.backgroundCheckInterval
    ),
    markPlayedDurationSeconds = fields.importedValue(
        "markPlayedDurationSeconds",
        markPlayedDurationSeconds,
        current.markPlayedDurationSeconds
    ),
    feedUpdateMode = fields.importedValue("feedUpdateMode", feedUpdateMode, current.feedUpdateMode),
    smartStreamItemLimit = fields.importedValue(
        "smartStreamItemLimit",
        smartStreamItemLimit,
        current.smartStreamItemLimit
    ),
    saveToDownloadsFolder = fields.importedValue(
        "saveToDownloadsFolder",
        saveToDownloadsFolder,
        current.saveToDownloadsFolder
    ),
    autoCleanupEnabled = fields.importedValue("autoCleanupEnabled", autoCleanupEnabled, current.autoCleanupEnabled),
    cleanupKeepLimit = fields.importedValue("cleanupKeepLimit", cleanupKeepLimit, current.cleanupKeepLimit),
    cleanupIntervalHours = fields.importedValue(
        "cleanupIntervalHours",
        cleanupIntervalHours,
        current.cleanupIntervalHours
    )
)

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
    @param:JsonAlias("auto_download_enabled")
    @JsonProperty("autoDownloadEnabled")
    val autoDownloadEnabled: Boolean = false,
    @JsonProperty("allow_insecure_http") val allowInsecureHttp: Boolean = false,
    @JsonProperty("allow_local_network") val allowLocalNetwork: Boolean = false,
    // Metadata for offline import resilience
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("image_url") val imageUrl: String? = null,
    // Read-only compatibility for backups created before validators became origin-bound.
    @param:JsonProperty("last_modified")
    @get:JsonProperty(value = "last_modified", access = JsonProperty.Access.WRITE_ONLY)
    val lastModifiedHeader: String? = null,
    @param:JsonProperty("etag")
    @get:JsonProperty(value = "etag", access = JsonProperty.Access.WRITE_ONLY)
    val eTagHeader: String? = null
)

data class BackupEpisodeState(
    @param:JsonAlias("podcast_url")
    @JsonProperty("podcastUrl")
    val podcastUrl: String = "",
    @param:JsonAlias("episode_guid")
    @JsonProperty("episodeGuid")
    val episodeGuid: String = "",
    @JsonProperty("title") val title: String = "",
    @JsonProperty("description") val description: String = "",
    @param:JsonAlias("published_at")
    @JsonProperty("publishedAt")
    val publishedAt: Long? = null,
    @param:JsonAlias("duration_ms")
    @JsonProperty("duration")
    val duration: Long = 0,
    @param:JsonAlias("is_favorite")
    @JsonProperty("isFavorite")
    val isFavorite: Boolean = false,
    @param:JsonAlias("favorite_added_at")
    @JsonProperty("favoriteAddedAt")
    val favoriteAddedAt: Long? = null,
    @param:JsonAlias("favorite_order")
    @JsonProperty("favoriteOrder")
    val favoriteOrder: Long? = null,
    @param:JsonAlias("is_played")
    @JsonProperty("isPlayed")
    val isPlayed: Boolean = false,
    @param:JsonAlias("date_played")
    @JsonProperty("datePlayed")
    val datePlayed: Long? = null,
    @param:JsonAlias("playback_position_ms")
    @JsonProperty("playbackPositionMs")
    val playbackPositionMs: Long = 0
)

// Structure for a favorited entry in the backup
data class BackupFavorite(
    @JsonProperty("podcast_url") val podcastUrl: String = "",
    @JsonProperty("episode_guid") val episodeGuid: String = "",
    @JsonProperty("timestamp") val timestamp: Long? = null
)
