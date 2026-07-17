package com.example.pocastcloni.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction
import com.example.pocastcloni.ui.UiText

@Composable
internal fun SyncStorageSettingsContent(
    settings: SettingsUiState.Success,
    downloadMessage: UiText?,
    onEvent: (SettingsUiEvent) -> Unit
) {
    SettingsUrlImportSectionSmart()
    SettingsTabDivider()

    SectionAutomation(
        autoRefreshOnStart = settings.autoRefreshOnStart,
        backgroundCheckEnabled = settings.backgroundCheckEnabled,
        backgroundCheckInterval = settings.backgroundCheckInterval,
        feedUpdateMode = settings.feedUpdateMode,
        smartStreamItemLimit = settings.smartStreamItemLimit,
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
        },
        onSetSmartStreamItemLimit =
        remember(onEvent) {
            {
                    limit: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetSmartStreamItemLimit(limit)))
            }
        },
        onStartManualDownload = remember(onEvent) { { onEvent(SettingsUiEvent.StartManualDownload) } }
    )
    SettingsTabDivider()

    SectionDownloads(
        autoDownloadLimit = settings.autoDownloadLimit,
        message = downloadMessage,
        onSetAutoDownloadLimit =
        remember(onEvent) {
            {
                    limit: Int ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetAutoDownloadLimit(limit)))
            }
        }
    )

    SectionDownloadLocation(
        saveToDownloadsFolder = settings.saveToDownloadsFolder,
        onToggle = remember(onEvent) {
            { enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleSaveToDownloadsFolder(enabled)))
            }
        }
    )

    SettingsTabDivider()

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
fun SettingsUrlImportSectionSmart(viewModel: SettingsUrlImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SectionAddPodcast(
        urlInput = state.urlInput,
        isAdding = state.isAdding,
        allowInsecureHttp = state.allowInsecureHttp,
        allowLocalNetwork = state.allowLocalNetwork,
        message = state.message,
        isError = state.isError,
        onUrlChange = viewModel::onUrlChange,
        onAllowInsecureHttpChange = viewModel::setAllowInsecureHttp,
        onAllowLocalNetworkChange = viewModel::setAllowLocalNetwork,
        onAddClick = viewModel::onAddPodcast
    )
}
