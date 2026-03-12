package com.example.pocastcloni.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import kotlinx.coroutines.flow.StateFlow

private const val TEXT_COLUMN_WEIGHT = 1f
private const val TEXT_MAX_LINES = 1

@Composable
fun MiniPlayer(
    playerState: PlayerUiState,
    playbackStateFlow: StateFlow<PlaybackState>,
    onEvent: (PlayerScreenEvent) -> Unit,
    progressBarHeight: Dp,
    modifier: Modifier = Modifier,
    onCoverClick: () -> Unit,
    onExpand: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingVerySmall)
            .clickable(onClick = onExpand),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.MiniPlayerElevation),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            MiniPlayerContent(
                playerState = playerState,
                onEvent = onEvent,
                onCoverClick = onCoverClick
            )

            // OPTIMIZED PROGRESS BAR
            MiniPlayerProgressBar(
                playbackStateFlow = playbackStateFlow,
                isPlaying = playerState.isPlaying, // WICHTIG für Interpolation
                progressBarHeight = progressBarHeight,
                onSeek = { onEvent(PlayerScreenEvent.SeekTo(it)) },
                onSeekStart = { onEvent(PlayerScreenEvent.SeekStarted) },
                onSeekEnd = { onEvent(PlayerScreenEvent.SeekFinished) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    playbackStateFlow: StateFlow<PlaybackState>,
    isPlaying: Boolean,
    progressBarHeight: Dp,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1. State Object holen (vermeidet Recomposition bei jedem Tick im Body)
    val playbackStateState = playbackStateFlow.collectAsStateWithLifecycle()

    // 2. Interpolation Hook nutzen (60 FPS Smoothness)
    val smoothState = rememberSmoothedProgressState(
        playbackStateFlow = playbackStateFlow,
        isPlaying = isPlaying
    )

    // 3. Provider Lambdas für Phase-Awareness erstellen
    val currentPositionProvider = remember(smoothState) { { smoothState.currentPosition.value } }
    val bufferedPositionProvider = remember(playbackStateState) { { playbackStateState.value.bufferedPositionMs } }
    val durationProvider = remember(playbackStateState) { { playbackStateState.value.durationMs } }

    // 4. Seek Wrapper für sofortiges Feedback
    val onSeekWrapped: (Long) -> Unit = remember(onSeek, smoothState) {
        { newPos ->
            smoothState.jumpTo(newPos)
            onSeek(newPos)
        }
    }

    // Nur zeichnen, wenn Duration bekannt ist, sonst flackert es beim Laden
    // Wir prüfen hier den State Value direkt im if, das ist okay.
    if (playbackStateState.value.durationMs > 0) {
        CustomProgressBar(
            currentPositionMs = currentPositionProvider,
            bufferedPositionMs = bufferedPositionProvider,
            durationMs = durationProvider,
            height = progressBarHeight,
            color = MaterialTheme.colorScheme.primary,
            onSeek = onSeekWrapped,
            onSeekStart = onSeekStart,
            onSeekEnd = onSeekEnd,
            modifier = modifier
        )
    }
}

@Composable
private fun MiniPlayerContent(
    playerState: PlayerUiState,
    onEvent: (PlayerScreenEvent) -> Unit,
    onCoverClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.PaddingVerySmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TODO: Replace android.R.drawable.ic_menu_gallery with a proper drawable resource from the project.
        AsyncImage(
            model = playerState.coverUrl.ifBlank { android.R.drawable.ic_menu_gallery },
            contentDescription = stringResource(R.string.desc_cover),
            modifier = Modifier
                .size(Dimens.MiniPlayerImageSize)
                .clip(RoundedCornerShape(Dimens.RoundedCornerSmall))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onCoverClick),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))

        Column(modifier = Modifier.weight(TEXT_COLUMN_WEIGHT)) {
            Text(
                text = playerState.currentEpisodeTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playerState.currentEpisodeSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = TEXT_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.size(Dimens.LargeIconSize), contentAlignment = Alignment.Center) {
            if (playerState.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.MediumIconSize))
            } else {
                IconButton(onClick = { onEvent(PlayerScreenEvent.TogglePlayPause) }) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (playerState.isPlaying) R.string.desc_pause else R.string.desc_play),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}