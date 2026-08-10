package com.example.pocastcloni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.data.cover.PodcastCoverMaintenance
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class PodcastCoverSettingsState(
    val isRefreshing: Boolean = false,
    val message: UiText? = null
)

@HiltViewModel
class PodcastCoverSettingsViewModel
@Inject
constructor(
    private val maintenance: PodcastCoverMaintenance,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val refreshClaimed = AtomicBoolean(false)
    private val _state = MutableStateFlow(PodcastCoverSettingsState())
    val state = _state.asStateFlow()

    fun refreshCovers() {
        if (!refreshClaimed.compareAndSet(false, true)) return
        viewModelScope.launch(dispatcherProvider.io) {
            _state.update { it.copy(isRefreshing = true, message = null) }
            try {
                val result = maintenance.refreshAllNow()
                val message =
                    when {
                        result.totalCount == 0 -> UiText.StringResource(R.string.settings_cover_refresh_empty)
                        result.failureCount == 0 ->
                            UiText.StringResource(
                                R.string.settings_cover_refresh_success,
                                result.successfulCount
                            )
                        result.successfulCount == 0 -> UiText.StringResource(R.string.settings_cover_refresh_failed)
                        else ->
                            UiText.StringResource(
                                R.string.settings_cover_refresh_partial,
                                result.successfulCount,
                                result.totalCount
                            )
                    }
                _state.update { it.copy(message = message) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.update {
                    it.copy(message = UiText.StringResource(R.string.settings_cover_refresh_failed))
                }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
                refreshClaimed.set(false)
            }
        }
    }
}
