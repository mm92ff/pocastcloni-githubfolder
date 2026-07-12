package com.example.pocastcloni.service

import androidx.media3.common.Player
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
}
