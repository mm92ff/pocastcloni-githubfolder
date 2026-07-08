package com.example.pocastcloni.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.ui.theme.Motion

object TransparentSurfaceDefaults {
    private const val BORDER_ALPHA = 0.78f
    private const val DIVIDER_ALPHA = 0.48f
    private const val SECONDARY_TEXT_ALPHA = 0.72f

    @Composable
    fun containerColor(
        transparent: Boolean,
        filledColor: Color
    ): Color = if (transparent) Color.Transparent else filledColor

    @Composable
    fun animatedContainerColor(
        transparent: Boolean,
        filledColor: Color,
        label: String
    ): State<Color> =
        animateColorAsState(
            targetValue = containerColor(transparent, filledColor),
            animationSpec = Motion.stateSpec(),
            label = label
        )

    @Composable
    fun contentColor(
        transparent: Boolean,
        filledColor: Color
    ): Color = if (transparent) MaterialTheme.colorScheme.onBackground else filledColor

    @Composable
    fun animatedContentColor(
        transparent: Boolean,
        filledColor: Color,
        label: String
    ): State<Color> =
        animateColorAsState(
            targetValue = contentColor(transparent, filledColor),
            animationSpec = Motion.stateSpec(),
            label = label
        )

    @Composable
    fun secondaryTextColor(
        transparent: Boolean,
        filledColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
    ): Color =
        if (transparent) {
            MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_TEXT_ALPHA)
        } else {
            filledColor
        }

    @Composable
    fun animatedSecondaryTextColor(
        transparent: Boolean,
        filledColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        label: String
    ): State<Color> =
        animateColorAsState(
            targetValue = secondaryTextColor(transparent, filledColor),
            animationSpec = Motion.stateSpec(),
            label = label
        )

    @Composable
    fun borderColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = BORDER_ALPHA)

    @Composable
    fun animatedBorderColor(label: String): State<Color> =
        animateColorAsState(
            targetValue = borderColor(),
            animationSpec = Motion.stateSpec(),
            label = label
        )

    @Composable
    fun dividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)

    @Composable
    fun animatedDividerColor(label: String): State<Color> =
        animateColorAsState(
            targetValue = dividerColor(),
            animationSpec = Motion.stateSpec(),
            label = label
        )

    @Composable
    fun border(
        transparent: Boolean,
        label: String = "transparentSurfaceBorder"
    ): BorderStroke {
        val color by animateColorAsState(
            targetValue = if (transparent) borderColor() else Color.Transparent,
            animationSpec = Motion.stateSpec(),
            label = label
        )

        return BorderStroke(Dimens.BorderWidthDefault, color)
    }

    fun elevation(
        transparent: Boolean,
        filledElevation: Dp
    ): Dp = if (transparent) Dimens.Zero else filledElevation
}
