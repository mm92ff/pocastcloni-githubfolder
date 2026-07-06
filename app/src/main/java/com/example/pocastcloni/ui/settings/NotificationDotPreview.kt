package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.ui.home.feed.IndicatorCutout
import com.example.pocastcloni.ui.home.feed.IndicatorDot
import com.example.pocastcloni.ui.theme.Dimens
import kotlin.math.floor

@Composable
fun NotificationDotPreview(
    indicatorState: IndicatorSettingsUiState,
    gridSizeDp: Int,
    modifier: Modifier = Modifier
) {
    val displayTileSize = Dimens.IndicatorPreviewTileSize

    // ---- effective cell size matching GridCells.Adaptive(minSize = gridSize.dp) ----
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    val horizontalContentPadding = Dimens.PaddingLarge // HomeScreen contentPadding start/end
    val horizontalSpacing = Dimens.PaddingMedium // HomeScreen spacedBy(...)
    val minCellSize = gridSizeDp.dp

    val availableWidth =
        (screenWidthDp.value - (horizontalContentPadding.value * 2f)).coerceAtLeast(0f)

    val minPlusSpacing = (minCellSize.value + horizontalSpacing.value).coerceAtLeast(1f)
    val columnsRaw = (availableWidth + horizontalSpacing.value) / minPlusSpacing
    val columns = floor(columnsRaw).toInt().coerceAtLeast(1)

    val cellSizeValue =
        ((availableWidth - horizontalSpacing.value * (columns - 1)) / columns).coerceAtLeast(1f)
    val baseTileSize = cellSizeValue.dp

    // Scale factor for the miniature (all elements proportional: tile + padding + spacing)
    val scaleFactor = (displayTileSize / baseTileSize)

    val scaledContentPadding = Dimens.PaddingLarge * scaleFactor
    val scaledSpacing = Dimens.PaddingMedium * scaleFactor

    // ---- Stage background covers the entire preview area (hides the card grey) ----
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(Dimens.IndicatorPreviewBoxHeight)
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(Dimens.RoundedCornerExtraLarge)
            )
            .padding(scaledContentPadding),
        contentAlignment = Alignment.Center
    ) {
        // Small "grid excerpt" (2 tiles) -> shows spacing/background as it appears in the Home screen
        Row(
            horizontalArrangement = Arrangement.spacedBy(scaledSpacing),
            verticalAlignment = Alignment.Top
        ) {
            // Tile 1: with indicator dot (current settings)
            ScaledFixedSizeBox(baseSize = baseTileSize, displaySize = displayTileSize) {
                PreviewTile(withDot = true, indicatorState = indicatorState)
            }

            // Tile 2: dummy with dot (context for spacing/background only)
            ScaledFixedSizeBox(baseSize = baseTileSize, displaySize = displayTileSize) {
                PreviewTile(withDot = true, indicatorState = indicatorState)
            }
        }
    }
}

@Composable
private fun PreviewTile(
    withDot: Boolean,
    indicatorState: IndicatorSettingsUiState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            Card(
                shape = RoundedCornerShape(Dimens.RoundedCornerLarge),
                elevation = CardDefaults.cardElevation(defaultElevation = Dimens.PaddingSmall)
            ) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            if (withDot) {
                IndicatorCutout(
                    xOffset = indicatorState.xOffset,
                    yOffset = indicatorState.yOffset,
                    borderWidth = indicatorState.borderWidth,
                    size = indicatorState.size
                )
            }
        }

        if (withDot) {
            IndicatorDot(
                xOffset = indicatorState.xOffset,
                yOffset = indicatorState.yOffset,
                borderWidth = indicatorState.borderWidth,
                size = indicatorState.size,
                colorArgb = indicatorState.colorArgb
            )
        }
    }
}

@Composable
private fun ScaledFixedSizeBox(
    baseSize: Dp,
    displaySize: Dp,
    content: @Composable BoxScope.() -> Unit
) {
    val safeBase = if (baseSize.value <= 0f) 1.dp else baseSize
    val scale = (displaySize / safeBase)

    val density = LocalDensity.current
    val basePx = with(density) { safeBase.roundToPx().coerceAtLeast(1) }
    val displayPx = with(density) { displaySize.roundToPx().coerceAtLeast(1) }

    Layout(
        modifier = Modifier.size(displaySize),
        content = {
            Box(
                modifier =
                Modifier
                    .size(safeBase)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                content = content
            )
        }
    ) { measurables, _ ->
        val placeable = measurables.first().measure(Constraints.fixed(basePx, basePx))
        layout(displayPx, displayPx) {
            placeable.place(0, 0)
        }
    }
}
