package com.example.pocastcloni.ui.history

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.domain.model.Podcast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class HistoryUiItem(
    val id: String,
    val episode: EpisodeEntity,
    val podcast: Podcast?
)

@Immutable
data class HistoryUiState(
    val isLoading: Boolean = true,
    // FIX: ImmutableList statt Map -> Stabil & Performant
    val historyItems: ImmutableList<HistoryUiItem> = persistentListOf(),
    val showConfirmClearDialog: Boolean = false,
    val oneHandedMode: Boolean = false,
    val isPlayerVisible: Boolean = false,
    val navBarHeight: Int = 0,
    val progressBarHeight: Int = 0
)

sealed interface HistoryAction {
    data class OnEpisodeClick(val episode: EpisodeEntity) : HistoryAction
    data object ClearHistory : HistoryAction
    data object ConfirmClearHistory : HistoryAction
    data object DismissClearHistoryDialog : HistoryAction
}