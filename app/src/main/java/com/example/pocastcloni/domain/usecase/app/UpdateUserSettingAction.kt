package com.example.pocastcloni.domain.usecase.app

import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.ui.settings.AppColor
import com.example.pocastcloni.ui.settings.AppTheme
import com.example.pocastcloni.ui.settings.BufferMode

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

    data class ToggleOneHandedMode(val enabled: Boolean) : UpdateUserSettingAction

    data class ToggleBottomBarCleanMode(val enabled: Boolean) : UpdateUserSettingAction

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
