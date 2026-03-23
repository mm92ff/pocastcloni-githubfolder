package com.example.pocastcloni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.AppStatistics
import com.example.pocastcloni.domain.usecase.app.AppMaintenanceUseCases
import com.example.pocastcloni.domain.usecase.app.GetAppStatisticsUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsStatisticsViewModel
@Inject
constructor(
    getAppStatistics: GetAppStatisticsUseCase,
    private val appMaintenanceUseCases: AppMaintenanceUseCases,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {
    private companion object {
        const val FLOW_RETRY_ATTEMPTS = 3L
    }

    /**
     * One-time UI events (Snackbars/Toasts).
     * Collect in UI via LaunchedEffect + lifecycle-aware collection.
     */
    private val _events =
        MutableSharedFlow<UiText>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    val events = _events.asSharedFlow()

    /**
     * Statistics state (Loading/Success/Error) - stable, lifecycle-friendly.
     */
    val statsState: StateFlow<StatisticsScreenUiState> =
        getAppStatistics()
            .distinctUntilChanged()
            .map<AppStatistics, StatisticsScreenUiState> { stats ->
                stats.toStatisticsUiState()
            }
            .flowOn(dispatcherProvider.io)
            .retryWhen { cause, attempt ->
                // Never retry cancellation; retry transient errors a few times.
                cause !is CancellationException && attempt < FLOW_RETRY_ATTEMPTS
            }
            .catch { t ->
                if (t is CancellationException) throw t
                Timber.e(t, "Failed to load app statistics")
                emit(StatisticsScreenUiState.Error(UiText.StringResource(R.string.settings_statistics_error)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
                initialValue = StatisticsScreenUiState.Loading
            )

    /**
     * Optional event entry-point if your Settings UI forwards SettingsUiEvent.
     * Keeps type-safety without silently "else -> Unit" in callers.
     */
    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            SettingsUiEvent.ResetStatistics -> onResetStatistics()
            else -> Unit // other events belong to other VMs
        }
    }

    fun onResetStatistics() {
        // Use appScope for “maintenance” operations that should not be tied to screen lifetime.
        appScope.launch(dispatcherProvider.io) {
            try {
                appMaintenanceUseCases.resetStatistics()
                // No success message here to avoid introducing new string resources.
                // If you want one, add a proper string resource and emit it:
                // _events.tryEmit(UiText.StringResource(R.string.settings_statistics_reset_success))
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Timber.e(t, "Failed to reset statistics")
                _events.tryEmit(UiText.StringResource(R.string.settings_statistics_error))
            }
        }
    }

    private fun AppStatistics.toStatisticsUiState(): StatisticsScreenUiState.Success {
        return StatisticsScreenUiState.Success(
            totalPlayTimeMs = totalListeningTimeMs,
            totalEpisodes = totalEpisodes,
            episodesInProgress = episodesInProgress,
            episodesPlayed = episodesPlayed,
            downloadWifiBytes = downloadWifiBytes,
            downloadMobileBytes = downloadMobileBytes,
            streamWifiBytes = streamWifiBytes,
            streamMobileBytes = streamMobileBytes
        )
    }
}
