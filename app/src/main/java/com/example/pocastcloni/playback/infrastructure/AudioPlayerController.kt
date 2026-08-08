package com.example.pocastcloni.playback.infrastructure

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import com.example.pocastcloni.R
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.player.PlaybackUnavailableException
import com.example.pocastcloni.domain.usecase.player.PreparePlaybackUseCase
import com.example.pocastcloni.playback.api.PlaybackState
import com.example.pocastcloni.playback.api.PlaybackStarter
import com.example.pocastcloni.playback.api.PlayerCommandPort
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.playback.api.PlayerStatePort
import com.example.pocastcloni.playback.api.PlayerUiState
import com.example.pocastcloni.playback.api.PlayerVisibilityProvider
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Coordinates player commands, observable state, and controller-bound background work.
 *
 * [controllerScope] lives for this singleton, while each connected controller owns a child media
 * scope that is cancelled on disconnect or release. Low-level construction retries belong to
 * [MediaControllerConnection]; this class serializes activation and uses a lifecycle generation so
 * a suspended connect or reconnect from before [releaseResources] cannot attach after later reuse.
 *
 * Reconnect jobs have identity-based ownership. Acceptance hands ownership off before post-connect
 * synchronization, cancellation detaches the exact owner, and an old job's `finally` cannot clear
 * a newer job. Coroutine cancellation is otherwise propagated by the suspended connection path.
 * [releaseResources] serializes generation invalidation, cleanup, and low-level release against
 * activation; it remains reusable so a later user-initiated [play] can open the current generation.
 */
@Singleton
@Suppress("TooManyFunctions", "LargeClass")
class AudioPlayerController
@Inject
@Suppress("LongParameterList")
constructor(
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
    private val connectionLifecycleLock = Any()
    private val connectMutex = Mutex()
    private val playCommitMutex = Mutex()
    private val playRequestGeneration = AtomicLong(0L)
    private val mediaStateRevision = AtomicLong(0L)
    private val connectionLifecycleGeneration = AtomicLong(0L)
    private val reconnectOwnership = ReconnectJobOwnership()

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

    // Playback decisions wait for a persisted settings value instead of observing a synthetic default.
    private val userSettings =
        userPreferencesRepository.userSettingsFlow
            .shareIn(controllerScope, SharingStarted.Eagerly, replay = 1)

    // Seek State
    private val seekLock = Any()
    private val seekGeneration = AtomicLong(0L)

    @Volatile
    private var pendingSeek: SeekTransaction? = null

    @Volatile
    private var lastAuthoritativePositionMs: Long = 0L

    private var seekConfirmationJob: Job? = null
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

    private suspend fun connectInternal(
        userInitiated: Boolean = false,
        expectedLifecycleGeneration: Long? = null,
        onControllerAccepted: (() -> Unit)? = null
    ) {
        val connectGeneration =
            synchronized(connectionLifecycleLock) {
                if (userInitiated) explicitlyReleased = false
                val currentGeneration = connectionLifecycleGeneration.get()
                currentGeneration.takeIf {
                    !explicitlyReleased &&
                        (expectedLifecycleGeneration == null || expectedLifecycleGeneration == currentGeneration)
                }
            } ?: return

        ensureForegroundObservation()
        val connectedController =
            connectMutex.withLock {
                connectControllerLocked(connectGeneration, onControllerAccepted)
            }
        if (connectedController != null && isCurrentController(connectedController, connectGeneration)) {
            updateProgressAndAnalytics(deltaMs = 0L, updateUi = true)
            reconcileTicker()
            syncCurrentEpisodeUi()
        }
    }

    private suspend fun connectControllerLocked(
        connectGeneration: Long,
        onControllerAccepted: (() -> Unit)?
    ): MediaController? {
        var connectedController: MediaController? = null
        if (isCurrentLifecycle(connectGeneration) && controller == null) {
            val candidate = mediaConnection.connect()
            when {
                candidate == null -> {
                    if (isCurrentLifecycle(connectGeneration)) {
                        _internalPlayerState.update {
                            it.copy(error = UiText.StringResource(R.string.playback_failed_error))
                        }
                    }
                }
                else -> {
                    val accepted =
                        synchronized(connectionLifecycleLock) {
                            if (
                                connectionLifecycleGeneration.get() == connectGeneration &&
                                !explicitlyReleased &&
                                controller == null
                            ) {
                                controller = candidate
                                ensureMediaScope(candidate)
                                attachListener(candidate)
                                onControllerAccepted?.invoke()
                                true
                            } else {
                                false
                            }
                        }
                    if (accepted) {
                        connectedController = candidate
                    } else {
                        mediaConnection.release()
                    }
                }
            }
        }
        return connectedController
    }

    private fun isCurrentLifecycle(connectGeneration: Long): Boolean =
        synchronized(connectionLifecycleLock) {
            connectionLifecycleGeneration.get() == connectGeneration && !explicitlyReleased
        }

    private fun isCurrentController(
        connectedController: MediaController,
        connectGeneration: Long
    ): Boolean =
        synchronized(connectionLifecycleLock) {
            connectionLifecycleGeneration.get() == connectGeneration &&
                !explicitlyReleased &&
                controller === connectedController
        }

    private fun onControllerDisconnected(disconnectedController: MediaController) {
        controllerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val reconnectGeneration =
                connectMutex.withLock {
                    if (controller !== disconnectedController) return@withLock null
                    val releaseTransition = tickerLifecycle.release()
                    applyTickerTransition(releaseTransition)
                    if (!releaseTransition.flushPlayback) {
                        flushCurrentPlaybackSnapshot(disconnectedController)
                    }
                    cleanupController(disconnectedController)
                    synchronized(connectionLifecycleLock) {
                        connectionLifecycleGeneration.get().takeIf { !explicitlyReleased }
                    }
                }
            reconnectGeneration?.let(::scheduleReconnect)
        }
    }

    private fun scheduleReconnect(connectGeneration: Long) {
        if (!isCurrentLifecycle(connectGeneration)) return
        val owner = Any()
        val job =
            controllerScope.launch(start = CoroutineStart.LAZY) {
                try {
                    connectInternal(
                        expectedLifecycleGeneration = connectGeneration,
                        onControllerAccepted = { reconnectOwnership.clearIfOwned(owner) }
                    )
                } finally {
                    reconnectOwnership.clearIfOwned(owner)
                }
            }
        if (reconnectOwnership.tryOwn(owner, job)) {
            if (isCurrentLifecycle(connectGeneration)) {
                job.start()
            } else {
                reconnectOwnership.cancelIfOwned(owner)
            }
        } else {
            job.cancel()
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
        positionMs: Long? = null
    ) {
        val resolvedPositionMs =
            positionMs
                ?: pendingSeek
                    ?.takeIf { transaction -> player != null && isCurrentSeekContext(transaction, player) }
                    ?.authoritativePositionMs
                ?: player?.currentPosition
        if (episodeId == null || episodeId <= 0L || resolvedPositionMs == null) return
        val snapshot =
            synchronized(flushLock) {
                TerminalPlaybackSnapshot(
                    episodeId = episodeId,
                    positionMs = resolvedPositionMs.coerceAtLeast(0L),
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
        val activeSeek = pendingSeek
        val analyticsPositionMs = activeSeek?.authoritativePositionMs ?: currentPositionMs

        if (activeSeek == null) lastAuthoritativePositionMs = currentPositionMs

        if (settings != null && deltaMs > 0L) {
            markPlaybackSnapshotDirty()
            analyticsHandler.onTick(
                episodeId = player.currentMediaItem?.mediaId?.toLongOrNull(),
                currentPositionMs = analyticsPositionMs,
                durationMs = durationMs,
                deltaMs = deltaMs,
                isPlaying = isPlaying || countAsPlaying,
                markPlayedThresholdSeconds = settings.markPlayedDurationSeconds
            )
        }

        if (updateUi) {
            _internalPlaybackState.update {
                if (pendingSeek == null) {
                    it.copy(
                        currentPositionMs = currentPositionMs,
                        bufferedPositionMs = player.bufferedPosition,
                        durationMs = durationMs,
                        isSeekable = isSeekCommandAvailable(player),
                        isSeekPending = false
                    )
                } else {
                    it.copy(
                        bufferedPositionMs = player.bufferedPosition,
                        durationMs = durationMs,
                        isSeekable = isSeekCommandAvailable(player),
                        isSeekPending = true
                    )
                }
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
                    if (controller !== mediaController) return
                    _internalPlayerState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                    if (playbackState == Player.STATE_READY) {
                        launchOnMedia { confirmPendingSeekFromPlayer(mediaController) }
                    }
                    requestTickerReconciliation()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (controller !== mediaController) return
                    _internalPlayerState.update { it.copy(isPlaying = isPlaying) }
                    requestTickerReconciliation()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (controller !== mediaController) return
                    Timber.e(error, "Player error code: ${error.errorCode}")
                    val errorString =
                        mapper.mapError(error)?.let(UiText::StringResource)
                            ?: UiText.StringResource(R.string.playback_failed_error)
                    _internalPlayerState.update {
                        it.copy(isPlaying = false, isBuffering = false, error = errorString)
                    }
                    requestTickerReconciliation()
                }

                override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                    if (controller !== mediaController) return
                    val isSeekable = isSeekCommandAvailable(mediaController, availableCommands)
                    if (isSeekable) {
                        _internalPlaybackState.update { it.copy(isSeekable = true) }
                    } else {
                        cancelPendingSeekAndPublishActual(mediaController)
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (controller !== mediaController) return
                    if (
                        reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                    ) {
                        completePendingSeekFromDiscontinuity(
                            mediaController = mediaController,
                            confirmedPositionMs = newPosition.positionMs,
                            reason = reason
                        )
                    }
                }

                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    if (controller !== mediaController) return
                    if (isSeekCommandAvailable(mediaController)) {
                        _internalPlaybackState.update { it.copy(isSeekable = true) }
                    } else {
                        cancelPendingSeekAndPublishActual(mediaController)
                    }
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    if (
                        controller !== mediaController ||
                        mediaItem?.mediaId != mediaController.currentMediaItem?.mediaId
                    ) {
                        return
                    }
                    mediaStateRevision.incrementAndGet()
                    cancelPendingSeekAndPublishActual(
                        mediaController = mediaController,
                        queryControllerPosition = true
                    )
                    analyticsHandler.onMediaItemTransition()
                    controllerScope.launch { syncCurrentEpisodeUi() }
                }
            }
        controllerListener = listener
        _internalPlaybackState.update {
            it.copy(isSeekable = isSeekCommandAvailable(mediaController))
        }
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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun play(episodeId: Long) {
        try {
            val request = preparePlayRequest(episodeId) ?: return
            withContext(mediaDispatcherOrFallback()) { commitPlayRequest(request) }
            controllerScope.launch { syncCurrentEpisodeUi() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: PlaybackUnavailableException) {
            reportPlaybackStartFailure(error, R.string.playback_unavailable_error)
        } catch (error: Exception) {
            reportPlaybackStartFailure(error, R.string.playback_failed_error)
        }
    }

    private fun reportPlaybackStartFailure(
        error: Exception,
        messageResource: Int
    ) {
        Timber.w(error, "Playback could not be started")
        _internalPlayerState.update {
            it.copy(error = UiText.StringResource(messageResource))
        }
    }

    private suspend fun preparePlayRequest(episodeId: Long): PreparedPlayRequest? {
        val requestGeneration =
            playCommitMutex.withLock {
                playRequestGeneration.incrementAndGet()
            }
        controller?.let(::cancelPendingSeekAndPublishActual)
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
                    current.copy(
                        currentPodcastUrl = request.podcastRssUrl,
                        error = null
                    )
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
            is PlayerScreenEvent.SeekTo -> handleSeekTo(event.positionMs)
            PlayerScreenEvent.SeekStarted -> startSeekGesture()
            PlayerScreenEvent.SeekFinished -> finishSeekGesture()
            else -> Unit
        }
    }

    private fun startSeekGesture() {
        val currentController = controller
        if (currentController == null || !isSeekCommandAvailable(currentController)) {
            currentController?.let(::cancelPendingSeekAndPublishActual)
            return
        }

        val transaction =
            createSeekTransaction(
                mediaController = currentController,
                targetPositionMs = _internalPlaybackState.value.currentPositionMs,
                phase = SeekPhase.GESTURE_ACTIVE,
                hasTarget = false
            ) ?: return
        replacePendingSeek(transaction)
        _internalPlaybackState.update {
            it.copy(isSeekable = true, isSeekPending = true)
        }
    }

    @Suppress("ReturnCount")
    private fun handleSeekTo(requestedPositionMs: Long) {
        val currentController = controller ?: return
        if (!isSeekCommandAvailable(currentController)) {
            cancelPendingSeekAndPublishActual(currentController)
            return
        }

        val targetPositionMs = clampSeekTarget(currentController, requestedPositionMs)
        val gestureUpdate =
            synchronized(seekLock) {
                pendingSeek
                    ?.takeIf {
                        it.phase == SeekPhase.GESTURE_ACTIVE &&
                            isCurrentSeekContext(it, currentController)
                    }
                    ?.copy(targetPositionMs = targetPositionMs, hasTarget = true)
                    ?.also { pendingSeek = it }
            }

        if (gestureUpdate != null) {
            _internalPlaybackState.update {
                it.copy(
                    currentPositionMs = targetPositionMs,
                    isSeekable = true,
                    isSeekPending = true
                )
            }
            return
        }

        val directSeek =
            createSeekTransaction(
                mediaController = currentController,
                targetPositionMs = targetPositionMs,
                phase = SeekPhase.AWAITING_CONFIRMATION,
                hasTarget = true
            ) ?: return
        val installedSeek = replacePendingSeek(directSeek)
        _internalPlaybackState.update {
            it.copy(
                currentPositionMs = targetPositionMs,
                isSeekable = true,
                isSeekPending = true
            )
        }
        launchOnMedia { dispatchSeek(installedSeek) }
    }

    private fun finishSeekGesture() {
        var shouldRestoreActual = false
        val transaction =
            synchronized(seekLock) {
                val active = pendingSeek
                when {
                    active?.phase != SeekPhase.GESTURE_ACTIVE -> null
                    !active.hasTarget -> {
                        pendingSeek = null
                        seekConfirmationJob?.cancel()
                        seekConfirmationJob = null
                        shouldRestoreActual = true
                        null
                    }
                    else ->
                        active.copy(phase = SeekPhase.AWAITING_CONFIRMATION)
                            .also { pendingSeek = it }
                }
            }
        if (shouldRestoreActual) {
            controller?.let(::publishActualPlaybackPosition)
        } else if (transaction != null) {
            launchOnMedia { dispatchSeek(transaction) }
        }
    }

    private fun createSeekTransaction(
        mediaController: MediaController,
        targetPositionMs: Long,
        phase: SeekPhase,
        hasTarget: Boolean
    ): SeekTransaction? {
        val episodeId = mediaController.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        return SeekTransaction(
            controller = mediaController,
            episodeId = episodeId,
            requestGeneration = playRequestGeneration.get(),
            mediaRevision = mediaStateRevision.get(),
            generation = seekGeneration.incrementAndGet(),
            targetPositionMs = clampSeekTarget(mediaController, targetPositionMs),
            authoritativePositionMs =
            pendingSeek?.authoritativePositionMs ?: lastAuthoritativePositionMs,
            phase = phase,
            hasTarget = hasTarget
        )
    }

    private fun replacePendingSeek(transaction: SeekTransaction): SeekTransaction =
        synchronized(seekLock) {
            seekConfirmationJob?.cancel()
            seekConfirmationJob = null
            val previous = pendingSeek
            val inheritedSupersededSeeks = previous?.supersededSeeks.orEmpty()
            val newlySupersededSeek =
                previous
                    ?.takeIf { it.commandIssued }
                    ?.let { SupersededSeek(it.generation, it.targetPositionMs) }
            transaction
                .copy(
                    authoritativePositionMs =
                    previous?.authoritativePositionMs ?: transaction.authoritativePositionMs,
                    supersededSeeks =
                    (inheritedSupersededSeeks + listOfNotNull(newlySupersededSeek))
                        .takeLast(MAX_SUPERSEDED_SEEKS)
                ).also { pendingSeek = it }
        }

    @Suppress("ReturnCount")
    private fun dispatchSeek(transaction: SeekTransaction) {
        val currentController = controller
        if (
            currentController == null ||
            !isCurrentSeekContext(transaction, currentController) ||
            !isSeekCommandAvailable(currentController)
        ) {
            cancelPendingSeekAndPublishActual(
                mediaController = currentController ?: transaction.controller,
                expectedGeneration = transaction.generation
            )
            return
        }

        val issuedTransaction =
            synchronized(seekLock) {
                pendingSeek
                    ?.takeIf {
                        it.generation == transaction.generation &&
                            it.phase == SeekPhase.AWAITING_CONFIRMATION
                    }
                    ?.copy(commandIssued = true)
                    ?.also { pendingSeek = it }
            } ?: return

        try {
            currentController.seekTo(issuedTransaction.targetPositionMs)
        } catch (error: IllegalStateException) {
            Timber.w(error, "Timeline seek command failed")
            cancelPendingSeekAndPublishActual(currentController, issuedTransaction.generation)
            return
        }

        if (
            positionsRepresentSamePoint(
                issuedTransaction.authoritativePositionMs,
                issuedTransaction.targetPositionMs
            )
        ) {
            completeSeek(
                currentController,
                issuedTransaction.generation,
                issuedTransaction.authoritativePositionMs
            )
        } else {
            scheduleSeekConfirmationTimeout(issuedTransaction)
        }
    }

    private fun scheduleSeekConfirmationTimeout(transaction: SeekTransaction) {
        val timeoutJob =
            launchOnMedia {
                delay(SEEK_CONFIRMATION_TIMEOUT_MS)
                val currentController = controller
                if (currentController != null) {
                    cancelPendingSeekAndPublishActual(
                        mediaController = currentController,
                        expectedGeneration = transaction.generation,
                        queryControllerPosition = true
                    )
                } else {
                    clearPendingSeek(transaction.generation)
                }
            }
        synchronized(seekLock) {
            if (pendingSeek?.generation == transaction.generation) {
                seekConfirmationJob?.cancel()
                seekConfirmationJob = timeoutJob
            } else {
                timeoutJob.cancel()
            }
        }
    }

    private fun completePendingSeekFromDiscontinuity(
        mediaController: MediaController,
        confirmedPositionMs: Long,
        reason: Int
    ) {
        val settlingTransaction =
            synchronized(seekLock) {
                val transaction = pendingSeek ?: return@synchronized null
                if (
                    !isIssuedSeekForCurrentContext(transaction, mediaController) ||
                    !isSeekDiscontinuityReason(reason)
                ) {
                    return@synchronized null
                }

                val currentDistanceMs =
                    abs(confirmedPositionMs - transaction.targetPositionMs)
                val closestSupersededSeek =
                    transaction.supersededSeeks
                        .minByOrNull { superseded ->
                            abs(confirmedPositionMs - superseded.targetPositionMs)
                        }?.takeIf { superseded ->
                            val distanceMs =
                                abs(confirmedPositionMs - superseded.targetPositionMs)
                            distanceMs <= SEEK_DISCONTINUITY_MATCH_TOLERANCE_MS &&
                                distanceMs < currentDistanceMs
                        }

                if (closestSupersededSeek != null) {
                    pendingSeek =
                        transaction.copy(
                            supersededSeeks =
                            transaction.supersededSeeks - closestSupersededSeek
                        )
                    return@synchronized null
                }

                if (currentDistanceMs > SEEK_DISCONTINUITY_MATCH_TOLERANCE_MS) {
                    return@synchronized null
                }

                transaction
                    .copy(
                        phase = SeekPhase.SETTLING,
                        confirmedPositionMs = confirmedPositionMs
                    ).also { pendingSeek = it }
            } ?: return

        if (mediaController.playbackState != Player.STATE_BUFFERING) {
            completeSeek(
                mediaController = mediaController,
                expectedGeneration = settlingTransaction.generation,
                confirmedPositionMs = confirmedPositionMs
            )
        }
    }

    private fun confirmPendingSeekFromPlayer(mediaController: MediaController) {
        val transaction = pendingSeek ?: return
        if (
            transaction.phase != SeekPhase.SETTLING ||
            transaction.confirmedPositionMs == null ||
            !isIssuedSeekForCurrentContext(transaction, mediaController)
        ) {
            return
        }
        val actualPositionMs = mediaController.currentPosition.coerceAtLeast(0L)
        completeSeek(mediaController, transaction.generation, actualPositionMs)
    }

    private fun completeSeek(
        mediaController: MediaController,
        expectedGeneration: Long,
        confirmedPositionMs: Long
    ) {
        val completed =
            synchronized(seekLock) {
                pendingSeek
                    ?.takeIf {
                        it.generation == expectedGeneration &&
                            it.commandIssued &&
                            isCurrentSeekContext(it, mediaController)
                    }
                    ?.also {
                        pendingSeek = null
                        seekConfirmationJob?.cancel()
                        seekConfirmationJob = null
                    }
            } ?: return
        val safePositionMs = clampSeekTarget(mediaController, confirmedPositionMs)
        lastAuthoritativePositionMs = safePositionMs
        _internalPlaybackState.update {
            if (pendingSeek == null) {
                it.copy(
                    currentPositionMs = safePositionMs,
                    bufferedPositionMs = mediaController.bufferedPosition,
                    durationMs = mediaController.duration.takeIf { duration -> duration > 0L } ?: it.durationMs,
                    isSeekable = isSeekCommandAvailable(mediaController),
                    isSeekPending = false
                )
            } else {
                it
            }
        }
        markPlaybackSnapshotDirty()
        flushCurrentPlaybackSnapshot(
            player = mediaController,
            episodeId = completed.episodeId,
            positionMs = safePositionMs
        )
    }

    private fun cancelPendingSeekAndPublishActual(
        mediaController: MediaController,
        expectedGeneration: Long? = null,
        queryControllerPosition: Boolean = false
    ) {
        val cleared = clearPendingSeek(expectedGeneration)
        if (cleared != null || expectedGeneration == null) {
            val positionMs =
                if (queryControllerPosition) {
                    mediaController.currentPosition.coerceAtLeast(0L)
                } else {
                    cleared?.authoritativePositionMs
                        ?: mediaController.currentPosition.coerceAtLeast(0L)
                }
            publishPlaybackPosition(mediaController, positionMs)
        }
    }

    private fun clearPendingSeek(expectedGeneration: Long? = null): SeekTransaction? =
        synchronized(seekLock) {
            if (expectedGeneration != null && pendingSeek?.generation != expectedGeneration) {
                null
            } else {
                val cleared = pendingSeek
                pendingSeek = null
                seekConfirmationJob?.cancel()
                seekConfirmationJob = null
                cleared
            }
        }

    private fun publishActualPlaybackPosition(mediaController: MediaController) {
        publishPlaybackPosition(
            mediaController = mediaController,
            positionMs = mediaController.currentPosition.coerceAtLeast(0L)
        )
    }

    private fun publishPlaybackPosition(
        mediaController: MediaController,
        positionMs: Long
    ) {
        if (controller !== mediaController || pendingSeek != null) return
        val safePositionMs = clampSeekTarget(mediaController, positionMs)
        lastAuthoritativePositionMs = safePositionMs
        _internalPlaybackState.update {
            if (pendingSeek == null) {
                it.copy(
                    currentPositionMs = safePositionMs,
                    bufferedPositionMs = mediaController.bufferedPosition,
                    durationMs = mediaController.duration.takeIf { duration -> duration > 0L } ?: it.durationMs,
                    isSeekable = isSeekCommandAvailable(mediaController),
                    isSeekPending = false
                )
            } else {
                it
            }
        }
    }

    private fun clampSeekTarget(
        mediaController: MediaController,
        requestedPositionMs: Long
    ): Long {
        val durationMs =
            mediaController.duration.takeIf { it > 0L }
                ?: _internalPlaybackState.value.durationMs.takeIf { it > 0L }
        val nonNegativePositionMs = requestedPositionMs.coerceAtLeast(0L)
        return durationMs?.let { nonNegativePositionMs.coerceAtMost(it) } ?: nonNegativePositionMs
    }

    private fun isCurrentSeekContext(
        transaction: SeekTransaction,
        mediaController: MediaController
    ): Boolean =
        controller === mediaController &&
            transaction.controller === mediaController &&
            playRequestGeneration.get() == transaction.requestGeneration &&
            mediaStateRevision.get() == transaction.mediaRevision &&
            mediaController.currentMediaItem?.mediaId?.toLongOrNull() == transaction.episodeId

    private fun isIssuedSeekForCurrentContext(
        transaction: SeekTransaction,
        mediaController: MediaController
    ): Boolean =
        (
            transaction.phase == SeekPhase.AWAITING_CONFIRMATION ||
                transaction.phase == SeekPhase.SETTLING
            ) &&
            transaction.commandIssued &&
            isCurrentSeekContext(transaction, mediaController)

    private fun isSeekCommandAvailable(
        mediaController: MediaController,
        availableCommands: Player.Commands? = null
    ): Boolean =
        runCatching {
            val commandAvailable =
                availableCommands?.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    ?: mediaController.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            commandAvailable && mediaController.isCurrentMediaItemSeekable
        }.getOrDefault(false)

    private fun positionsRepresentSamePoint(
        actualPositionMs: Long,
        requestedPositionMs: Long
    ): Boolean = abs(actualPositionMs - requestedPositionMs) <= SAME_POSITION_TOLERANCE_MS

    private fun isSeekDiscontinuityReason(reason: Int): Boolean =
        reason == Player.DISCONTINUITY_REASON_SEEK ||
            reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT

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

    /**
     * Invalidates suspended connection work and releases controller-bound jobs and resources.
     * Generation change, cleanup, and low-level release are one non-suspending transition relative
     * to controller activation. The singleton scope remains alive for later user-initiated reuse.
     */
    override fun releaseResources() {
        synchronized(connectionLifecycleLock) {
            explicitlyReleased = true
            connectionLifecycleGeneration.incrementAndGet()
            playRequestGeneration.incrementAndGet()
            mediaStateRevision.incrementAndGet()
            reconnectOwnership.cancelCurrent()
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
        clearPendingSeek()
        _internalPlaybackState.update {
            it.copy(isSeekable = false, isSeekPending = false)
        }
        _internalPlayerState.update { it.copy(isPlaying = false, isBuffering = false) }
    }
}

internal class ReconnectJobOwnership {
    private val lock = Any()
    private var ownedJob: OwnedReconnectJob? = null

    internal val hasOwner: Boolean
        get() = synchronized(lock) { ownedJob != null }

    fun tryOwn(
        owner: Any,
        job: Job
    ): Boolean =
        synchronized(lock) {
            if (ownedJob != null) {
                false
            } else {
                ownedJob = OwnedReconnectJob(owner, job)
                true
            }
        }

    fun clearIfOwned(owner: Any) {
        synchronized(lock) {
            if (ownedJob?.owner === owner) ownedJob = null
        }
    }

    fun cancelIfOwned(owner: Any) {
        val job =
            synchronized(lock) {
                ownedJob
                    ?.takeIf { it.owner === owner }
                    ?.also { ownedJob = null }
                    ?.job
            }
        job?.cancel()
    }

    fun cancelCurrent() {
        val job = synchronized(lock) { ownedJob?.job.also { ownedJob = null } }
        job?.cancel()
    }
}

private data class OwnedReconnectJob(
    val owner: Any,
    val job: Job
)

private data class PreparedPlayRequest(
    val generation: Long,
    val controller: MediaController,
    val episodeId: Long,
    val podcastRssUrl: String,
    val mediaItem: MediaItem,
    val startPositionMs: Long
)

private data class SeekTransaction(
    val controller: MediaController,
    val episodeId: Long,
    val requestGeneration: Long,
    val mediaRevision: Long,
    val generation: Long,
    val targetPositionMs: Long,
    val authoritativePositionMs: Long,
    val phase: SeekPhase,
    val hasTarget: Boolean,
    val commandIssued: Boolean = false,
    val confirmedPositionMs: Long? = null,
    val supersededSeeks: List<SupersededSeek> = emptyList()
)

private data class SupersededSeek(
    val generation: Long,
    val targetPositionMs: Long
)

private enum class SeekPhase {
    GESTURE_ACTIVE,
    AWAITING_CONFIRMATION,
    SETTLING
}

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
private const val SEEK_CONFIRMATION_TIMEOUT_MS = 5_000L
private const val SAME_POSITION_TOLERANCE_MS = 250L
private const val SEEK_DISCONTINUITY_MATCH_TOLERANCE_MS = 2_000L
private const val MAX_SUPERSEDED_SEEKS = 8
