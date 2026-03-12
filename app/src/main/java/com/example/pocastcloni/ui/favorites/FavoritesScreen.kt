package com.example.pocastcloni.ui.favorites

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.common.EpisodeDetailsDialog
import com.example.pocastcloni.ui.common.ListableEpisodeItem
import com.example.pocastcloni.ui.common.ReorderableLazyColumn
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.episodeForDetails?.let { item ->
        EpisodeDetailsDialog(
            episodeTitle = item.episode.title,
            podcastTitle = item.podcast?.title ?: "",
            episodeDescription = item.episode.description,
            onDismissRequest = { viewModel.onAction(FavoritesAction.OnDismissEpisodeDetails) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.favorites)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.desc_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onAction(FavoritesAction.ToggleEditMode) }) {
                        Icon(
                            imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.edit)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.favorites.isEmpty()) {
                Text(text = stringResource(id = R.string.favorites_empty))
            } else {
                // PERFORMANCE: Padding-Berechnung cachen
                val bottomPadding = remember(
                    uiState.isPlayerVisible,
                    uiState.navBarHeight,
                    uiState.progressBarHeight
                ) {
                    if (uiState.isPlayerVisible) {
                        (uiState.navBarHeight + uiState.progressBarHeight).dp + Dimens.PaddingSmall
                    } else {
                        Dimens.PaddingSmall
                    }
                }

                ReorderableLazyColumn(
                    items = uiState.favorites, // Nimmt jetzt ImmutableList
                    key = { item -> item.id },
                    onReorder = { from, to -> viewModel.onAction(FavoritesAction.OnReorder(from, to)) },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = Dimens.PaddingSmall,
                        bottom = bottomPadding
                    ),
                    reverseLayout = uiState.oneHandedMode,
                    verticalArrangement = if (uiState.oneHandedMode) Arrangement.Bottom else Arrangement.Top
                ) { _, item, _ ->
                    // FIX: "isDragging" wird ignoriert, da die Animation jetzt intern im ReorderableLC passiert.
                    // SwipeToDelete wird nur aktiviert, wenn NICHT editiert wird (das bleibt gleich)

                        SwipeToDeleteFavorite(
                            onDelete = { viewModel.onAction(FavoritesAction.OnEpisodeSwiped(item.episode.guid)) },
                            enabled = !uiState.isEditMode
                        ) {
                            ListableEpisodeItem(
                                episode = item.episode,
                                podcast = item.podcast,
                                // FIX: Lambda fängt uiState nicht mehr ein. Die Logik "if (!isEditMode)"
                                // sollte idealerweise im ViewModel in onAction geprüft werden.
                                // Hier feuern wir einfach immer, das ViewModel entscheidet.
                                onClick = {
                                    viewModel.onAction(FavoritesAction.OnEpisodeClick(item.episode.guid))
                                },
                                onImageClick = { viewModel.onAction(FavoritesAction.OnEpisodeImageClick(item)) }
                            )
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteFavorite(
    onDelete: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    Color.Transparent
                },
                label = "background color animation"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Dimens.PaddingLarge),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.desc_delete_episode)
                )
            }
        },
        modifier = Modifier
    ) {
        content()
    }
}
