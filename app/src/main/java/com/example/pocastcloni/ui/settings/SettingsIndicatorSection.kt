package com.example.pocastcloni.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.ui.theme.bestContrastingColor
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.Constants.SettingsDefaults

private const val INDICATOR_GREEN_ARGB = 0xFF4CAF50
private const val INDICATOR_BLUE_ARGB = 0xFF2196F3
private const val INDICATOR_AMBER_ARGB = 0xFFFFC107
private const val INDICATOR_RED_ARGB = 0xFFF44336
private const val INDICATOR_PURPLE_ARGB = 0xFF9C27B0

@StringRes
private fun indicatorColorLabelRes(colorArgb: Long): Int =
    when (colorArgb) {
        INDICATOR_GREEN_ARGB -> R.string.color_green
        INDICATOR_BLUE_ARGB -> R.string.color_blue
        INDICATOR_AMBER_ARGB -> R.string.color_amber
        INDICATOR_RED_ARGB -> R.string.color_red
        INDICATOR_PURPLE_ARGB -> R.string.color_purple
        else -> R.string.color_slate
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
    SettingsSectionTitle(stringResource(R.string.settings_notification_dot))

    SettingsCard {
        Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            Constants.UI.INDICATOR_COLORS.forEach { colorArg ->
                IndicatorColorCircle(
                    colorArgb = colorArg,
                    colorName = stringResource(indicatorColorLabelRes(colorArg)),
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
    colorName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val color = Color(colorArgb)

    Box(
        modifier =
        Modifier
            .size(Dimens.ColorCircleSize)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, borderColor, CircleShape)
            .semantics { contentDescription = colorName }
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = bestContrastingColor(color),
                modifier =
                Modifier
                    .size(Dimens.CheckIconSize)
                    .align(Alignment.Center)
            )
        }
    }
}
