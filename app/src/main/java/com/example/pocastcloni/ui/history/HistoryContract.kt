package com.example.pocastcloni.ui.history

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class HistoryTimeBucket {
    TODAY,
    YESTERDAY,
    LAST_WEEK,
    LAST_MONTH,
    LAST_TWO_MONTHS,
    LAST_FIVE_MONTHS,
    LAST_YEAR,
    OLDER
}

@Immutable
data class HistoryUiItem(
    val id: String,
    val episode: EpisodeDisplayModel,
    val podcast: Podcast?
)

@Immutable
sealed interface HistoryListRow {
    val key: String

    @Immutable
    data class SectionHeader(
        val bucket: HistoryTimeBucket
    ) : HistoryListRow {
        override val key: String = "section-${bucket.name}"
    }

    @Immutable
    data class EpisodeRow(
        val item: HistoryUiItem
    ) : HistoryListRow {
        override val key: String = "episode-${item.id}"
    }
}

@Immutable
data class HistoryUiState(
    val isLoading: Boolean = true,
    // ImmutableList enforces stability and enables skipping in the UI
    val historyItems: ImmutableList<HistoryUiItem> = persistentListOf(),
    val historyRows: ImmutableList<HistoryListRow> = persistentListOf(),
    val showConfirmClearDialog: Boolean = false,
    val oneHandedMode: Boolean = false,
    val isPlayerVisible: Boolean = false,
    val navBarHeight: Int = 0,
    val progressBarHeight: Int = 0
)

sealed interface HistoryAction {
    data class OnEpisodeClick(val guid: String) : HistoryAction

    data object ClearHistory : HistoryAction

    data object ConfirmClearHistory : HistoryAction

    data object DismissClearHistoryDialog : HistoryAction
}
