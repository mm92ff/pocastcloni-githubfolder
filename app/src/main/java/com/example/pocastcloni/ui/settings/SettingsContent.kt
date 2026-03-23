package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * Baut die gesamte Einstellungs-Liste auf.
 * PERFORMANCE-OPTIMIERT:
 * 1. Stabile Lazy-Keys
 * 2. Granulare State-Übergabe (kein Monolith)
 * 3. Stabilisierte Lambdas
 */
@Composable
fun SettingsListContent(
    settings: SettingsUiState.Success,
    downloadMessage: UiText?,
    isPlayerVisible: Boolean,
    onEvent: (SettingsUiEvent) -> Unit, // Muss stabil sein (Method Reference)
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    val bottomPadding =
        if (isPlayerVisible) {
            (settings.navBarHeight + settings.progressBarHeight).dp + Dimens.PaddingMedium
        } else {
            Dimens.PaddingMedium
        }

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
        // 1. URL Import (Smart Section -> Eigenes VM)
        item(key = "add_podcast") {
            SettingsUrlImportSectionSmart()
            Divider()
        }

        // 2. Allgemeine Einstellungen
        item(key = "general_settings") {
            GeneralSettingsContent(settings, downloadMessage, onEvent)
        }

        // 3. Statistik
        item(key = "statistics") {
            SettingsStatisticsSectionSmart()
            Divider()
        }

        // 4. Backup
        item(key = "backup") {
            SectionBackup(onExport = onExportClick, onImport = onImportClick)
            Divider()
        }

        // 5. Reset Zone
        item(key = "reset_zone") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reset_zone_title), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.settings_reset_zone_subtitle)) },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onEvent(SettingsUiEvent.OnResetClicked) }
            )
        }
    }
}

/**
 * Container für Settings-Sektionen.
 * ZERLEGT den State in primitive Werte für maximales Recomposition Skipping.
 */
@Composable
private fun GeneralSettingsContent(
    settings: SettingsUiState.Success,
    downloadMessage: UiText?,
    onEvent: (SettingsUiEvent) -> Unit
) {
    // --- APPEARANCE ---
    SectionAppearance(
        theme = settings.theme,
        appColor = settings.appColor,
        colorStrength = settings.colorStrength,
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
        }
    )
    Divider()

    // --- AUTOMATION ---
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

    // --- DOWNLOADS ---
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
    Divider()

    // --- INDICATOR ---
    SectionIndicator(
        gridSizeDp = settings.gridSize,
        indicatorState = settings.indicator,
        // FIX: Explizite Typangabe (Long, Int, etc.) behebt den "Cannot infer type"-Fehler
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

    // --- INTERFACE ---
    SectionInterface(
        layoutMode = settings.layoutMode,
        gridSize = settings.gridSize,
        showGridTitles = settings.showGridTitles,
        oneHandedMode = settings.oneHandedMode,
        progressBarHeight = settings.progressBarHeight,
        navBarHeight = settings.navBarHeight,
        confirmDelete = settings.confirmDelete,
        // FIX: Auch hier explizite Typen
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
    Divider()

    // --- PLAYBACK ---
    SectionPlayback(
        markPlayedDurationSeconds = settings.markPlayedDurationSeconds,
        bufferMode = settings.bufferMode,
        onSetMarkPlayedDuration =
        remember(onEvent) {
            {
                    seconds: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetMarkPlayedDuration(seconds)))
            }
        },
        onSetBufferMode =
        remember(onEvent) {
            { mode: BufferMode -> onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetBufferSettings(mode))) }
        }
    )
    Divider()
}

// --- Smart Sections bleiben unverändert ---

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
