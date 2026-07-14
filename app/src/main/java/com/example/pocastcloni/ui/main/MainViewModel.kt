package com.example.pocastcloni.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.RetainedLoad
import com.example.pocastcloni.ui.common.asRetainedLoad
import com.example.pocastcloni.ui.common.retainLatestValue
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import timber.log.Timber
import javax.inject.Inject

data class MainUiState(
    val settingsLoad: RetainedLoad<UserSettings> = RetainedLoad(),
    val isPlayerExpanded: Boolean = false,
) {
    val userSettings: UserSettings
        get() = settingsLoad.lastValue ?: UserSettings()

    val isLoading: Boolean
        get() = settingsLoad.loading && settingsLoad.lastValue == null

    val error: UiText?
        get() = settingsLoad.error
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel
@Inject
constructor(
    getUserSettings: GetUserSettingsUseCase,
    val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _isPlayerExpanded = MutableStateFlow(false)
    private val _settingsReloadKey = MutableStateFlow(0)

    private val settingsLoad =
        _settingsReloadKey.flatMapLatest {
            getUserSettings()
                .asRetainedLoad(UiText.StringResource(R.string.settings_error_load))
        }.retainLatestValue()

    val uiState: StateFlow<MainUiState> =
        combine(
            settingsLoad,
            _isPlayerExpanded
        ) { load, isPlayerExpanded ->
            MainUiState(
                settingsLoad = load,
                isPlayerExpanded = isPlayerExpanded
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = MainUiState()
        )

    fun retrySettings() {
        _settingsReloadKey.update { it + 1 }
    }

    fun onPlayerExpanded(expanded: Boolean) {
        Timber.d("Player expanded: %s", expanded)
        _isPlayerExpanded.value = expanded
    }
}
