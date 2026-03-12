package com.example.pocastcloni.ui.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.pocastcloni.R
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.player.PreparePlaybackUseCase
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val podcastRepository: PodcastRepository,
    private val mediaConnection: MediaControllerConnection,
    private val analyticsHandler: PlaybackAnalyticsHandler,
    private val mapper: MediaStateMapper,
    private val ticker: PlaybackTicker,
    private val preparePlaybackUseCase: PreparePlaybackUseCase
) : PlayerActions, PlayerStateObserver {

    // --- Scope ---
    private val controllerScope = CoroutineScope(dispatcherProvider.main + SupervisorJob())

    @Volatile private var mediaDispatcher: CoroutineDispatcher? = null
    @Volatile private var mediaScope: CoroutineScope? = null
    @Volatile private var controller: MediaController? = null
    private var controllerListener: Player.Listener? = null

    // --- State Management (Internal) ---
    private val _internalPlayerState = MutableStateFlow(PlayerUiState())
    private val _internalPlaybackState = MutableStateFlow(PlaybackState())

    // --- Reactive Connection ---
    override val playerState: StateFlow<PlayerUiState> = _internalPlayerState
        .onStart { connectInternal() }
        .stateIn(
            scope = controllerScope,
            started = SharingStarted.Lazily,
            initialValue = PlayerUiState()
        )

    override val playbackState: StateFlow<PlaybackState> = _internalPlaybackState.asStateFlow()

    // **FIX**: Use shareIn to wait for the first real value from DataStore, avoiding the default initialValue.
    private val userSettings = userPreferencesRepository.userSettingsFlow
        .shareIn(controllerScope, SharingStarted.Eagerly, replay = 1)


    // Seek State
    @Volatile private var isUserSeeking: Boolean = false
    private var pendingSeekPositionMs: Long? = null
    private var progressJob: Job? = null

    private companion object {
        private const val TICK_INTERVAL_MS = 500L
    }

    private suspend fun connectInternal() {
        if (controller != null) return

        val connectedController = mediaConnection.connect() ?: run {
            _internalPlayerState.update { it.copy(error = context.getString(R.string.playback_failed_error)) }
            return
        }

        controller = connectedController
        ensureMediaScope(connectedController)
        attachListener(connectedController)
        startReactiveTicker()
        syncCurrentEpisodeUi()
    }

    private fun startReactiveTicker() {
        progressJob?.cancel()
        progressJob = ticker.tick(TICK_INTERVAL_MS)
            .onEach {
                updateProgressAndAnalytics()
            }
            .flowOn(mediaDispatcher ?: dispatcherProvider.main)
            .launchIn(mediaScope ?: controllerScope)
    }

    private fun updateProgressAndAnalytics() {
        val player = controller ?: return
        val settings = userSettings.replayCache.firstOrNull() ?: return // Don't run if settings aren't loaded yet

        val durationMs = player.duration.takeIf { it > 0 } ?: 0L
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val isPlaying = player.isPlaying && player.playbackState == Player.STATE_READY

        analyticsHandler.onTick(
            scope = controllerScope,
            guid = player.currentMediaItem?.mediaId,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            deltaMs = TICK_INTERVAL_MS,
            isPlaying = isPlaying,
            markPlayedThresholdSeconds = settings.markPlayedDurationSeconds
        )

        if (!isUserSeeking) {
            _internalPlaybackState.update {
                it.copy(
                    currentPositionMs = currentPositionMs,
                    bufferedPositionMs = player.bufferedPosition,
                    durationMs = durationMs
                )
            }
        }
    }

    private fun ensureMediaScope(ctrl: MediaController) {
        if (mediaDispatcher != null && mediaScope != null) return
        val dispatcher = Handler(ctrl.applicationLooper).asCoroutineDispatcher()
        mediaDispatcher = dispatcher
        mediaScope = CoroutineScope(dispatcher + SupervisorJob(controllerScope.coroutineContext.job))
    }

    private fun mediaDispatcherOrFallback(): CoroutineDispatcher = mediaDispatcher ?: dispatcherProvider.main

    private fun launchOnMedia(block: suspend CoroutineScope.() -> Unit): Job {
        val scope = mediaScope ?: controllerScope
        return scope.launch(block = block)
    }

    private fun attachListener(mediaController: MediaController) {
        if (controllerListener != null) return
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _internalPlayerState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _internalPlayerState.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onPlayerError(error: PlaybackException) {
                Timber.e(error, "Player error code: ${error.errorCode}")
                val errorUiText = mapper.mapError(error)
                val errorString = errorUiText?.asString(context)
                    ?: context.getString(R.string.playback_failed_error)
                _internalPlayerState.update {
                    it.copy(isPlaying = false, isBuffering = false, error = errorString)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                analyticsHandler.onMediaItemTransition()
                controllerScope.launch { syncCurrentEpisodeUi() }
            }
        }
        controllerListener = listener
        launchOnMedia { runCatching { mediaController.addListener(listener) } }
        startFavoriteStatusLoop()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startFavoriteStatusLoop() {
        playerState.map { it.currentEpisodeGuid }.distinctUntilChanged()
            .flatMapLatest { guid -> if (guid.isNullOrBlank()) flowOf(false) else podcastRepository.isFavorite(guid) }
            .onEach { isFav -> _internalPlayerState.update { it.copy(isCurrentEpisodeFavorite = isFav) } }
            .launchIn(controllerScope)
    }

    override suspend fun play(episodeGuid: String) {
        connectInternal()
        val playbackInfo = preparePlaybackUseCase(episodeGuid)
        val episode = playbackInfo.episode
        _internalPlayerState.update { it.copy(currentPodcastUrl = episode.podcastRssUrl) }
        val mediaController = controller ?: return
        withContext(mediaDispatcherOrFallback()) {
            val currentId = mediaController.currentMediaItem?.mediaId
            if (currentId == episode.guid) {
                if (!mediaController.isPlaying) mediaController.play()
                return@withContext
            }
            val mediaItem = mapper.mapToMediaItem(
                episode,
                playbackInfo.podcast?.let { podcastRepository.getPodcastEntityByUrl(it.rssUrl) },
                playbackInfo.playUri
            )
            mediaController.setMediaItem(mediaItem, playbackInfo.startPosition)
            mediaController.prepare()
            mediaController.play()
        }
        controllerScope.launch { syncCurrentEpisodeUi() }
    }

    override fun pause() { launchOnMedia { controller?.pause() } }
    override fun resume() { launchOnMedia { controller?.play() } }

    override fun onEvent(event: PlayerScreenEvent) {
        when (event) {
            PlayerScreenEvent.TogglePlayPause -> if (playerState.value.isPlaying) pause() else resume()
            PlayerScreenEvent.Rewind -> launchOnMedia { controller?.let { it.seekTo((it.currentPosition - Constants.PlayerDefaults.REWIND_INTERVAL_MS).coerceAtLeast(0L)) } }
            PlayerScreenEvent.Forward -> launchOnMedia { controller?.let { it.seekTo(it.currentPosition + Constants.PlayerDefaults.FORWARD_INTERVAL_MS) } }
            is PlayerScreenEvent.SeekTo -> {
                val pos = event.positionMs.coerceAtLeast(0L)
                _internalPlaybackState.update { it.copy(currentPositionMs = pos) }
                if (isUserSeeking) pendingSeekPositionMs = pos else launchOnMedia { controller?.seekTo(pos) }
            }
            PlayerScreenEvent.SeekStarted -> isUserSeeking = true
            PlayerScreenEvent.SeekFinished -> {
                pendingSeekPositionMs?.let { launchOnMedia { controller?.seekTo(it) } }
                pendingSeekPositionMs = null
                isUserSeeking = false
            }
            else -> Unit
        }
    }

    private suspend fun syncCurrentEpisodeUi() {
        val ctrl = controller ?: return
        val guid = ctrl.currentMediaItem?.mediaId
        val episode = if (guid != null) withContext(dispatcherProvider.io) { podcastRepository.getEpisode(guid) } else null
        val podcast = episode?.podcastRssUrl?.let { withContext(dispatcherProvider.io) { podcastRepository.getPodcastEntityByUrl(it) } }
        _internalPlayerState.update { current ->
            mapper.mapToUiState(ctrl, episode, podcast, current)
        }
    }

    override fun releaseResources() {
        controller?.let { p -> analyticsHandler.saveProgressBestEffort(controllerScope, p.currentMediaItem?.mediaId, p.currentPosition) }
        progressJob?.cancel()
        val ctrl = controller
        val listener = controllerListener
        if (ctrl != null && listener != null) {
            Handler(ctrl.applicationLooper).post { runCatching { ctrl.removeListener(listener) } }
        }
        controller = null
        controllerListener = null
        mediaConnection.release()
        mediaScope?.cancel()
        mediaScope = null
        mediaDispatcher = null
    }
}
