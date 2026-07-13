package com.example.pocastcloni.service

import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

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
}
