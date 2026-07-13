package com.example.pocastcloni.ui.home.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.common.formatEpisodeDuration
import com.example.pocastcloni.ui.common.formatEpisodePublishDate
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants
import java.util.Locale

@Composable
fun PodcastHeader(
    imageUrl: String?,
    title: String,
    description: String,
    isAutoDownloadEnabled: Boolean,
    onToggleAutoDownload: (Boolean) -> Unit,
    onShowPodcastDescription: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onBackground
    val secondaryContentColor = contentColor.copy(alpha = 0.78f)

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingMedium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.desc_podcast_cover),
                modifier =
                Modifier
                    .size(Dimens.PodcastImageSize)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onShowPodcastDescription),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )

                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = if (isAutoDownloadEnabled) MaterialTheme.colorScheme.primary else secondaryContentColor
                    )
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text(
                        text = stringResource(R.string.title_auto_download),
                        color = contentColor,
                        modifier = Modifier.weight(Constants.Weights.FULL)
                    )
                    Switch(checked = isAutoDownloadEnabled, onCheckedChange = onToggleAutoDownload)
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryContentColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EpisodeListItem(
    episode: EpisodeUiModel,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val formatLocale = Locale.getDefault(Locale.Category.FORMAT)
    val displayDate =
        remember(episode.pubDateEpochMs, episode.date, configuration, formatLocale) {
            if (episode.pubDateEpochMs > 0L) {
                formatEpisodePublishDate(episode.pubDateEpochMs, locale = formatLocale)
            } else {
                episode.date
            }
        }
    val displayDuration =
        remember(episode.durationSeconds, episode.duration, configuration, formatLocale) {
            if (episode.durationSeconds > 0L) {
                formatEpisodeDuration(context, episode.durationSeconds, formatLocale)
            } else {
                episode.duration
            }
        }
    val titleColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(Dimens.PaddingMedium)
    ) {
        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor
                )
                Spacer(modifier = Modifier.width(Dimens.PaddingTiny))
                Text(text = "·", style = MaterialTheme.typography.bodySmall, color = metadataColor)
                Spacer(modifier = Modifier.width(Dimens.PaddingTiny))
                Text(
                    text = displayDuration,
                    style = MaterialTheme.typography.bodySmall,
                    color = metadataColor
                )
            }

            EpisodeActions(
                isPlaying = isPlaying,
                isFavorite = episode.isFavorite,
                isPlayed = episode.isPlayed,
                downloadStatus = episode.downloadStatus,
                downloadProgress = episode.downloadProgress,
                onPlayClick = onPlayClick,
                onDownloadClick = onDownloadClick,
                onTogglePlayed = onTogglePlayed,
                onToggleFavorite = onToggleFavorite
            )
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        episode.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EpisodeActions(
    isPlaying: Boolean,
    isFavorite: Boolean,
    isPlayed: Boolean,
    downloadStatus: DownloadStatusUiModel,
    downloadProgress: Float,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Zero),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FavoriteActionButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
        PlayedActionButton(isPlayed = isPlayed, onTogglePlayed = onTogglePlayed)
        DownloadAction(
            downloadStatus = downloadStatus,
            downloadProgress = downloadProgress,
            onDownloadClick = onDownloadClick
        )
        PlayPauseActionButton(isPlaying = isPlaying, onPlayClick = onPlayClick)
    }
}

@Composable
private fun FavoriteActionButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)

    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(Dimens.ActionButtonSize)) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.desc_toggle_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else inactiveColor,
            modifier = Modifier.size(Dimens.ActionButtonIconSize)
        )
    }
}

@Composable
private fun PlayedActionButton(
    isPlayed: Boolean,
    onTogglePlayed: () -> Unit
) {
    val inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)

    IconButton(onClick = onTogglePlayed, modifier = Modifier.size(Dimens.ActionButtonSize)) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.desc_toggle_played),
            tint = if (isPlayed) MaterialTheme.colorScheme.primary else inactiveColor,
            modifier = Modifier.size(Dimens.ActionButtonIconSize)
        )
    }
}

@Composable
private fun DownloadAction(
    downloadStatus: DownloadStatusUiModel,
    downloadProgress: Float,
    onDownloadClick: () -> Unit
) {
    if (downloadStatus == DownloadStatusUiModel.DOWNLOADING) {
        Box(
            modifier = Modifier.size(Dimens.ActionButtonSize),
            contentAlignment = Alignment.Center
        ) {
            if (downloadProgress > 0f) {
                // Progress known -> determinate indicator
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(Dimens.ActionButtonIconSize),
                    strokeWidth = Dimens.ProgressIndicatorStrokeWidth
                )
            } else {
                // Progress unknown (0%) -> indeterminate spinner
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.ActionButtonIconSize),
                    strokeWidth = Dimens.ProgressIndicatorStrokeWidth
                )
            }
        }
    } else {
        val inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f)

        IconButton(onClick = onDownloadClick, modifier = Modifier.size(Dimens.ActionButtonSize)) {
            when (downloadStatus) {
                DownloadStatusUiModel.DOWNLOADED -> {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = stringResource(R.string.desc_episode_downloaded),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.ActionButtonIconSize)
                    )
                }
                DownloadStatusUiModel.NOT_DOWNLOADED -> {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = stringResource(R.string.desc_download_episode),
                        tint = inactiveColor,
                        modifier = Modifier.size(Dimens.ActionButtonIconSize)
                    )
                }
                else -> Unit // Should not happen due to if check above
            }
        }
    }
}

@Composable
private fun PlayPauseActionButton(
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    IconButton(onClick = onPlayClick, modifier = Modifier.size(Dimens.ActionButtonSize)) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.desc_play_pause),
            modifier = Modifier.size(Dimens.PlayPauseIconSize),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
