package com.example.pocastcloni.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PlayerContainer(
    modifier: Modifier = Modifier,
    progressBarHeight: Dp,
    navBarHeight: Dp,
    onNavigateToPodcastDetail: (podcastUrl: String) -> Unit,
    suppress: Boolean = false
) {
    val viewModel: PlayerViewModel = hiltViewModel()

    // WICHTIG: Der Zugriff auf 'playerState' triggert die automatische Verbindung (Reaktive Architektur)
    // Siehe AudioPlayerController.kt -> onStart { connectInternal() }
    val playerState by viewModel.playerController.playerState.collectAsStateWithLifecycle()
    val playbackStateFlow = viewModel.playerController.playbackState

    // NEU: Wir holen die UI-Daten für die Beschreibung direkt aus dem ViewModel
    val episodeDescription by viewModel.descriptionState.collectAsStateWithLifecycle()
    val isDescriptionVisible by viewModel.isDescriptionVisible.collectAsStateWithLifecycle()

    var isExpanded by rememberSaveable { mutableStateOf(false) }

    // Zeige Player nur, wenn eine Episode geladen ist UND dieser Screen den Player nicht unterdrückt
    if (!suppress && !playerState.currentEpisodeGuid.isNullOrBlank()) {
        ExpandablePlayer(
            modifier = modifier,
            isExpanded = isExpanded,
            onExpandToggle = { isExpanded = !isExpanded },
            playerState = playerState,
            playbackStateFlow = playbackStateFlow,
            // NEU: Parameter weitergeben
            episodeDescription = episodeDescription,
            isDescriptionVisible = isDescriptionVisible,
            onEvent = viewModel::handlePlayerEvent,
            onNavigateToPodcastDetail = onNavigateToPodcastDetail,
            progressBarHeight = progressBarHeight,
            navBarHeight = navBarHeight
        )
    }
}
