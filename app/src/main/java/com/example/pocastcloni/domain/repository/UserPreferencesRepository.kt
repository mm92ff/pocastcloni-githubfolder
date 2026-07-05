package com.example.pocastcloni.domain.repository

import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userSettingsFlow: Flow<UserSettings>

    suspend fun updateTheme(theme: AppTheme)

    suspend fun updateAppColor(color: AppColor)

    suspend fun updateColorStrength(strength: Float)

    suspend fun updateBufferSettings(mode: BufferMode)

    // Update function for Layout Mode
    suspend fun updateLayoutMode(mode: LayoutMode)

    suspend fun updateGridSize(size: Int)

    suspend fun updateShowGridTitles(show: Boolean)

    suspend fun updateConfirmDelete(confirm: Boolean)

    suspend fun updateProgressBarHeight(height: Int)

    suspend fun updateNavBarHeight(height: Int)

    suspend fun updateShowMiniPlayerTimeOverlay(enabled: Boolean)

    suspend fun updateOneHandedMode(enabled: Boolean)

    suspend fun updateBottomBarCleanModeEnabled(enabled: Boolean)

    suspend fun updateBottomBarAutoHideEnabled(enabled: Boolean)

    suspend fun updateBottomBarAutoHideDelaySeconds(seconds: Int)

    suspend fun updateGradientBackgroundEnabled(enabled: Boolean)

    suspend fun updateGradientBackgroundStrength(strength: Float)

    suspend fun updateTransparentSearchCards(enabled: Boolean)

    suspend fun updateTransparentEpisodeRows(enabled: Boolean)

    suspend fun updateAutoDownloadLimit(limit: Int)

    suspend fun updateAutoRefreshOnStart(enabled: Boolean)

    suspend fun updateBackgroundCheckEnabled(enabled: Boolean)

    suspend fun updateBackgroundCheckInterval(hours: Int)

    suspend fun updateMarkPlayedDuration(seconds: Int)

    suspend fun updateFeedUpdateMode(mode: FeedUpdateMode)

    suspend fun updateIndicatorColor(colorArgb: Long)

    suspend fun updateIndicatorSize(sizeDp: Int)

    suspend fun updateIndicatorBorderWidth(widthDp: Int)

    suspend fun updateIndicatorXOffset(offsetDp: Int)

    suspend fun updateIndicatorYOffset(offsetDp: Int)

    suspend fun updateSaveToDownloadsFolder(enabled: Boolean)

    suspend fun updateAutoCleanupEnabled(enabled: Boolean)

    suspend fun updateCleanupKeepLimit(limit: Int)

    suspend fun updateCleanupIntervalHours(hours: Int)

    suspend fun restoreSettings(settings: UserSettings)

    suspend fun clearSettings()
}
