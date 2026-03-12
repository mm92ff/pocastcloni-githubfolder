package com.example.pocastcloni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.player.PlayerVisibilityProvider
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.AppMaintenanceUseCases
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingAction
import com.example.pocastcloni.domain.usecase.app.UpdateUserSettingsUseCase
import com.example.pocastcloni.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getUserSettings: GetUserSettingsUseCase,
    private val appMaintenanceUseCases: AppMaintenanceUseCases,
    private val updateUserSettings: UpdateUserSettingsUseCase,
    playerVisibilityProvider: PlayerVisibilityProvider,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private companion object {
        const val MAX_ATTEMPTS_TOTAL = 3L
        const val UPDATE_DEBOUNCE_MS = 300L
        const val UPDATE_ACTION_BUFFER = 64
        const val STATE_IN_TIMEOUT_MS = 5_000L
        const val MANUAL_REFRESH_TIMEOUT_MS = 60_000L // 60 seconds
    }

    sealed interface SettingsUiEffect {
        data class Snackbar(val message: UiText) : SettingsUiEffect
    }

    private val _effects = MutableSharedFlow<SettingsUiEffect>(
        replay = 0,
        extraBufferCapacity = UPDATE_ACTION_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects = _effects.asSharedFlow()

    private data class LocalUiState(
        val showResetDialog: Boolean = false,
        val isManualRefreshRunning: Boolean = false
    )

    private val _localUiState = MutableStateFlow(LocalUiState())

    private val updateActions = MutableSharedFlow<UpdateUserSettingAction>(
        extraBufferCapacity = UPDATE_ACTION_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        val immediate = updateActions.filterNot { it.shouldDebounce() }
        val debounced = updateActions.filter { it.shouldDebounce() }.debounce(UPDATE_DEBOUNCE_MS).distinctUntilChanged()
        merge(immediate, debounced).onEach { applySettingUpdate(it) }.launchIn(viewModelScope)
    }

    private val settingsFlow: Flow<SettingsUiState> = getUserSettings()
        .map<UserSettings, SettingsUiState> { it.toUiState() }
        .onStart { emit(SettingsUiState.Loading) }
        .distinctUntilChanged()
        .flowOn(dispatcherProvider.io)
        .retryWhen { cause, attempt -> cause !is CancellationException && attempt < (MAX_ATTEMPTS_TOTAL - 1) }
        .catch { t ->
            if (t is CancellationException) throw t
            Timber.e(t, "Failed to load user settings after retries")
            emit(SettingsUiState.Error(UiText.StringResource(R.string.settings_error_load)))
        }

    private val isPlayerVisibleFlow: Flow<Boolean> = playerVisibilityProvider.isPlayerVisible

    val uiState: StateFlow<SettingsScreenState> = combine(
        settingsFlow,
        isPlayerVisibleFlow,
        _localUiState
    ) { settings, isPlayerVisible, localState ->
        SettingsScreenState(
            settings = settings,
            isPlayerVisible = isPlayerVisible,
            showResetDialog = localState.showResetDialog,
            isManualRefreshRunning = localState.isManualRefreshRunning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_IN_TIMEOUT_MS),
        initialValue = SettingsScreenState()
    )

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.UpdateSetting -> handleUpdateSetting(event)
            SettingsUiEvent.StartManualDownload -> handleStartManualDownload()
            SettingsUiEvent.OnResetClicked -> _localUiState.update { it.copy(showResetDialog = true) }
            SettingsUiEvent.OnResetDismissed -> _localUiState.update { it.copy(showResetDialog = false) }
            SettingsUiEvent.OnResetConfirmed -> handleResetConfirmed()
            else -> Timber.w("SettingsViewModel ignoring event %s", event::class.java.simpleName)
        }
    }

    private fun handleUpdateSetting(event: SettingsUiEvent.UpdateSetting) {
        val action = event.action
        if (!updateActions.tryEmit(action)) {
            viewModelScope.launch { updateActions.emit(action) }
        }
    }

    private suspend fun applySettingUpdate(action: UpdateUserSettingAction) {
        try {
            withContext(dispatcherProvider.io) { updateUserSettings(action) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.e(t, "Failed to update setting: %s", action)
            _effects.tryEmit(SettingsUiEffect.Snackbar(UiText.StringResource(R.string.generic_error_update_failed)))
        }
    }

    private fun handleStartManualDownload() {
        viewModelScope.launch {
            _localUiState.update { it.copy(isManualRefreshRunning = true) }
            try {
                val result = withTimeoutOrNull(MANUAL_REFRESH_TIMEOUT_MS) {
                    appMaintenanceUseCases.manualFeedUpdate()
                }

                if (result == null) {
                    _effects.tryEmit(SettingsUiEffect.Snackbar(UiText.StringResource(R.string.error_timeout)))
                } else if (result.allFailed) {
                    _effects.tryEmit(SettingsUiEffect.Snackbar(UiText.StringResource(R.string.generic_error_update_failed)))
                } else if (result.hasFailures) {
                    _effects.tryEmit(
                        SettingsUiEffect.Snackbar(
                            UiText.StringResource(
                                R.string.podcasts_partially_updated,
                                result.successfulCount,
                                result.totalCount
                            )
                        )
                    )
                } else if (result.isEmpty) {
                    _effects.tryEmit(SettingsUiEffect.Snackbar(UiText.StringResource(R.string.everything_up_to_date)))
                } else {
                    val message = UiText.fromCount(R.plurals.podcasts_updated_count, result.successfulCount)
                    _effects.tryEmit(SettingsUiEffect.Snackbar(message))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Manual feed update failed")
                _effects.tryEmit(SettingsUiEffect.Snackbar(UiText.StringResource(R.string.generic_error_update_failed)))
            } finally {
                _localUiState.update { it.copy(isManualRefreshRunning = false) }
            }
        }
    }

    private fun handleResetConfirmed() {
        _localUiState.update { it.copy(showResetDialog = false) }
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                appMaintenanceUseCases.resetApp()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to reset app")
                _effects.tryEmit(SettingsUiEffect.Snackbar(UiText.StringResource(R.string.settings_error_reset_app)))
            }
        }
    }

    private fun UpdateUserSettingAction.shouldDebounce(): Boolean = when (this) {
        is UpdateUserSettingAction.SetColorStrength,
        is UpdateUserSettingAction.SetGridSize,
        is UpdateUserSettingAction.SetProgressBarHeight,
        is UpdateUserSettingAction.SetNavBarHeight,
        is UpdateUserSettingAction.SetAutoDownloadLimit,
        is UpdateUserSettingAction.SetBackgroundCheckInterval,
        is UpdateUserSettingAction.SetMarkPlayedDuration,
        is UpdateUserSettingAction.SetIndicatorColor,
        is UpdateUserSettingAction.SetIndicatorSize,
        is UpdateUserSettingAction.SetIndicatorBorderWidth,
        is UpdateUserSettingAction.SetIndicatorXOffset,
        is UpdateUserSettingAction.SetIndicatorYOffset -> true
        // NEU: Layout Mode nicht debouncen für direktes Feedback
        is UpdateUserSettingAction.SetLayoutMode -> false
        else -> false
    }
}

private fun UserSettings.toUiState(): SettingsUiState.Success {
    return SettingsUiState.Success(
        theme = theme,
        appColor = appColor,
        colorStrength = colorStrength,
        bufferMode = bufferMode,
        // NEU: Layout Mode übergeben
        layoutMode = layoutMode,
        gridSize = gridSize,
        showGridTitles = showGridTitles,
        confirmDelete = confirmDelete,
        progressBarHeight = progressBarHeight,
        navBarHeight = navBarHeight,
        oneHandedMode = oneHandedMode,
        autoDownloadLimit = autoDownloadLimit,
        autoRefreshOnStart = autoRefreshOnStart,
        backgroundCheckEnabled = backgroundCheckEnabled,
        backgroundCheckInterval = backgroundCheckInterval,
        markPlayedDurationSeconds = markPlayedDurationSeconds,
        feedUpdateMode = feedUpdateMode,
        indicator = IndicatorSettingsUiState(
            colorArgb = this.indicator.colorArgb,
            size = this.indicator.size,
            borderWidth = this.indicator.borderWidth,
            xOffset = this.indicator.xOffset,
            yOffset = this.indicator.yOffset
        )
    )
}
