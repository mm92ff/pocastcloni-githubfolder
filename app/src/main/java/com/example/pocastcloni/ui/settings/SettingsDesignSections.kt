package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.Constants.SettingsDefaults

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionAppearance(
    theme: AppTheme,
    appColor: AppColor,
    colorStrength: Float,
    gradientBackgroundEnabled: Boolean,
    gradientBackgroundStrength: Float,
    transparentSearchCards: Boolean,
    transparentEpisodeRows: Boolean,
    onSetTheme: (AppTheme) -> Unit,
    onSetAppColor: (AppColor) -> Unit,
    onSetColorStrength: (Float) -> Unit,
    onToggleGradientBackground: (Boolean) -> Unit,
    onSetGradientBackgroundStrength: (Float) -> Unit,
    onToggleTransparentSearchCards: (Boolean) -> Unit,
    onToggleTransparentEpisodeRows: (Boolean) -> Unit
) {
    Text(
        stringResource(R.string.settings_section_design),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ThemeChip(stringResource(R.string.theme_system), theme == AppTheme.SYSTEM) { onSetTheme(AppTheme.SYSTEM) }
            ThemeChip(stringResource(R.string.theme_light), theme == AppTheme.LIGHT) { onSetTheme(AppTheme.LIGHT) }
            ThemeChip(stringResource(R.string.theme_dark), theme == AppTheme.DARK) { onSetTheme(AppTheme.DARK) }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard {
        Text(stringResource(R.string.settings_app_color), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            AppColor.entries.forEach { color ->
                ColorCircle(color.hexValue, appColor == color) { onSetAppColor(color) }
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_gradient_background),
        subtitle = stringResource(R.string.settings_gradient_background_subtitle),
        checked = gradientBackgroundEnabled,
        onCheckedChange = onToggleGradientBackground
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    if (gradientBackgroundEnabled) {
        SettingsSliderCard(
            title = stringResource(R.string.settings_gradient_strength),
            value = (gradientBackgroundStrength.coerceIn(0f, 1f) * 100).toInt(),
            valueRange = 0f..100f,
            steps = 9,
            onValueChangeFinished = { onSetGradientBackgroundStrength(it / 100f) },
            valueDisplay = { Text(text = stringResource(R.string.settings_percentage, it.toFloat())) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
    }

    SettingsSwitchCard(
        title = stringResource(R.string.settings_transparent_search_cards),
        subtitle = stringResource(R.string.settings_transparent_search_cards_subtitle),
        checked = transparentSearchCards,
        onCheckedChange = onToggleTransparentSearchCards
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_transparent_episode_rows),
        subtitle = stringResource(R.string.settings_transparent_episode_rows_subtitle),
        checked = transparentEpisodeRows,
        onCheckedChange = onToggleTransparentEpisodeRows
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    var localStrength by remember(colorStrength) { mutableFloatStateOf(colorStrength) }
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.settings_color_strength), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.settings_percentage, localStrength * 100))
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        Slider(
            value = localStrength,
            onValueChange = { localStrength = it },
            onValueChangeFinished = { onSetColorStrength(localStrength) },
            valueRange = 0.0f..1.0f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionIndicator(
    gridSizeDp: Int,
    indicatorState: IndicatorSettingsUiState,
    onColorClick: (Long) -> Unit,
    onSizeChange: (Int) -> Unit,
    onBorderChange: (Int) -> Unit,
    onXOffsetChange: (Int) -> Unit,
    onYOffsetChange: (Int) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_notification_dot),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            Constants.UI.INDICATOR_COLORS.forEach { colorArg ->
                IndicatorColorCircle(
                    colorArgb = colorArg,
                    isSelected = indicatorState.colorArgb == colorArg,
                    onClick = { onColorClick(colorArg) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        NotificationDotPreview(
            indicatorState = indicatorState,
            gridSizeDp = gridSizeDp
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        SettingsSliderCard(
            title = stringResource(R.string.settings_size),
            value = indicatorState.size,
            valueRange = SettingsDefaults.MIN_INDICATOR_SIZE_DP..SettingsDefaults.MAX_INDICATOR_SIZE_DP,
            onValueChangeFinished = onSizeChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        SettingsSliderCard(
            title = stringResource(R.string.settings_border_width),
            value = indicatorState.borderWidth,
            valueRange = SettingsDefaults.MIN_INDICATOR_BORDER_DP..SettingsDefaults.MAX_INDICATOR_BORDER_DP,
            onValueChangeFinished = onBorderChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        SettingsSliderCard(
            title = stringResource(R.string.settings_horizontal_offset),
            value = indicatorState.xOffset,
            valueRange = SettingsDefaults.MIN_INDICATOR_OFFSET_DP..SettingsDefaults.MAX_INDICATOR_OFFSET_DP,
            onValueChangeFinished = onXOffsetChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        SettingsSliderCard(
            title = stringResource(R.string.settings_vertical_offset),
            value = indicatorState.yOffset,
            valueRange = SettingsDefaults.MIN_INDICATOR_OFFSET_DP..SettingsDefaults.MAX_INDICATOR_OFFSET_DP,
            onValueChangeFinished = onYOffsetChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )
    }
}

@Composable
fun IndicatorColorCircle(
    colorArgb: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val color = Color(colorArgb)
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)

    Box(
        modifier =
        Modifier
            .size(Dimens.ColorCircleSize)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (luminance > 0.5) Color.Black else Color.White,
                modifier =
                Modifier
                    .size(Dimens.CheckIconSize)
                    .align(Alignment.Center)
            )
        }
    }
}
