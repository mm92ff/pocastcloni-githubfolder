package com.example.pocastcloni.playback.infrastructure

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.player.PreparePlaybackUseCase
import com.example.pocastcloni.playback.api.PlaybackState
import com.example.pocastcloni.playback.api.PlaybackStarter
import com.example.pocastcloni.playback.api.PlayerCommandPort
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.playback.api.PlayerUiState
import com.example.pocastcloni.playback.api.PlayerVisibilityProvider
import com.example.pocastcloni.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class AudioPlayerController
@Inject
@Suppress("LongParameterList")
constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val podcastQuery: PodcastQueryPort,
    private val mediaConnection: MediaControllerConnection,
    private val analyticsHandler: PlaybackAnalyticsHandler,
    private val mapper: MediaStateMapper,
    private val ticker: PlaybackTickSource,
    private val foregroundMonitor: AppForegroundMonitor,
    private val monotonicClock: MonotonicClock,
    private val mediaDispatcherFactory: MediaDispatcherFactory,
    private val preparePlaybackUseCase: PreparePlaybackUseCase
) : PlaybackStarter, PlayerCommandPort, PlayerStatePort, PlayerVisibilityProvider {
    // --- Scope ---
    private val controllerScope = CoroutineScope(dispatcherProvider.main + SupervisorJob())

    @Volatile private var mediaDispatcher: CoroutineDispatcher? = null

    @Volatile private var mediaScope: CoroutineScope? = null

    @Volatile private var controller: MediaController? = null
    private var controllerListener: Player.Listener? = null
    private val connectMutex = Mutex()
    private val playCommitMutex = Mutex()
    private val playRequestGeneration = AtomicLong(0L)
    private val mediaStateRevision = AtomicLong(0L)
    private val reconnectScheduled = AtomicBoolean(false)
    private var reconnectJob: Job? = null

    @Volatile private var explicitlyReleased = false

    // --- State Management (Internal) ---
    private val _internalPlayerState = MutableStateFlow(PlayerUiState())
    private val _internalPlaybackState = MutableStateFlow(PlaybackState())

    // --- Reactive Connection ---
    override val playerState: StateFlow<PlayerUiState> =
        _internalPlayerState
            .onStart { connectInternal() }
            .stateIn(
                scope = controllerScope,
                started = SharingStarted.Lazily,
                initialValue = PlayerUiState()
            )

    override val isPlayerVisible: Flow<Boolean> =
        playerState
            .map { state -> state.currentEpisodeId != null }
            .distinctUntilChanged()

    override val playbackState: StateFlow<PlaybackState> = _internalPlaybackState.asStateFlow()

    // **FIX**: Use shareIn to wait for the first real value from DataStore, avoiding the default initialValue.
    private val userSettings =
        userPreferencesRepository.userSettingsFlow
            .shareIn(controllerScope, SharingStarted.Eagerly, replay = 1)

    // Seek State
    @Volatile private var isUserSeeking: Boolean = false
    private var pendingSeek: SeekSnapshot? = null
    private var progressJob: Job? = null
    private var foregroundJob: Job? = null
    private var favoriteStatusJob: Job? = null
    private val tickerLifecycle = PlaybackTickerLifecycle(monotonicClock)
    private val flushLock = Any()
    private var flushRevision = 0L
    private var lastFlushedSnapshot: TerminalPlaybackSnapshot? = null

    init {
        mediaConnection.setOnDisconnected(::onControllerDisconnected)
        ensureForegroundObservation()
    }

    private fun ensureForegroundObservation() {
        if (foregroundJob?.isActive == true) return
        foregroundJob =
            foregroundMonitor.isForeground
                .onEach { requestTickerReconciliation() }
                .launchIn(controllerScope)
    }

    private suspend fun connectInternal(userInitiated: Boolean = false) {
        if (userInitiated) explicitlyReleased = false
        if (!explicitlyReleased && controller == null) {
            ensureForegroundObservation()
            val connectedController = connectMutex.withLock { connectControllerLocked() }
            if (connectedController != null && controller === connectedController) {
                updateProgressAndAnalytics(deltaMs = 0L, updateUi = true)
                reconcileTicker()
                syncCurrentEpisodeUi()
            }
        }
    }

    private suspend fun connectControllerLocked(): MediaController? {
        var connectedController: MediaController? = null
        if (!explicitlyReleased && controller == null) {
            val candidate = mediaConnection.connect()
            when {
                candidate == null -> {
                    _internalPlayerState.update {
                        it.copy(error = context.getString(R.string.playback_failed_error))
                    }
                }
                explicitlyReleased -> mediaConnection.release()
                else -> {
                    controller = candidate
                    ensureMediaScope(candidate)
                    attachListener(candidate)
                    connectedController = candidate
                }
            }
        }
        return connectedController
    }

    private fun onControllerDisconnected(disconnectedController: MediaController) {
        controllerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val shouldReconnect =
                connectMutex.withLock {
                    if (controller !== disconnectedController) return@withLock false
                    val releaseTransition = tickerLifecycle.release()
                    applyTickerTransition(releaseTransition)
                    if (!releaseTransition.flushPlayback) {
                        flushCurrentPlaybackSnapshot(disconnectedController)
                    }
                    cleanupController(disconnectedController)
                    !explicitlyReleased
                }
            if (shouldReconnect) scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!reconnectScheduled.compareAndSet(false, true)) return
        reconnectJob =
            controllerScope.launch {
                try {
                    connectInternal()
                } finally {
                    reconnectScheduled.set(false)
                }
            }
    }

    private fun requestTickerReconciliation() {
        if (controller == null) return
        launchOnMedia { reconcileTicker() }
    }

    private fun reconcileTicker() {
        val player = controller
        val transition =
            tickerLifecycle.reconcile(
                isPlayingReady = player?.isPlaying == true && player.playbackState == Player.STATE_READY,
                isForeground = foregroundMonitor.isForeground.value
            )
        applyTickerTransition(transition)
    }

    private fun applyTickerTransition(transition: PlaybackTickerTransition) {
        if (transition.elapsedPlayingMs > 0L || transition.syncUi) {
            updateProgressAndAnalytics(
                deltaMs = transition.elapsedPlayingMs,
                updateUi = transition.syncUi,
                countAsPlaying = transition.elapsedPlayingMs > 0L
            )
        }

        if (transition.restartTicker || (transition.desiredIntervalMs != null && progressJob?.isActive != true)) {
            progressJob?.cancel()
            progressJob = null
            transition.desiredIntervalMs?.let { intervalMs ->
                progressJob =
                    ticker.tick(intervalMs)
                        .onEach {
                            applyTickerTransition(
                                tickerLifecycle.onTick(foregroundMonitor.isForeground.value)
                            )
                        }
                        .flowOn(mediaDispatcher ?: dispatcherProvider.main)
                        .launchIn(mediaScope ?: controllerScope)
            }
        }

        if (transition.flushPlayback) flushCurrentPlaybackSnapshot()
    }

    private fun flushCurrentPlaybackSnapshot(
        player: MediaController? = controller,
        episodeId: Long? = player?.currentMediaItem?.mediaId?.toLongOrNull(),
        positionMs: Long? = player?.currentPosition
    ) {
        if (episodeId == null || episodeId <= 0L || positionMs == null) return
        val snapshot =
            synchronized(flushLock) {
                TerminalPlaybackSnapshot(
                    episodeId = episodeId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    revision = flushRevision
                ).also { candidate ->
                    if (candidate == lastFlushedSnapshot) return
                    lastFlushedSnapshot = candidate
                }
            }
        analyticsHandler.saveProgressBestEffort(snapshot.episodeId, snapshot.positionMs)
        analyticsHandler.flushListeningTime()
    }

    private fun markPlaybackSnapshotDirty() {
        synchronized(flushLock) { flushRevision += 1L }
    }

    private fun updateProgressAndAnalytics(
        deltaMs: Long,
        updateUi: Boolean,
        countAsPlaying: Boolean = false
    ) {
        val player = controller ?: return
        val settings = userSettings.replayCache.firstOrNull()

        val durationMs = player.duration.takeIf { it > 0 } ?: 0L
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val isPlaying = player.isPlaying && player.playbackState == Player.STATE_READY

        if (settings != null && deltaMs > 0L) {
            markPlaybackSnapshotDirty()
            analyticsHandler.onTick(
                episodeId = player.currentMediaItem?.mediaId?.toLongOrNull(),
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                deltaMs = deltaMs,
                isPlaying = isPlaying || countAsPlaying,
                markPlayedThresholdSeconds = settings.markPlayedDurationSeconds
            )
        }

        if (updateUi && !isUserSeeking) {
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
        val dispatcher = mediaDispatcherFactory.create(ctrl)
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
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _internalPlayerState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                    requestTickerReconciliation()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _internalPlayerState.update { it.copy(isPlaying = isPlaying) }
                    requestTickerReconciliation()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Player error code: ${error.errorCode}")
                    val errorString =
                        mapper.mapError(error)?.let(context::getString)
                            ?: context.getString(R.string.playback_failed_error)
                    _internalPlayerState.update {
                        it.copy(isPlaying = false, isBuffering = false, error = errorString)
                    }
                    requestTickerReconciliation()
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    mediaStateRevision.incrementAndGet()
                    pendingSeek = null
                    isUserSeeking = false
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
        favoriteStatusJob?.cancel()
        favoriteStatusJob = playerState.map { it.currentEpisodeId }.distinctUntilChanged()
            .flatMapLatest { episodeId ->
                if (episodeId == null) flowOf(false) else podcastQuery.isFavorite(episodeId)
            }
            .onEach { isFav -> _internalPlayerState.update { it.copy(isCurrentEpisodeFavorite = isFav) } }
            .launchIn(controllerScope)
    }

    override suspend fun play(episodeId: Long) {
        val request = preparePlayRequest(episodeId) ?: return
        withContext(mediaDispatcherOrFallback()) { commitPlayRequest(request) }
        controllerScope.launch { syncCurrentEpisodeUi() }
    }

    private suspend fun preparePlayRequest(episodeId: Long): PreparedPlayRequest? {
        val requestGeneration =
            playCommitMutex.withLock {
                playRequestGeneration.incrementAndGet()
            }
        val playbackInfo = preparePlaybackUseCase(episodeId)
        var preparedRequest: PreparedPlayRequest? = null
        if (playRequestGeneration.get() == requestGeneration) {
            val episode = playbackInfo.episode
            val podcast =
                playbackInfo.podcast?.let {
                    withContext(dispatcherProvider.io) {
                        podcastQuery.getPodcast(it.rssUrl)
                    }
                }
            if (playRequestGeneration.get() == requestGeneration) {
                connectInternal(userInitiated = true)
                val connectedController = controller
                if (playRequestGeneration.get() == requestGeneration && connectedController != null) {
                    preparedRequest =
                        PreparedPlayRequest(
                            generation = requestGeneration,
                            controller = connectedController,
                            episodeId = episode.episodeId,
                            podcastRssUrl = episode.podcastRssUrl,
                            mediaItem = mapper.mapToMediaItem(episode, podcast, playbackInfo.playUri),
                            startPositionMs = playbackInfo.startPosition
                        )
                }
            }
        }
        return preparedRequest
    }

    private suspend fun commitPlayRequest(request: PreparedPlayRequest) {
        playCommitMutex.withLock {
            if (!isCurrentPlayRequest(request)) return@withLock
            _internalPlayerState.update { current ->
                if (isCurrentPlayRequest(request)) {
                    current.copy(currentPodcastUrl = request.podcastRssUrl)
                } else {
                    current
                }
            }

            val currentId = request.controller.currentMediaItem?.mediaId
            if (currentId == request.episodeId.toString()) {
                if (!request.controller.isPlaying) {
                    request.controller.play()
                    reconcileTicker()
                }
            } else {
                if (!currentId.isNullOrBlank()) settlePlaybackBeforeSwitch(request.controller)
                if (isCurrentPlayRequest(request)) {
                    request.controller.setMediaItem(request.mediaItem, request.startPositionMs)
                    request.controller.prepare()
                    request.controller.play()
                    reconcileTicker()
                }
            }
        }
    }

    private fun isCurrentPlayRequest(request: PreparedPlayRequest): Boolean =
        playRequestGeneration.get() == request.generation && controller === request.controller

    private fun settlePlaybackBeforeSwitch(mediaController: MediaController) {
        val transition =
            tickerLifecycle.reconcile(
                isPlayingReady = false,
                isForeground = foregroundMonitor.isForeground.value
            )
        applyTickerTransition(transition)
        if (!transition.flushPlayback) flushCurrentPlaybackSnapshot(mediaController)
    }

    override fun pause() {
        launchOnMedia {
            controller?.let {
                it.pause()
                reconcileTicker()
            }
        }
    }

    override fun resume() {
        launchOnMedia {
            controller?.let {
                it.play()
                reconcileTicker()
            }
        }
    }

    override fun onEvent(event: PlayerScreenEvent) {
        when (event) {
            PlayerScreenEvent.TogglePlayPause -> if (playerState.value.isPlaying) pause() else resume()
            PlayerScreenEvent.Rewind ->
                launchOnMedia {
                    controller?.let {
                        it.seekTo(
                            (it.currentPosition - Constants.PlayerDefaults.REWIND_INTERVAL_MS)
                                .coerceAtLeast(0L)
                        )
                    }
                }
            PlayerScreenEvent.Forward ->
                launchOnMedia {
                    controller?.let { it.seekTo(it.currentPosition + Constants.PlayerDefaults.FORWARD_INTERVAL_MS) }
                }
            is PlayerScreenEvent.SeekTo -> {
                val pos = event.positionMs.coerceAtLeast(0L)
                _internalPlaybackState.update { it.copy(currentPositionMs = pos) }
                if (isUserSeeking) {
                    pendingSeek = pendingSeek?.copy(targetPositionMs = pos)
                } else {
                    val snapshot = currentSeekSnapshot(pos)
                    if (snapshot != null) launchOnMedia { finishSeek(snapshot) }
                }
            }
            PlayerScreenEvent.SeekStarted -> {
                isUserSeeking = true
                pendingSeek = currentSeekSnapshot(_internalPlaybackState.value.currentPositionMs)
            }
            PlayerScreenEvent.SeekFinished -> {
                val snapshot = pendingSeek
                pendingSeek = null
                isUserSeeking = false
                if (snapshot != null) launchOnMedia { finishSeek(snapshot) }
            }
            else -> Unit
        }
    }

    private fun currentSeekSnapshot(targetPositionMs: Long): SeekSnapshot? {
        val currentController = controller
        val episodeId = currentController?.currentMediaItem?.mediaId?.toLongOrNull()
        return if (currentController != null && episodeId != null) {
            SeekSnapshot(
                controller = currentController,
                episodeId = episodeId,
                requestGeneration = playRequestGeneration.get(),
                mediaRevision = mediaStateRevision.get(),
                targetPositionMs = targetPositionMs.coerceAtLeast(0L)
            )
        } else {
            null
        }
    }

    private fun finishSeek(snapshot: SeekSnapshot) {
        val currentController = controller ?: return
        val seekContextChanged =
            listOf(
                currentController !== snapshot.controller,
                playRequestGeneration.get() != snapshot.requestGeneration,
                mediaStateRevision.get() != snapshot.mediaRevision,
                currentController.currentMediaItem?.mediaId?.toLongOrNull() != snapshot.episodeId
            ).any { it }
        if (seekContextChanged) return
        currentController.seekTo(snapshot.targetPositionMs)
        markPlaybackSnapshotDirty()
        flushCurrentPlaybackSnapshot(
            player = currentController,
            episodeId = snapshot.episodeId,
            positionMs = snapshot.targetPositionMs
        )
    }

    private suspend fun syncCurrentEpisodeUi() {
        controller?.let { capturedController ->
            val capturedEpisodeId = capturedController.currentMediaItem?.mediaId?.toLongOrNull()
            val capturedRequestGeneration = playRequestGeneration.get()
            val capturedMediaRevision = mediaStateRevision.get()
            val episode =
                if (capturedEpisodeId != null) {
                    withContext(dispatcherProvider.io) {
                        podcastQuery.getEpisode(capturedEpisodeId)
                    }
                } else {
                    null
                }
            if (
                isCurrentUiSnapshot(
                    capturedController,
                    capturedEpisodeId,
                    capturedRequestGeneration,
                    capturedMediaRevision
                )
            ) {
                val podcast =
                    episode?.podcastRssUrl?.let {
                        withContext(dispatcherProvider.io) {
                            podcastQuery.getPodcast(it)
                        }
                    }
                if (
                    isCurrentUiSnapshot(
                        capturedController,
                        capturedEpisodeId,
                        capturedRequestGeneration,
                        capturedMediaRevision
                    )
                ) {
                    _internalPlayerState.update { current ->
                        if (
                            isCurrentUiSnapshot(
                                capturedController,
                                capturedEpisodeId,
                                capturedRequestGeneration,
                                capturedMediaRevision
                            )
                        ) {
                            mapper.mapToUiState(capturedController, episode, podcast, current)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private fun isCurrentUiSnapshot(
        capturedController: MediaController,
        capturedEpisodeId: Long?,
        capturedRequestGeneration: Long,
        capturedMediaRevision: Long
    ): Boolean =
        controller === capturedController &&
            playRequestGeneration.get() == capturedRequestGeneration &&
            mediaStateRevision.get() == capturedMediaRevision &&
            capturedController.currentMediaItem?.mediaId?.toLongOrNull() == capturedEpisodeId

    override fun releaseResources() {
        explicitlyReleased = true
        playRequestGeneration.incrementAndGet()
        mediaStateRevision.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        val releaseTransition = tickerLifecycle.release()
        applyTickerTransition(releaseTransition)
        if (!releaseTransition.flushPlayback) flushCurrentPlaybackSnapshot()
        progressJob?.cancel()
        progressJob = null
        foregroundJob?.cancel()
        foregroundJob = null
        favoriteStatusJob?.cancel()
        favoriteStatusJob = null
        val ctrl = controller
        if (ctrl != null) cleanupController(ctrl)
        mediaConnection.release()
    }

    private fun cleanupController(ctrl: MediaController) {
        if (controller !== ctrl) return
        controllerListener?.let { listener ->
            runCatching { ctrl.removeListener(listener) }
        }
        controller = null
        controllerListener = null
        progressJob?.cancel()
        progressJob = null
        mediaScope?.cancel()
        mediaScope = null
        mediaDispatcher = null
        pendingSeek = null
        isUserSeeking = false
        _internalPlayerState.update { it.copy(isPlaying = false, isBuffering = false) }
    }
}

private data class PreparedPlayRequest(
    val generation: Long,
    val controller: MediaController,
    val episodeId: Long,
    val podcastRssUrl: String,
    val mediaItem: MediaItem,
    val startPositionMs: Long
)

private data class SeekSnapshot(
    val controller: MediaController,
    val episodeId: Long,
    val requestGeneration: Long,
    val mediaRevision: Long,
    val targetPositionMs: Long
)

private data class TerminalPlaybackSnapshot(
    val episodeId: Long,
    val positionMs: Long,
    val revision: Long
)

internal fun playbackTickIntervalMs(
    isPlayingReady: Boolean,
    isForeground: Boolean
): Long? =
    when {
        !isPlayingReady -> null
        isForeground -> FOREGROUND_TICK_INTERVAL_MS
        else -> BACKGROUND_TICK_INTERVAL_MS
    }

private const val FOREGROUND_TICK_INTERVAL_MS = 500L
private const val BACKGROUND_TICK_INTERVAL_MS = 5_000L
