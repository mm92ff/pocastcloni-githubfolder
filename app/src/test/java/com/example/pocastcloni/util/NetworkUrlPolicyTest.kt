package com.example.pocastcloni.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkUrlPolicyTest {
    @Test
    fun `accepts HTTPS and legacy HTTP URLs`() {
        assertFalse(requiresCleartextConfirmation("https://example.com/feed.xml"))
        assertTrue(requiresCleartextConfirmation("http://example.com/feed.xml"))
    }

    @Test
    fun `rejects non-network schemes and embedded credentials`() {
        assertNull(parseNetworkUrl("file:///data/local/feed.xml"))
        assertNull(parseNetworkUrl("content://provider/feed"))
        assertNull(parseNetworkUrl("https://user:secret@example.com/feed.xml"))
        assertNull(parseNetworkUrl("http://localhost/feed.xml"))
        assertNull(parseNetworkUrl("http://127.0.0.1/feed.xml"))
        assertNull(parseNetworkUrl("http://10.0.0.1/feed.xml"))
        assertNull(parseNetworkUrl("http://169.254.1.1/feed.xml"))
        assertNull(parseNetworkUrl("http://0.1.2.3/feed.xml"))
        assertNull(parseNetworkUrl("http://100.64.0.1/feed.xml"))
        assertNull(parseNetworkUrl("http://198.18.0.1/feed.xml"))
        assertNull(parseNetworkUrl("http://[::1]/feed.xml"))
        assertNull(parseNetworkUrl("http://[fc00::1]/feed.xml"))
    }

    @Test
    fun `blocks only HTTPS to HTTP downgrade redirects`() {
        assertTrue(
            isUnsafeRedirect(
                "https://example.com/feed".toHttpUrl(),
                "http://example.com/feed".toHttpUrl()
            )
        )
        assertFalse(
            isUnsafeRedirect(
                "http://example.com/feed".toHttpUrl(),
                "https://example.com/feed".toHttpUrl()
            )
        )
    }

    @Test
    fun `HTTP requires a persistent approval at the central policy`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireApprovedNetworkUrl("http://example.com/feed.xml", allowInsecureHttp = false)
        }
        requireApprovedNetworkUrl("http://example.com/feed.xml", allowInsecureHttp = true)
        requireApprovedNetworkUrl("https://example.com/feed.xml", allowInsecureHttp = false)
    }

    @Test
    fun `derived media follows the same HTTP approval`() {
        assertFalse(isAllowedRemoteResource("http://example.com/audio.mp3", allowInsecureHttp = false))
        assertTrue(isAllowedRemoteResource("http://example.com/audio.mp3", allowInsecureHttp = true))
        assertFalse(isAllowedRemoteResource("file:///tmp/audio.mp3", allowInsecureHttp = true))
    }
}
