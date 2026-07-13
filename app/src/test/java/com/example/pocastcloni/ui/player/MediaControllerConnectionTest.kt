package com.example.pocastcloni.ui.player

import androidx.media3.session.MediaController
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaControllerConnectionTest {
    @Test
    fun `disconnect before activation rejects awaited controller and fresh attempt accepts`() {
        val disconnectedController = mockk<MediaController>()
        val firstAttempt = MediaControllerConnectionAttempt()

        firstAttempt.onDisconnected(disconnectedController)

        assertEquals(
            AwaitedControllerDecision.RETRY,
            firstAttempt.awaitedControllerDecision(disconnectedController)
        )
        assertEquals(
            AwaitedControllerDecision.ACCEPTED,
            MediaControllerConnectionAttempt().awaitedControllerDecision(disconnectedController)
        )
    }
}
