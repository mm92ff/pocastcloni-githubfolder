package com.example.pocastcloni.ui.home.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.home.detail.EpisodeListItem
import com.example.pocastcloni.ui.home.detail.EpisodeUiModel
import com.example.pocastcloni.ui.player.MiniPlayerLayoutDefaults
import com.example.pocastcloni.ui.theme.Dimens

// Local dialog specific to Downloads (move to common if reused elsewhere)
@Composable
fun DownloadsDeleteDialog(
    episodeTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(stringResource(R.string.dialog_delete_download_msg, episodeTitle)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.btn_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.episodeToDelete?.let { episode ->
        DownloadsDeleteDialog(
            episodeTitle = episode.title,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_downloads)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        if (uiState.episodes.isEmpty()) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_downloads),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            val bottomPadding =
                remember(uiState.isPlayerVisible, uiState.progressBarHeight) {
                    MiniPlayerLayoutDefaults.reservedBottomPadding(
                        isPlayerVisible = uiState.isPlayerVisible,
                        progressBarHeight = uiState.progressBarHeight,
                        extraPadding = Dimens.PaddingLarge
                    )
                }

            LazyColumn(
                modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding =
                PaddingValues(
                    top = Dimens.PaddingLarge,
                    bottom = bottomPadding
                ),
                reverseLayout = uiState.oneHandedMode
            ) {
                items(
                    items = uiState.episodes,
                    key = { it.guid },
                    contentType = { "download-episode" }
                ) { episode ->
                    val isPlaying = (episode.guid == uiState.currentPlayingGuid) && uiState.isPlayerPlaying

                    // PERFORMANCE FIX: stabilise lambdas
                    val onPlayClick = remember(episode) { { viewModel.playEpisode(episode) } }
                    val onFavoriteClick = remember(episode) { { viewModel.onFavoriteToggle(episode) } }

                    SwipeToDeleteBox(episode, viewModel) {
                        EpisodeListItem(
                            episode = episode,
                            isPlaying = isPlaying,
                            onPlayClick = onPlayClick,
                            onDownloadClick = { }, // No action needed in the Downloads screen
                            onTogglePlayed = { },
                            onToggleFavorite = onFavoriteClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteBox(
    episode: EpisodeUiModel,
    viewModel: DownloadsViewModel,
    content: @Composable () -> Unit
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    viewModel.deleteEpisode(episode)
                }
                false
            }
        )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isDeleting = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

            val color by animateColorAsState(
                targetValue =
                if (isDeleting) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    Color.Transparent
                },
                label = "background color animation"
            )
            val scale by animateFloatAsState(
                targetValue = if (isDeleting) 1.3f else 1.0f,
                label = "icon scale animation"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Dimens.PaddingLarge),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isDeleting) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.cd_delete_episode),
                        modifier = Modifier.scale(scale)
                    )
                }
            }
        },
        modifier = Modifier.padding(vertical = Dimens.PaddingSmall)
    ) {
        content()
    }
}
