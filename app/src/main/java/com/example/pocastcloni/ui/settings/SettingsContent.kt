package com.example.pocastcloni.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.theme.Dimens

private enum class SettingsTab(@StringRes val labelRes: Int) {
    DESIGN(R.string.settings_tab_design),
    PLAYBACK(R.string.settings_tab_playback),
    SYNC_STORAGE(R.string.settings_tab_sync),
    DATA(R.string.settings_tab_data)
}

/**
 * Builds the full settings list.
 * PERFORMANCE-OPTIMISED:
 * 1. Stable lazy keys
 * 2. Granular state passing (no monolith)
 * 3. Stabilised lambdas
 */
@Composable
fun SettingsListContent(
    settings: SettingsUiState.Success,
    downloadMessage: UiText?,
    isPlayerVisible: Boolean,
    onEvent: (SettingsUiEvent) -> Unit, // Must be stable (method reference)
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.DESIGN) }
    val tabs = SettingsTab.entries
    val bottomPadding =
        if (isPlayerVisible) {
            (settings.navBarHeight + settings.progressBarHeight).dp + Dimens.PaddingMedium
        } else {
            Dimens.PaddingMedium
        }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = Dimens.PaddingSmall
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(stringResource(tab.labelRes)) }
                )
            }
        }

        CompositionLocalProvider(LocalTransparentSettingsCards provides settings.transparentSearchCards) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                PaddingValues(
                    start = Dimens.PaddingMedium,
                    top = Dimens.PaddingMedium,
                    end = Dimens.PaddingMedium,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
            ) {
                item(key = selectedTab.name) {
                    when (selectedTab) {
                        SettingsTab.DESIGN -> DesignSettingsContent(settings = settings, onEvent = onEvent)
                        SettingsTab.PLAYBACK -> PlaybackSettingsContent(settings = settings, onEvent = onEvent)
                        SettingsTab.SYNC_STORAGE ->
                            SyncStorageSettingsContent(
                                settings = settings,
                                downloadMessage = downloadMessage,
                                onEvent = onEvent
                            )
                        SettingsTab.DATA ->
                            DataSettingsContent(
                                onEvent = onEvent,
                                onExportClick = onExportClick,
                                onImportClick = onImportClick
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignSettingsContent(
    settings: SettingsUiState.Success,
    onEvent: (SettingsUiEvent) -> Unit
) {
    SectionAppearance(
        theme = settings.theme,
        appColor = settings.appColor,
        colorStrength = settings.colorStrength,
        gradientBackgroundEnabled = settings.gradientBackgroundEnabled,
        gradientBackgroundStrength = settings.gradientBackgroundStrength,
        transparentSearchCards = settings.transparentSearchCards,
        transparentEpisodeRows = settings.transparentEpisodeRows,
        onSetTheme =
        remember(onEvent) {
            {
                    theme: AppTheme ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetAppTheme(theme)))
            }
        },
        onSetAppColor =
        remember(onEvent) {
            {
                    color: AppColor ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetAppColor(color)))
            }
        },
        onSetColorStrength =
        remember(onEvent) {
            {
                    strength: Float ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetColorStrength(strength)))
            }
        },
        onToggleGradientBackground =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleGradientBackground(enabled)))
            }
        },
        onSetGradientBackgroundStrength =
        remember(onEvent) {
            {
                    strength: Float ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetGradientBackgroundStrength(strength)))
            }
        },
        onToggleTransparentSearchCards =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleTransparentSearchCards(enabled)))
            }
        },
        onToggleTransparentEpisodeRows =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleTransparentEpisodeRows(enabled)))
            }
        }
    )
    Divider()

    SectionIndicator(
        gridSizeDp = settings.gridSize,
        indicatorState = settings.indicator,
        onColorClick =
        remember(onEvent) {
            {
                    color: Long ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorColor(color)))
            }
        },
        onSizeChange =
        remember(onEvent) {
            {
                    size: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorSize(size)))
            }
        },
        onBorderChange =
        remember(onEvent) {
            {
                    width: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorBorderWidth(width)))
            }
        },
        onXOffsetChange =
        remember(onEvent) {
            {
                    x: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorXOffset(x)))
            }
        },
        onYOffsetChange =
        remember(onEvent) {
            {
                    y: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetIndicatorYOffset(y)))
            }
        }
    )
    Divider()

    SectionInterface(
        layoutMode = settings.layoutMode,
        gridSize = settings.gridSize,
        showGridTitles = settings.showGridTitles,
        oneHandedMode = settings.oneHandedMode,
        bottomBarCleanModeEnabled = settings.bottomBarCleanModeEnabled,
        bottomBarAutoHideEnabled = settings.bottomBarAutoHideEnabled,
        bottomBarAutoHideDelaySeconds = settings.bottomBarAutoHideDelaySeconds,
        progressBarHeight = settings.progressBarHeight,
        navBarHeight = settings.navBarHeight,
        confirmDelete = settings.confirmDelete,
        onSetLayoutMode =
        remember(onEvent) {
            {
                    mode: LayoutMode ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetLayoutMode(mode)))
            }
        },
        onSetGridSize =
        remember(onEvent) {
            {
                    size: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetGridSize(size)))
            }
        },
        onToggleShowGridTitles =
        remember(onEvent) {
            {
                    show: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleShowGridTitles(show)))
            }
        },
        onToggleOneHandedMode =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleOneHandedMode(enabled)))
            }
        },
        onToggleBottomBarCleanMode =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleBottomBarCleanMode(enabled)))
            }
        },
        onToggleBottomBarAutoHide =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleBottomBarAutoHide(enabled)))
            }
        },
        onSetBottomBarAutoHideDelay =
        remember(onEvent) {
            {
                    seconds: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetBottomBarAutoHideDelay(seconds)))
            }
        },
        onSetProgressBarHeight =
        remember(onEvent) {
            {
                    height: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetProgressBarHeight(height)))
            }
        },
        onSetNavBarHeight =
        remember(onEvent) {
            {
                    height: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetNavBarHeight(height)))
            }
        },
        onToggleConfirmDelete =
        remember(onEvent) {
            {
                    confirm: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleConfirmDelete(confirm)))
            }
        }
    )
}

@Composable
private fun PlaybackSettingsContent(
    settings: SettingsUiState.Success,
    onEvent: (SettingsUiEvent) -> Unit
) {
    SectionPlayback(
        markPlayedDurationSeconds = settings.markPlayedDurationSeconds,
        showMiniPlayerTimeOverlay = settings.showMiniPlayerTimeOverlay,
        bufferMode = settings.bufferMode,
        onSetMarkPlayedDuration =
        remember(onEvent) {
            {
                    seconds: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetMarkPlayedDuration(seconds)))
            }
        },
        onToggleMiniPlayerTimeOverlay =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleMiniPlayerTimeOverlay(enabled)))
            }
        },
        onSetBufferMode =
        remember(onEvent) {
            { mode: BufferMode -> onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetBufferSettings(mode))) }
        }
    )
}

@Composable
private fun SyncStorageSettingsContent(
    settings: SettingsUiState.Success,
    downloadMessage: UiText?,
    onEvent: (SettingsUiEvent) -> Unit
) {
    SettingsUrlImportSectionSmart()
    Divider()

    SectionAutomation(
        autoRefreshOnStart = settings.autoRefreshOnStart,
        backgroundCheckEnabled = settings.backgroundCheckEnabled,
        backgroundCheckInterval = settings.backgroundCheckInterval,
        feedUpdateMode = settings.feedUpdateMode,
        onToggleAutoRefreshOnStart =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleAutoRefreshOnStart(enabled)))
            }
        },
        onToggleBackgroundCheck =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleBackgroundCheck(enabled)))
            }
        },
        onSetBackgroundCheckInterval =
        remember(onEvent) {
            {
                    hours: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetBackgroundCheckInterval(hours)))
            }
        },
        onSetFeedUpdateMode =
        remember(onEvent) {
            {
                    mode: FeedUpdateMode ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetFeedUpdateMode(mode)))
            }
        }
    )
    Divider()

    SectionDownloads(
        autoDownloadLimit = settings.autoDownloadLimit,
        message = downloadMessage,
        onSetAutoDownloadLimit =
        remember(onEvent) {
            {
                    limit: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetAutoDownloadLimit(limit)))
            }
        },
        onStartManualDownload = remember(onEvent) { { onEvent(SettingsUiEvent.StartManualDownload) } }
    )

    SectionDownloadLocation(
        saveToDownloadsFolder = settings.saveToDownloadsFolder,
        onToggle = remember(onEvent) {
            { enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleSaveToDownloadsFolder(enabled)))
            }
        }
    )

    Divider()

    SectionCleanup(
        autoCleanupEnabled = settings.autoCleanupEnabled,
        cleanupKeepLimit = settings.cleanupKeepLimit,
        cleanupIntervalHours = settings.cleanupIntervalHours,
        onToggleAutoCleanup =
        remember(onEvent) {
            { enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleAutoCleanup(enabled)))
            }
        },
        onSetCleanupKeepLimit =
        remember(onEvent) {
            { limit: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetCleanupKeepLimit(limit)))
            }
        },
        onSetCleanupIntervalHours =
        remember(onEvent) {
            { hours: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetCleanupIntervalHours(hours)))
            }
        }
    )
}

@Composable
private fun DataSettingsContent(
    onEvent: (SettingsUiEvent) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    SettingsStatisticsSectionSmart()
    Divider()

    SectionBackup(onExport = onExportClick, onImport = onImportClick)
    Divider()

    SettingsCard(onClick = { onEvent(SettingsUiEvent.OnResetClicked) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Column {
                Text(stringResource(R.string.settings_reset_zone_title), color = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.settings_reset_zone_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Smart Sections ---

@Composable
fun SettingsUrlImportSectionSmart(viewModel: SettingsUrlImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SectionAddPodcast(
        urlInput = state.urlInput,
        isAdding = state.isAdding,
        message = state.message,
        isError = state.isError,
        onUrlChange = viewModel::onUrlChange,
        onAddClick = viewModel::onAddPodcast
    )
}

@Composable
fun SettingsStatisticsSectionSmart(viewModel: SettingsStatisticsViewModel = hiltViewModel()) {
    val state by viewModel.statsState.collectAsStateWithLifecycle()
    SectionStatistics(
        statsState = state,
        onResetStatistics = viewModel::onResetStatistics
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(modifier = Modifier.padding(top = Dimens.PaddingLarge))
}
