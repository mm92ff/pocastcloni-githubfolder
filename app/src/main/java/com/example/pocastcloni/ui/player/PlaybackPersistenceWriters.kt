package com.example.pocastcloni.ui.player

import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.usecase.player.SavePlaybackProgressUseCase
import com.example.pocastcloni.domain.usecase.stats.AddListeningTimeUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlaybackProgressSnapshot(
    val episodeId: Long,
    val positionMs: Long
)

@Singleton
@Suppress("TooGenericExceptionCaught")
class PlaybackProgressWriter
@Inject
constructor(
    private val savePlaybackProgress: SavePlaybackProgressUseCase,
    @ApplicationScope applicationScope: CoroutineScope
) {
    private val requests = Channel<PlaybackProgressSnapshot>(Channel.CONFLATED)

    init {
        applicationScope.launch {
            var lastPersisted: PlaybackProgressSnapshot? = null
            var snapshot = requests.receive()
            var retryDelayMs = INITIAL_RETRY_DELAY_MS

            while (true) {
                if (snapshot == lastPersisted) {
                    snapshot = requests.receive()
                    continue
                }
                try {
                    savePlaybackProgress(snapshot.episodeId, snapshot.positionMs)
                    lastPersisted = snapshot
                    retryDelayMs = INITIAL_RETRY_DELAY_MS
                    snapshot = requests.receive()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.e(error, "Failed to persist playback progress")
                    val newerSnapshot =
                        withTimeoutOrNull(retryDelayMs) {
                            requests.receive()
                        }
                    currentCoroutineContext().ensureActive()
                    if (newerSnapshot != null) snapshot = newerSnapshot
                    retryDelayMs = (retryDelayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }
    }

    fun request(
        episodeId: Long,
        positionMs: Long
    ) {
        if (episodeId <= 0L) return
        requests.trySend(PlaybackProgressSnapshot(episodeId, positionMs.coerceAtLeast(0L)))
    }

    internal companion object {
        const val INITIAL_RETRY_DELAY_MS = 5_000L
        const val MAX_RETRY_DELAY_MS = 5L * 60L * 1_000L
    }
}

@Singleton
@Suppress("TooGenericExceptionCaught")
class PlaybackListeningTimeWriter
@Inject
constructor(
    private val addListeningTime: AddListeningTimeUseCase,
    @ApplicationScope applicationScope: CoroutineScope
) {
    private val pendingMs = AtomicLong(0L)
    private val flushRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        applicationScope.launch {
            for (ignored in flushRequests) {
                val amount = pendingMs.getAndSet(0L)
                if (amount <= 0L) continue

                try {
                    addListeningTime(amount)
                    if (pendingMs.get() > 0L) flushRequests.trySend(Unit)
                } catch (cancellation: CancellationException) {
                    pendingMs.addAndGet(amount)
                    throw cancellation
                } catch (error: Exception) {
                    pendingMs.addAndGet(amount)
                    Timber.e(error, "Failed to persist listening time")
                }
            }
        }
    }

    fun recordAndFlush(durationMs: Long) {
        if (durationMs > 0L) pendingMs.addAndGet(durationMs)
        flushRequests.trySend(Unit)
    }
}
