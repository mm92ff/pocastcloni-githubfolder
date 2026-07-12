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

    @Test
    fun `local network URLs require a separate approval`() {
        val localHttps = "https://192.168.1.20/feed.xml"
        assertNull(parseNetworkUrl(localHttps))
        assertTrue(requiresLocalNetworkConfirmation(localHttps))
        assertThrows(IllegalArgumentException::class.java) {
            requireApprovedNetworkUrl(localHttps, allowInsecureHttp = false)
        }
        requireApprovedNetworkUrl(
            localHttps,
            allowInsecureHttp = false,
            allowLocalNetwork = true
        )
        assertTrue(parseNetworkUrl("https://podcast.local/feed.xml", true) != null)
        assertTrue(parseNetworkUrl("https://nas/feed.xml", true) != null)
    }

    @Test
    fun `local HTTP requires both local and cleartext approvals`() {
        val localHttp = "http://10.0.2.2/feed.xml"
        assertThrows(IllegalArgumentException::class.java) {
            requireApprovedNetworkUrl(
                localHttp,
                allowInsecureHttp = true,
                allowLocalNetwork = false
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireApprovedNetworkUrl(
                localHttp,
                allowInsecureHttp = false,
                allowLocalNetwork = true
            )
        }
        requireApprovedNetworkUrl(
            localHttp,
            allowInsecureHttp = true,
            allowLocalNetwork = true
        )
    }

    @Test
    fun `local podcast resources must stay on the approved feed origin`() {
        val feed = "http://192.168.1.20:8080/feed.xml"
        assertTrue(
            isAllowedPodcastResource(
                feed,
                "http://192.168.1.20:8080/audio.mp3",
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
        assertFalse(
            isAllowedPodcastResource(
                feed,
                "http://192.168.1.21:8080/audio.mp3",
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
        assertFalse(
            isAllowedPodcastResource(
                feed,
                "http://192.168.1.20:9090/audio.mp3",
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
        assertFalse(
            isAllowedPodcastResource(
                feed,
                "https://192.168.1.20:8080/audio.mp3",
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        )
    }

    @Test
    fun `client routing uses local network only for the approved origin`() {
        val feed = "https://podcast.local:8443/feed.xml"
        assertTrue(
            shouldUseLocalNetworkForResource(
                feed,
                "https://podcast.local:8443/cover.jpg",
                allowLocalNetwork = true
            )
        )
        assertFalse(
            shouldUseLocalNetworkForResource(
                feed,
                "https://podcast.local:9443/cover.jpg",
                allowLocalNetwork = true
            )
        )
        assertFalse(
            shouldUseLocalNetworkForResource(
                feed,
                "https://cdn.example.com/cover.jpg",
                allowLocalNetwork = true
            )
        )
    }
}
