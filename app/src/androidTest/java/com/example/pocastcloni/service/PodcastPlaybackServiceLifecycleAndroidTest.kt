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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
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
                controller.play()
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
                it.isConnected && it.isPlaying && it.currentPositionMs > 0L
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
                    audioFile.delete()
                }
            }
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

    private data class ControllerState(
        val isConnected: Boolean,
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val playbackState: Int,
        val currentPositionMs: Long,
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
    }
}
