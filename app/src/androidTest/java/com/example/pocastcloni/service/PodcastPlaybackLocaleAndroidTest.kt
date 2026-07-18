package com.example.pocastcloni.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.main.MainActivity
import com.example.pocastcloni.ui.locale.AppLocaleController
import com.example.pocastcloni.ui.locale.SupportedAppLanguage
import com.example.pocastcloni.util.Constants
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PodcastPlaybackLocaleAndroidTest {
    @Test
    fun activeNotificationProviderAndChannelFollowExplicitLocaleChange() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val serviceIntent = Intent(context, PodcastPlaybackService::class.java)
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        var controllerFuture: ListenableFuture<MediaController>? = null
        var controller: MediaController? = null
        val audioFile =
            File.createTempFile("playback-locale-", ".wav", context.cacheDir).apply {
                writeBytes(createSilentWav())
            }
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            context.stopService(serviceIntent)
            notificationManager.deleteNotificationChannel(Constants.Notification.CHANNEL_PLAYBACK_ID)
            setApplicationLocales(
                instrumentation,
                LocaleListCompat.forLanguageTags(SupportedAppLanguage.ENGLISH.languageTags)
            )

            val token = SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, token).buildAsync()
            val activeController = controllerFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            controller = activeController
            val externalTitle = "External podcast 🌍"
            instrumentation.runOnMainSync {
                activeController.setMediaItem(mediaItem(audioFile, externalTitle))
                activeController.prepare()
                activeController.play()
            }

            val englishChannel = awaitPlaybackChannel(notificationManager)
            assertEquals(context.getString(R.string.playback_channel_name), englishChannel.name.toString())

            var changed = false
            instrumentation.runOnMainSync {
                changed = AppLocaleController().select(SupportedAppLanguage.GERMAN)
            }
            instrumentation.waitForIdleSync()
            assertTrue(changed)
            val germanContext =
                context.createConfigurationContext(
                    context.resources.configuration.run {
                        android.content.res.Configuration(this).apply {
                            setLocales(android.os.LocaleList.forLanguageTags("de"))
                        }
                    }
                )
            val germanChannel =
                awaitPlaybackChannel(notificationManager) { channel ->
                    channel.name.toString() == germanContext.getString(R.string.playback_channel_name)
                }
            assertEquals(
                germanContext.getString(R.string.playback_channel_name),
                germanChannel.name.toString()
            )

            instrumentation.runOnMainSync {
                activeController.setMediaItem(mediaItem(audioFile, externalTitle))
                activeController.prepare()
                activeController.play()
            }
        } finally {
            controller?.let { activeController ->
                instrumentation.runOnMainSync {
                    activeController.stop()
                    activeController.clearMediaItems()
                }
            }
            controllerFuture?.let { future ->
                instrumentation.runOnMainSync {
                    MediaController.releaseFuture(future)
                }
            }
            context.stopService(serviceIntent)
            instrumentation.waitForIdleSync()
            awaitNoPlaybackNotification(notificationManager)
            setApplicationLocales(instrumentation, previousLocales)
            notificationManager.cancelAll()
            notificationManager.deleteNotificationChannel(Constants.Notification.CHANNEL_PLAYBACK_ID)
            audioFile.delete()
            activityScenario.close()
        }
    }

    private fun mediaItem(
        file: File,
        title: String
    ): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    private fun awaitPlaybackChannel(
        notificationManager: NotificationManager,
        condition: (android.app.NotificationChannel) -> Boolean = { true }
    ): android.app.NotificationChannel {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.elapsedRealtime() < deadline) {
            val channel =
                notificationManager.getNotificationChannel(Constants.Notification.CHANNEL_PLAYBACK_ID)
            if (channel != null && condition(channel)) return channel
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("Timed out waiting for the localized playback notification channel")
    }

    private fun awaitNoPlaybackNotification(notificationManager: NotificationManager) {
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS)
        while (SystemClock.elapsedRealtime() < deadline) {
            val hasPlaybackNotification =
                notificationManager.activeNotifications.any {
                    it.notification.channelId == Constants.Notification.CHANNEL_PLAYBACK_ID
                }
            if (!hasPlaybackNotification) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("Timed out waiting for the playback foreground notification to stop")
    }

    private fun setApplicationLocales(
        instrumentation: android.app.Instrumentation,
        locales: LocaleListCompat
    ) {
        instrumentation.runOnMainSync {
            AppCompatDelegate.setApplicationLocales(locales)
        }
        instrumentation.waitForIdleSync()
    }

    private fun createSilentWav(): ByteArray {
        val dataSize = WAV_SAMPLE_RATE * WAV_BYTES_PER_SAMPLE * WAV_DURATION_SECONDS
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
        const val TIMEOUT_SECONDS = 10L
        const val POLL_INTERVAL_MS = 50L
        const val WAV_DURATION_SECONDS = 3
        const val WAV_SAMPLE_RATE = 8_000
        const val WAV_BYTES_PER_SAMPLE = 2
        const val WAV_BITS_PER_SAMPLE = 16
        const val WAV_HEADER_SIZE = 44
    }
}
