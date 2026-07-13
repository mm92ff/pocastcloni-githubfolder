package com.example.pocastcloni.ui.player

import com.example.pocastcloni.domain.usecase.player.MarkEpisodePlayedUseCase
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackAnalyticsHandlerTest {
    @Test
    fun `ten minutes of frequent ticks reserve at most two regular saves per minute`() = runTest {
        var nowMs = 0L
        val progressWriter = mockk<PlaybackProgressWriter>(relaxed = true)
        val handler = createHandler(progressWriter = progressWriter, clock = MonotonicClock { nowMs })

        repeat(1_200) {
            nowMs += 500L
            handler.onTick(
                episodeId = EPISODE_ID,
                currentPositionMs = nowMs,
                durationMs = 1_000_000L,
                deltaMs = 500L,
                isPlaying = true,
                markPlayedThresholdSeconds = 10_000
            )
        }

        verify(exactly = 20) { progressWriter.request(EPISODE_ID, any()) }
    }

    @Test
    fun `ticks before thirty seconds do not reserve a regular save`() = runTest {
        var nowMs = 0L
        val progressWriter = mockk<PlaybackProgressWriter>(relaxed = true)
        val handler = createHandler(progressWriter = progressWriter, clock = MonotonicClock { nowMs })

        repeat(59) {
            nowMs += 500L
            handler.onTick(
                episodeId = EPISODE_ID,
                currentPositionMs = nowMs,
                durationMs = 60_000L,
                deltaMs = 500L,
                isPlaying = true,
                markPlayedThresholdSeconds = 10_000
            )
        }

        verify(exactly = 0) { progressWriter.request(any(), any()) }
    }

    @Test
    fun `event flush reserves the latest position immediately`() = runTest {
        val progressWriter = mockk<PlaybackProgressWriter>(relaxed = true)
        var nowMs = 30_000L
        val handler = createHandler(progressWriter = progressWriter, clock = MonotonicClock { nowMs })

        handler.onTick(
            episodeId = EPISODE_ID,
            currentPositionMs = 30_000L,
            durationMs = 100_000L,
            deltaMs = 500L,
            isPlaying = true,
            markPlayedThresholdSeconds = 10_000
        )
        nowMs += 500L

        handler.saveProgressBestEffort(EPISODE_ID, 30_500L)

        verify(exactly = 1) { progressWriter.request(EPISODE_ID, 30_000L) }
        verify(exactly = 1) { progressWriter.request(EPISODE_ID, 30_500L) }
    }

    @Test
    fun `media transition resets analytics without duplicating old progress`() = runTest {
        val progressWriter = mockk<PlaybackProgressWriter>(relaxed = true)
        val handler = createHandler(progressWriter = progressWriter, clock = MonotonicClock { 1_000L })

        handler.onTick(
            episodeId = EPISODE_ID,
            currentPositionMs = 1_000L,
            durationMs = 100_000L,
            deltaMs = 500L,
            isPlaying = true,
            markPlayedThresholdSeconds = 10_000
        )
        handler.onMediaItemTransition()

        verify(exactly = 0) { progressWriter.request(any(), any()) }
    }

    @Test
    fun `repeated completion ticks mark an episode only once`() = runTest {
        var nowMs = 30_000L
        val markPlayed = mockk<MarkEpisodePlayedUseCase>(relaxed = true)
        val handler =
            createHandler(
                markPlayed = markPlayed,
                clock = MonotonicClock { nowMs }
            )

        repeat(5) {
            handler.onTick(
                episodeId = EPISODE_ID,
                currentPositionMs = 95_000L,
                durationMs = 100_000L,
                deltaMs = 500L,
                isPlaying = true,
                markPlayedThresholdSeconds = 0
            )
            nowMs += 500L
        }
        runCurrent()

        coVerify(exactly = 1) { markPlayed(EPISODE_ID) }
    }

    private fun kotlinx.coroutines.test.TestScope.createHandler(
        progressWriter: PlaybackProgressWriter = mockk(relaxed = true),
        markPlayed: MarkEpisodePlayedUseCase = mockk(relaxed = true),
        clock: MonotonicClock
    ): PlaybackAnalyticsHandler =
        PlaybackAnalyticsHandler(
            progressWriter = progressWriter,
            markEpisodePlayedUseCase = markPlayed,
            listeningTimeWriter = mockk(relaxed = true),
            monotonicClock = clock,
            applicationScope = backgroundScope
        )

    private companion object {
        private const val EPISODE_ID = 101L
    }
}
