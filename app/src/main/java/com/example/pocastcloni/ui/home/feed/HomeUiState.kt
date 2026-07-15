package com.example.pocastcloni.ui.home.feed

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.RetainedLoad
import com.example.pocastcloni.util.Constants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class HomeUiState(
    val contentLoad: RetainedLoad<ImmutableList<Podcast>> = RetainedLoad(),
    val podcasts: ImmutableList<Podcast> = persistentListOf(),
    val isLoading: Boolean = true,
    val layoutMode: LayoutMode = LayoutMode.GRID,
    val gridSize: Int = Constants.Preferences.DEFAULT_GRID_SIZE,
    val showGridTitles: Boolean = Constants.Preferences.DEFAULT_SHOW_GRID_TITLES,
    val transparentPodcastCards: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_PODCAST_CARDS,
    val oneHandedMode: Boolean = Constants.Preferences.DEFAULT_ONE_HANDED_MODE,
    val isEditMode: Boolean = false,
    // Set instead of a single String to support multi-select
    val selectedPodcastRssUrls: ImmutableSet<String> = persistentSetOf(),
    val confirmDelete: Boolean = Constants.Preferences.DEFAULT_CONFIRM_DELETE,
    val indicatorColorArgb: Long = Constants.Preferences.DEFAULT_INDICATOR_COLOR,
    val indicatorSize: Int = Constants.Preferences.DEFAULT_INDICATOR_SIZE,
    val indicatorBorderWidth: Int = Constants.Preferences.DEFAULT_INDICATOR_BORDER,
    val indicatorXOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_X_OFFSET,
    val indicatorYOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_Y_OFFSET,
    val isRefreshing: Boolean = false,
    val progressBarHeight: Int = Constants.ViewModel.DEFAULT_PROGRESS_BAR_HEIGHT,
    val navBarHeight: Int = Constants.ViewModel.DEFAULT_NAV_BAR_HEIGHT,
    val isPlayerVisible: Boolean = false,
    val userMessage: UiText? = null,
    // Batch-delete: store dialog state and the list of podcasts to delete
    val showDeleteConfirmation: Boolean = false,
    val selectedPodcastsForDelete: List<Podcast> = emptyList(),
    val screenError: UiText? = null
)
