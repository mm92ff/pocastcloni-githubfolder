package com.example.pocastcloni.playback.infrastructure

import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.usecase.player.SavePlaybackProgressUseCase
import com.example.pocastcloni.domain.usecase.stats.AddListeningTimeUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlaybackProgressSnapshot(
    val episodeId: Long,
    val positionMs: Long,
    val sequence: Long
)

/**
 * App-scoped, non-blocking playback-progress writer.
 *
 * Requests are normalized and coalesced per episode in memory. A single application-scope worker
 * serializes writes, skips positions already persisted, and retries failures with bounded
 * exponential backoff. Cancelling the application scope cancels the worker immediately.
 */
@Singleton
@Suppress("TooGenericExceptionCaught")
class PlaybackProgressWriter
@Inject
constructor(
    private val savePlaybackProgress: SavePlaybackProgressUseCase,
    @ApplicationScope applicationScope: CoroutineScope
) {
    private val nextSequence = AtomicLong(0L)
    private val pendingByEpisodeId = ConcurrentHashMap<Long, PlaybackProgressSnapshot>()
    private val lastPersistedPositionByEpisodeId = ConcurrentHashMap<Long, Long>()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)

    init {
        applicationScope.launch {
            var retryDelayMs = INITIAL_RETRY_DELAY_MS

            while (true) {
                wakeups.receive()
                while (pendingByEpisodeId.isNotEmpty()) {
                    var writeFailed = false
                    val pendingPass = pendingByEpisodeId.values.sortedBy { it.sequence }

                    pendingPass.forEach { snapshot ->
                        if (lastPersistedPositionByEpisodeId[snapshot.episodeId] == snapshot.positionMs) {
                            pendingByEpisodeId.remove(snapshot.episodeId, snapshot)
                            return@forEach
                        }

                        try {
                            savePlaybackProgress(snapshot.episodeId, snapshot.positionMs)
                            lastPersistedPositionByEpisodeId[snapshot.episodeId] = snapshot.positionMs
                            pendingByEpisodeId.remove(snapshot.episodeId, snapshot)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Exception) {
                            writeFailed = true
                            Timber.e(error, "Failed to persist playback progress")
                        }
                    }

                    if (pendingByEpisodeId.isEmpty()) {
                        retryDelayMs = INITIAL_RETRY_DELAY_MS
                    } else if (writeFailed) {
                        delay(retryDelayMs)
                        retryDelayMs = (retryDelayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
                    }
                }

                if (pendingByEpisodeId.isEmpty()) {
                    retryDelayMs = INITIAL_RETRY_DELAY_MS
                }
            }
        }
    }

    fun request(
        episodeId: Long,
        positionMs: Long
    ) {
        if (episodeId <= 0L) return
        val snapshot =
            PlaybackProgressSnapshot(
                episodeId = episodeId,
                positionMs = positionMs.coerceAtLeast(0L),
                sequence = nextSequence.incrementAndGet()
            )
        pendingByEpisodeId.compute(episodeId) { _, current ->
            if (current == null || snapshot.sequence > current.sequence) snapshot else current
        }
        wakeups.trySend(Unit)
    }

    internal companion object {
        const val INITIAL_RETRY_DELAY_MS = 5_000L
        const val MAX_RETRY_DELAY_MS = 5L * 60L * 1_000L
    }
}

/**
 * App-scoped accumulator for listening time.
 *
 * Callers only add positive durations and request a flush; one worker serializes persistence.
 * Failed or cancelled writes put the claimed duration back before retrying or propagating
 * cancellation, so concurrent additions are not lost.
 */
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
            var retryDelayMs = INITIAL_RETRY_DELAY_MS
            while (true) {
                flushRequests.receive()
                while (pendingMs.get() > 0L) {
                    val amount = pendingMs.getAndSet(0L)
                    if (amount <= 0L) continue

                    try {
                        addListeningTime(amount)
                        retryDelayMs = INITIAL_RETRY_DELAY_MS
                    } catch (cancellation: CancellationException) {
                        pendingMs.addAndGet(amount)
                        throw cancellation
                    } catch (error: Exception) {
                        pendingMs.addAndGet(amount)
                        Timber.e(error, "Failed to persist listening time")
                        delay(retryDelayMs)
                        retryDelayMs = (retryDelayMs * 2L).coerceAtMost(MAX_RETRY_DELAY_MS)
                    }
                }
            }
        }
    }

    fun recordAndFlush(durationMs: Long) {
        if (durationMs > 0L) pendingMs.addAndGet(durationMs)
        flushRequests.trySend(Unit)
    }

    internal companion object {
        const val INITIAL_RETRY_DELAY_MS = 5_000L
        const val MAX_RETRY_DELAY_MS = 5L * 60L * 1_000L
    }
}
