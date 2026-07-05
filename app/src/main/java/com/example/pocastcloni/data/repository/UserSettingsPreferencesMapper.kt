package com.example.pocastcloni.data.repository

import androidx.datastore.preferences.core.Preferences
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.util.Constants

private val Keys = UserPreferenceKeys

internal fun Preferences.toUserSettings(): UserSettings {
    val defaultSettings = UserSettings()

    val theme =
        try {
            AppTheme.valueOf(this[Keys.THEME] ?: defaultSettings.theme.name)
        } catch (e: Exception) {
            defaultSettings.theme
        }
    val appColor =
        try {
            AppColor.valueOf(this[Keys.APP_COLOR] ?: defaultSettings.appColor.name)
        } catch (e: Exception) {
            defaultSettings.appColor
        }
    val bufferMode =
        try {
            BufferMode.valueOf(this[Keys.BUFFER_MODE] ?: defaultSettings.bufferMode.name)
        } catch (e: Exception) {
            defaultSettings.bufferMode
        }
    val feedMode =
        try {
            FeedUpdateMode.valueOf(this[Keys.FEED_UPDATE_MODE] ?: defaultSettings.feedUpdateMode.name)
        } catch (e: Exception) {
            defaultSettings.feedUpdateMode
        }
    val layoutMode =
        try {
            LayoutMode.valueOf(this[Keys.LAYOUT_MODE] ?: defaultSettings.layoutMode.name)
        } catch (e: Exception) {
            defaultSettings.layoutMode
        }

    return UserSettings(
        theme = theme,
        appColor = appColor,
        colorStrength = this[Keys.COLOR_STRENGTH] ?: defaultSettings.colorStrength,
        bufferMode = bufferMode,
        layoutMode = layoutMode,
        gridSize = this[Keys.GRID_SIZE] ?: defaultSettings.gridSize,
        showGridTitles = this[Keys.SHOW_GRID_TITLES] ?: defaultSettings.showGridTitles,
        confirmDelete = this[Keys.CONFIRM_DELETE] ?: defaultSettings.confirmDelete,
        progressBarHeight = this[Keys.PROGRESS_BAR_HEIGHT] ?: defaultSettings.progressBarHeight,
        navBarHeight = this[Keys.NAV_BAR_HEIGHT] ?: defaultSettings.navBarHeight,
        showMiniPlayerTimeOverlay = this[Keys.SHOW_MINI_PLAYER_TIME_OVERLAY] ?: defaultSettings.showMiniPlayerTimeOverlay,
        oneHandedMode = this[Keys.ONE_HANDED_MODE] ?: defaultSettings.oneHandedMode,
        bottomBarCleanModeEnabled =
        this[Keys.BOTTOM_BAR_CLEAN_MODE_ENABLED] ?: defaultSettings.bottomBarCleanModeEnabled,
        bottomBarAutoHideEnabled =
        this[Keys.BOTTOM_BAR_AUTO_HIDE_ENABLED] ?: defaultSettings.bottomBarAutoHideEnabled,
        bottomBarAutoHideDelaySeconds =
        this[Keys.BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS] ?: defaultSettings.bottomBarAutoHideDelaySeconds,
        gradientBackgroundEnabled = this[Keys.GRADIENT_BACKGROUND_ENABLED] ?: defaultSettings.gradientBackgroundEnabled,
        gradientBackgroundStrength = this[Keys.GRADIENT_BACKGROUND_STRENGTH] ?: defaultSettings.gradientBackgroundStrength,
        transparentSearchCards = this[Keys.TRANSPARENT_SEARCH_CARDS] ?: defaultSettings.transparentSearchCards,
        transparentEpisodeRows = this[Keys.TRANSPARENT_EPISODE_ROWS] ?: defaultSettings.transparentEpisodeRows,
        autoDownloadLimit = this[Keys.AUTO_DOWNLOAD_LIMIT] ?: defaultSettings.autoDownloadLimit,
        autoRefreshOnStart = this[Keys.AUTO_REFRESH_ON_START] ?: defaultSettings.autoRefreshOnStart,
        backgroundCheckEnabled = this[Keys.BACKGROUND_CHECK_ENABLED] ?: defaultSettings.backgroundCheckEnabled,
        backgroundCheckInterval = this[Keys.BACKGROUND_CHECK_INTERVAL] ?: defaultSettings.backgroundCheckInterval,
        markPlayedDurationSeconds = this[Keys.MARK_PLAYED_DURATION] ?: defaultSettings.markPlayedDurationSeconds,
        feedUpdateMode = feedMode,
        indicator =
        IndicatorSettings(
            colorArgb = this[Keys.INDICATOR_COLOR] ?: defaultSettings.indicator.colorArgb,
            size = this[Keys.INDICATOR_SIZE] ?: defaultSettings.indicator.size,
            borderWidth = this[Keys.INDICATOR_BORDER] ?: defaultSettings.indicator.borderWidth,
            xOffset = this[Keys.INDICATOR_X_OFFSET] ?: defaultSettings.indicator.xOffset,
            yOffset = this[Keys.INDICATOR_Y_OFFSET] ?: defaultSettings.indicator.yOffset
        ),
        saveToDownloadsFolder = this[Keys.SAVE_TO_DOWNLOADS_FOLDER] ?: Constants.Preferences.DEFAULT_SAVE_TO_DOWNLOADS_FOLDER,
        autoCleanupEnabled = this[Keys.AUTO_CLEANUP_ENABLED] ?: Constants.Preferences.DEFAULT_AUTO_CLEANUP_ENABLED,
        cleanupKeepLimit = this[Keys.CLEANUP_KEEP_LIMIT] ?: Constants.Preferences.DEFAULT_CLEANUP_KEEP_LIMIT,
        cleanupIntervalHours = this[Keys.CLEANUP_INTERVAL_HOURS] ?: Constants.Preferences.DEFAULT_CLEANUP_INTERVAL_HOURS,
    )
}
