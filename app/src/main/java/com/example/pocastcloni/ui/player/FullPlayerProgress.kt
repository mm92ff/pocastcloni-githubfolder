package com.example.pocastcloni.ui.player

import com.example.pocastcloni.playback.api.PlaybackState

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.appFormatLocale
import com.example.pocastcloni.util.formatTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Column as LayoutColumn

/**
 * Progress area containing the bar and time labels.
 * Spacing stays compact so the labels remain close to the bar.
 */
@Composable
fun FullPlayerProgressSection(
    playbackStateFlow: StateFlow<PlaybackState>,
    isPlaying: Boolean,
    progressBarHeight: Dp,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit
) {
    LayoutColumn {
        FullPlayerProgressBar(
            playbackStateFlow = playbackStateFlow,
            isPlaying = isPlaying,
            progressBarHeight = progressBarHeight,
            onSeek = onSeek,
            onSeekStart = onSeekStart,
            onSeekEnd = onSeekEnd
        )

        // Keep the time labels close to the progress bar.
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        FullPlayerTimeLabels(playbackStateFlow = playbackStateFlow)
    }
}

@Composable
fun FullPlayerProgressBar(
    playbackStateFlow: StateFlow<PlaybackState>,
    isPlaying: Boolean,
    progressBarHeight: Dp,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit
) {
    val playbackStateState = playbackStateFlow.collectAsStateWithLifecycle()

    val smoothState =
        rememberSmoothedProgressState(
            playbackStateFlow = playbackStateFlow,
            isPlaying = isPlaying
        )

    val currentPositionProvider = remember(smoothState) { { smoothState.currentPosition.value } }
    val bufferedPositionProvider = remember(playbackStateState) { { playbackStateState.value.bufferedPositionMs } }
    val durationProvider = remember(playbackStateState) { { playbackStateState.value.durationMs } }

    CustomProgressBar(
        currentPositionMs = currentPositionProvider,
        bufferedPositionMs = bufferedPositionProvider,
        durationMs = durationProvider,
        height = progressBarHeight,
        color = MaterialTheme.colorScheme.primary,
        onSeek = onSeek,
        isSeekable = playbackStateState.value.isSeekable,
        trackColor = Color.Transparent,
        bufferedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = Dimens.PROGRESS_BAR_BUFFERED_ALPHA),
        onSeekStart = onSeekStart,
        onSeekEnd = onSeekEnd
    )
}

private data class TimeSeconds(
    val positionSec: Long,
    val durationSec: Long
)

@Composable
fun FullPlayerTimeLabels(playbackStateFlow: StateFlow<PlaybackState>) {
    val formatLocale = LocalConfiguration.current.appFormatLocale()
    val secondsFlow =
        remember(playbackStateFlow) {
            playbackStateFlow
                .map { state ->
                    TimeSeconds(
                        positionSec = state.currentPositionMs / 1000L,
                        durationSec = state.durationMs / 1000L
                    )
                }
                .distinctUntilChanged()
        }

    val seconds by secondsFlow.collectAsStateWithLifecycle(
        initialValue = TimeSeconds(positionSec = 0L, durationSec = 0L)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTime(seconds.positionSec * 1000L, formatLocale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
        Text(
            text = formatTime(seconds.durationSec * 1000L, formatLocale),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )
    }
}
