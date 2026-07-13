package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.GradientDirection
import com.example.pocastcloni.ui.common.TransparentSurfaceDefaults
import com.example.pocastcloni.ui.home.feed.IndicatorCutout
import com.example.pocastcloni.ui.home.feed.IndicatorDot
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.ui.theme.gradientBackgroundBrush
import com.example.pocastcloni.ui.theme.isPocastCloniDarkTheme
import kotlin.math.roundToInt

@Composable
fun DesignPreviewCard(
    appColor: AppColor,
    theme: AppTheme,
    gradientBackgroundEnabled: Boolean,
    gradientBackgroundStrength: Float,
    gradientBackgroundDirection: GradientDirection,
    transparentCardsAndRows: Boolean,
    transparentBottomBar: Boolean,
    transparentMiniPlayer: Boolean,
    showMiniPlayerTimeOverlay: Boolean,
    progressBarHeight: Int,
    gridSize: Int,
    indicatorState: IndicatorSettingsUiState
) {
    SettingsCard {
        DesignPreviewStage(
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
    }
}

@Composable
private fun DesignPreviewStage(
    appColor: AppColor,
    theme: AppTheme,
    gradientBackgroundEnabled: Boolean,
    gradientBackgroundStrength: Float,
    gradientBackgroundDirection: GradientDirection,
    transparentCardsAndRows: Boolean,
    transparentBottomBar: Boolean,
    transparentMiniPlayer: Boolean,
    showMiniPlayerTimeOverlay: Boolean,
    progressBarHeight: Int,
    gridSize: Int,
    indicatorState: IndicatorSettingsUiState
) {
    val darkTheme = isPocastCloniDarkTheme(theme)
    val density = LocalDensity.current
    val stageHeight = 212.dp
    val stageShape = RoundedCornerShape(Dimens.RoundedCornerExtraLarge)
    val stageSize =
        with(density) {
            Size(
                width = 360.dp.toPx(),
                height = stageHeight.toPx()
            )
        }
    val backgroundBrush =
        if (gradientBackgroundEnabled) {
            gradientBackgroundBrush(
                appColor = appColor,
                darkTheme = darkTheme,
                strength = gradientBackgroundStrength,
                direction = gradientBackgroundDirection,
                rootSize = stageSize
            )
        } else {
            SolidColor(MaterialTheme.colorScheme.background)
        }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(stageHeight)
            .clip(stageShape)
            .background(backgroundBrush)
            .padding(Dimens.PaddingSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            PreviewPodcastTile(
                indicatorState = indicatorState.scaledForPreview(
                    previewTileSize = DesignPreviewPodcastTileSize,
                    sourceTileSize = gridSize.dp
                )
            )
            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
            PreviewEpisodeSummary(
                transparent = transparentCardsAndRows,
                modifier = Modifier.weight(1f)
            )
        }

        if (transparentBottomBar) {
            PreviewBottomHandle(
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-66).dp)
            )
        }

        PreviewMiniPlayer(
            transparent = transparentMiniPlayer,
            showTimeOverlay = showMiniPlayerTimeOverlay,
            progressBarHeight = progressBarHeight.dp.coercePreviewHeight(),
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun PreviewPodcastTile(
    indicatorState: IndicatorSettingsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(DesignPreviewPodcastTileSize)) {
            Box(
                modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                Box(
                    modifier =
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(Dimens.RoundedCornerLarge))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "KI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                IndicatorCutout(
                    xOffset = indicatorState.xOffset,
                    yOffset = indicatorState.yOffset,
                    borderWidth = indicatorState.borderWidth,
                    size = indicatorState.size
                )
            }
            IndicatorDot(
                xOffset = indicatorState.xOffset,
                yOffset = indicatorState.yOffset,
                borderWidth = indicatorState.borderWidth,
                size = indicatorState.size,
                colorArgb = indicatorState.colorArgb
            )
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingMicro))
        Text(
            text = "KI verstehen",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun IndicatorSettingsUiState.scaledForPreview(
    previewTileSize: Dp,
    sourceTileSize: Dp
): IndicatorSettingsUiState {
    val source = sourceTileSize.value.coerceAtLeast(previewTileSize.value)
    val scale = (previewTileSize.value / source).coerceIn(0.1f, 1f)

    return copy(
        colorArgb = colorArgb,
        size = (size * scale).roundToInt().coerceAtLeast(1),
        borderWidth = (borderWidth * scale).roundToInt().coerceAtLeast(0),
        xOffset = (xOffset * scale).roundToInt(),
        yOffset = (yOffset * scale).roundToInt()
    )
}

@Composable
private fun PreviewEpisodeSummary(
    transparent: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.RoundedCornerMedium)
    Card(
        modifier = modifier,
        shape = shape,
        border = if (transparent) PreviewSubtleBorder() else null,
        elevation = CardDefaults.cardElevation(defaultElevation = TransparentSurfaceDefaults.elevation(transparent, Dimens.Zero)),
        colors =
        CardDefaults.cardColors(
            containerColor =
            TransparentSurfaceDefaults.containerColor(
                transparent = transparent,
                filledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            ),
            contentColor = TransparentSurfaceDefaults.contentColor(transparent, MaterialTheme.colorScheme.onSurface)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.PaddingSmall, vertical = Dimens.PaddingVerySmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Dimens.RoundedCornerSmall))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
            )
            Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KI als Sicherheitsrisiko",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "KI verstehen",
                    style = MaterialTheme.typography.labelSmall,
                    color = TransparentSurfaceDefaults.secondaryTextColor(transparent),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PreviewMiniPlayer(
    transparent: Boolean,
    showTimeOverlay: Boolean,
    progressBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.RoundedCornerLarge)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        border = if (transparent) PreviewSubtleBorder() else null,
        colors =
        CardDefaults.cardColors(
            containerColor =
            TransparentSurfaceDefaults.containerColor(
                transparent = transparent,
                filledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ),
            contentColor = TransparentSurfaceDefaults.contentColor(transparent, MaterialTheme.colorScheme.onSurface)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = TransparentSurfaceDefaults.elevation(transparent, Dimens.Zero))
    ) {
        Column {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.PaddingVerySmall, vertical = Dimens.PaddingTiny),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Dimens.RoundedCornerSmall))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f))
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KI als Sicherheitsrisiko - Wie sich Chatbots ...",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "KI verstehen",
                        style = MaterialTheme.typography.labelSmall,
                        color = TransparentSurfaceDefaults.secondaryTextColor(transparent),
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.MediumIconSize)
                )
            }
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(progressBarHeight)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f))
            ) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth(0.38f)
                        .height(progressBarHeight)
                        .background(MaterialTheme.colorScheme.primary)
                )
                if (showTimeOverlay) {
                    Row(
                        modifier =
                        Modifier
                            .matchParentSize()
                            .padding(horizontal = Dimens.PaddingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("09:52", style = MaterialTheme.typography.labelSmall)
                        Text("40:33", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBottomHandle(
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
        modifier
            .width(48.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f))
    )
}

@Composable
private fun PreviewSubtleBorder() =
    BorderStroke(1.dp, TransparentSurfaceDefaults.borderColor().copy(alpha = 0.62f))

private fun Dp.coercePreviewHeight(): Dp = value.coerceIn(4f, 10f).dp

private val DesignPreviewPodcastTileSize = 56.dp
