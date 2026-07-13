package com.example.pocastcloni.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.ui.theme.Motion
import com.example.pocastcloni.ui.theme.bestContrastingColor
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.Constants.SettingsDefaults

private const val INDICATOR_GREEN_ARGB = 0xFF4CAF50
private const val INDICATOR_BLUE_ARGB = 0xFF2196F3
private const val INDICATOR_AMBER_ARGB = 0xFFFFC107
private const val INDICATOR_RED_ARGB = 0xFFF44336
private const val INDICATOR_PURPLE_ARGB = 0xFF9C27B0
private const val EIGHTH_TURN_DEGREES = 45f
private const val QUARTER_TURN_DEGREES = 90f
private const val THREE_EIGHTHS_TURN_DEGREES = 135f
private const val HALF_TURN_DEGREES = 180f

private val GradientDirection.labelRes: Int
    @StringRes
    get() =
        when (this) {
            GradientDirection.TOP_TO_BOTTOM -> R.string.settings_gradient_direction_top_to_bottom
            GradientDirection.BOTTOM_TO_TOP -> R.string.settings_gradient_direction_bottom_to_top
            GradientDirection.LEFT_TO_RIGHT -> R.string.settings_gradient_direction_left_to_right
            GradientDirection.RIGHT_TO_LEFT -> R.string.settings_gradient_direction_right_to_left
            GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> R.string.settings_gradient_direction_top_left_to_bottom_right
            GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT -> R.string.settings_gradient_direction_bottom_right_to_top_left
            GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT -> R.string.settings_gradient_direction_top_right_to_bottom_left
            GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> R.string.settings_gradient_direction_bottom_left_to_top_right
        }

private val AppColor.labelRes: Int
    @StringRes
    get() =
        when (this) {
            AppColor.GREEN -> R.string.color_green
            AppColor.RED -> R.string.color_red
            AppColor.BLUE -> R.string.color_blue
            AppColor.YELLOW -> R.string.color_yellow
            AppColor.PURPLE -> R.string.color_purple
            AppColor.ORANGE -> R.string.color_orange
            AppColor.TURQUOISE -> R.string.color_turquoise
        }

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

private val GradientDirection.rotationDegrees: Float
    get() =
        when (this) {
            GradientDirection.BOTTOM_TO_TOP -> 0f
            GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> EIGHTH_TURN_DEGREES
            GradientDirection.LEFT_TO_RIGHT -> QUARTER_TURN_DEGREES
            GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> THREE_EIGHTHS_TURN_DEGREES
            GradientDirection.TOP_TO_BOTTOM -> HALF_TURN_DEGREES
            GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT -> -THREE_EIGHTHS_TURN_DEGREES
            GradientDirection.RIGHT_TO_LEFT -> -QUARTER_TURN_DEGREES
            GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT -> -EIGHTH_TURN_DEGREES
        }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionAppearance(
    theme: AppTheme,
    appColor: AppColor,
    colorStrength: Float,
    gradientBackgroundEnabled: Boolean,
    gradientBackgroundStrength: Float,
    gradientBackgroundDirection: GradientDirection,
    transparentSearchCards: Boolean,
    transparentPodcastCards: Boolean,
    transparentEpisodeRows: Boolean,
    transparentBottomBar: Boolean,
    transparentMiniPlayer: Boolean,
    showMiniPlayerTimeOverlay: Boolean,
    progressBarHeight: Int,
    gridSize: Int,
    indicatorState: IndicatorSettingsUiState,
    onSetTheme: (AppTheme) -> Unit,
    onSetAppColor: (AppColor) -> Unit,
    onSetColorStrength: (Float) -> Unit,
    onToggleGradientBackground: (Boolean) -> Unit,
    onSetGradientBackgroundStrength: (Float) -> Unit,
    onSetGradientBackgroundDirection: (GradientDirection) -> Unit,
    onToggleTransparentCardsAndRows: (Boolean) -> Unit,
    onToggleTransparentBottomBar: (Boolean) -> Unit
) {
    val transparentCardsAndRows = transparentSearchCards && transparentPodcastCards && transparentEpisodeRows

    SettingsSectionTitle(stringResource(R.string.settings_section_design))

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
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            AppColor.entries.forEach { color ->
                ColorCircle(
                    colorHex = color.hexValue,
                    colorName = stringResource(color.labelRes),
                    isSelected = appColor == color,
                    onClick = { onSetAppColor(color) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    DesignPreviewCard(
        appColor = appColor,
        theme = theme,
        gradientBackgroundEnabled = gradientBackgroundEnabled,
        gradientBackgroundStrength = gradientBackgroundStrength,
        gradientBackgroundDirection = gradientBackgroundDirection,
        transparentCardsAndRows = transparentCardsAndRows,
        transparentBottomBar = transparentBottomBar,
        transparentMiniPlayer = transparentMiniPlayer,
        showMiniPlayerTimeOverlay = showMiniPlayerTimeOverlay,
        progressBarHeight = progressBarHeight,
        gridSize = gridSize,
        indicatorState = indicatorState
    )

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

        SettingsCard {
            Text(stringResource(R.string.settings_gradient_direction), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

            GradientDirectionPicker(
                selectedDirection = gradientBackgroundDirection,
                onDirectionSelected = onSetGradientBackgroundDirection
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

            Text(
                text = stringResource(gradientBackgroundDirection.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
    }

    SettingsSwitchCard(
        title = stringResource(R.string.settings_transparent_cards_rows),
        subtitle = stringResource(R.string.settings_transparent_cards_rows_subtitle),
        checked = transparentCardsAndRows,
        onCheckedChange = onToggleTransparentCardsAndRows
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_transparent_bottom_bar),
        subtitle = stringResource(R.string.settings_transparent_bottom_bar_subtitle),
        checked = transparentBottomBar,
        onCheckedChange = onToggleTransparentBottomBar
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

@Composable
private fun GradientDirectionPicker(
    selectedDirection: GradientDirection,
    onDirectionSelected: (GradientDirection) -> Unit
) {
    val grid =
        listOf(
            listOf(
                GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT,
                GradientDirection.BOTTOM_TO_TOP,
                GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
            ),
            listOf(
                GradientDirection.RIGHT_TO_LEFT,
                null,
                GradientDirection.LEFT_TO_RIGHT
            ),
            listOf(
                GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT,
                GradientDirection.TOP_TO_BOTTOM,
                GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT
            )
        )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GradientDirectionButtonGap)
    ) {
        grid.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GradientDirectionButtonGap)) {
                row.forEach { direction ->
                    if (direction == null) {
                        Box(modifier = Modifier.size(GradientDirectionButtonSize))
                    } else {
                        GradientDirectionButton(
                            direction = direction,
                            selected = selectedDirection == direction,
                            onClick = { onDirectionSelected(direction) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientDirectionButton(
    direction: GradientDirection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.RoundedCornerMedium)
    val backgroundColor by animateColorAsState(
        targetValue =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = Motion.stateSpec(),
        label = "gradientDirectionBackground"
    )
    val borderColor by animateColorAsState(
        targetValue =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
        },
        animationSpec = Motion.stateSpec(),
        label = "gradientDirectionBorder"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        animationSpec = Motion.stateSpec(),
        label = "gradientDirectionIcon"
    )

    Box(
        modifier =
        Modifier
            .size(GradientDirectionButtonSize)
            .clip(shape)
            .background(backgroundColor)
            .border(Dimens.BorderWidthDefault, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DirectionArrowIcon(
            rotationDegrees = direction.rotationDegrees,
            color = iconColor
        )
    }
}

@Composable
private fun DirectionArrowIcon(
    rotationDegrees: Float,
    color: Color
) {
    Canvas(modifier = Modifier.size(GradientDirectionArrowSize)) {
        val strokeWidth = GradientDirectionArrowStrokeWidth.toPx()
        val centerX = size.width / 2f
        val start = Offset(centerX, size.height * 0.78f)
        val end = Offset(centerX, size.height * 0.22f)
        val headSize = size.width * 0.22f

        rotate(degrees = rotationDegrees) {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = end,
                end = Offset(end.x - headSize, end.y + headSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = end,
                end = Offset(end.x + headSize, end.y + headSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private val GradientDirectionButtonSize = 64.dp
private val GradientDirectionButtonGap = 8.dp
private val GradientDirectionArrowSize = 30.dp
private val GradientDirectionArrowStrokeWidth = 2.4.dp

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
