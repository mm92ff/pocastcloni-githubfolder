package com.example.pocastcloni.ui.player

import com.example.pocastcloni.playback.api.PlaybackState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive

data class SmoothedProgressState(
    val currentPosition: State<Long>
)

/**
 * Smoothes the position on a per-frame basis (60/120 fps) even when the backend tick is infrequent (e.g. 500 ms).
 *
 * Fixes:
 * - No "fast-forward" after Pause/Resume: the time base is reset at the start of each loop.
 * - Pending-state transitions reset the smoothing base even when the numeric position is unchanged.
 */
@Composable
fun rememberSmoothedProgressState(
    playbackStateFlow: StateFlow<PlaybackState>,
    isPlaying: Boolean
): SmoothedProgressState {
    val basePositionMs = remember { mutableLongStateOf(0L) }
    val baseTimeNanos = remember { mutableLongStateOf(0L) }
    val latestDurationMs = remember { mutableLongStateOf(0L) }

    // UI state (read during the Draw phase)
    val smoothedMs = remember { mutableLongStateOf(0L) }

    // 1) Sync with real player events (tick, seek-complete, track-change)
    LaunchedEffect(playbackStateFlow) {
        playbackStateFlow
            .map { state ->
                ProgressSnapshot(
                    positionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    isSeekPending = state.isSeekPending
                )
            }
            .distinctUntilChanged()
            .collect { snapshot ->
                val clampedDur = snapshot.durationMs.coerceAtLeast(0L)
                val clampedPos = snapshot.positionMs.coerceAtLeast(0L).coerceIn(0L, clampedDur)

                latestDurationMs.longValue = clampedDur
                basePositionMs.longValue = clampedPos

                // Reset time base so prediction starts from "now" (prevents drift / jumps)
                baseTimeNanos.longValue = withFrameNanos { it }

                // Reset to the real position
                smoothedMs.longValue = clampedPos
            }
    }

    // 2) Interpolation loop (runs only when playing)
    LaunchedEffect(isPlaying) {
        // On any pause/resume, re-sync cleanly once
        val now = withFrameNanos { it }
        val dur = playbackStateFlow.value.durationMs.coerceAtLeast(0L)
        val pos = playbackStateFlow.value.currentPositionMs.coerceAtLeast(0L).coerceIn(0L, dur)

        latestDurationMs.longValue = dur
        basePositionMs.longValue = pos
        baseTimeNanos.longValue = now
        smoothedMs.longValue = pos

        if (!isPlaying) return@LaunchedEffect

        while (isActive) {
            val frameTime = withFrameNanos { it }

            val currentDur = latestDurationMs.longValue
            if (currentDur <= 0L) {
                smoothedMs.longValue = 0L
                continue
            }

            val elapsedMs = ((frameTime - baseTimeNanos.longValue).coerceAtLeast(0L)) / 1_000_000L
            val predicted = basePositionMs.longValue + elapsedMs
            smoothedMs.longValue = predicted.coerceIn(0L, currentDur)
        }
    }

    return remember(smoothedMs) {
        SmoothedProgressState(currentPosition = smoothedMs)
    }
}

private data class ProgressSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val isSeekPending: Boolean
)
