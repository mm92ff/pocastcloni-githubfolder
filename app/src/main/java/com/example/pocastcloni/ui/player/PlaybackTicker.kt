package com.example.pocastcloni.ui.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Vorschlag 1: PlaybackTicker
 * Kapselt die Zeit-Schleife (Progress Loop).
 * Single Responsibility: Erzeugt nur Ticks, kennt keinen Player.
 */
class PlaybackTicker @Inject constructor() {

    /**
     * Erzeugt einen unendlichen Flow von Ticks.
     * @param intervalMs Das Intervall zwischen den Ticks.
     */
    fun tick(intervalMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }
}