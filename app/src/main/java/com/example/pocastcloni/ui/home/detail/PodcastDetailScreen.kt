package com.example.pocastcloni.ui.home.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(
    viewModel: PodcastDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    if (uiState.isPodcastDescriptionDialogVisible) {
        PodcastDescriptionDialog(
            description = uiState.podcastDescription,
            onDismissRequest = { viewModel.onAction(PodcastDetailAction.DismissPodcastDescription) }
        )
    }

    // One-time events (Channel -> Flow)
    LaunchedEffect(snackbarHostState) {
        viewModel.userMessageFlow.collect { message ->
            val snackbarMessage = message.asString(context)
            snackbarHostState.showSnackbar(snackbarMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.podcastTitle,
                        maxLines = Constants.UI.APP_BAR_TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val state = uiState

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(Dimens.PaddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error.asString(context),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                val playerState = state.playerState
                val settings = state.appSettings

                val bottomPadding =
                    if (playerState.isPlayerVisible) {
                        Dimens.MiniPlayerImageSize + (Dimens.PaddingMedium * 2) + settings.progressBarHeight.dp
                    } else {
                        Dimens.Zero
                    }

                // Patch 3: Werte einmal ziehen (statt in jedem Item mehrfach)
                val playingGuid = playerState.currentPlayingGuid
                val isPlayerPlaying = playerState.isPlayerPlaying

                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    reverseLayout = settings.oneHandedMode
                ) {
                    item {
                        PodcastHeader(
                            imageUrl = state.podcastImageUrl,
                            title = state.podcastTitle,
                            description = state.podcastDescription,
                            isAutoDownloadEnabled = state.isAutoDownloadEnabled,
                            onToggleAutoDownload = { viewModel.onAction(PodcastDetailAction.ToggleAutoDownload(it)) },
                            onShowPodcastDescription = { viewModel.onAction(PodcastDetailAction.ShowPodcastDescription) }
                        )
                        HorizontalDivider()
                    }

                    items(state.episodes, key = { it.guid }) { episode ->
                        val guid = episode.guid

                        // Patch 3: stabile Callbacks (verhindert Recompose nur wegen neuer Lambda-Instanzen)
                        val onPlayClick =
                            remember(guid) {
                                { viewModel.onAction(PodcastDetailAction.PlayEpisode(guid)) }
                            }
                        val onDownloadClick =
                            remember(guid) {
                                { viewModel.onAction(PodcastDetailAction.ToggleDownload(guid)) }
                            }
                        val onTogglePlayed =
                            remember(guid) {
                                { viewModel.onAction(PodcastDetailAction.TogglePlayedStatus(guid)) }
                            }
                        val onToggleFavorite =
                            remember(guid) {
                                { viewModel.onAction(PodcastDetailAction.ToggleFavorite(guid)) }
                            }

                        // Patch 3: isPlaying wird pro Item nur neu berechnet, wenn sich Player-Status ändert
                        val isPlaying =
                            remember(guid, playingGuid, isPlayerPlaying) {
                                guid == playingGuid && isPlayerPlaying
                            }

                        EpisodeListItem(
                            episode = episode,
                            isPlaying = isPlaying,
                            onPlayClick = onPlayClick,
                            onDownloadClick = onDownloadClick,
                            onTogglePlayed = onTogglePlayed,
                            onToggleFavorite = onToggleFavorite
                        )
                        HorizontalDivider(
                            thickness = Dimens.ThicknessDefault,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
