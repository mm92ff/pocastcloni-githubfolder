package com.example.pocastcloni.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.app.GetUserSettingsUseCase
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

data class MainUiState(
    val userSettings: UserSettings = UserSettings(),
    val isPlayerExpanded: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MainViewModel
@Inject
constructor(
    getUserSettings: GetUserSettingsUseCase,
    val playerController: AudioPlayerController,
    val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _isPlayerExpanded = MutableStateFlow(false)
    private val _error: MutableStateFlow<String?> = MutableStateFlow(null)

    val uiState: StateFlow<MainUiState> =
        combine(
            // Catch upstream errors here so 'combine' does not cancel
            getUserSettings().catch { e ->
                Timber.e(e, "Failed to load user settings")
                _error.value = e.message
                emit(UserSettings()) // Fallback: emit defaults so the flow stays alive
            },
            _isPlayerExpanded,
            _error
        ) { settings, isPlayerExpanded, error ->
            MainUiState(
                userSettings = settings,
                isPlayerExpanded = isPlayerExpanded,
                error = error,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = MainUiState(isLoading = true)
        )

    fun onPlayerExpanded(expanded: Boolean) {
        Timber.d("Player expanded: %s", expanded)
        _isPlayerExpanded.value = expanded
    }
}
