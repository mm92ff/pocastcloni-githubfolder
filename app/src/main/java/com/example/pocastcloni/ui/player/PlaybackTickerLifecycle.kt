package com.example.pocastcloni.ui.player

internal data class PlaybackTickerTransition(
    val desiredIntervalMs: Long?,
    val elapsedPlayingMs: Long,
    val restartTicker: Boolean,
    val syncUi: Boolean,
    val flushPlayback: Boolean
)

internal class PlaybackTickerLifecycle(
    private val clock: MonotonicClock
) {
    private var activeIntervalMs: Long? = null
    private var lastSampleAtMs: Long? = null
    private var wasForeground = false

    fun reconcile(
        isPlayingReady: Boolean,
        isForeground: Boolean
    ): PlaybackTickerTransition {
        val nowMs = clock.elapsedRealtimeMs()
        val previousInterval = activeIntervalMs
        val desiredInterval = playbackTickIntervalMs(isPlayingReady, isForeground)
        val elapsedMs = elapsedSinceLastSample(nowMs, previousInterval != null)
        val returningToForeground = !wasForeground && isForeground
        val restartTicker = desiredInterval != previousInterval
        val stoppedPlayback = previousInterval != null && desiredInterval == null

        activeIntervalMs = desiredInterval
        lastSampleAtMs = if (desiredInterval != null) nowMs else null
        wasForeground = isForeground

        return PlaybackTickerTransition(
            desiredIntervalMs = desiredInterval,
            elapsedPlayingMs = elapsedMs,
            restartTicker = restartTicker,
            syncUi = isForeground && (returningToForeground || restartTicker),
            flushPlayback = stoppedPlayback
        )
    }

    fun onTick(isForeground: Boolean): PlaybackTickerTransition {
        val nowMs = clock.elapsedRealtimeMs()
        val interval = activeIntervalMs
        val elapsedMs = elapsedSinceLastSample(nowMs, interval != null)
        if (interval != null) lastSampleAtMs = nowMs
        wasForeground = isForeground
        return PlaybackTickerTransition(
            desiredIntervalMs = interval,
            elapsedPlayingMs = elapsedMs,
            restartTicker = false,
            syncUi = isForeground,
            flushPlayback = false
        )
    }

    fun release(): PlaybackTickerTransition {
        val nowMs = clock.elapsedRealtimeMs()
        val wasActive = activeIntervalMs != null
        val elapsedMs = elapsedSinceLastSample(nowMs, wasActive)
        activeIntervalMs = null
        lastSampleAtMs = null
        return PlaybackTickerTransition(
            desiredIntervalMs = null,
            elapsedPlayingMs = elapsedMs,
            restartTicker = wasActive,
            syncUi = false,
            flushPlayback = wasActive
        )
    }

    private fun elapsedSinceLastSample(
        nowMs: Long,
        countAsPlaying: Boolean
    ): Long {
        val previousMs = lastSampleAtMs ?: return 0L
        return if (countAsPlaying) (nowMs - previousMs).coerceAtLeast(0L) else 0L
    }
}
