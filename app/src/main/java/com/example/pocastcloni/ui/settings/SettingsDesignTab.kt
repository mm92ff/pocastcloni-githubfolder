package com.example.pocastcloni.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction

@Composable
internal fun DesignSettingsContent(
    settings: SettingsUiState.Success,
    onEvent: (SettingsUiEvent) -> Unit
) {
    SectionAppearance(
        theme = settings.theme,
        appColor = settings.appColor,
        colorStrength = settings.colorStrength,
        gradientBackgroundEnabled = settings.gradientBackgroundEnabled,
        gradientBackgroundStrength = settings.gradientBackgroundStrength,
        gradientBackgroundDirection = settings.gradientBackgroundDirection,
        transparentSearchCards = settings.transparentSearchCards,
        transparentPodcastCards = settings.transparentPodcastCards,
        transparentEpisodeRows = settings.transparentEpisodeRows,
        transparentBottomBar = settings.transparentBottomBar,
        transparentMiniPlayer = settings.transparentMiniPlayer,
        showMiniPlayerTimeOverlay = settings.showMiniPlayerTimeOverlay,
        progressBarHeight = settings.progressBarHeight,
        gridSize = settings.gridSize,
        indicatorState = settings.indicator,
        onSetTheme =
        remember(onEvent) {
            {
                    theme: AppTheme ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetAppTheme(theme)))
            }
        },
        onSetAppColor =
        remember(onEvent) {
            {
                    color: AppColor ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetAppColor(color)))
            }
        },
        onSetColorStrength =
        remember(onEvent) {
            {
                    strength: Float ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetColorStrength(strength)))
            }
        },
        onToggleGradientBackground =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleGradientBackground(enabled)))
            }
        },
        onSetGradientBackgroundStrength =
        remember(onEvent) {
            {
                    strength: Float ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetGradientBackgroundStrength(strength)))
            }
        },
        onSetGradientBackgroundDirection =
        remember(onEvent) {
            {
                    direction: GradientDirection ->
                onEvent(
                    SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetGradientBackgroundDirection(direction))
                )
            }
        },
        onToggleTransparentCardsAndRows =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleTransparentCardsAndRows(enabled)))
            }
        },
        onToggleTransparentBottomBar =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleTransparentBottomBar(enabled)))
            }
        }
    )
    SettingsTabDivider()

    SectionIndicator(
        gridSizeDp = settings.gridSize,
        indicatorState = settings.indicator,
        onColorClick =
        remember(onEvent) {
            {
                    color: Long ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorColor(color)))
            }
        },
        onSizeChange =
        remember(onEvent) {
            {
                    size: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorSize(size)))
            }
        },
        onBorderChange =
        remember(onEvent) {
            {
                    width: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorBorderWidth(width)))
            }
        },
        onXOffsetChange =
        remember(onEvent) {
            {
                    x: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorXOffset(x)))
            }
        },
        onYOffsetChange =
        remember(onEvent) {
            {
                    y: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorYOffset(y)))
            }
        }
    )
    SettingsTabDivider()

    SectionInterface(
        layoutMode = settings.layoutMode,
        gridSize = settings.gridSize,
        showGridTitles = settings.showGridTitles,
        oneHandedMode = settings.oneHandedMode,
        bottomBarCleanModeEnabled = settings.bottomBarCleanModeEnabled,
        bottomBarAutoHideEnabled = settings.bottomBarAutoHideEnabled,
        bottomBarAutoHideDelaySeconds = settings.bottomBarAutoHideDelaySeconds,
        progressBarHeight = settings.progressBarHeight,
        navBarHeight = settings.navBarHeight,
        confirmDelete = settings.confirmDelete,
        onSetLayoutMode =
        remember(onEvent) {
            {
                    mode: LayoutMode ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetLayoutMode(mode)))
            }
        },
        onSetGridSize =
        remember(onEvent) {
            {
                    size: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetGridSize(size)))
            }
        },
        onToggleShowGridTitles =
        remember(onEvent) {
            {
                    show: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleShowGridTitles(show)))
            }
        },
        onToggleOneHandedMode =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleOneHandedMode(enabled)))
            }
        },
        onToggleBottomBarCleanMode =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleBottomBarCleanMode(enabled)))
            }
        },
        onToggleBottomBarAutoHide =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleBottomBarAutoHide(enabled)))
            }
        },
        onSetBottomBarAutoHideDelay =
        remember(onEvent) {
            {
                    seconds: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetBottomBarAutoHideDelay(seconds)))
            }
        },
        onSetProgressBarHeight =
        remember(onEvent) {
            {
                    height: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetProgressBarHeight(height)))
            }
        },
        onSetNavBarHeight =
        remember(onEvent) {
            {
                    height: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetNavBarHeight(height)))
            }
        },
        onToggleConfirmDelete =
        remember(onEvent) {
            {
                    confirm: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleConfirmDelete(confirm)))
            }
        }
    )
}
