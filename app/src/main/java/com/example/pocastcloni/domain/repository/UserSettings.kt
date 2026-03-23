package com.example.pocastcloni.domain.repository

import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.ui.settings.AppColor
import com.example.pocastcloni.ui.settings.AppTheme
import com.example.pocastcloni.ui.settings.BufferMode
import com.example.pocastcloni.util.Constants

data class IndicatorSettings(
    val colorArgb: Long = Constants.Preferences.DEFAULT_INDICATOR_COLOR,
    val size: Int = Constants.Preferences.DEFAULT_INDICATOR_SIZE,
    val borderWidth: Int = Constants.Preferences.DEFAULT_INDICATOR_BORDER,
    val xOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_X_OFFSET,
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
    val oneHandedMode: Boolean = Constants.Preferences.DEFAULT_ONE_HANDED_MODE,
    val autoDownloadLimit: Int = Constants.Preferences.DEFAULT_AUTO_DOWNLOAD_LIMIT,
    val autoRefreshOnStart: Boolean = Constants.Preferences.DEFAULT_AUTO_REFRESH_ON_START,
    val backgroundCheckEnabled: Boolean = Constants.Preferences.DEFAULT_BACKGROUND_CHECK_ENABLED,
    val backgroundCheckInterval: Int = Constants.Preferences.DEFAULT_BACKGROUND_CHECK_INTERVAL,
    val markPlayedDurationSeconds: Int = 0,
    val feedUpdateMode: FeedUpdateMode = FeedUpdateMode.ALWAYS_FULL,
    val indicator: IndicatorSettings = IndicatorSettings(),
    val saveToDownloadsFolder: Boolean = Constants.Preferences.DEFAULT_SAVE_TO_DOWNLOADS_FOLDER,
)
