package com.example.pocastcloni.ui.home.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.home.feed.IndicatorCutout
import com.example.pocastcloni.ui.home.feed.IndicatorDot
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants

@Immutable
data class PodcastIndicatorStyle(
    val xOffset: Int,
    val yOffset: Int,
    val borderWidth: Int,
    val size: Int,
    val colorArgb: Long
)

@Composable
private fun rememberPodcastImageRequest(
    url: String,
    size: Int
): ImageRequest {
    val context = LocalContext.current
    return remember(url, context, size) {
        ImageRequest.Builder(context)
            .data(url.takeIf { it.isNotBlank() })
            .crossfade(true)
            .size(size)
            .precision(Precision.EXACT)
            .build()
    }
}

@Composable
private fun BoxScope.PodcastIndicatorCutout(
    podcast: Podcast,
    indicatorStyle: PodcastIndicatorStyle
) {
    if (podcast.hasNewEpisodes) {
        IndicatorCutout(
            xOffset = indicatorStyle.xOffset,
            yOffset = indicatorStyle.yOffset,
            borderWidth = indicatorStyle.borderWidth,
            size = indicatorStyle.size
        )
    }
}

@Composable
private fun BoxScope.PodcastIndicatorDot(
    podcast: Podcast,
    indicatorStyle: PodcastIndicatorStyle
) {
    if (podcast.hasNewEpisodes) {
        IndicatorDot(
            xOffset = indicatorStyle.xOffset,
            yOffset = indicatorStyle.yOffset,
            borderWidth = indicatorStyle.borderWidth,
            size = indicatorStyle.size,
            colorArgb = indicatorStyle.colorArgb
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastItem(
    podcast: Podcast,
    isEditMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit,
    indicatorStyle: PodcastIndicatorStyle
) {
    val animatedPadding by animateDpAsState(
        targetValue = if (isSelected) Dimens.Zero else Dimens.PaddingTiny,
        label = "padding"
    )

    val targetContainerColor = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val containerColor by animateColorAsState(targetContainerColor, label = "color")
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    val elevation = if (isSelected) Dimens.PaddingSix else Dimens.CardElevation
    val alpha = if (isEditMode && !isSelected) Constants.UI.EDIT_MODE_NON_SELECTED_ALPHA else 1f

    val clickableModifier =
        if (onLongClick != null) {
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        } else {
            Modifier.clickable(onClick = onClick)
        }

    Box(
        modifier =
        Modifier
            .padding(vertical = animatedPadding / 2, horizontal = animatedPadding)
    ) {
        Box(
            modifier =
            Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            Card(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .then(clickableModifier),
                shape = RoundedCornerShape(Dimens.RoundedCornerLarge),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                colors =
                CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                Row(
                    modifier = Modifier.padding(Dimens.PaddingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imageRequest = rememberPodcastImageRequest(podcast.imageUrl, Constants.Image.IMAGE_SIZE_LIST)

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(id = R.string.desc_cover),
                        modifier =
                        Modifier
                            .size(Dimens.PodcastItemImageSize)
                            .clip(RoundedCornerShape(Dimens.RoundedCornerMedium))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))

                    Column {
                        Text(
                            text = podcast.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (podcast.description.isNotBlank()) {
                            Text(
                                text = podcast.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = contentColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            PodcastIndicatorCutout(podcast = podcast, indicatorStyle = indicatorStyle)
        }

        PodcastIndicatorDot(podcast = podcast, indicatorStyle = indicatorStyle)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastGridItem(
    podcast: Podcast,
    isEditMode: Boolean,
    isSelected: Boolean,
    showGridTitles: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onDeleteClick: () -> Unit,
    indicatorStyle: PodcastIndicatorStyle
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) Dimens.BorderWidthSelected else Dimens.Zero
    val scale = if (isSelected) Constants.UI.GRID_ITEM_SELECTED_SCALE else 1f
    val alpha = if (isEditMode && !isSelected) Constants.UI.EDIT_MODE_NON_SELECTED_ALPHA_GRID else 1f

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                Card(
                    shape = RoundedCornerShape(Dimens.RoundedCornerLarge),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.PaddingSmall),
                    modifier = Modifier.border(borderWidth, borderColor, RoundedCornerShape(Dimens.RoundedCornerLarge))
                ) {
                    val imageRequest = rememberPodcastImageRequest(podcast.imageUrl, Constants.Image.IMAGE_SIZE_GRID)

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(id = R.string.desc_cover),
                        modifier =
                        Modifier
                            .aspectRatio(Constants.UI.ASPECT_RATIO_1F)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                }

                PodcastIndicatorCutout(podcast = podcast, indicatorStyle = indicatorStyle)
            }

            PodcastIndicatorDot(podcast = podcast, indicatorStyle = indicatorStyle)
        }

        if (showGridTitles) {
            Spacer(modifier = Modifier.height(Dimens.PaddingSix))
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = Constants.UI.GRID_ITEM_TITLE_MAX_LINES,
                minLines = Constants.UI.GRID_ITEM_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = Dimens.PaddingMicro)
            )
        }
    }
}
