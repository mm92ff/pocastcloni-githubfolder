package com.example.pocastcloni.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.pocastcloni.ui.theme.Dimens

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
    fun contentColor(
        transparent: Boolean,
        filledColor: Color
    ): Color = if (transparent) MaterialTheme.colorScheme.onBackground else filledColor

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
    fun borderColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = BORDER_ALPHA)

    @Composable
    fun dividerColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA)

    @Composable
    fun border(transparent: Boolean): BorderStroke? =
        if (transparent) {
            BorderStroke(Dimens.BorderWidthDefault, borderColor())
        } else {
            null
        }

    fun elevation(
        transparent: Boolean,
        filledElevation: Dp
    ): Dp = if (transparent) Dimens.Zero else filledElevation
}
