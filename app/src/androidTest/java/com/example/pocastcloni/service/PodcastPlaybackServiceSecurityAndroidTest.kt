package com.example.pocastcloni.service

import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PodcastPlaybackServiceSecurityAndroidTest {
    @Test
    fun controllerFromSeparateInstrumentationAppIsRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val targetContext = instrumentation.targetContext
        val token = SessionToken(
            testContext,
            ComponentName(targetContext, PodcastPlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(testContext, token).buildAsync()

        val error = assertThrows(ExecutionException::class.java) {
            controllerFuture.get(10, TimeUnit.SECONDS)
        }
        assertTrue(error.cause is SecurityException)
    }
}
