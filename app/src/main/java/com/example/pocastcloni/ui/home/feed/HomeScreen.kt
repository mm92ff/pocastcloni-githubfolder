package com.example.pocastcloni.ui.home.feed

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.common.ReorderableLazyVerticalGrid
import com.example.pocastcloni.ui.home.common.PodcastGridItem
import com.example.pocastcloni.ui.home.common.PodcastIndicatorStyle
import com.example.pocastcloni.ui.home.common.PodcastItem
import com.example.pocastcloni.ui.player.MiniPlayerLayoutDefaults
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onPodcastClicked: (String) -> Unit,
    onFavoritesClicked: () -> Unit,
    onHistoryClicked: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAutoRefreshing by viewModel.isAutoRefreshing.collectAsStateWithLifecycle()

    val pullToRefreshState = rememberPullToRefreshState()
    val snackBarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // --- AUTO REFRESH ON START ---
    LaunchedEffect(Unit) {
        delay(900)
        viewModel.autoRefreshOnStartIfEnabled()
    }

    // --- REFRESH LOGIC ---
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) { viewModel.refresh() }
    }

    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) pullToRefreshState.startRefresh() else pullToRefreshState.endRefresh()
    }

    // --- ONE-TIME EVENTS ---
    LaunchedEffect(key1 = true) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeUiEvent.ShowUserMessage -> {
                    snackBarHostState.showSnackbar(
                        message = event.message.asString(context)
                    )
                }
            }
        }
    }

    val currentOnPodcastClicked by rememberUpdatedState(onPodcastClicked)

    // Click logic: select in edit mode, navigate otherwise
    val handlePodcastClick: (Podcast) -> Unit =
        remember(viewModel, uiState.isEditMode) {
            { podcast ->
                if (uiState.isEditMode) {
                    viewModel.toggleSelection(podcast.rssUrl)
                } else {
                    currentOnPodcastClicked(podcast.rssUrl)
                }
            }
        }

    // --- DELETE DIALOG (Multi-Select Support) ---
    if (uiState.showDeleteConfirmation) {
        val count = uiState.selectedPodcastsForDelete.size
        val titleText =
            if (count == 1) {
                uiState.selectedPodcastsForDelete.first().title
            } else {
                stringResource(R.string.delete_multiple_count, count)
            }

        DeleteConfirmDialog(
            podcastTitle = titleText,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (!uiState.isEditMode) {
                        Text(stringResource(R.string.home_title))
                    } else {
                        // Show count of selected items
                        val selectedCount = uiState.selectedPodcastGuids.size
                        if (selectedCount > 0) {
                            Text("$selectedCount")
                        }
                    }
                },
                actions = {
                    // --- AUTO REFRESH INDICATOR ---
                    if (isAutoRefreshing) {
                        Box(
                            modifier = Modifier.padding(end = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    if (uiState.isEditMode) {
                        // Show delete icon only when items are selected
                        if (uiState.selectedPodcastGuids.isNotEmpty()) {
                            IconButton(onClick = viewModel::onDeleteSelectedRequest) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                        TextButton(onClick = viewModel::exitEditMode) { Text(stringResource(R.string.done)) }
                    } else {
                        IconButton(onClick = onHistoryClicked) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                        }
                        IconButton(onClick = onFavoritesClicked) {
                            Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.favorites))
                        }
                        IconButton(onClick = viewModel::markAllAsSeen) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.mark_all_as_seen))
                        }
                    }
                },
                colors =
                if (uiState.isEditMode) {
                    TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (uiState.isLoading && !uiState.isRefreshing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val screenError = uiState.screenError
                if (screenError != null) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimens.PaddingLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = viewModel::clearScreenError) {
                            Text(
                                text = screenError.asString(context),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (uiState.podcasts.isEmpty() && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.home_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    PodcastListContent(
                        podcasts = uiState.podcasts,
                        layoutMode = uiState.layoutMode,
                        gridSize = uiState.gridSize,
                        oneHandedMode = uiState.oneHandedMode,
                        isPlayerVisible = uiState.isPlayerVisible,
                        isEditMode = uiState.isEditMode,
                        selectedPodcastGuids = uiState.selectedPodcastGuids,
                        navBarHeight = uiState.navBarHeight,
                        progressBarHeight = uiState.progressBarHeight,
                        showGridTitles = uiState.showGridTitles,
                        indicatorColorArgb = uiState.indicatorColorArgb,
                        indicatorSize = uiState.indicatorSize,
                        indicatorBorderWidth = uiState.indicatorBorderWidth,
                        indicatorXOffset = uiState.indicatorXOffset,
                        indicatorYOffset = uiState.indicatorYOffset,
                        onPodcastClick = handlePodcastClick,
                        onEnterEditMode = viewModel::enterEditMode,
                        onReorder = viewModel::onReorder
                    )
                }
            }

            if (pullToRefreshState.progress > 0f || uiState.isRefreshing) {
                PullToRefreshContainer(
                    state = pullToRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PodcastListContent(
    podcasts: ImmutableList<Podcast>,
    layoutMode: LayoutMode,
    gridSize: Int,
    oneHandedMode: Boolean,
    isPlayerVisible: Boolean,
    isEditMode: Boolean,
    showGridTitles: Boolean,
    selectedPodcastGuids: ImmutableSet<String>,
    navBarHeight: Int,
    progressBarHeight: Int,
    indicatorColorArgb: Long,
    indicatorSize: Int,
    indicatorBorderWidth: Int,
    indicatorXOffset: Int,
    indicatorYOffset: Int,
    onPodcastClick: (Podcast) -> Unit,
    onEnterEditMode: (String) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val commonModifier = Modifier.fillMaxSize()
    val context = LocalContext.current
    val podcastImageUrls =
        remember(podcasts) {
            podcasts
                .map { it.imageUrl.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }

    LaunchedEffect(podcastImageUrls) {
        val imageLoader = Coil.imageLoader(context)
        podcastImageUrls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(Constants.Image.IMAGE_SIZE_GRID)
                    .precision(Precision.EXACT)
                    .memoryCacheKey("$url#${Constants.Image.IMAGE_SIZE_GRID}")
                    .diskCacheKey(url)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    val bottomPadding =
        MiniPlayerLayoutDefaults.reservedBottomPadding(
            isPlayerVisible = isPlayerVisible,
            navBarHeight = navBarHeight,
            progressBarHeight = progressBarHeight,
            extraPadding = Dimens.PaddingLarge
        )

    val contentPadding =
        PaddingValues(
            start = Dimens.PaddingLarge,
            top = Dimens.PaddingLarge,
            end = Dimens.PaddingLarge,
            bottom = bottomPadding
        )

    val indicatorStyle =
        remember(indicatorColorArgb, indicatorSize, indicatorBorderWidth, indicatorXOffset, indicatorYOffset) {
            PodcastIndicatorStyle(
                xOffset = indicatorXOffset,
                yOffset = indicatorYOffset,
                borderWidth = indicatorBorderWidth,
                size = indicatorSize,
                colorArgb = indicatorColorArgb
            )
        }

    val columns =
        remember(layoutMode, gridSize) {
            if (layoutMode == LayoutMode.LIST) {
                GridCells.Fixed(1)
            } else {
                GridCells.Adaptive(minSize = gridSize.dp)
            }
        }

    // Dynamic vertical spacing based on layout mode:
    // Grid: PaddingMedium (16dp) — airy; List: PaddingVerySmall (8dp) — compact
    val verticalSpacing = if (layoutMode == LayoutMode.LIST) Dimens.PaddingVerySmall else Dimens.PaddingMedium

    ReorderableLazyVerticalGrid(
        items = podcasts,
        key = { it.rssUrl },
        columns = columns,
        onReorder = onReorder,
        modifier = commonModifier,
        contentPadding = contentPadding,
        reverseLayout = oneHandedMode,
        verticalArrangement =
        Arrangement.spacedBy(
            verticalSpacing,
            if (oneHandedMode) Alignment.Bottom else Alignment.Top
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
        onDragStartIndex = { index ->
            podcasts.getOrNull(index)?.let { p ->
                onEnterEditMode(p.rssUrl)
            }
        }
    ) { _, podcast, isDragging ->
        Box(
            modifier =
            Modifier.animateItemPlacement(
                animationSpec = spring(stiffness = Constants.Animation.STIFFNESS)
            )
        ) {
            val isSelected = selectedPodcastGuids.contains(podcast.rssUrl) || isDragging

            if (layoutMode == LayoutMode.LIST) {
                PodcastItem(
                    podcast = podcast,
                    isEditMode = isEditMode,
                    isSelected = isSelected,
                    onClick = { onPodcastClick(podcast) },
                    onLongClick = null,
                    onDeleteClick = { },
                    indicatorStyle = indicatorStyle
                )
            } else {
                PodcastGridItem(
                    podcast = podcast,
                    isEditMode = isEditMode,
                    showGridTitles = showGridTitles,
                    isSelected = isSelected,
                    onClick = { onPodcastClick(podcast) },
                    onLongClick = null,
                    onDeleteClick = { },
                    indicatorStyle = indicatorStyle
                )
            }
        }
    }
}
