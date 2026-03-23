package com.example.pocastcloni.ui.player

import android.text.Spanned
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.pocastcloni.ui.theme.Dimens
import kotlinx.coroutines.flow.StateFlow

private const val EXPAND_ANIMATION_DURATION_MS = 300

@Composable
fun ExpandablePlayer(
    playerState: PlayerUiState,
    playbackStateFlow: StateFlow<PlaybackState>,
    episodeDescription: Spanned?,
    isDescriptionVisible: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEvent: (PlayerScreenEvent) -> Unit,
    onNavigateToPodcastDetail: (podcastUrl: String) -> Unit,
    progressBarHeight: Dp,
    navBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
            .animateContentSize(animationSpec = tween(EXPAND_ANIMATION_DURATION_MS)),
        shadowElevation = Dimens.PaddingLarge,
        shape =
        if (isExpanded) {
            RoundedCornerShape(Dimens.Zero)
        } else {
            RoundedCornerShape(topStart = Dimens.PaddingLarge, topEnd = Dimens.PaddingLarge)
        },
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        if (isExpanded) {
            FullPlayerScreen(
                playerState = playerState,
                playbackStateFlow = playbackStateFlow,
                episodeDescription = episodeDescription,
                isDescriptionVisible = isDescriptionVisible,
                onCollapse = onExpandToggle,
                onEvent = onEvent,
                progressBarHeight = progressBarHeight,
                navBarHeight = navBarHeight
            )
        } else {
            MiniPlayer(
                playerState = playerState,
                playbackStateFlow = playbackStateFlow,
                onExpand = onExpandToggle,
                onCoverClick = {
                    playerState.currentPodcastUrl?.let {
                        onNavigateToPodcastDetail(it)
                    }
                },
                onEvent = onEvent,
                progressBarHeight = progressBarHeight
            )
        }
    }
}
