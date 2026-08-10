package com.example.pocastcloni.ui.home.detail

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.RetainedLoad
import com.example.pocastcloni.ui.settings.ThemeUiModel
import com.example.pocastcloni.util.Constants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PlayerStatusUiState(
    val currentPlayingEpisodeId: Long? = null,
    val isPlayerPlaying: Boolean = false,
    val isPlayerVisible: Boolean = false
)

@Immutable
data class SettingsUiModel(
    val theme: ThemeUiModel = ThemeUiModel.SYSTEM,
    val oneHandedMode: Boolean = false,
    val navBarHeight: Int = Constants.ViewModel.DEFAULT_NAV_BAR_HEIGHT,
    val progressBarHeight: Int = Constants.ViewModel.DEFAULT_PROGRESS_BAR_HEIGHT
)

@Immutable
data class PodcastDetailContent(
    val rssUrl: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val coverFileName: String? = null,
    val coverRevision: Long = 0L,
    val autoDownloadEnabled: Boolean = false,
    val episodes: ImmutableList<EpisodeUiModel> = persistentListOf(),
    val error: UiText? = null
)

@Immutable
data class PodcastDetailUiState(
    val contentLoad: RetainedLoad<PodcastDetailContent> = RetainedLoad(),
    val podcastTitle: String = "",
    val podcastDescription: String = "",
    val podcastImageUrl: String? = null,
    val podcastCoverFileName: String? = null,
    val podcastCoverRevision: Long = 0L,
    val podcastRssUrl: String = "",
    val isAutoDownloadEnabled: Boolean = false,
    val episodes: ImmutableList<EpisodeUiModel> = persistentListOf(),
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val isPodcastDescriptionDialogVisible: Boolean = false,
    val playerState: PlayerStatusUiState = PlayerStatusUiState(),
    val appSettings: SettingsUiModel = SettingsUiModel()
)
