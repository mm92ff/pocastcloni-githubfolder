package com.example.pocastcloni.ui.home.add

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.Constants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AddPodcastScreenUiState(
    val addSuccess: Boolean = false,
    val searchQuery: String = Constants.EMPTY_STRING,
    val isSearching: Boolean = false,
    val searchResults: ImmutableList<PodcastSearchResult> = persistentListOf(),
    val searchError: UiText? = null,
    val oneHandedMode: Boolean = false,
    // FIX: ImmutableSet
    val subscribedUrls: ImmutableSet<String> = persistentHashSetOf(),
    val progressBarHeight: Int = Constants.ViewModel.DEFAULT_PROGRESS_BAR_HEIGHT,
    val navBarHeight: Int = Constants.ViewModel.DEFAULT_NAV_BAR_HEIGHT,
    val isPlayerVisible: Boolean = false,
    val transparentSearchCards: Boolean = Constants.Preferences.DEFAULT_TRANSPARENT_SEARCH_CARDS
)
