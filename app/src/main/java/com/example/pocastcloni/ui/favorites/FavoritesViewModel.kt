package com.example.pocastcloni.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.episode.GetFavoriteEpisodesWithPodcastInfoUseCase
import com.example.pocastcloni.domain.usecase.episode.ToggleFavoriteEpisodeUseCase
import com.example.pocastcloni.domain.usecase.favorite.ReorderFavoritesUseCase
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class FavoritesViewModel
@Inject
constructor(
    getFavoriteEpisodesWithPodcastInfoUseCase: GetFavoriteEpisodesWithPodcastInfoUseCase,
    private val audioPlayerController: AudioPlayerController,
    private val toggleFavoriteEpisodeUseCase: ToggleFavoriteEpisodeUseCase,
    private val reorderFavoritesUseCase: ReorderFavoritesUseCase,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val _isEditMode = MutableStateFlow(false)
    private val _optimisticFavorites = MutableStateFlow<List<FavoriteUiItem>?>(null)
    private val _episodeForDetails = MutableStateFlow<FavoriteUiItem?>(null)
    private val _sortMode = MutableStateFlow(FavoritesSortMode.MANUAL)

    private val dbFavoritesFlow =
        getFavoriteEpisodesWithPodcastInfoUseCase()
            .map { list ->
                list.map { info ->
                    FavoriteUiItem(
                        id = info.episode.episodeId,
                        episode = EpisodeDisplayModel.from(info.episode, info.podcast),
                        podcast = info.podcast
                    )
                }
            }
            .distinctUntilChanged()

    private val isPlayerVisibleFlow =
        audioPlayerController.playerState
            .map { it.currentEpisodeId != null }
            .distinctUntilChanged()

    // STAGE 1: Data Consolidation
    private val dataFlow =
        combine(
            dbFavoritesFlow,
            userPreferencesRepository.userSettingsFlow,
            isPlayerVisibleFlow
        ) { dbFavorites, settings, isPlayerVisible ->
            Triple(dbFavorites, settings, isPlayerVisible)
        }

    // STAGE 2: Final Assembly
    val uiState =
        combine(
            dataFlow,
            _optimisticFavorites,
            _isEditMode,
            _episodeForDetails,
            _sortMode
        ) { (dbFavorites, settings, isPlayerVisible), optimisticFavorites, isEditMode, episodeForDetails, sortMode ->

            val currentFavorites =
                if (optimisticFavorites != null && optimisticFavorites.size == dbFavorites.size) {
                    optimisticFavorites
                } else {
                    dbFavorites
                }

            FavoritesUiState(
                isLoading = false,
                favorites = currentFavorites.toImmutableList(),
                dateGroupedRows = buildFavoriteDateRows(
                    items = dbFavorites,
                    reverseDisplayOrder = settings.oneHandedMode
                ).toImmutableList(),
                sortMode = sortMode,
                isEditMode = isEditMode,
                oneHandedMode = settings.oneHandedMode,
                isPlayerVisible = isPlayerVisible,
                navBarHeight = settings.navBarHeight,
                progressBarHeight = settings.progressBarHeight,
                transparentEpisodeRows = settings.transparentEpisodeRows,
                episodeForDetails = episodeForDetails
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = FavoritesUiState(isLoading = true)
        )

    fun onAction(action: FavoritesAction) {
        when (action) {
            is FavoritesAction.OnEpisodeClick -> {
                if (!_isEditMode.value) {
                    viewModelScope.launch {
                        audioPlayerController.play(action.episodeId)
                    }
                }
            }

            is FavoritesAction.OnEpisodeImageClick -> {
                _episodeForDetails.value = action.item
            }

            FavoritesAction.OnDismissEpisodeDetails -> {
                _episodeForDetails.value = null
            }

            is FavoritesAction.OnEpisodeSwiped -> {
                viewModelScope.launch {
                    toggleFavoriteEpisodeUseCase(action.episodeId, true)
                }
            }

            is FavoritesAction.OnReorder -> {
                if (_sortMode.value == FavoritesSortMode.MANUAL) {
                    handleReorder(action.fromIndex, action.toIndex)
                }
            }

            is FavoritesAction.ChangeSortMode -> {
                _sortMode.value = action.mode
                if (action.mode == FavoritesSortMode.ADDED_DATE) {
                    _isEditMode.value = false
                    _optimisticFavorites.value = null
                }
            }

            FavoritesAction.ToggleEditMode -> {
                if (_sortMode.value == FavoritesSortMode.MANUAL) {
                    _isEditMode.update { wasEditMode ->
                        if (wasEditMode) _optimisticFavorites.value = null
                        !wasEditMode
                    }
                }
            }
        }
    }

    private fun handleReorder(
        fromIndex: Int,
        toIndex: Int
    ) {
        val currentList = uiState.value.favorites.toMutableList()

        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            Collections.swap(currentList, fromIndex, toIndex)
            _optimisticFavorites.value = currentList.toList()

            viewModelScope.launch {
                try {
                    reorderFavoritesUseCase(currentList.map { it.episode.episodeId })
                } catch (_: Exception) {
                    _optimisticFavorites.value = null
                }
            }
        }
    }
}
