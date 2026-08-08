package com.example.pocastcloni.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.PocastApplication
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.playback.api.PlaybackControllerEntryPoint
import com.example.pocastcloni.playback.api.PlayerScreenEvent
import com.example.pocastcloni.service.PodcastPlaybackService
import com.example.pocastcloni.ui.main.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PlayerTimelineEndToEndAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val database by lazy { AppDatabase.getDatabase(context) }
    private val dao by lazy { database.podcastDao() }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var entryPoint: PlaybackControllerEntryPoint
    private var audioFile: File? = null
    private var activityScenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() = runBlocking {
        (context.applicationContext as PocastApplication).appInitializer.awaitStartupCompletion()
        entryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                PlaybackControllerEntryPoint::class.java
            )
        instrumentation.runOnMainSync {
            entryPoint.playerCommandPort().releaseResources()
        }
        context.stopService(Intent(context, PodcastPlaybackService::class.java))
        dao.deleteAllPodcasts()
    }

    @After
    fun tearDown() = runBlocking {
        activityScenario?.close()
        activityScenario = null
        if (::entryPoint.isInitialized) {
            instrumentation.runOnMainSync {
                entryPoint.playerCommandPort().releaseResources()
            }
        }
        context.stopService(Intent(context, PodcastPlaybackService::class.java))
        dao.deleteAllPodcasts()
        audioFile?.delete()
        audioFile = null
    }

    @Test
    fun timelineEventPersistsPlayerConfirmedPositionWithoutSnapBack() = runBlocking {
        val episodeId = seedDownloadedEpisode()
        activityScenario =
            ActivityScenario.launch(MainActivity::class.java).also {
                it.moveToState(Lifecycle.State.RESUMED)
            }
        val playbackStarter = entryPoint.playbackStarter()
        val playerCommands = entryPoint.playerCommandPort()
        val playerState = entryPoint.playerStatePort()

        withContext(Dispatchers.Main) {
            playbackStarter.play(episodeId)
        }
        awaitCondition("downloaded episode to become seekable") {
            val state = playerState.playbackState.value
            state.isSeekable && state.durationMs >= SEEK_TARGET_MS
        }

        instrumentation.runOnMainSync {
            playerCommands.onEvent(PlayerScreenEvent.SeekStarted)
            playerCommands.onEvent(PlayerScreenEvent.SeekTo(SEEK_TARGET_MS))
            playerCommands.onEvent(PlayerScreenEvent.SeekFinished)
        }

        awaitCondition("timeline seek to settle") {
            val state = playerState.playbackState.value
            !state.isSeekPending && abs(state.currentPositionMs - SEEK_TARGET_MS) <= POSITION_TOLERANCE_MS
        }
        awaitCondition("confirmed seek position to reach Room") {
            val storedPositionMs = dao.getEpisodeById(episodeId)?.playbackPositionMs ?: 0L
            abs(storedPositionMs - SEEK_TARGET_MS) <= POSITION_TOLERANCE_MS
        }

        SystemClock.sleep(FIVE_FOREGROUND_TICKS_MS)
        val stablePositionMs = playerState.playbackState.value.currentPositionMs
        assertTrue(
            "Timeline returned behind the confirmed seek: $stablePositionMs",
            stablePositionMs >= SEEK_TARGET_MS - POSITION_TOLERANCE_MS
        )
    }

    private suspend fun seedDownloadedEpisode(): Long {
        val podcastUrl = "https://example.test/timeline-e2e.xml"
        dao.insertPodcast(
            PodcastEntity(
                rssUrl = podcastUrl,
                title = "Timeline end-to-end podcast",
                description = "Player timeline integration fixture",
                imageUrl = ""
            )
        )
        val wavBytes = createSilentWav(WAV_DURATION_SECONDS)
        val file =
            File.createTempFile("timeline-e2e-", ".wav", context.cacheDir).apply {
                writeBytes(wavBytes)
            }
        audioFile = file
        dao.insertEpisode(
            EpisodeEntity(
                guid = "timeline-e2e-episode",
                podcastRssUrl = podcastUrl,
                title = "Timeline end-to-end episode",
                description = "Local seek fixture",
                pubDate = null,
                link = "https://example.test/timeline-e2e.wav",
                enclosureUrl = "https://example.test/timeline-e2e.wav",
                type = "audio/wav",
                fileSize = wavBytes.size.toLong(),
                duration = WAV_DURATION_SECONDS * 1_000L,
                downloadStatus = DownloadStatus.DOWNLOADED,
                downloadPath = file.absolutePath
            )
        )
        return requireNotNull(
            dao.getEpisodeByFeedAndGuid(podcastUrl, "timeline-e2e-episode")
        ).episodeId
    }

    private suspend fun awaitCondition(
        description: String,
        condition: suspend () -> Boolean
    ) {
        val deadlineMs = SystemClock.elapsedRealtime() + TEST_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        val playbackState =
            if (::entryPoint.isInitialized) entryPoint.playerStatePort().playbackState.value else null
        val playerState =
            if (::entryPoint.isInitialized) entryPoint.playerStatePort().playerState.value else null
        throw AssertionError(
            "Timed out waiting for $description; playbackState=$playbackState, playerState=$playerState"
        )
    }

    private fun createSilentWav(durationSeconds: Int): ByteArray {
        val dataSize = WAV_SAMPLE_RATE * WAV_BYTES_PER_SAMPLE * durationSeconds
        return ByteBuffer.allocate(WAV_HEADER_SIZE + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(WAV_HEADER_SIZE - 8 + dataSize)
                put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(WAV_SAMPLE_RATE)
                putInt(WAV_SAMPLE_RATE * WAV_BYTES_PER_SAMPLE)
                putShort(WAV_BYTES_PER_SAMPLE.toShort())
                putShort(WAV_BITS_PER_SAMPLE.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataSize)
            }.array()
    }

    private companion object {
        const val TEST_TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 25L
        const val POSITION_TOLERANCE_MS = 750L
        const val FIVE_FOREGROUND_TICKS_MS = 2_500L
        const val WAV_DURATION_SECONDS = 20
        const val WAV_SAMPLE_RATE = 8_000
        const val WAV_BYTES_PER_SAMPLE = 2
        const val WAV_BITS_PER_SAMPLE = 16
        const val WAV_HEADER_SIZE = 44
        const val SEEK_TARGET_MS = 10_000L
    }
}
