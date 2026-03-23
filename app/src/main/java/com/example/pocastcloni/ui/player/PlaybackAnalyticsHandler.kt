package com.example.pocastcloni.ui.player

import android.os.SystemClock
import com.example.pocastcloni.domain.usecase.player.MarkEpisodePlayedUseCase
import com.example.pocastcloni.domain.usecase.player.SavePlaybackProgressUseCase
import com.example.pocastcloni.domain.usecase.stats.AddListeningTimeUseCase
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.abs

class PlaybackAnalyticsHandler
@Inject
constructor(
    private val savePlaybackProgressUseCase: SavePlaybackProgressUseCase,
    private val markEpisodePlayedUseCase: MarkEpisodePlayedUseCase,
    private val addListeningTimeUseCase: AddListeningTimeUseCase
) {
    private companion object {
        private const val LISTENING_FLUSH_INTERVAL_MS = 60_000L
        private const val AUTO_SAVE_INTERVAL_MS = 15_000L
        private const val MIN_MANUAL_SAVE_INTERVAL_MS = 3_000L
        private const val MIN_MANUAL_SAVE_DELTA_MS = 1_000L

        // Debugging: Nur alle X ms loggen, um Logcat nicht zu fluten
        private const val DEBUG_LOG_INTERVAL_MS = 5_000L
    }

    private var listeningAccumMs = 0L
    private var lastListeningFlushMs = 0L
    private var lastDbSaveMs = 0L
    private var lastSavedGuid: String? = null
    private var lastPersistedPositionMs: Long = 0L
    private var lastPersistedAtMs: Long = 0L
    private var hasBeenMarkedAsPlayed: Boolean = false

    private var lastDebugLogMs = 0L // Für Log-Drosselung

    fun onMediaItemTransition() {
        hasBeenMarkedAsPlayed = false
        Timber.d("Analytics: Media Item Transition -> Reset markedAsPlayed")
    }

    fun onTick(
        scope: CoroutineScope,
        guid: String?,
        currentPositionMs: Long,
        durationMs: Long,
        deltaMs: Long,
        isPlaying: Boolean,
        markPlayedThresholdSeconds: Int
    ) {
        if (guid.isNullOrBlank() || !isPlaying) return
        val nowMs = SystemClock.elapsedRealtime()

        // --- DEBUG LOGGING (Alle 5 Sekunden) ---
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
            flushListeningTime(scope)
            lastListeningFlushMs = nowMs
        }

        if ((nowMs - lastDbSaveMs) >= AUTO_SAVE_INTERVAL_MS) {
            // Hinweis: Das hier ruft das Repository auf, welches den "Smart Completion" Bug hat!
            saveProgressInternal(scope, guid, currentPositionMs, nowMs)
        }

        if (hasBeenMarkedAsPlayed) return

        // --- LOGIK PRÜFUNG ---

        var shouldMark = false
        var reason = ""

        if (markPlayedThresholdSeconds > 0) {
            // Logik A: Feste Zeit (z.B. 30s)
            // Hier prüfen wir auf Sekunden-Ebene, aber rechnen alles in MS um
            val thresholdMs = markPlayedThresholdSeconds * 1000L

            // Logik: Markieren, wenn wir thresholdMs erreicht haben
            // (Achtung: Deine ursprüngliche Logik war "nach X Sekunden ab Start".
            // Falls du "X Sekunden vor Ende" meinst, muss hier: durationMs - thresholdMs hin)
            if (currentPositionMs >= thresholdMs) {
                shouldMark = true
                reason = "Fixed Time Threshold reached ($currentPositionMs >= $thresholdMs)"
            }
        } else {
            // Logik B: 95% Regel (Standard)
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
            scope.launch {
                runCatching {
                    markEpisodePlayedUseCase(guid)
                }.onFailure {
                    Timber.e(it, "Failed to mark episode as played")
                    hasBeenMarkedAsPlayed = false
                }
            }
        }
    }

    fun flushListeningTime(scope: CoroutineScope) {
        if (listeningAccumMs <= 0) return
        val toFlush = listeningAccumMs
        listeningAccumMs = 0L
        scope.launch {
            runCatching { addListeningTimeUseCase(toFlush) }
                .onFailure { Timber.e(it, "Failed to flush listening time") }
        }
    }

    fun saveProgressBestEffort(
        scope: CoroutineScope,
        guid: String?,
        positionMs: Long
    ) {
        if (guid.isNullOrBlank()) return
        val now = SystemClock.elapsedRealtime()
        val isRedundant =
            (guid == lastSavedGuid) &&
                (abs(positionMs - lastPersistedPositionMs) < MIN_MANUAL_SAVE_DELTA_MS) &&
                ((now - lastPersistedAtMs) < MIN_MANUAL_SAVE_INTERVAL_MS)
        if (!isRedundant) {
            saveProgressInternal(scope, guid, positionMs, now)
        }
    }

    private fun saveProgressInternal(
        scope: CoroutineScope,
        guid: String,
        positionMs: Long,
        nowMs: Long
    ) {
        scope.launch {
            runCatching { savePlaybackProgressUseCase(guid, positionMs) }
                .onFailure { Timber.e(it, "Failed to auto-save progress") }
                .onSuccess {
                    lastSavedGuid = guid
                    lastPersistedPositionMs = positionMs
                    lastPersistedAtMs = nowMs
                    lastDbSaveMs = nowMs
                }
        }
    }
}
