package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants.SettingsDefaults
import kotlin.math.roundToInt

@Composable
@Suppress("LongMethod", "LongParameterList")
fun SectionInterface(
    layoutMode: LayoutMode,
    gridSize: Int,
    showGridTitles: Boolean,
    oneHandedMode: Boolean,
    bottomBarCleanModeEnabled: Boolean,
    bottomBarAutoHideEnabled: Boolean,
    bottomBarAutoHideDelaySeconds: Int,
    bottomBarRevealHandleHeight: Int,
    progressBarHeight: Int,
    navBarHeight: Int,
    homeBottomSpacing: Int,
    confirmDelete: Boolean,
    onSetLayoutMode: (LayoutMode) -> Unit,
    onSetGridSize: (Int) -> Unit,
    onToggleShowGridTitles: (Boolean) -> Unit,
    onToggleOneHandedMode: (Boolean) -> Unit,
    onToggleBottomBarCleanMode: (Boolean) -> Unit,
    onToggleBottomBarAutoHide: (Boolean) -> Unit,
    onSetBottomBarAutoHideDelay: (Int) -> Unit,
    onSetBottomBarRevealHandleHeight: (Int) -> Unit,
    onSetProgressBarHeight: (Int) -> Unit,
    onSetNavBarHeight: (Int) -> Unit,
    onSetHomeBottomSpacing: (Int) -> Unit,
    onToggleConfirmDelete: (Boolean) -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_section_interface))

    AppLanguageSettings()

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard {
        Text(stringResource(R.string.settings_layout_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LayoutChip(
                label = stringResource(R.string.layout_grid),
                selected = layoutMode == LayoutMode.GRID,
                onClick = { onSetLayoutMode(LayoutMode.GRID) },
                icon = Icons.Default.GridView
            )
            LayoutChip(
                label = stringResource(R.string.layout_list),
                selected = layoutMode == LayoutMode.LIST,
                onClick = { onSetLayoutMode(LayoutMode.LIST) },
                icon = Icons.AutoMirrored.Filled.ViewList
            )
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        if (layoutMode == LayoutMode.GRID) {
            SettingsSliderCard(
                title = stringResource(R.string.settings_tile_size),
                value = gridSize,
                valueRange = SettingsDefaults.MIN_GRID_SIZE_DP..SettingsDefaults.MAX_GRID_SIZE_DP,
                onValueChangeFinished = onSetGridSize,
                valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        }

        SettingsSwitchCard(
            title = stringResource(R.string.settings_show_titles),
            checked = showGridTitles,
            onCheckedChange = onToggleShowGridTitles
        )
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSliderCard(
        title = stringResource(R.string.settings_home_bottom_spacing),
        value = homeBottomSpacing,
        valueRange =
        SettingsDefaults.MIN_HOME_BOTTOM_SPACING_DP..SettingsDefaults.MAX_HOME_BOTTOM_SPACING_DP,
        steps =
        (
            (SettingsDefaults.MAX_HOME_BOTTOM_SPACING_DP - SettingsDefaults.MIN_HOME_BOTTOM_SPACING_DP) /
                SettingsDefaults.HOME_BOTTOM_SPACING_STEP_DP - 1
            ).toInt(),
        onValueChangeFinished = onSetHomeBottomSpacing,
        valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) },
        valueMapping =
        SettingsSliderValueMapping(
            toSliderPosition = { it.toFloat() },
            toValue = { it.roundToInt() },
            stateDescription = { stringResource(R.string.settings_unit_dp, it) }
        )
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_one_handed_mode),
        subtitle = stringResource(R.string.settings_one_handed_mode_subtitle),
        checked = oneHandedMode,
        onCheckedChange = onToggleOneHandedMode
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_bottom_bar_clean_mode),
        subtitle = stringResource(R.string.settings_bottom_bar_clean_mode_subtitle),
        checked = bottomBarCleanModeEnabled,
        onCheckedChange = onToggleBottomBarCleanMode
    )

    if (bottomBarCleanModeEnabled) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_bottom_bar_reveal_handle_height),
            value = bottomBarRevealHandleHeight,
            valueRange =
            with(SettingsDefaults) {
                MIN_BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_DP..MAX_BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_DP
            },
            steps =
            with(SettingsDefaults) {
                (
                    (MAX_BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_DP - MIN_BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_DP) /
                        BOTTOM_BAR_REVEAL_HANDLE_HEIGHT_STEP_DP - 1
                    ).toInt()
            },
            onValueChangeFinished = onSetBottomBarRevealHandleHeight,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSwitchCard(
            title = stringResource(R.string.settings_bottom_bar_auto_hide),
            subtitle = stringResource(R.string.settings_bottom_bar_auto_hide_subtitle),
            checked = bottomBarAutoHideEnabled,
            onCheckedChange = onToggleBottomBarAutoHide
        )

        if (bottomBarAutoHideEnabled) {
            Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
            SettingsSliderCard(
                title = stringResource(R.string.settings_bottom_bar_auto_hide_delay),
                value = bottomBarAutoHideDelaySeconds,
                valueRange =
                SettingsDefaults.MIN_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS..SettingsDefaults.MAX_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS,
                steps =
                (
                    SettingsDefaults.MAX_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS -
                        SettingsDefaults.MIN_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS - 1
                    ).toInt(),
                onValueChangeFinished = onSetBottomBarAutoHideDelay,
                valueDisplay = { Text(stringResource(R.string.settings_unit_seconds, it)) }
            )
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSliderCard(
        title = stringResource(R.string.settings_progress_bar_height),
        value = progressBarHeight,
        valueRange = SettingsDefaults.MIN_PROGRESS_BAR_HEIGHT_DP..SettingsDefaults.MAX_PROGRESS_BAR_HEIGHT_DP,
        onValueChangeFinished = onSetProgressBarHeight,
        valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
    )

    if (oneHandedMode) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_navigation_bar_height),
            value = navBarHeight,
            valueRange = SettingsDefaults.MIN_NAV_BAR_HEIGHT_DP..SettingsDefaults.MAX_NAV_BAR_HEIGHT_DP,
            onValueChangeFinished = onSetNavBarHeight,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_confirm_delete),
        subtitle = stringResource(R.string.settings_confirm_delete_subtitle),
        checked = confirmDelete,
        onCheckedChange = onToggleConfirmDelete
    )
}

@Composable
fun SectionPlayback(
    markPlayedDurationSeconds: Int,
    showMiniPlayerTimeOverlay: Boolean,
    transparentMiniPlayer: Boolean,
    bufferMode: BufferMode,
    onSetMarkPlayedDuration: (Int) -> Unit,
    onToggleMiniPlayerTimeOverlay: (Boolean) -> Unit,
    onToggleTransparentMiniPlayer: (Boolean) -> Unit,
    onSetBufferMode: (BufferMode) -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_section_playback))

    SettingsSliderCard(
        title = stringResource(R.string.settings_mark_as_played_after),
        value = markPlayedDurationSeconds,
        valueRange = SettingsDefaults.MIN_MARK_PLAYED_DURATION_SECONDS..SettingsDefaults.MAX_MARK_PLAYED_DURATION_SECONDS,
        steps = (SettingsDefaults.MAX_MARK_PLAYED_DURATION_SECONDS - SettingsDefaults.MIN_MARK_PLAYED_DURATION_SECONDS - 1).toInt(),
        onValueChangeFinished = onSetMarkPlayedDuration,
        valueDisplay = {
            val seconds = it
            if (seconds == 0) {
                Text(stringResource(R.string.settings_mark_as_played_percentage))
            } else {
                Text(stringResource(R.string.settings_unit_seconds, seconds))
            }
        }
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_mini_player_time_overlay),
        subtitle = stringResource(R.string.settings_mini_player_time_overlay_subtitle),
        checked = showMiniPlayerTimeOverlay,
        onCheckedChange = onToggleMiniPlayerTimeOverlay
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_transparent_mini_player),
        subtitle = stringResource(R.string.settings_transparent_mini_player_subtitle),
        checked = transparentMiniPlayer,
        onCheckedChange = onToggleTransparentMiniPlayer
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard {
        Text(stringResource(R.string.settings_buffer_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSetBufferMode(BufferMode.NORMAL) }
        ) {
            RadioButton(
                selected = bufferMode == BufferMode.NORMAL,
                onClick = { onSetBufferMode(BufferMode.NORMAL) }
            )
            Text(
                stringResource(R.string.settings_buffer_mode_normal),
                modifier = Modifier.padding(start = Dimens.PaddingVerySmall)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSetBufferMode(BufferMode.MAXIMAL) }
        ) {
            RadioButton(
                selected = bufferMode == BufferMode.MAXIMAL,
                onClick = { onSetBufferMode(BufferMode.MAXIMAL) }
            )
            Text(
                stringResource(R.string.settings_buffer_mode_maximal),
                modifier = Modifier.padding(start = Dimens.PaddingVerySmall)
            )
        }
    }
}

@Composable
fun LayoutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.PaddingMedium)
            )
        }
    )
}
