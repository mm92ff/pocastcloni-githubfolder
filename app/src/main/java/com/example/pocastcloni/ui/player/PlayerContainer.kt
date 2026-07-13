package com.example.pocastcloni.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.domain.repository.UserSettings

@Composable
fun PlayerContainer(
    modifier: Modifier = Modifier,
    userSettings: UserSettings,
    progressBarHeight: Dp,
    navBarHeight: Dp,
    showMiniPlayerTimeOverlay: Boolean,
    transparentMiniPlayer: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNavigateToPodcastDetail: (podcastUrl: String) -> Unit,
    suppress: Boolean = false
) {
    val viewModel: PlayerViewModel = hiltViewModel()

    // Accessing 'playerState' triggers the automatic connection (reactive architecture).
    // See AudioPlayerController.kt -> onStart { connectInternal() }
    val playerState by viewModel.playerController.playerState.collectAsStateWithLifecycle()
    val playbackStateFlow = viewModel.playerController.playbackState

    val episodeDescription by viewModel.descriptionState.collectAsStateWithLifecycle()
    val isDescriptionVisible by viewModel.isDescriptionVisible.collectAsStateWithLifecycle()

    PlayerBackHandler(
        isExpanded = isExpanded && !suppress && playerState.currentEpisodeId != null,
        onCollapse = { onExpandedChange(false) }
    )

    // Show the player only when an episode is loaded and this screen does not suppress it
    if (!suppress && playerState.currentEpisodeId != null) {
        ExpandablePlayer(
            modifier = modifier,
            isExpanded = isExpanded,
            onExpandToggle = { onExpandedChange(!isExpanded) },
            playerState = playerState,
            userSettings = userSettings,
            playbackStateFlow = playbackStateFlow,
            episodeDescription = episodeDescription,
            isDescriptionVisible = isDescriptionVisible,
            onEvent = viewModel::handlePlayerEvent,
            onNavigateToPodcastDetail = onNavigateToPodcastDetail,
            progressBarHeight = progressBarHeight,
            showMiniPlayerTimeOverlay = showMiniPlayerTimeOverlay,
            transparentMiniPlayer = transparentMiniPlayer,
            navBarHeight = navBarHeight
        )
    }
}

@Composable
// This UI-emitting composable follows Compose's PascalCase naming convention.
@Suppress("FunctionNaming")
internal fun PlayerBackHandler(
    isExpanded: Boolean,
    onCollapse: () -> Unit
) {
    BackHandler(enabled = isExpanded, onBack = onCollapse)
}
