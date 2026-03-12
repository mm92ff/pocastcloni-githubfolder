package com.example.pocastcloni.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

data class SmoothedProgressState(
    val currentPosition: State<Long>,
    val jumpTo: (Long) -> Unit
)

/**
 * Glättet die Position auf Frame-Basis (60/120fps), auch wenn der Backend-Tick selten ist (z.B. 500ms).
 *
 * Fixes:
 * - Kein "Vorspulen" nach Pause/Resume: Timebase wird bei Start des Loops neu gesetzt.
 * - jumpTo() setzt die Timebase im nächsten Frame (kompatibel mit withFrameNanos-Zeitbasis).
 */
@Composable
fun rememberSmoothedProgressState(
    playbackStateFlow: StateFlow<PlaybackState>,
    isPlaying: Boolean
): SmoothedProgressState {
    val basePositionMs = remember { mutableLongStateOf(0L) }
    val baseTimeNanos = remember { mutableLongStateOf(0L) }
    val latestDurationMs = remember { mutableLongStateOf(0L) }

    // UI-State (wird in der Draw-Phase gelesen)
    val smoothedMs = remember { mutableLongStateOf(0L) }

    // Signal: Timebase soll im nächsten Frame neu gesetzt werden (z.B. nach jumpTo)
    val needsTimebaseReset = remember { mutableStateOf(false) }

    // 1) Sync mit echten Player-Events (Tick, Seek-Complete, Track-Change)
    LaunchedEffect(playbackStateFlow) {
        playbackStateFlow
            .map { it.currentPositionMs to it.durationMs }
            .distinctUntilChanged()
            .collect { (pos, dur) ->
                val clampedDur = dur.coerceAtLeast(0L)
                val clampedPos = pos.coerceAtLeast(0L).coerceIn(0L, clampedDur)

                latestDurationMs.longValue = clampedDur
                basePositionMs.longValue = clampedPos

                // Timebase neu setzen, damit Prediction ab "jetzt" läuft (verhindert Drift / Sprünge)
                baseTimeNanos.longValue = withFrameNanos { it }
                needsTimebaseReset.value = false

                // Reset auf echten Wert
                smoothedMs.longValue = clampedPos
            }
    }

    // 2) Interpolation Loop (läuft nur wenn Playing)
    LaunchedEffect(isPlaying) {
        // Egal ob Pause oder Play: beim Umschalten einmal sauber resyncen
        val now = withFrameNanos { it }
        val dur = playbackStateFlow.value.durationMs.coerceAtLeast(0L)
        val pos = playbackStateFlow.value.currentPositionMs.coerceAtLeast(0L).coerceIn(0L, dur)

        latestDurationMs.longValue = dur
        basePositionMs.longValue = pos
        baseTimeNanos.longValue = now
        smoothedMs.longValue = pos
        needsTimebaseReset.value = false

        if (!isPlaying) return@LaunchedEffect

        while (isActive) {
            val frameTime = withFrameNanos { it }

            val currentDur = latestDurationMs.longValue
            if (currentDur <= 0L) {
                smoothedMs.longValue = 0L
                continue
            }

            // Falls jumpTo() gerufen wurde: Timebase im Frame-Kontext neu setzen
            if (needsTimebaseReset.value) {
                baseTimeNanos.longValue = frameTime
                needsTimebaseReset.value = false
            }

            val elapsedMs = ((frameTime - baseTimeNanos.longValue).coerceAtLeast(0L)) / 1_000_000L
            val predicted = basePositionMs.longValue + elapsedMs
            smoothedMs.longValue = predicted.coerceIn(0L, currentDur)
        }
    }

    // 3) Jump-Funktion für sofortiges UI-Feedback beim User-Seek
    val jumpTo: (Long) -> Unit = remember {
        { newPos ->
            val dur = latestDurationMs.longValue
            val clamped = if (dur > 0L) newPos.coerceIn(0L, dur) else newPos.coerceAtLeast(0L)

            // Basis sofort setzen
            basePositionMs.longValue = clamped
            smoothedMs.longValue = clamped

            // Timebase im nächsten Frame setzen (passend zur withFrameNanos Zeitbasis)
            needsTimebaseReset.value = true
        }
    }

    return remember(smoothedMs, jumpTo) {
        SmoothedProgressState(
            currentPosition = smoothedMs,
            jumpTo = jumpTo
        )
    }
}
