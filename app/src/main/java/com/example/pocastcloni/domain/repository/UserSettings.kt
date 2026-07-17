package com.example.pocastcloni.domain.repository

import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty

data class IndicatorSettings(
    val colorArgb: Long = Constants.Preferences.DEFAULT_INDICATOR_COLOR,
    val size: Int = Constants.Preferences.DEFAULT_INDICATOR_SIZE,
    val borderWidth: Int = Constants.Preferences.DEFAULT_INDICATOR_BORDER,
    @get:JsonProperty("xOffset")
    @param:JsonProperty("xOffset")
    @param:JsonAlias("xoffset")
    val xOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_X_OFFSET,
    @get:JsonProperty("yOffset")
    @param:JsonProperty("yOffset")
    @param:JsonAlias("yoffset")
    val yOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_Y_OFFSET
)

data class UserSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val appColor: AppColor = AppColor.GREEN,
    val colorStrength: Float = 0.1f,
    val bufferMode: BufferMode = BufferMode.NORMAL,
    val layoutMode: LayoutMode = LayoutMode.GRID,
    val gridSize: Int = Constants.Preferences.DEFAULT_GRID_SIZE,
    val showGridTitles: Boolean = Constants.Preferences.DEFAULT_SHOW_GRID_TITLES,
    val confirmDelete: Boolean = Constants.Preferences.DEFAULT_CONFIRM_DELETE,
    val progressBarHeight: Int = Constants.Preferences.DEFAULT_PROGRESS_BAR_HEIGHT,
    val navBarHeight: Int = Constants.Preferences.DEFAULT_NAV_BAR_HEIGHT,
    val showMiniPlayerTimeOverlay: Boolean = Constants.Preferences.DEFAULT_SHOW_MINI_PLAYER_TIME_OVERLAY,
    val transparentMiniPlayer: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_MINI_PLAYER,
    val transparentBottomBar: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_BOTTOM_BAR,
    val oneHandedMode: Boolean = Constants.Preferences.DEFAULT_ONE_HANDED_MODE,
    val bottomBarCleanModeEnabled: Boolean = Constants.Preferences.DEFAULT_BOTTOM_BAR_CLEAN_MODE_ENABLED,
    val bottomBarAutoHideEnabled: Boolean = Constants.Preferences.DEFAULT_BOTTOM_BAR_AUTO_HIDE_ENABLED,
    val bottomBarAutoHideDelaySeconds: Int = Constants.Preferences.DEFAULT_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS,
    val gradientBackgroundEnabled: Boolean = Constants.Preferences.DEFAULT_GRADIENT_BACKGROUND_ENABLED,
    val gradientBackgroundStrength: Float = Constants.Preferences.DEFAULT_GRADIENT_BACKGROUND_STRENGTH,
    val gradientBackgroundDirection: GradientDirection = GradientDirection.TOP_TO_BOTTOM,
    val transparentSearchCards: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_SEARCH_CARDS,
    val transparentPodcastCards: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_PODCAST_CARDS,
    val transparentEpisodeRows: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_EPISODE_ROWS,
    val autoDownloadLimit: Int = Constants.Preferences.DEFAULT_AUTO_DOWNLOAD_LIMIT,
    val autoRefreshOnStart: Boolean = Constants.Preferences.DEFAULT_AUTO_REFRESH_ON_START,
    val backgroundCheckEnabled: Boolean = Constants.Preferences.DEFAULT_BACKGROUND_CHECK_ENABLED,
    val backgroundCheckInterval: Int = Constants.Preferences.DEFAULT_BACKGROUND_CHECK_INTERVAL,
    val markPlayedDurationSeconds: Int = 0,
    val feedUpdateMode: FeedUpdateMode = FeedUpdateMode.ALWAYS_FULL,
    val smartStreamItemLimit: Int = Constants.Preferences.DEFAULT_SMART_STREAM_ITEM_LIMIT,
    val indicator: IndicatorSettings = IndicatorSettings(),
    val saveToDownloadsFolder: Boolean = Constants.Preferences.DEFAULT_SAVE_TO_DOWNLOADS_FOLDER,
    val autoCleanupEnabled: Boolean = Constants.Preferences.DEFAULT_AUTO_CLEANUP_ENABLED,
    val cleanupKeepLimit: Int = Constants.Preferences.DEFAULT_CLEANUP_KEEP_LIMIT,
    val cleanupIntervalHours: Int = Constants.Preferences.DEFAULT_CLEANUP_INTERVAL_HOURS,
)
