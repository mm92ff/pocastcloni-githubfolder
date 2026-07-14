package com.example.pocastcloni.playback.infrastructure

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Supplies cold tick flows without owning a coroutine scope or player state.
 *
 * Collection owns the timing loop: the first tick arrives after the requested interval, each later
 * tick uses the same delay, and cancelling the collector promptly cancels the suspending delay.
 */
interface PlaybackTickSource {
    fun tick(intervalMs: Long): Flow<Unit>
}

/** Creates scope-neutral playback ticks while controller lifecycle policy stays with its caller. */
class PlaybackTicker
@Inject
constructor() : PlaybackTickSource {
    /** Returns a cold, cancellation-cooperative flow with no immediate tick. */
    override fun tick(intervalMs: Long): Flow<Unit> =
        flow {
            require(intervalMs > 0L) { "Tick interval must be positive" }
            while (true) {
                delay(intervalMs)
                emit(Unit)
            }
        }
}
