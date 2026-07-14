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
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.playback.api.PlayerUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerControllerConcurrencyTest {
    @Test
    fun `inverse preparation completion lets only the latest play request commit`() = runTest {
        val fixture = Fixture(this)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        coEvery { fixture.preparePlayback(FIRST_EPISODE_ID) } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
            playResult(FIRST_EPISODE_ID)
        }

        val firstRequest = async { fixture.subject.play(FIRST_EPISODE_ID) }
        runCurrent()
        assertTrue(firstStarted.isCompleted)

        val latestRequest = async { fixture.subject.play(SECOND_EPISODE_ID) }
        runCurrent()
        latestRequest.await()
        releaseFirst.complete(Unit)
        runCurrent()
        firstRequest.await()

        verify(exactly = 1) {
            fixture.primary.controller.setMediaItem(
                match { it.mediaId == SECOND_EPISODE_ID.toString() },
                0L
            )
        }
        verify(exactly = 0) {
            fixture.primary.controller.setMediaItem(
                match { it.mediaId == FIRST_EPISODE_ID.toString() },
                any<Long>()
            )
        }
        verify(exactly = 1) { fixture.primary.controller.prepare() }
        verify(exactly = 1) { fixture.primary.controller.play() }
    }

    @Test
    fun `delayed previous transition does not invalidate newer play request`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.play(SECOND_EPISODE_ID)
        runCurrent()
        clearMocks(fixture.primary.controller, answers = false, recordedCalls = true)

        val thirdPreparationStarted = CompletableDeferred<Unit>()
        val releaseThirdPreparation = CompletableDeferred<Unit>()
        coEvery { fixture.preparePlayback(THIRD_EPISODE_ID) } coAnswers {
            thirdPreparationStarted.complete(Unit)
            releaseThirdPreparation.await()
            playResult(THIRD_EPISODE_ID)
        }
        val newestRequest = async { fixture.subject.play(THIRD_EPISODE_ID) }
        runCurrent()
        assertTrue(thirdPreparationStarted.isCompleted)

        fixture.primary.listeners.single().onMediaItemTransition(
            MediaItem.Builder().setMediaId(SECOND_EPISODE_ID.toString()).build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        )
        runCurrent()
        releaseThirdPreparation.complete(Unit)
        runCurrent()
        newestRequest.await()

        verify(exactly = 1) {
            fixture.primary.controller.setMediaItem(
                match { it.mediaId == THIRD_EPISODE_ID.toString() },
                0L
            )
        }
        verify(exactly = 1) { fixture.primary.controller.prepare() }
        verify(exactly = 1) { fixture.primary.controller.play() }
    }

    @Test
    fun `episode switch during seek discards stale target without persistence`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()

        fixture.subject.onEvent(PlayerScreenEvent.SeekStarted)
        fixture.subject.onEvent(PlayerScreenEvent.SeekTo(STALE_SEEK_POSITION_MS))
        fixture.subject.play(SECOND_EPISODE_ID)
        fixture.subject.onEvent(PlayerScreenEvent.SeekFinished)
        runCurrent()

        verify(exactly = 0) { fixture.primary.controller.seekTo(STALE_SEEK_POSITION_MS) }
        verify(exactly = 0) {
            fixture.analytics.saveProgressBestEffort(any(), STALE_SEEK_POSITION_MS)
        }
    }

    @Test
    fun `delayed episode mapping cannot overwrite the current episode ui`() = runTest {
        val fixture = Fixture(this)
        val firstReadStarted = CompletableDeferred<Unit>()
        val releaseFirstRead = CompletableDeferred<Unit>()
        coEvery { fixture.podcastQuery.getEpisode(FIRST_EPISODE_ID) } coAnswers {
            firstReadStarted.complete(Unit)
            releaseFirstRead.await()
            episode(FIRST_EPISODE_ID)
        }
        coEvery { fixture.podcastQuery.getEpisode(SECOND_EPISODE_ID) } returns episode(SECOND_EPISODE_ID)

        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()
        assertTrue(firstReadStarted.isCompleted)

        fixture.subject.play(SECOND_EPISODE_ID)
        runCurrent()
        releaseFirstRead.complete(Unit)
        runCurrent()

        assertFalse(fixture.mappedEpisodeIds.contains(FIRST_EPISODE_ID))
        assertTrue(fixture.mappedEpisodeIds.contains(SECOND_EPISODE_ID))
    }

    @Test
    fun `disconnect cleans the same controller and schedules exactly one reconnect`() = runTest {
        val replacement = TestMediaController()
        val fixture = Fixture(this, listOf(TestMediaController(), replacement))
        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()

        fixture.disconnectListener(fixture.primary.controller)
        fixture.disconnectListener(fixture.primary.controller)
        runCurrent()

        coVerify(exactly = 2) { fixture.mediaConnection.connect() }

        fixture.subject.releaseResources()
        fixture.disconnectListener(replacement.controller)
        runCurrent()

        coVerify(exactly = 2) { fixture.mediaConnection.connect() }
    }

    @Test
    fun `pause and repeated state callbacks flush one terminal snapshot`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()

        fixture.subject.pause()
        runCurrent()
        fixture.primary.listeners.single().onIsPlayingChanged(false)
        fixture.primary.listeners.single().onPlaybackStateChanged(Player.STATE_READY)
        runCurrent()

        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(FIRST_EPISODE_ID, CURRENT_POSITION_MS)
        }
        verify(exactly = 1) { fixture.analytics.flushListeningTime() }
    }

    @Test
    fun `ended playback flushes one terminal snapshot`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()

        fixture.primary.isPlaying = false
        fixture.primary.playbackState = Player.STATE_ENDED
        fixture.primary.listeners.single().onPlaybackStateChanged(Player.STATE_ENDED)
        fixture.primary.listeners.single().onIsPlayingChanged(false)
        runCurrent()

        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(FIRST_EPISODE_ID, CURRENT_POSITION_MS)
        }
        verify(exactly = 1) { fixture.analytics.flushListeningTime() }
    }

    @Test
    fun `release while playing flushes one terminal snapshot`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()

        fixture.subject.releaseResources()
        runCurrent()

        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(FIRST_EPISODE_ID, CURRENT_POSITION_MS)
        }
        verify(exactly = 1) { fixture.analytics.flushListeningTime() }
    }

    @Test
    fun `episode switch flushes the previous snapshot once`() = runTest {
        val fixture = Fixture(this)
        fixture.subject.play(FIRST_EPISODE_ID)
        runCurrent()

        fixture.subject.play(SECOND_EPISODE_ID)
        runCurrent()

        verify(exactly = 1) {
            fixture.analytics.saveProgressBestEffort(FIRST_EPISODE_ID, CURRENT_POSITION_MS)
        }
        verify(exactly = 1) { fixture.analytics.flushListeningTime() }
    }

    private class Fixture(
        scope: TestScope,
        controllers: List<TestMediaController> = listOf(TestMediaController())
    ) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val primary = controllers.first()
        val analytics = mockk<PlaybackAnalyticsHandler>(relaxed = true)
        val mediaConnection = mockk<MediaControllerConnection>(relaxed = true)
        val podcastQuery = mockk<PodcastQueryPort>(relaxed = true)
        val preparePlayback = mockk<PreparePlaybackUseCase>()
        val mappedEpisodeIds = mutableListOf<Long>()
        lateinit var disconnectListener: (MediaController) -> Unit
        val subject: AudioPlayerController

        init {
            val dispatcherProvider = mockk<DispatcherProvider>()
            val preferences = mockk<UserPreferencesRepository>()
            val mapper = mockk<MediaStateMapper>(relaxed = true)
            val foreground = object : AppForegroundMonitor {
                override val isForeground = MutableStateFlow(true)
            }
            val ticker = object : PlaybackTickSource {
                override fun tick(intervalMs: Long) = flow<Unit> { awaitCancellation() }
            }

            every { dispatcherProvider.main } returns dispatcher
            every { dispatcherProvider.io } returns dispatcher
            every { dispatcherProvider.default } returns dispatcher
            every { preferences.userSettingsFlow } returns flowOf(UserSettings())
            every { mediaConnection.setOnDisconnected(any()) } answers {
                disconnectListener = firstArg()
            }
            coEvery { mediaConnection.connect() } returnsMany controllers.map { it.controller }
            every { podcastQuery.isFavorite(any()) } returns flowOf(false)
            coEvery { podcastQuery.getEpisode(any()) } returns null
            coEvery { podcastQuery.getPodcast(any()) } returns null
            coEvery { preparePlayback(any()) } answers { playResult(firstArg()) }
            every { mapper.mapToMediaItem(any(), any(), any()) } answers {
                MediaItem.Builder()
                    .setMediaId(firstArg<Episode>().episodeId.toString())
                    .build()
            }
            every { mapper.mapToUiState(any(), any(), any(), any()) } answers {
                val mappedEpisode = secondArg<Episode?>()
                mappedEpisode?.episodeId?.let(mappedEpisodeIds::add)
                arg<PlayerUiState>(3).copy(currentEpisodeId = mappedEpisode?.episodeId)
            }

            subject =
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
                    monotonicClock = MonotonicClock { scope.testScheduler.currentTime },
                    mediaDispatcherFactory = MediaDispatcherFactory { dispatcher },
                    preparePlaybackUseCase = preparePlayback
                )
        }
    }

    private class TestMediaController {
        var currentMediaItem: MediaItem? = null
        var isPlaying: Boolean = false
        var playbackState: Int = Player.STATE_READY
        val listeners = mutableListOf<Player.Listener>()
        val controller = mockk<MediaController>(relaxed = true)

        init {
            every { controller.currentMediaItem } answers { currentMediaItem }
            every { controller.currentPosition } returns CURRENT_POSITION_MS
            every { controller.bufferedPosition } returns CURRENT_POSITION_MS
            every { controller.duration } returns 60_000L
            every { controller.isPlaying } answers { isPlaying }
            every { controller.playbackState } answers { playbackState }
            every { controller.addListener(any()) } answers {
                listeners += firstArg<Player.Listener>()
            }
            every { controller.removeListener(any()) } answers {
                listeners -= firstArg<Player.Listener>()
            }
            every { controller.setMediaItem(any(), any<Long>()) } answers {
                currentMediaItem = firstArg()
            }
            every { controller.play() } answers { isPlaying = true }
            every { controller.pause() } answers { isPlaying = false }
        }
    }

    private companion object {
        private const val FIRST_EPISODE_ID = 101L
        private const val SECOND_EPISODE_ID = 202L
        private const val THIRD_EPISODE_ID = 303L
        private const val CURRENT_POSITION_MS = 1_000L
        private const val STALE_SEEK_POSITION_MS = 45_000L

        private fun playResult(episodeId: Long): PlayEpisodeResult {
            val episode = episode(episodeId)
            return PlayEpisodeResult(
                episode = episode,
                startPosition = 0L,
                podcast = null,
                playUri = episode.enclosureUrl
            )
        }

        private fun episode(episodeId: Long): Episode =
            Episode(
                guid = "guid-$episodeId",
                podcastRssUrl = "https://example.com/$episodeId/feed.xml",
                title = "Episode $episodeId",
                description = "",
                pubDate = Date(0L),
                link = "",
                enclosureUrl = "https://example.com/$episodeId/audio.mp3",
                episodeId = episodeId
            )
    }
}
