package com.example.pocastcloni.ui.favorites

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.common.DateBucket
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

enum class FavoritesSortMode {
    MANUAL,
    ADDED_DATE
}

@Immutable
sealed interface FavoriteListRow {
    val key: String

    @Immutable
    data class SectionHeader(
        val bucket: DateBucket
    ) : FavoriteListRow {
        override val key: String = "favorite-section-${bucket.name}"
    }

    @Immutable
    data class EpisodeRow(
        val item: FavoriteUiItem
    ) : FavoriteListRow {
        override val key: String = "favorite-episode-${item.id}"
    }
}

@Immutable
data class FavoritesUiState(
    val isLoading: Boolean = true,
    // FIX: ImmutableList enforces stability and enables skipping in the UI
    val favorites: ImmutableList<FavoriteUiItem> = persistentListOf(),
    val dateGroupedRows: ImmutableList<FavoriteListRow> = persistentListOf(),
    val sortMode: FavoritesSortMode = FavoritesSortMode.MANUAL,
    val isEditMode: Boolean = false,
    val oneHandedMode: Boolean = false,
    val isPlayerVisible: Boolean = false,
    val navBarHeight: Int = 0,
    val progressBarHeight: Int = 0,
    val transparentEpisodeRows: Boolean = false,
    val episodeForDetails: FavoriteUiItem? = null
)

sealed interface FavoritesAction {
    data class OnEpisodeClick(val guid: String) : FavoritesAction

    data class OnEpisodeImageClick(val item: FavoriteUiItem) : FavoritesAction

    data object OnDismissEpisodeDetails : FavoritesAction

    data class OnEpisodeSwiped(val guid: String) : FavoritesAction

    data class OnReorder(val fromIndex: Int, val toIndex: Int) : FavoritesAction

    data class ChangeSortMode(val mode: FavoritesSortMode) : FavoritesAction

    data object ToggleEditMode : FavoritesAction
}
