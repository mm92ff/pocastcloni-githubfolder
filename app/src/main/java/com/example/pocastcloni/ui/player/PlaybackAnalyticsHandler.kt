package com.example.pocastcloni.ui.player

import com.example.pocastcloni.di.ApplicationScope
import com.example.pocastcloni.domain.usecase.player.MarkEpisodePlayedUseCase
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackAnalyticsHandler
@Inject
constructor(
    private val progressWriter: PlaybackProgressWriter,
    private val markEpisodePlayedUseCase: MarkEpisodePlayedUseCase,
    private val listeningTimeWriter: PlaybackListeningTimeWriter,
    private val monotonicClock: MonotonicClock,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private companion object {
        private const val LISTENING_FLUSH_INTERVAL_MS = 60_000L
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L
        // Throttle debug logging to avoid flooding Logcat
        private const val DEBUG_LOG_INTERVAL_MS = 5_000L
    }

    private var listeningAccumMs = 0L
    private var lastListeningFlushMs = 0L
    private var lastDbSaveMs = 0L
    private var lastObservedGuid: String? = null
    private var lastObservedPositionMs: Long = 0L
    private var hasBeenMarkedAsPlayed: Boolean = false

    private var lastDebugLogMs = 0L

    fun onMediaItemTransition() {
        lastObservedGuid?.let { progressWriter.request(it, lastObservedPositionMs) }
        lastObservedGuid = null
        lastObservedPositionMs = 0L
        hasBeenMarkedAsPlayed = false
        Timber.d("Analytics: Media Item Transition -> Reset markedAsPlayed")
    }

    fun onTick(
        guid: String?,
        currentPositionMs: Long,
        durationMs: Long,
        deltaMs: Long,
        isPlaying: Boolean,
        markPlayedThresholdSeconds: Int
    ) {
        if (guid.isNullOrBlank() || !isPlaying) return
        val nowMs = monotonicClock.elapsedRealtimeMs()
        lastObservedGuid = guid
        lastObservedPositionMs = currentPositionMs

        // --- DEBUG LOGGING (every 5 seconds) ---
        if (nowMs - lastDebugLogMs > DEBUG_LOG_INTERVAL_MS) {
            Timber.v(
                "Analytics Debug: Pos=$currentPositionMs ms, Dur=$durationMs ms, " +
                    "ThresholdSeconds=$markPlayedThresholdSeconds, Marked=$hasBeenMarkedAsPlayed"
            )
            lastDebugLogMs = nowMs
        }
        // ---------------------------------------

        listeningAccumMs += deltaMs
        if ((nowMs - lastListeningFlushMs) >= LISTENING_FLUSH_INTERVAL_MS) {
            flushListeningTime()
            lastListeningFlushMs = nowMs
        }

        if ((nowMs - lastDbSaveMs) >= AUTO_SAVE_INTERVAL_MS) {
            saveProgressInternal(guid, currentPositionMs, nowMs)
        }

        if (hasBeenMarkedAsPlayed) return

        var shouldMark = false
        var reason = ""

        if (markPlayedThresholdSeconds > 0) {
            // Strategy A: fixed time threshold (e.g. 30 s)
            val thresholdMs = markPlayedThresholdSeconds * 1000L

            if (currentPositionMs >= thresholdMs) {
                shouldMark = true
                reason = "Fixed Time Threshold reached ($currentPositionMs >= $thresholdMs)"
            }
        } else {
            // Strategy B: 95 % completion rule (default)
            if (durationMs > 0) {
                val percentageThresholdMs = (durationMs * Constants.COMPLETION_PERCENTAGE).toLong()

                if (currentPositionMs >= percentageThresholdMs) {
                    shouldMark = true
                    reason = "95% Completion reached ($currentPositionMs >= $percentageThresholdMs of $durationMs)"
                }
            }
        }

        if (shouldMark) {
            Timber.i("Analytics: Marking as played! Reason: $reason")
            hasBeenMarkedAsPlayed = true
            applicationScope.launch {
                runCatching {
                    markEpisodePlayedUseCase(guid)
                }.onFailure {
                    Timber.e(it, "Failed to mark episode as played")
                    hasBeenMarkedAsPlayed = false
                }
            }
        }
    }

    fun flushListeningTime() {
        val toFlush = listeningAccumMs
        listeningAccumMs = 0L
        listeningTimeWriter.recordAndFlush(toFlush)
    }

    fun saveProgressBestEffort(
        guid: String?,
        positionMs: Long
    ) {
        if (guid.isNullOrBlank()) return
        val now = monotonicClock.elapsedRealtimeMs()
        lastObservedGuid = guid
        lastObservedPositionMs = positionMs
        saveProgressInternal(guid, positionMs, now)
    }

    private fun saveProgressInternal(
        guid: String,
        positionMs: Long,
        nowMs: Long
    ) {
        // Reserve the interval before the asynchronous write to prevent slow I/O from spawning more work.
        lastDbSaveMs = nowMs
        progressWriter.request(guid, positionMs)
    }
}
