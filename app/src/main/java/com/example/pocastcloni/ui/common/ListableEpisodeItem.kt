package com.example.pocastcloni.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.theme.Dimens

@Composable
fun ListableEpisodeItem(
    episode: EpisodeDisplayModel,
    podcast: Podcast?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPublishDate: Boolean = false,
    transparentBackground: Boolean = false,
    onImageClick: (() -> Unit)? = null // Optional callback for image click
) {
    val publishDate =
        remember(showPublishDate, episode.pubDateMs) {
            if (showPublishDate) {
                formatEpisodePublishDateOrNull(episode.pubDateMs)
            } else {
                null
            }
        }

    val primaryTextColor by TransparentSurfaceDefaults.animatedContentColor(
        transparent = transparentBackground,
        filledColor = MaterialTheme.colorScheme.onSurface,
        label = "episodeRowPrimaryTextColor"
    )
    val secondaryTextColor by TransparentSurfaceDefaults.animatedSecondaryTextColor(
        transparent = transparentBackground,
        label = "episodeRowSecondaryTextColor"
    )
    val rowBackground by TransparentSurfaceDefaults.animatedContainerColor(
        transparent = transparentBackground,
        filledColor = MaterialTheme.colorScheme.surface,
        label = "episodeRowBackground"
    )
    val dividerColor by TransparentSurfaceDefaults.animatedDividerColor(label = "episodeRowDivider")

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(rowBackground)
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingLarge, vertical = Dimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = episode.podcastImageUrl ?: podcast?.imageUrl ?: "",
                contentDescription = null,
                modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(Dimens.RoundedCornerSmall))
                    // Make clickable if a callback is provided
                    .clickable(enabled = onImageClick != null) { onImageClick?.invoke() },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(Dimens.PaddingLarge))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Dimens.PaddingExtraSmall))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = episode.podcastTitle ?: podcast?.title ?: stringResource(id = R.string.unknown_podcast_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (publishDate != null) {
                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                        Text(
                            text = publishDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (transparentBackground) {
            HorizontalDivider(
                color = dividerColor,
                thickness = Dimens.ThicknessDefault
            )
        }
    }
}
