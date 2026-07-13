package com.example.pocastcloni.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingDebugTreeTest {
    @Test
    fun `credentials private path query and fragment are reduced to origin`() {
        val redacted = redactNetworkUrlsForLog(
            "GET https://user:secret@private.example:8443/private/feed/token.rss?key=abc#episode"
        )

        assertEquals("GET https://private.example:8443", redacted)
    }

    @Test
    fun `default ports remain explicit and malformed URL tokens are replaced`() {
        val redacted = redactNetworkUrlsForLog(
            "good=https://example.com/private.xml bad=https://user:secret@"
        )

        assertEquals(
            "good=https://example.com:443 bad=[redacted-network-url]",
            redacted
        )
    }

    @Test
    fun `formatted and throwable messages are both fully redacted`() {
        val formatted = "Sync failed for %s".format("http://example.com/private/feed?token=secret")
        val throwable = IllegalStateException(
            "Request https://user:password@example.org/account/feed.xml?auth=hidden failed"
        )
        val redacted = redactNetworkUrlsForLog("$formatted\n${throwable.stackTraceToString()}")

        assertTrue(redacted.contains("http://example.com:80"))
        assertTrue(redacted.contains("https://example.org:443"))
        assertFalse(redacted.contains("private"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("password"))
        assertFalse(redacted.contains("hidden"))
    }
}
