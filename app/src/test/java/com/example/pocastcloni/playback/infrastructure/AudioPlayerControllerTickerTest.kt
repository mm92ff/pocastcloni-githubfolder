package com.example.pocastcloni.playback.infrastructure

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.Episode
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.usecase.player.PlayEpisodeResult
import com.example.pocastcloni.domain.usecase.player.PreparePlaybackUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerControllerTickerTest {
    @Test
    fun `controller owns one ticker settles transitions and restores observation after reconnect`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val foreground = FakeForegroundMonitor(initiallyForeground = true)
        val ticker = RecordingTickSource()
        val analytics = mockk<PlaybackAnalyticsHandler>(relaxed = true)
        val mediaController = mockk<MediaController>(relaxed = true)
        val mediaConnection = mockk<MediaControllerConnection>(relaxed = true)
        val podcastQuery = mockk<PodcastQueryPort>(relaxed = true)
        val preferences = mockk<UserPreferencesRepository>()
        val dispatcherProvider = mockk<DispatcherProvider>()
        val mapper = mockk<MediaStateMapper>(relaxed = true)
        val preparePlayback = mockk<PreparePlaybackUseCase>()
        val listeners = mutableListOf<Player.Listener>()
        val mediaItem = MediaItem.Builder().setMediaId(EPISODE_ID.toString()).build()
        var isPlaying = true
        var positionMs = 10_000L
        var nowMs = 0L

        every { dispatcherProvider.main } returns dispatcher
        every { dispatcherProvider.io } returns dispatcher
        every { dispatcherProvider.default } returns dispatcher
        every { preferences.userSettingsFlow } returns flowOf(UserSettings())
        coEvery { mediaConnection.connect() } returns mediaController
        coEvery { podcastQuery.getEpisode(any()) } returns null
        every { podcastQuery.isFavorite(any()) } returns flowOf(false)
        every { mediaController.currentMediaItem } returns mediaItem
        every { mediaController.isPlaying } answers { isPlaying }
        every { mediaController.playbackState } returns Player.STATE_READY
        every { mediaController.currentPosition } answers { positionMs }
        every { mediaController.bufferedPosition } returns 20_000L
        every { mediaController.duration } returns 40_000L
        every { mediaController.addListener(any()) } answers {
            listeners.add(firstArg<Player.Listener>())
        }
        every { mapper.mapToUiState(any(), any(), any(), any()) } answers { arg(3) }
        val episode =
            Episode(
                guid = GUID,
                podcastRssUrl = "https://example.com/feed.xml",
                title = "Episode",
                description = "",
                pubDate = Date(),
                link = "",
                enclosureUrl = "https://example.com/audio.mp3",
                episodeId = EPISODE_ID
            )
        coEvery { preparePlayback(EPISODE_ID) } returns
            PlayEpisodeResult(episode, 0L, null, episode.enclosureUrl)

        val controller =
            AudioPlayerController(
                context = mockk<Context>(relaxed = true),
                dispatcherProvider = dispatcherProvider,
                userPreferencesRepository = preferences,
                podcastQuery = podcastQuery,
                mediaConnection = mediaConnection,
                analyticsHandler = analytics,
                mapper = mapper,
                ticker = ticker,
                foregroundMonitor = foreground,
                monotonicClock = MonotonicClock { nowMs },
                mediaDispatcherFactory = MediaDispatcherFactory { dispatcher },
                preparePlaybackUseCase = preparePlayback
            )
        val stateCollector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                controller.playerState.collect()
            }
        runCurrent()

        assertEquals(listOf(500L), ticker.startedIntervals)
        val firstListener = listeners.last()
        firstListener.onIsPlayingChanged(true)
        runCurrent()
        assertEquals(listOf(500L), ticker.startedIntervals)

        nowMs = 500L
        foreground.isForeground.value = false
        runCurrent()
        assertEquals(listOf(500L, 5_000L), ticker.startedIntervals)
        assertEquals(listOf(500L), ticker.cancelledIntervals)

        nowMs = 1_500L
        isPlaying = false
        firstListener.onIsPlayingChanged(false)
        runCurrent()
        assertEquals(listOf(500L, 5_000L), ticker.cancelledIntervals)
        verify {
            analytics.onTick(
                episodeId = EPISODE_ID,
                currentPositionMs = 10_000L,
                durationMs = 40_000L,
                deltaMs = 1_000L,
                isPlaying = true,
                markPlayedThresholdSeconds = 0
            )
        }
        verify { analytics.saveProgressBestEffort(EPISODE_ID, 10_000L) }
        verify { analytics.flushListeningTime() }

        positionMs = 12_345L
        foreground.isForeground.value = true
        runCurrent()
        assertEquals(12_345L, controller.playbackState.value.currentPositionMs)

        isPlaying = true
        firstListener.onIsPlayingChanged(true)
        runCurrent()
        assertEquals(500L, ticker.startedIntervals.last())
        nowMs = 1_800L
        controller.releaseResources()
        runCurrent()
        verify {
            analytics.onTick(
                episodeId = EPISODE_ID,
                currentPositionMs = 12_345L,
                durationMs = 40_000L,
                deltaMs = 300L,
                isPlaying = true,
                markPlayedThresholdSeconds = 0
            )
        }
        verify(exactly = 1) { mediaConnection.release() }

        controller.play(EPISODE_ID)
        runCurrent()
        assertTrue(ticker.startedIntervals.count { it == 500L } >= 3)
        foreground.isForeground.value = false
        runCurrent()
        assertEquals(5_000L, ticker.startedIntervals.last())

        controller.releaseResources()
        stateCollector.cancel()
    }

    private class FakeForegroundMonitor(initiallyForeground: Boolean) : AppForegroundMonitor {
        override val isForeground = MutableStateFlow(initiallyForeground)
    }

    private class RecordingTickSource : PlaybackTickSource {
        val startedIntervals = mutableListOf<Long>()
        val cancelledIntervals = mutableListOf<Long>()

        override fun tick(intervalMs: Long): Flow<Unit> =
            flow {
                startedIntervals += intervalMs
                try {
                    awaitCancellation()
                } finally {
                    cancelledIntervals += intervalMs
                }
            }
    }

    private companion object {
        private const val EPISODE_ID = 101L
        private const val GUID = "episode-guid"
    }
}
