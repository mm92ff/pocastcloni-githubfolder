package com.example.pocastcloni.service

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.PocastApplication
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.LocalNetworkRegistryEntryPoint
import com.example.pocastcloni.ui.main.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PodcastPlaybackServiceLifecycleAndroidTest {
    @Test
    fun coldStartPublishesSessionBeforeFirstMediaCommand() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val serviceIntent = Intent(context, PodcastPlaybackService::class.java)
        context.stopService(serviceIntent)

        val token =
            SessionToken(
                context,
                ComponentName(context, PodcastPlaybackService::class.java)
            )
        val controllerFuture = MediaController.Builder(context, token).buildAsync()

        try {
            val controller = controllerFuture.get(10L, TimeUnit.SECONDS)
            var connectedBeforeCommand = false
            instrumentation.runOnMainSync {
                connectedBeforeCommand = controller.isConnected
                controller.prepare()
            }
            assertTrue(connectedBeforeCommand)
            instrumentation.waitForIdleSync()
            var connectedAfterCommand = false
            instrumentation.runOnMainSync {
                connectedAfterCommand = controller.isConnected
            }
            assertTrue(connectedAfterCommand)
        } finally {
            instrumentation.runOnMainSync {
                MediaController.releaseFuture(controllerFuture)
            }
            context.stopService(serviceIntent)
        }
    }

    @Test
    fun playPauseSeekAndQueuePositionSurviveControllerReconnect() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        val serviceIntent = Intent(context, PodcastPlaybackService::class.java)
        context.stopService(serviceIntent)
        instrumentation.waitForIdleSync()

        val audioFile =
            File.createTempFile("playback-lifecycle-", ".wav", context.cacheDir).apply {
                writeBytes(createSilentWav(WAV_DURATION_SECONDS))
            }
        val audioUri = Uri.fromFile(audioFile)
        val mediaItems =
            listOf(
                playableMediaItem(FIRST_MEDIA_ID, audioUri),
                playableMediaItem(SECOND_MEDIA_ID, audioUri)
            )
        val token =
            SessionToken(
                context,
                ComponentName(context, PodcastPlaybackService::class.java)
            )
        val activeControllerFutures = linkedSetOf<ListenableFuture<MediaController>>()

        try {
            val initialControllerFuture = MediaController.Builder(context, token).buildAsync()
            activeControllerFutures += initialControllerFuture
            val initialController = initialControllerFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

            instrumentation.runOnMainSync {
                initialController.setMediaItems(mediaItems)
                initialController.prepare()
                initialController.play()
            }
            awaitControllerState(instrumentation, initialController, "local WAV playback to start") {
                it.isConnected &&
                    it.isPlaying &&
                    it.currentPositionMs > 0L &&
                    it.isCurrentMediaItemSeekable &&
                    it.isSeekCommandAvailable
            }

            instrumentation.runOnMainSync {
                initialController.pause()
            }
            awaitControllerState(instrumentation, initialController, "playback to pause") {
                it.isConnected && !it.isPlaying && !it.playWhenReady
            }

            instrumentation.runOnMainSync {
                initialController.seekTo(RESTORED_QUEUE_INDEX, RESTORED_POSITION_MS)
            }
            awaitControllerState(instrumentation, initialController, "seek to settle") {
                it.playbackState == Player.STATE_READY &&
                    it.currentMediaItemIndex == RESTORED_QUEUE_INDEX &&
                    abs(it.currentPositionMs - RESTORED_POSITION_MS) <= POSITION_TOLERANCE_MS
            }

            releaseController(instrumentation, initialControllerFuture)
            activeControllerFutures -= initialControllerFuture

            val reconnectedControllerFuture = MediaController.Builder(context, token).buildAsync()
            activeControllerFutures += reconnectedControllerFuture
            val reconnectedController =
                reconnectedControllerFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val restoredState =
                awaitControllerState(
                    instrumentation,
                    reconnectedController,
                    "queue and position to restore after reconnect"
                ) {
                    it.isConnected &&
                        it.mediaIds.size == mediaItems.size &&
                        it.currentMediaItemIndex == RESTORED_QUEUE_INDEX &&
                        abs(it.currentPositionMs - RESTORED_POSITION_MS) <= POSITION_TOLERANCE_MS
                }

            assertEquals(listOf(FIRST_MEDIA_ID, SECOND_MEDIA_ID), restoredState.mediaIds)
            assertEquals(RESTORED_QUEUE_INDEX, restoredState.currentMediaItemIndex)
            assertTrue(
                "Expected position near $RESTORED_POSITION_MS ms but was " +
                    "${restoredState.currentPositionMs} ms",
                abs(restoredState.currentPositionMs - RESTORED_POSITION_MS) <= POSITION_TOLERANCE_MS
            )
            assertFalse(restoredState.playWhenReady)
        } finally {
            try {
                activeControllerFutures.forEach { releaseController(instrumentation, it) }
            } finally {
                try {
                    context.stopService(serviceIntent)
                    instrumentation.waitForIdleSync()
                } finally {
                    try {
                        audioFile.delete()
                    } finally {
                        activityScenario.close()
                    }
                }
            }
        }
    }

    @Test
    fun rangeCapableHttpSourceKeepsTheSettledSeekPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val serviceIntent = Intent(context, PodcastPlaybackService::class.java)
        val audioBytes = createSilentWav(STREAM_WAV_DURATION_SECONDS)
        val server = MockWebServer()
        server.dispatcher = RangeAudioDispatcher(audioBytes)
        server.start()
        val audioUri = Uri.parse(server.url(AUDIO_PATH).toString())
        val approvedFeedUrl = persistLocalOriginApproval(context, audioUri)
        context.stopService(serviceIntent)
        instrumentation.waitForIdleSync()
        val token = SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()

        try {
            val controller = controllerFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            instrumentation.runOnMainSync {
                controller.setMediaItem(
                    playableMediaItem(STREAM_MEDIA_ID, audioUri)
                )
                controller.prepare()
                controller.play()
            }
            awaitControllerState(instrumentation, controller, "HTTP WAV source to become seekable") {
                it.playbackState == Player.STATE_READY &&
                    it.isCurrentMediaItemSeekable &&
                    it.durationMs >= STREAM_SEEK_TARGET_MS
            }

            instrumentation.runOnMainSync { controller.seekTo(STREAM_SEEK_TARGET_MS) }
            awaitControllerState(instrumentation, controller, "HTTP seek to settle") {
                abs(it.currentPositionMs - STREAM_SEEK_TARGET_MS) <= STREAM_POSITION_TOLERANCE_MS
            }
            SystemClock.sleep(FIVE_FOREGROUND_TICKS_MS)
            val stableState = controllerState(instrumentation, controller)

            assertTrue(
                "HTTP position returned behind the seek target: $stableState",
                stableState.currentPositionMs >= STREAM_SEEK_TARGET_MS - STREAM_POSITION_TOLERANCE_MS
            )
        } finally {
            releaseController(instrumentation, controllerFuture)
            context.stopService(serviceIntent)
            removeLocalOriginApproval(context, approvedFeedUrl)
            server.shutdown()
        }
    }

    @Test
    fun chunkedAmrSourceReportsSeekingUnavailable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val serviceIntent = Intent(context, PodcastPlaybackService::class.java)
        val server = MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setHeader("Content-Type", "audio/amr")
                    .setChunkedBody(Buffer().write(createAmrStream(AMR_FRAME_COUNT)), AMR_CHUNK_BYTES)
            )
            start()
        }
        val audioUri = Uri.parse(server.url(AUDIO_PATH).toString())
        val approvedFeedUrl = persistLocalOriginApproval(context, audioUri)
        context.stopService(serviceIntent)
        instrumentation.waitForIdleSync()
        val token = SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()

        try {
            val controller = controllerFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            instrumentation.runOnMainSync {
                controller.setMediaItem(
                    playableMediaItem(UNSEEKABLE_MEDIA_ID, audioUri)
                )
                controller.prepare()
            }
            val unseekableState =
                awaitControllerState(instrumentation, controller, "chunked AMR source to prepare") {
                    it.playbackState == Player.STATE_READY &&
                        !it.isCurrentMediaItemSeekable &&
                        !it.isSeekCommandAvailable
                }

            assertFalse(unseekableState.isCurrentMediaItemSeekable)
            assertFalse(unseekableState.isSeekCommandAvailable)
        } finally {
            releaseController(instrumentation, controllerFuture)
            context.stopService(serviceIntent)
            removeLocalOriginApproval(context, approvedFeedUrl)
            server.shutdown()
        }
    }

    private fun playableMediaItem(
        mediaId: String,
        uri: Uri
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .build()

    private fun persistLocalOriginApproval(
        context: android.content.Context,
        uri: Uri
    ): String {
        val approvedFeedUrl =
            uri.buildUpon()
                .path(APPROVED_FEED_PATH)
                .clearQuery()
                .fragment(null)
                .build()
                .toString()
        runBlocking {
            (context.applicationContext as PocastApplication).appInitializer.awaitStartupCompletion()
            AppDatabase.getDatabase(context).podcastDao().insertPodcast(
                PodcastEntity(
                    rssUrl = approvedFeedUrl,
                    title = "Local playback fixture",
                    description = "Instrumentation-only local network approval",
                    imageUrl = "",
                    allowLocalNetwork = true
                )
            )
        }
        val registry = localNetworkRegistry(context)
        val deadlineMs = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (!registry.isApproved(uri.toString()) && SystemClock.elapsedRealtime() < deadlineMs) {
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        assertTrue("Persisted local origin was not propagated", registry.isApproved(uri.toString()))
        return approvedFeedUrl
    }

    private fun removeLocalOriginApproval(
        context: android.content.Context,
        approvedFeedUrl: String
    ) {
        runBlocking {
            AppDatabase.getDatabase(context).podcastDao().deletePodcastAtomic(approvedFeedUrl)
        }
    }

    private fun localNetworkRegistry(context: android.content.Context) =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LocalNetworkRegistryEntryPoint::class.java
        ).localNetworkAccessRegistry()

    private fun releaseController(
        instrumentation: Instrumentation,
        controllerFuture: ListenableFuture<MediaController>
    ) {
        instrumentation.runOnMainSync {
            MediaController.releaseFuture(controllerFuture)
        }
        instrumentation.waitForIdleSync()
    }

    private fun awaitControllerState(
        instrumentation: Instrumentation,
        controller: MediaController,
        description: String,
        condition: (ControllerState) -> Boolean
    ): ControllerState {
        val deadlineMs = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        var latestState = controllerState(instrumentation, controller)

        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (condition(latestState)) return latestState
            SystemClock.sleep(POLL_INTERVAL_MS)
            latestState = controllerState(instrumentation, controller)
        }

        throw AssertionError(
            "Timed out waiting for $description. Last controller state: $latestState"
        )
    }

    private fun controllerState(
        instrumentation: Instrumentation,
        controller: MediaController
    ): ControllerState {
        var state: ControllerState? = null
        instrumentation.runOnMainSync {
            state =
                ControllerState(
                    isConnected = controller.isConnected,
                    isPlaying = controller.isPlaying,
                    playWhenReady = controller.playWhenReady,
                    playbackState = controller.playbackState,
                    currentPositionMs = controller.currentPosition,
                    durationMs = controller.duration,
                    isCurrentMediaItemSeekable = controller.isCurrentMediaItemSeekable,
                    isSeekCommandAvailable =
                    controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                    currentMediaItemIndex = controller.currentMediaItemIndex,
                    mediaIds =
                    (0 until controller.mediaItemCount).map { index ->
                        controller.getMediaItemAt(index).mediaId
                    }
                )
        }
        return requireNotNull(state)
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

    private fun createAmrStream(frameCount: Int): ByteArray =
        Buffer()
            .writeUtf8(AMR_HEADER)
            .apply {
                repeat(frameCount) {
                    writeByte(AMR_12_2_FRAME_HEADER)
                    write(ByteArray(AMR_12_2_PAYLOAD_BYTES))
                }
            }.readByteArray()

    private class RangeAudioDispatcher(
        private val audioBytes: ByteArray
    ) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.requestUrl?.encodedPath != AUDIO_PATH) {
                return MockResponse().setResponseCode(404)
            }
            val offset =
                request.getHeader("Range")
                    ?.let { header -> RANGE_PATTERN.matchEntire(header)?.groupValues?.get(1)?.toIntOrNull() }
                    ?: 0
            if (offset !in 0 until audioBytes.size) {
                return MockResponse().setResponseCode(416)
            }
            val body = audioBytes.copyOfRange(offset, audioBytes.size)
            return MockResponse()
                .setResponseCode(if (offset == 0) 200 else 206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", "audio/wav")
                .apply {
                    if (offset > 0) {
                        setHeader(
                            "Content-Range",
                            "bytes $offset-${audioBytes.lastIndex}/${audioBytes.size}"
                        )
                    }
                }.setBody(Buffer().write(body))
                .throttleBody(STREAM_THROTTLE_BYTES, STREAM_THROTTLE_PERIOD_MS, TimeUnit.MILLISECONDS)
        }
    }

    private data class ControllerState(
        val isConnected: Boolean,
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val playbackState: Int,
        val currentPositionMs: Long,
        val durationMs: Long,
        val isCurrentMediaItemSeekable: Boolean,
        val isSeekCommandAvailable: Boolean,
        val currentMediaItemIndex: Int,
        val mediaIds: List<String>
    )

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MS = 25L
        const val POSITION_TOLERANCE_MS = 150L
        const val WAV_DURATION_SECONDS = 6
        const val WAV_SAMPLE_RATE = 8_000
        const val WAV_BYTES_PER_SAMPLE = 2
        const val WAV_BITS_PER_SAMPLE = 16
        const val WAV_HEADER_SIZE = 44
        const val RESTORED_QUEUE_INDEX = 1
        const val RESTORED_POSITION_MS = 2_000L
        const val FIRST_MEDIA_ID = "lifecycle-first"
        const val SECOND_MEDIA_ID = "lifecycle-second"
        const val STREAM_MEDIA_ID = "range-http"
        const val UNSEEKABLE_MEDIA_ID = "chunked-amr"
        const val AUDIO_PATH = "/episode-audio"
        const val APPROVED_FEED_PATH = "/approved-feed.xml"
        const val STREAM_WAV_DURATION_SECONDS = 30
        const val STREAM_SEEK_TARGET_MS = 20_000L
        const val STREAM_POSITION_TOLERANCE_MS = 750L
        const val FIVE_FOREGROUND_TICKS_MS = 2_500L
        const val STREAM_THROTTLE_BYTES = 16_384L
        const val STREAM_THROTTLE_PERIOD_MS = 50L
        const val AMR_HEADER = "#!AMR\n"
        const val AMR_FRAME_COUNT = 500
        const val AMR_CHUNK_BYTES = 64
        const val AMR_12_2_FRAME_HEADER = 0x3C
        const val AMR_12_2_PAYLOAD_BYTES = 31
        val RANGE_PATTERN = Regex("bytes=(\\d+)-")
    }
}
