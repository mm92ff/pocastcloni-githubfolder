package com.example.pocastcloni.playback.infrastructure

import androidx.media3.session.MediaController
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.playback.api.PlayerCommandPort
import com.google.common.util.concurrent.Futures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackResetCoordinatorTest {
    @Test
    fun `reset stops active controller before releasing player resources`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatcherProvider = mockk<DispatcherProvider>()
        val mediaController = mockk<MediaController>(relaxed = true)
        val playerCommands = mockk<PlayerCommandPort>(relaxed = true)
        val connection =
            MediaControllerConnection(
                controllerFutureFactory = MediaControllerFutureFactory {
                    Futures.immediateFuture(mediaController)
                },
                retryDelayMs = 0L,
                maxAttempts = 1
            )
        every { dispatcherProvider.main } returns dispatcher
        assertSame(mediaController, connection.connect())
        val subject = PlaybackResetCoordinator(connection, playerCommands, dispatcherProvider)

        subject.stopAndReleaseForReset()

        verifyOrder {
            mediaController.stop()
            mediaController.clearMediaItems()
            playerCommands.releaseResources()
        }
    }
}
