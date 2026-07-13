package com.example.pocastcloni.ui.home.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.data.remote.ItunesPodcastDto
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.podcast.AddPodcastFromUrlUseCase
import com.example.pocastcloni.domain.usecase.podcast.RemovePodcastSubscriptionUseCase
import com.example.pocastcloni.domain.usecase.podcast.SearchPodcastsUseCase
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.common.asRetainedLoad
import com.example.pocastcloni.ui.player.AudioPlayerController
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class AddPodcastViewModel
@Inject
constructor(
    private val repository: PodcastRepository,
    userPreferencesRepository: UserPreferencesRepository,
    playerController: AudioPlayerController,
    private val addPodcastFromUrl: AddPodcastFromUrlUseCase,
    private val searchPodcasts: SearchPodcastsUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val removePodcastSubscription: RemovePodcastSubscriptionUseCase
) : ViewModel() {
    private val _internalState = MutableStateFlow(AddPodcastScreenUiState())

    private val isPlayerVisibleFlow =
        playerController.playerState
            .map { it.currentEpisodeId != null }
            .distinctUntilChanged()

    private val subscribedUrlsFlow =
        repository.getSubscribedUrlsFlow()
            .map { it.toImmutableSet() }
            .distinctUntilChanged()
            .asRetainedLoad(UiText.StringResource(R.string.error_unknown))

    val uiState: StateFlow<AddPodcastScreenUiState> =
        combine(
            _internalState,
            userPreferencesRepository.userSettingsFlow,
            subscribedUrlsFlow,
            isPlayerVisibleFlow
        ) { state, settings, contentLoad, isVisible ->
            state.copy(
                contentLoad = contentLoad,
                oneHandedMode = settings.oneHandedMode,
                subscribedUrls = contentLoad.lastValue ?: kotlinx.collections.immutable.persistentHashSetOf(),
                searchError = state.searchError ?: contentLoad.error,
                progressBarHeight = settings.progressBarHeight,
                navBarHeight = settings.navBarHeight,
                isPlayerVisible = isVisible,
                transparentSearchCards = settings.transparentSearchCards
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.ViewModel.STATE_IN_TIMEOUT),
            initialValue = AddPodcastScreenUiState()
        )

    private var searchJob: Job? = null

    private fun ItunesPodcastDto.toPodcastSearchResult(): PodcastSearchResult {
        return PodcastSearchResult(
            feedUrl = this.feedUrl ?: "",
            title = this.collectionName ?: "",
            artist = this.artistName ?: "",
            artworkUrl = this.artworkUrl600 ?: ""
        )
    }

    fun onSearchQueryChanged(query: String) {
        _internalState.update { it.copy(searchQuery = query, addSuccess = false) }
    }

    fun onTogglePodcast(podcast: PodcastSearchResult) {
        val url = podcast.feedUrl
        if (url.isBlank()) return
        val isSubscribed = uiState.value.subscribedUrls.contains(url)

        viewModelScope.launch(dispatcherProvider.io) {
            _internalState.update { it.copy(addSuccess = false, searchError = null) }
            try {
                if (isSubscribed) {
                    removePodcastSubscription(url)
                    _internalState.update { it.copy(addSuccess = false, searchError = null) }
                } else {
                    addPodcastFromUrl(url)
                    _internalState.update { it.copy(addSuccess = true, searchError = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                _internalState.update {
                    it.copy(
                        addSuccess = false,
                        searchError = UiText.StringResource(R.string.error_add_podcast_failed)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle podcast subscription for %s", url)
                val safeMessage = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "unknown error"
                _internalState.update {
                    it.copy(
                        addSuccess = false,
                        searchError = UiText.StringResource(R.string.add_podcast_failure, safeMessage)
                    )
                }
            }
        }
    }

    fun onSearchTriggered() {
        val query = _internalState.value.searchQuery
        if (query.isBlank()) return

        searchJob?.cancel()

        searchJob =
            viewModelScope.launch {
                _internalState.update { it.copy(isSearching = true, searchError = null, addSuccess = false) }

                try {
                    val results =
                        withContext(dispatcherProvider.io) {
                            searchPodcasts(query)
                        }
                    _internalState.update { state ->
                        state.copy(
                            isSearching = false,
                            // Filter duplicates to prevent a LazyColumn crash
                            searchResults =
                            results
                                .map { it.toPodcastSearchResult() }
                                .distinctBy { it.feedUrl }
                                .toImmutableList()
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: IOException) {
                    _internalState.update {
                        it.copy(isSearching = false, searchError = UiText.StringResource(R.string.error_network))
                    }
                } catch (_: Exception) {
                    _internalState.update {
                        it.copy(isSearching = false, searchError = UiText.StringResource(R.string.error_unknown))
                    }
                }
            }
    }
}
