package com.example.pocastcloni.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.pocastcloni.domain.model.BufferMode
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction

@Composable
internal fun PlaybackSettingsContent(
    settings: SettingsUiState.Success,
    onEvent: (SettingsUiEvent) -> Unit
) {
    SectionPlayback(
        markPlayedDurationSeconds = settings.markPlayedDurationSeconds,
        showMiniPlayerTimeOverlay = settings.showMiniPlayerTimeOverlay,
        transparentMiniPlayer = settings.transparentMiniPlayer,
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
        onToggleTransparentMiniPlayer =
        remember(onEvent) {
            {
                    enabled: Boolean ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.ToggleTransparentMiniPlayer(enabled)))
            }
        },
        onSetBufferMode =
        remember(onEvent) {
            { mode: BufferMode ->
                onEvent(SettingsUiEvent.UpdateSetting(UpdateUserSettingAction.SetBufferSettings(mode)))
            }
        }
    )
}
