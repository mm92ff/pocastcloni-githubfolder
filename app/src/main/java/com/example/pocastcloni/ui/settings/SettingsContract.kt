package com.example.pocastcloni.ui.settings

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.Constants

// ============================================================================================
// 1. EVENTS (User Actions) - STRICT UDF
// ============================================================================================

@Immutable
sealed interface SettingsUiEvent {
    data class UpdateSetting(val action: UpdateUserSettingAction) : SettingsUiEvent

    data class OnAddUrlQueryChange(val url: String) : SettingsUiEvent

    data object AddPodcastViaUrl : SettingsUiEvent

    data object ResetStatistics : SettingsUiEvent

    data object StartManualDownload : SettingsUiEvent

    // Obsolete events removed: ClearDownloadMessage, ClearUserMessage (handled via Effects now)
    data object ResetImportState : SettingsUiEvent

    data object ResetExportState : SettingsUiEvent

    data class ExportFullBackup(val path: String) : SettingsUiEvent

    data class ImportFullBackup(val path: String) : SettingsUiEvent

    data object OnResetClicked : SettingsUiEvent

    data object OnResetDismissed : SettingsUiEvent

    data object OnResetConfirmed : SettingsUiEvent
}

// ============================================================================================
// 2. TOP-LEVEL STATE (Single Source of Truth)
// ============================================================================================

/**
 * The "Master-State" produced by the ViewModel via combine().
 * The UI (Composable) observes ONLY this state.
 */
@Immutable
data class SettingsScreenState(
    val settings: SettingsUiState = SettingsUiState.Loading,
    val statistics: StatisticsScreenUiState = StatisticsScreenUiState.Loading,
    val isPlayerVisible: Boolean = false,
    val importState: ImportUiState = ImportUiState.Idle,
    val exportState: ExportUiState = ExportUiState.Idle,
    val isManualRefreshRunning: Boolean = false,
    val addUrlState: SettingsAddUrlState = SettingsAddUrlState(),
    val showResetDialog: Boolean = false
)

// ============================================================================================
// 3. SUB-STATES (Feature Slices)
// ============================================================================================

@Immutable
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    @Immutable
    data class Success(
        val theme: AppTheme = AppTheme.SYSTEM,
        val appColor: AppColor = AppColor.GREEN,
        val colorStrength: Float = 0.1f,
        val bufferMode: BufferMode = BufferMode.NORMAL,
        val bufferWholePodcast: Boolean = false,
        val layoutMode: LayoutMode = LayoutMode.GRID,
        val gridSize: Int = 120,
        val showGridTitles: Boolean = true,
        val confirmDelete: Boolean = true,
        val progressBarHeight: Int = 4,
        val navBarHeight: Int = 56,
        val showMiniPlayerTimeOverlay: Boolean = Constants.Preferences.DEFAULT_SHOW_MINI_PLAYER_TIME_OVERLAY,
        val oneHandedMode: Boolean = false,
        val bottomBarCleanModeEnabled: Boolean = Constants.Preferences.DEFAULT_BOTTOM_BAR_CLEAN_MODE_ENABLED,
        val bottomBarAutoHideEnabled: Boolean = Constants.Preferences.DEFAULT_BOTTOM_BAR_AUTO_HIDE_ENABLED,
        val bottomBarAutoHideDelaySeconds: Int = Constants.Preferences.DEFAULT_BOTTOM_BAR_AUTO_HIDE_DELAY_SECONDS,
        val autoDownloadLimit: Int = 5,
        val autoRefreshOnStart: Boolean = true,
        val backgroundCheckEnabled: Boolean = true,
        val backgroundCheckInterval: Int = 12,
        val markPlayedDurationSeconds: Int = 30,
        val feedUpdateMode: FeedUpdateMode = FeedUpdateMode.ALWAYS_FULL,
        val indicator: IndicatorSettingsUiState = IndicatorSettingsUiState(),
        val saveToDownloadsFolder: Boolean = Constants.Preferences.DEFAULT_SAVE_TO_DOWNLOADS_FOLDER,
        val autoCleanupEnabled: Boolean = Constants.Preferences.DEFAULT_AUTO_CLEANUP_ENABLED,
        val cleanupKeepLimit: Int = Constants.Preferences.DEFAULT_CLEANUP_KEEP_LIMIT,
        val cleanupIntervalHours: Int = Constants.Preferences.DEFAULT_CLEANUP_INTERVAL_HOURS,
    ) : SettingsUiState

    data class Error(val message: UiText) : SettingsUiState
}

@Immutable
data class IndicatorSettingsUiState(
    val colorArgb: Long = Constants.Preferences.DEFAULT_INDICATOR_COLOR,
    val size: Int = Constants.Preferences.DEFAULT_INDICATOR_SIZE,
    val borderWidth: Int = Constants.Preferences.DEFAULT_INDICATOR_BORDER,
    val xOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_X_OFFSET,
    val yOffset: Int = Constants.Preferences.DEFAULT_INDICATOR_Y_OFFSET
)

@Immutable
sealed interface StatisticsScreenUiState {
    data object Loading : StatisticsScreenUiState

    data class Success(
        val totalPlayTimeMs: Long,
        val totalEpisodes: Int,
        val episodesInProgress: Int,
        val episodesPlayed: Int,
        val downloadWifiBytes: Long,
        val downloadMobileBytes: Long,
        val streamWifiBytes: Long,
        val streamMobileBytes: Long,
        val statisticsStartedAt: Long
    ) : StatisticsScreenUiState

    data class Error(val message: UiText) : StatisticsScreenUiState
}

@Immutable
data class SettingsAddUrlState(
    val urlInput: String = "",
    val isAdding: Boolean = false,
    val message: UiText? = null,
    val isError: Boolean = false
)

// ============================================================================================
// 4. AUXILIARY STATES (Import/Export)
// ============================================================================================

@Immutable
sealed interface ImportUiState {
    data object Idle : ImportUiState

    data object Loading : ImportUiState

    data class Success(val message: UiText) : ImportUiState

    data class Error(val message: UiText) : ImportUiState
}

@Immutable
sealed interface ExportUiState {
    data object Idle : ExportUiState

    data object Loading : ExportUiState

    data class Success(val message: UiText) : ExportUiState

    data class Error(val message: UiText) : ExportUiState
}
