package com.example.pocastcloni.playback.infrastructure

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
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.playback.api.PlayerUiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerControllerSeekTest {
    @Test
    fun `stale ticker sample cannot roll direct timeline seek back`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()

        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()
        fixture.emitTick()
        runCurrent()

        assertEquals(SEEK_TARGET_MS, fixture.subject.playbackState.value.currentPositionMs)
        assertTrue(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 1) { fixture.controller.seekTo(SEEK_TARGET_MS) }
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }

        fixture.confirmSeek(
            positionMs = CONFIRMED_POSITION_MS,
            reason = Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        )
        runCurrent()

        assertEquals(CONFIRMED_POSITION_MS, fixture.subject.playbackState.value.currentPositionMs)
        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(EPISODE_ID, CONFIRMED_POSITION_MS)
        }
        fixture.release()
    }

    @Test
    fun `drag dispatches only its final target and waits before persistence`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()

        fixture.subject.onEvent(PlayerScreenEvent.SeekStarted)
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(500_000L))
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        verify(exactly = 0) { fixture.controller.seekTo(any<Long>()) }

        fixture.subject.onEvent(PlayerScreenEvent.SeekFinished)
        runCurrent()

        verify(exactly = 1) { fixture.controller.seekTo(SEEK_TARGET_MS) }
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }

        fixture.confirmSeek(SEEK_TARGET_MS)
        runCurrent()
        verify(exactly = 1) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        fixture.release()
    }

    @Test
    fun `cancelled drag without a target issues no seek command`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()

        fixture.subject.onEvent(PlayerScreenEvent.SeekStarted)
        fixture.subject.onEvent(PlayerScreenEvent.SeekFinished)
        runCurrent()

        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        assertEquals(INITIAL_POSITION_MS, fixture.subject.playbackState.value.currentPositionMs)
        verify(exactly = 0) { fixture.controller.seekTo(any<Long>()) }
        fixture.release()
    }

    @Test
    fun `unavailable seek command rejects optimistic position and persistence`() = runTest {
        val fixture = Fixture(this, initiallySeekable = false)
        fixture.connect()

        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        assertEquals(INITIAL_POSITION_MS, fixture.subject.playbackState.value.currentPositionMs)
        assertFalse(fixture.subject.playbackState.value.isSeekable)
        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 0) { fixture.controller.seekTo(any<Long>()) }
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(any(), any()) }
        fixture.release()
    }

    @Test
    fun `seek capability loss restores actual player position`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.setSeekable(false)
        runCurrent()

        assertEquals(INITIAL_POSITION_MS, fixture.subject.playbackState.value.currentPositionMs)
        assertFalse(fixture.subject.playbackState.value.isSeekable)
        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        fixture.release()
    }

    @Test
    fun `seek timeout restores actual position without saving requested target`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        advanceTimeBy(SEEK_TIMEOUT_MS + 1L)
        runCurrent()

        assertEquals(INITIAL_POSITION_MS, fixture.subject.playbackState.value.currentPositionMs)
        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        fixture.release()
    }

    @Test
    fun `late discontinuity from superseded seek cannot settle newer target`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(300_000L))
        runCurrent()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.confirmSeek(300_000L)
        runCurrent()

        assertEquals(SEEK_TARGET_MS, fixture.subject.playbackState.value.currentPositionMs)
        assertTrue(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, 300_000L) }

        fixture.confirmSeek(SEEK_TARGET_MS)
        runCurrent()
        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 1) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        fixture.release()
    }

    @Test
    fun `nearby stale discontinuity cannot settle newer target`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        val olderTargetMs = 600_000L
        val newerTargetMs = 605_000L

        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(olderTargetMs))
        runCurrent()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(newerTargetMs))
        runCurrent()

        fixture.confirmSeek(olderTargetMs)
        runCurrent()

        assertEquals(newerTargetMs, fixture.subject.playbackState.value.currentPositionMs)
        assertTrue(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, olderTargetMs) }

        fixture.confirmSeek(newerTargetMs)
        runCurrent()

        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 1) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, newerTargetMs) }
        fixture.release()
    }

    @Test
    fun `buffering seek settles only after ready with authoritative position`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.setPlaybackState(Player.STATE_BUFFERING)
        fixture.confirmSeek(CONFIRMED_POSITION_MS)
        runCurrent()

        assertTrue(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 0) {
            fixture.analytics.saveProgressBestEffort(EPISODE_ID, CONFIRMED_POSITION_MS)
        }

        val readyPositionMs = CONFIRMED_POSITION_MS + 250L
        fixture.currentPositionMs = readyPositionMs
        fixture.setPlaybackState(Player.STATE_READY)
        runCurrent()

        assertEquals(readyPositionMs, fixture.subject.playbackState.value.currentPositionMs)
        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(EPISODE_ID, readyPositionMs)
        }
        fixture.release()
    }

    @Test
    fun `pause during pending seek flushes only authoritative position`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.subject.pause()
        runCurrent()

        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        verify(atMost = 1) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, INITIAL_POSITION_MS) }
        fixture.release()
    }

    @Test
    fun `release during pending seek cancels timeout and flushes only authoritative position`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.release()
        advanceTimeBy(SEEK_TIMEOUT_MS + 1L)
        runCurrent()

        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        verify(atMost = 1) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, INITIAL_POSITION_MS) }
    }

    @Test
    fun `disconnect during pending seek cancels timeout and flushes only authoritative position`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.disconnect()
        runCurrent()
        advanceTimeBy(SEEK_TIMEOUT_MS + 1L)
        runCurrent()

        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        verify(atMost = 1) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, INITIAL_POSITION_MS) }
        fixture.release()
    }

    @Test
    fun `delayed transition from previous media item cannot cancel current seek`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.setCurrentMediaItem(NEW_EPISODE_ID)
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.emitMediaTransition(EPISODE_ID)
        runCurrent()

        assertTrue(fixture.subject.playbackState.value.isSeekPending)
        assertEquals(SEEK_TARGET_MS, fixture.subject.playbackState.value.currentPositionMs)

        fixture.confirmSeek(SEEK_TARGET_MS)
        runCurrent()
        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(NEW_EPISODE_ID, SEEK_TARGET_MS)
        }
        fixture.release()
    }

    @Test
    fun `current media transition cancels seek with new item position`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        val newItemPositionMs = 5_000L
        fixture.currentPositionMs = newItemPositionMs
        fixture.setCurrentMediaItem(NEW_EPISODE_ID)
        fixture.emitMediaTransition(NEW_EPISODE_ID)
        runCurrent()

        assertFalse(fixture.subject.playbackState.value.isSeekPending)
        assertEquals(newItemPositionMs, fixture.subject.playbackState.value.currentPositionMs)
        verify(exactly = 0) { fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS) }
        fixture.release()
    }

    @Test
    fun `delayed command callback from previous controller cannot cancel current seek`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()
        fixture.disconnectAndReconnect()
        runCurrent()
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
        runCurrent()

        fixture.emitOldControllerCommandsUnavailable()
        runCurrent()

        assertTrue(fixture.subject.playbackState.value.isSeekPending)
        assertTrue(fixture.subject.playbackState.value.isSeekable)
        verify(exactly = 1) { fixture.replacementController.seekTo(SEEK_TARGET_MS) }

        fixture.confirmSeek(SEEK_TARGET_MS)
        runCurrent()
        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(EPISODE_ID, SEEK_TARGET_MS)
        }
        fixture.release()
    }

    @Test
    fun `seek target is clamped to known duration`() = runTest {
        val fixture = Fixture(this)
        fixture.connect()

        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(DURATION_MS + 60_000L))
        runCurrent()

        assertEquals(DURATION_MS, fixture.subject.playbackState.value.currentPositionMs)
        verify(exactly = 1) { fixture.controller.seekTo(DURATION_MS) }
        fixture.release()
    }

    private class Fixture(
        scope: TestScope,
        initiallySeekable: Boolean = true
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private val mediaConnection = mockk<MediaControllerConnection>(relaxed = true)
        private val podcastQuery = mockk<PodcastQueryPort>(relaxed = true)
        private val preparePlayback = mockk<PreparePlaybackUseCase>()
        private val mapper = mockk<MediaStateMapper>(relaxed = true)
        private val activeListeners = mutableMapOf<MediaController, Player.Listener>()
        private val allListeners = mutableMapOf<MediaController, Player.Listener>()
        private var currentMediaItem: MediaItem? = null
        private var connectionController: MediaController
        private var isPlaying = false
        private var isSeekable = initiallySeekable
        private var playbackState = Player.STATE_READY
        private lateinit var disconnectCallback: (MediaController) -> Unit
        var currentPositionMs = INITIAL_POSITION_MS
        val controller = mockk<MediaController>(relaxed = true)
        val replacementController = mockk<MediaController>(relaxed = true)
        val analytics = mockk<PlaybackAnalyticsHandler>(relaxed = true)
        val subject: AudioPlayerController

        init {
            connectionController = controller
            val dispatcherProvider = mockk<DispatcherProvider>()
            val preferences = mockk<UserPreferencesRepository>()
            val foreground = object : AppForegroundMonitor {
                override val isForeground = MutableStateFlow(true)
            }
            val episode = episode()

            every { dispatcherProvider.main } returns dispatcher
            every { dispatcherProvider.io } returns dispatcher
            every { dispatcherProvider.default } returns dispatcher
            every { preferences.userSettingsFlow } returns flowOf(UserSettings())
            every { mediaConnection.setOnDisconnected(any()) } answers {
                disconnectCallback = firstArg()
            }
            coEvery { mediaConnection.connect() } answers { connectionController }
            every { podcastQuery.isFavorite(any()) } returns flowOf(false)
            coEvery { podcastQuery.getEpisode(any()) } returns null
            coEvery { podcastQuery.getPodcast(any()) } returns null
            coEvery { preparePlayback(EPISODE_ID) } returns
                PlayEpisodeResult(episode, 0L, null, episode.enclosureUrl)
            every { mapper.mapToMediaItem(any(), any(), any()) } answers {
                MediaItem.Builder().setMediaId(EPISODE_ID.toString()).build()
            }
            every { mapper.mapToUiState(any(), any(), any(), any()) } answers {
                arg<PlayerUiState>(3)
            }
            stubController(controller)
            stubController(replacementController)

            subject =
                AudioPlayerController(
                    dispatcherProvider = dispatcherProvider,
                    userPreferencesRepository = preferences,
                    podcastQuery = podcastQuery,
                    mediaConnection = mediaConnection,
                    analyticsHandler = analytics,
                    mapper = mapper,
                    ticker = object : PlaybackTickSource {
                        override fun tick(intervalMs: Long) = ticks
                    },
                    foregroundMonitor = foreground,
                    monotonicClock = MonotonicClock { scope.testScheduler.currentTime },
                    mediaDispatcherFactory = MediaDispatcherFactory { dispatcher },
                    preparePlaybackUseCase = preparePlayback
                )
        }

        suspend fun connect() {
            subject.play(EPISODE_ID)
        }

        fun emitTick() {
            check(ticks.tryEmit(Unit))
        }

        fun confirmSeek(
            positionMs: Long,
            reason: Int = Player.DISCONTINUITY_REASON_SEEK
        ) {
            currentPositionMs = positionMs
            val oldPosition = positionInfo(INITIAL_POSITION_MS)
            val newPosition = positionInfo(positionMs)
            activeListener().onPositionDiscontinuity(
                oldPosition,
                newPosition,
                reason
            )
        }

        fun setPlaybackState(value: Int) {
            playbackState = value
            activeListener().onPlaybackStateChanged(value)
        }

        fun setSeekable(value: Boolean) {
            isSeekable = value
            val commands = mockk<Player.Commands>()
            every {
                commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            } returns value
            activeListener().onAvailableCommandsChanged(commands)
        }

        fun setCurrentMediaItem(episodeId: Long) {
            currentMediaItem = MediaItem.Builder().setMediaId(episodeId.toString()).build()
        }

        fun emitMediaTransition(episodeId: Long) {
            activeListener().onMediaItemTransition(
                MediaItem.Builder().setMediaId(episodeId.toString()).build(),
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )
        }

        fun disconnect() {
            disconnectCallback(controller)
        }

        fun disconnectAndReconnect() {
            connectionController = replacementController
            disconnectCallback(controller)
        }

        fun emitOldControllerCommandsUnavailable() {
            val commands = mockk<Player.Commands>()
            every {
                commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            } returns false
            requireNotNull(allListeners[controller]).onAvailableCommandsChanged(commands)
        }

        fun release() {
            subject.releaseResources()
        }

        private fun activeListener(): Player.Listener =
            requireNotNull(activeListeners[connectionController])

        private fun stubController(mediaController: MediaController) {
            every { mediaController.currentMediaItem } answers { currentMediaItem }
            every { mediaController.currentPosition } answers { currentPositionMs }
            every { mediaController.bufferedPosition } answers { currentPositionMs }
            every { mediaController.duration } returns DURATION_MS
            every { mediaController.isPlaying } answers { isPlaying }
            every { mediaController.playbackState } answers { playbackState }
            every { mediaController.isCurrentMediaItemSeekable } answers { isSeekable }
            every {
                mediaController.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            } answers { isSeekable }
            every { mediaController.addListener(any()) } answers {
                val listener = firstArg<Player.Listener>()
                activeListeners[mediaController] = listener
                allListeners[mediaController] = listener
            }
            every { mediaController.removeListener(any()) } answers {
                activeListeners.remove(mediaController)
                Unit
            }
            every { mediaController.setMediaItem(any(), any<Long>()) } answers {
                currentMediaItem = firstArg()
            }
            every { mediaController.play() } answers { isPlaying = true }
            every { mediaController.pause() } answers { isPlaying = false }
        }
    }

    private companion object {
        private const val EPISODE_ID = 101L
        private const val NEW_EPISODE_ID = 202L
        private const val INITIAL_POSITION_MS = 120_000L
        private const val SEEK_TARGET_MS = 600_000L
        private const val CONFIRMED_POSITION_MS = 599_500L
        private const val DURATION_MS = 1_200_000L
        private const val SEEK_TIMEOUT_MS = 5_000L

        private fun positionInfo(positionMs: Long): Player.PositionInfo =
            Player.PositionInfo(
                null,
                0,
                null,
                null,
                0,
                positionMs,
                positionMs,
                0,
                0
            )

        private fun episode(): Episode =
            Episode(
                guid = "seek-test",
                podcastRssUrl = "https://example.com/feed.xml",
                title = "Seek test",
                description = "",
                pubDate = Date(0L),
                link = "",
                enclosureUrl = "https://example.com/audio.mp3",
                episodeId = EPISODE_ID
            )
    }
}
