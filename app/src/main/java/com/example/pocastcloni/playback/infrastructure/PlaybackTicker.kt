package com.example.pocastcloni.playback.infrastructure

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Vorschlag 1: PlaybackTicker
 * Kapselt die Zeit-Schleife (Progress Loop).
 * Single Responsibility: Erzeugt nur Ticks, kennt keinen Player.
 */
interface PlaybackTickSource {
    fun tick(intervalMs: Long): Flow<Unit>
}

class PlaybackTicker
@Inject
constructor() : PlaybackTickSource {
    /**
     * Erzeugt einen unendlichen Flow von Ticks.
     * @param intervalMs Das Intervall zwischen den Ticks.
     */
    override fun tick(intervalMs: Long): Flow<Unit> =
        flow {
            require(intervalMs > 0L) { "Tick interval must be positive" }
            while (true) {
                delay(intervalMs)
                emit(Unit)
            }
        }
}
