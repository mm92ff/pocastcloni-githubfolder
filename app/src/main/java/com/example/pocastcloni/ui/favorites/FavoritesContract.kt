package com.example.pocastcloni.ui.favorites

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A stable wrapper for the UI representation.
 * @Immutable guarantees the Compose compiler that this object will not change.
 */
@Immutable
data class FavoriteUiItem(
    val id: String, // Stable key for LazyColumn (guid)
    val episode: EpisodeDisplayModel,
    val podcast: Podcast? // Associated podcast (if available)
)

@Immutable
data class FavoritesUiState(
    val isLoading: Boolean = true,
    // FIX: ImmutableList enforces stability and enables skipping in the UI
    val favorites: ImmutableList<FavoriteUiItem> = persistentListOf(),
    val isEditMode: Boolean = false,
    val oneHandedMode: Boolean = false,
    val isPlayerVisible: Boolean = false,
    val navBarHeight: Int = 0,
    val progressBarHeight: Int = 0,
    val episodeForDetails: FavoriteUiItem? = null
)

sealed interface FavoritesAction {
    data class OnEpisodeClick(val guid: String) : FavoritesAction

    data class OnEpisodeImageClick(val item: FavoriteUiItem) : FavoritesAction

    data object OnDismissEpisodeDetails : FavoritesAction

    data class OnEpisodeSwiped(val guid: String) : FavoritesAction

    data class OnReorder(val fromIndex: Int, val toIndex: Int) : FavoritesAction

    data object ToggleEditMode : FavoritesAction
}
