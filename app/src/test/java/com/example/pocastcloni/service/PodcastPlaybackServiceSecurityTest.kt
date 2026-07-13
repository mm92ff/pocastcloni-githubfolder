package com.example.pocastcloni.service

import androidx.media3.common.Player
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastPlaybackServiceSecurityTest {
    @Test
    fun `untrusted media controllers are rejected without commands`() {
        val commands = mediaControllerCommands(
            isTrusted = false,
            availableCommands = mockk()
        )

        assertNull(commands)
    }

    @Test
    fun `trusted media controllers receive exactly the player available commands`() {
        val availableCommands = mockk<Player.Commands>()
        val commands = mediaControllerCommands(
            isTrusted = true,
            availableCommands = availableCommands
        )

        assertSame(availableCommands, commands)
    }

    @Test
    fun `own package and uid are allowed when media3 does not mark controller trusted`() {
        assertTrue(
            isMediaControllerAllowed(
                isTrusted = false,
                controllerPackageName = APP_PACKAGE,
                controllerUid = APP_UID,
                appPackageName = APP_PACKAGE,
                appUid = APP_UID
            )
        )
    }

    @Test
    fun `package name alone cannot impersonate the app controller`() {
        assertFalse(
            isMediaControllerAllowed(
                isTrusted = false,
                controllerPackageName = APP_PACKAGE,
                controllerUid = APP_UID + 1,
                appPackageName = APP_PACKAGE,
                appUid = APP_UID
            )
        )
    }

    private companion object {
        const val APP_PACKAGE = "com.example.pocastcloni"
        const val APP_UID = 10_123
    }
}
