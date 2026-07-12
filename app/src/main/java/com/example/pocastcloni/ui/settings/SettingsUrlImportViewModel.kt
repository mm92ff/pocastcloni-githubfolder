package com.example.pocastcloni.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.BuildConfig
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.usecase.podcast.AddPodcastFromUrlUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.parseNetworkUrl
import com.example.pocastcloni.util.requiresCleartextConfirmation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsUrlImportViewModel
@Inject
constructor(
    private val addPodcastFromUrl: AddPodcastFromUrlUseCase,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsAddUrlState())
    val uiState = _uiState.asStateFlow()

    /**
     * One-time events (Snackbars/Toasts). Optional to use, but recommended to avoid replay on rotation.
     * If your UI already uses uiState.message, you can ignore this and call onMessageConsumed() after showing.
     */
    private val _events =
        MutableSharedFlow<UiText>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    val events = _events.asSharedFlow()

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OnAddUrlQueryChange -> onUrlChange(event.url)
            SettingsUiEvent.AddPodcastViaUrl -> onAddPodcast()
            is SettingsUiEvent.SetAddUrlAllowInsecureHttp -> setAllowInsecureHttp(event.allowed)
            else ->
                Timber.w(
                    "SettingsUrlImportViewModel ignoring event %s",
                    event::class.java.simpleName
                )
        }
    }

    fun onUrlChange(newUrl: String) {
        _uiState.update {
            it.copy(
                urlInput = newUrl,
                message = null,
                isError = false,
                pendingCleartextConfirmationUrl = null,
                allowInsecureHttp = false
            )
        }
    }

    /**
     * Call this from UI after showing uiState.message (if you choose the state-based message approach).
     */
    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun setAllowInsecureHttp(allowed: Boolean) {
        _uiState.update {
            it.copy(
                allowInsecureHttp = allowed,
                pendingCleartextConfirmationUrl = null,
                message = null,
                isError = false
            )
        }
    }

    fun onAddPodcast() {
        val current = _uiState.value
        if (current.isAdding) return // double-tap / parallel request guard

        val url = current.urlInput.trim()

        if (url.isBlank()) {
            postError(UiText.StringResource(R.string.error_add_url_empty))
            return
        }

        if (parseNetworkUrl(url) == null) {
            postError(UiText.StringResource(R.string.error_add_url_invalid))
            return
        }

        if (
            requiresCleartextConfirmation(url) &&
            !current.allowInsecureHttp
        ) {
            _uiState.update {
                it.copy(
                    message = UiText.StringResource(R.string.warning_add_url_http_confirmation),
                    isError = true,
                    pendingCleartextConfirmationUrl = url
                )
            }
            return
        }

        viewModelScope.launch(dispatcherProvider.io) {
            _uiState.update { it.copy(isAdding = true, message = null, isError = false) }

            try {
                addPodcastFromUrl(
                    url = url,
                    allowInsecureHttp = current.allowInsecureHttp
                )

                val msg = UiText.StringResource(R.string.add_podcast_success)
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        urlInput = "",
                        message = msg,
                        isError = false,
                        pendingCleartextConfirmationUrl = null,
                        allowInsecureHttp = false
                    )
                }
                _events.tryEmit(msg)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Timber.e(t, "Add podcast via URL failed")

                // Do NOT leak exception details to the UI
                val benchmarkDetails =
                    if (BuildConfig.BENCHMARK_BUILD) {
                        "${t::class.java.simpleName}: ${t.message.orEmpty()}"
                    } else {
                        ""
                    }
                val msg = UiText.StringResource(R.string.add_podcast_failure, benchmarkDetails)
                _uiState.update {
                    it.copy(
                        isAdding = false,
                        message = msg,
                        isError = true
                    )
                }
                _events.tryEmit(msg)
            }
        }
    }

    private fun postError(message: UiText) {
        _uiState.update {
            it.copy(
                isAdding = false,
                message = message,
                isError = true
            )
        }
        _events.tryEmit(message)
    }

}
