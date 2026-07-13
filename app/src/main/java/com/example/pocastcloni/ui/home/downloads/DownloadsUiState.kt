package com.example.pocastcloni.ui.home.downloads

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.ui.home.detail.EpisodeUiModel
import com.example.pocastcloni.ui.common.RetainedLoad
import com.example.pocastcloni.util.Constants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class DownloadsUiState(
    val contentLoad: RetainedLoad<ImmutableList<EpisodeUiModel>> = RetainedLoad(),
    val isLoading: Boolean = true,
    val episodes: ImmutableList<EpisodeUiModel> = persistentListOf(),
    val confirmDelete: Boolean = false,
    val oneHandedMode: Boolean = false,
    val currentPlayingEpisodeId: Long? = null,
    val isPlayerPlaying: Boolean = false,
    val progressBarHeight: Int = Constants.ViewModel.DEFAULT_PROGRESS_BAR_HEIGHT,
    val navBarHeight: Int = Constants.ViewModel.DEFAULT_NAV_BAR_HEIGHT,
    val isPlayerVisible: Boolean = false,
    val episodeToDelete: EpisodeUiModel? = null
)
