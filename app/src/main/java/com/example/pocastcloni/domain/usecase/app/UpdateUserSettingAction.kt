package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode

sealed interface UpdateUserSettingAction {
    data class SetAppTheme(val theme: AppTheme) : UpdateUserSettingAction

    data class SetAppColor(val color: AppColor) : UpdateUserSettingAction

    data class SetColorStrength(val strength: Float) : UpdateUserSettingAction

    data class SetBufferSettings(val mode: BufferMode) : UpdateUserSettingAction

    data class SetLayoutMode(val mode: LayoutMode) : UpdateUserSettingAction

    data class SetGridSize(val size: Int) : UpdateUserSettingAction

    data class ToggleShowGridTitles(val show: Boolean) : UpdateUserSettingAction

    data class ToggleConfirmDelete(val confirm: Boolean) : UpdateUserSettingAction

    data class SetProgressBarHeight(val height: Int) : UpdateUserSettingAction

    data class SetNavBarHeight(val height: Int) : UpdateUserSettingAction

    data class ToggleMiniPlayerTimeOverlay(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleTransparentMiniPlayer(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleTransparentBottomBar(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleOneHandedMode(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleBottomBarCleanMode(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleBottomBarAutoHide(val enabled: Boolean) : UpdateUserSettingAction

    data class SetBottomBarAutoHideDelay(val seconds: Int) : UpdateUserSettingAction

    data class ToggleGradientBackground(val enabled: Boolean) : UpdateUserSettingAction

    data class SetGradientBackgroundStrength(val strength: Float) : UpdateUserSettingAction

    data class SetGradientBackgroundDirection(val direction: GradientDirection) : UpdateUserSettingAction

    data class ToggleTransparentSearchCards(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleTransparentPodcastCards(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleTransparentEpisodeRows(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleTransparentCardsAndRows(val enabled: Boolean) : UpdateUserSettingAction

    data class SetAutoDownloadLimit(val limit: Int) : UpdateUserSettingAction

    data class ToggleAutoRefreshOnStart(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleBackgroundCheck(val enabled: Boolean) : UpdateUserSettingAction

    data class SetBackgroundCheckInterval(val hours: Int) : UpdateUserSettingAction

    data class SetMarkPlayedDuration(val seconds: Int) : UpdateUserSettingAction

    data class SetFeedUpdateMode(val mode: FeedUpdateMode) : UpdateUserSettingAction

    data class SetIndicatorColor(val colorArgb: Long) : UpdateUserSettingAction

    data class SetIndicatorSize(val sizeDp: Int) : UpdateUserSettingAction

    data class SetIndicatorBorderWidth(val widthDp: Int) : UpdateUserSettingAction

    data class SetIndicatorXOffset(val offsetDp: Int) : UpdateUserSettingAction

    data class SetIndicatorYOffset(val offsetDp: Int) : UpdateUserSettingAction

    data class ToggleSaveToDownloadsFolder(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleAutoCleanup(val enabled: Boolean) : UpdateUserSettingAction

    data class SetCleanupKeepLimit(val limit: Int) : UpdateUserSettingAction

    data class SetCleanupIntervalHours(val hours: Int) : UpdateUserSettingAction
}
