package com.example.pocastcloni.ui.favorites

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.EpisodeDetailsDialog
import com.example.pocastcloni.ui.common.ListableEpisodeItem
import com.example.pocastcloni.ui.common.ReorderableLazyColumn
import com.example.pocastcloni.ui.player.MiniPlayerLayoutDefaults
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.ui.theme.Motion

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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.favorites)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.desc_back)
                        )
                    }
                },
                actions = {
                    if (uiState.sortMode == FavoritesSortMode.MANUAL) {
                        IconButton(onClick = { viewModel.onAction(FavoritesAction.ToggleEditMode) }) {
                            Icon(
                                imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.edit)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.favorites.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.favorites_empty),
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                // PERFORMANCE: cache the padding calculation
                val bottomPadding =
                    remember(uiState.isPlayerVisible, uiState.progressBarHeight) {
                        MiniPlayerLayoutDefaults.reservedBottomPadding(
                            isPlayerVisible = uiState.isPlayerVisible,
                            progressBarHeight = uiState.progressBarHeight,
                            extraPadding = Dimens.PaddingSmall
                        )
                    }

                Column(modifier = Modifier.fillMaxSize()) {
                    FavoritesSortModeSelector(
                        selectedMode = uiState.sortMode,
                        onModeSelected = { mode -> viewModel.onAction(FavoritesAction.ChangeSortMode(mode)) }
                    )

                    if (uiState.sortMode == FavoritesSortMode.MANUAL) {
                        ReorderableLazyColumn(
                            items = uiState.favorites, // Now accepts ImmutableList
                            key = { item -> item.id },
                            onReorder = { from, to -> viewModel.onAction(FavoritesAction.OnReorder(from, to)) },
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                            PaddingValues(
                                top = Dimens.PaddingSmall,
                                bottom = bottomPadding
                            ),
                            reverseLayout = uiState.oneHandedMode,
                            verticalArrangement = if (uiState.oneHandedMode) Arrangement.Bottom else Arrangement.Top
                        ) { _, item, _ ->
                            FavoriteEpisodeRow(
                                item = item,
                                swipeEnabled = !uiState.isEditMode,
                                transparentBackground = uiState.transparentEpisodeRows,
                                onAction = viewModel::onAction
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                            PaddingValues(
                                top = Dimens.PaddingSmall,
                                bottom = bottomPadding
                            ),
                            verticalArrangement = if (uiState.oneHandedMode) Arrangement.Bottom else Arrangement.Top
                        ) {
                            items(
                                items = uiState.dateGroupedRows,
                                key = { row -> row.key },
                                contentType = { row ->
                                    when (row) {
                                        is FavoriteListRow.SectionHeader -> "favorite-section"
                                        is FavoriteListRow.EpisodeRow -> "favorite-episode"
                                    }
                                }
                            ) { row ->
                                when (row) {
                                    is FavoriteListRow.SectionHeader -> {
                                        FavoriteSectionHeader(bucket = row.bucket)
                                    }

                                    is FavoriteListRow.EpisodeRow -> {
                                        FavoriteEpisodeRow(
                                            item = row.item,
                                            swipeEnabled = true,
                                            transparentBackground = uiState.transparentEpisodeRows,
                                            onAction = viewModel::onAction
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesSortModeSelector(
    selectedMode: FavoritesSortMode,
    onModeSelected: (FavoritesSortMode) -> Unit
) {
    val modes = listOf(FavoritesSortMode.MANUAL, FavoritesSortMode.ADDED_DATE)

    SingleChoiceSegmentedButtonRow(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PaddingLarge, vertical = Dimens.PaddingSmall)
    ) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) {
                Text(text = stringResource(id = mode.labelResId))
            }
        }
    }
}

@Composable
private fun FavoriteSectionHeader(bucket: DateBucket) {
    androidx.compose.foundation.layout.Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.PaddingMedium,
                vertical = Dimens.PaddingSmall
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = bucket.labelResId),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier =
            Modifier
                .padding(start = Dimens.PaddingSmall)
                .weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun FavoriteEpisodeRow(
    item: FavoriteUiItem,
    swipeEnabled: Boolean,
    transparentBackground: Boolean,
    onAction: (FavoritesAction) -> Unit
) {
    SwipeToDeleteFavorite(
        onDelete = { onAction(FavoritesAction.OnEpisodeSwiped(item.episode.episodeId)) },
        enabled = swipeEnabled
    ) {
        ListableEpisodeItem(
            episode = item.episode,
            podcast = item.podcast,
            showPublishDate = true,
            transparentBackground = transparentBackground,
            onClick = {
                onAction(FavoritesAction.OnEpisodeClick(item.episode.episodeId))
            },
            onImageClick = { onAction(FavoritesAction.OnEpisodeImageClick(item)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteFavorite(
    onDelete: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
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
            val isDeleting =
                dismissState.targetValue == SwipeToDismissBoxValue.EndToStart ||
                    dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                targetValue = if (isDeleting) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
                animationSpec = Motion.stateSpec(),
                label = "background color animation"
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
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.desc_delete_episode),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        modifier = Modifier
    ) {
        content()
    }
}

private val FavoritesSortMode.labelResId: Int
    get() =
        when (this) {
            FavoritesSortMode.MANUAL -> R.string.favorites_sort_manual
            FavoritesSortMode.ADDED_DATE -> R.string.favorites_sort_added
        }

private val DateBucket.labelResId: Int
    get() =
        when (this) {
            DateBucket.TODAY -> R.string.history_section_today
            DateBucket.YESTERDAY -> R.string.history_section_yesterday
            DateBucket.LAST_WEEK -> R.string.history_section_last_week
            DateBucket.LAST_MONTH -> R.string.history_section_last_month
            DateBucket.LAST_TWO_MONTHS -> R.string.history_section_last_two_months
            DateBucket.LAST_FIVE_MONTHS -> R.string.history_section_last_five_months
            DateBucket.LAST_YEAR -> R.string.history_section_last_year
            DateBucket.OLDER -> R.string.history_section_older
        }
