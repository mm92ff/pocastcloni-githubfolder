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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.theme.Dimens
import kotlinx.coroutines.flow.StateFlow

private const val EXPAND_ANIMATION_DURATION_MS = 300

@Composable
fun ExpandablePlayer(
    playerState: PlayerUiState,
    userSettings: UserSettings,
    playbackStateFlow: StateFlow<PlaybackState>,
    episodeDescription: Spanned?,
    isDescriptionVisible: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEvent: (PlayerScreenEvent) -> Unit,
    onNavigateToPodcastDetail: (podcastUrl: String) -> Unit,
    progressBarHeight: Dp,
    showMiniPlayerTimeOverlay: Boolean,
    navBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
            .animateContentSize(animationSpec = tween(EXPAND_ANIMATION_DURATION_MS)),
        shape =
        if (isExpanded) {
            RoundedCornerShape(Dimens.Zero)
        } else {
            RoundedCornerShape(topStart = Dimens.PaddingLarge, topEnd = Dimens.PaddingLarge)
        },
        shadowElevation = if (isExpanded) Dimens.Zero else Dimens.PaddingLarge,
        color = if (isExpanded) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isExpanded) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
    ) {
        if (isExpanded) {
            FullPlayerScreen(
                playerState = playerState,
                userSettings = userSettings,
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
                progressBarHeight = progressBarHeight,
                showTimeOverlay = showMiniPlayerTimeOverlay
            )
        }
    }
}
