package com.example.pocastcloni.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class LocalNetworkAccessRegistryTest {
    @Test
    fun `approved client reaches only the approved local origin`() {
        val approvedServer = MockWebServer()
        val otherPortServer = MockWebServer()
        approvedServer.enqueue(MockResponse().setBody("ok"))
        otherPortServer.enqueue(MockResponse().setBody("wrong"))
        approvedServer.start()
        otherPortServer.start()
        try {
            val registry = LocalNetworkAccessRegistry().apply {
                approveFeed(approvedServer.url("/feed.xml").toString())
            }
            val client = OkHttpClient.Builder()
                .dns(ApprovedOriginDns(registry))
                .addInterceptor(ApprovedLocalRequestInterceptor(registry))
                .build()

            client.newCall(
                Request.Builder().url(approvedServer.url("/cover.jpg")).build()
            ).execute().use { response ->
                assertEquals("ok", response.body?.string())
            }

            assertThrows(IOException::class.java) {
                client.newCall(
                    Request.Builder().url(otherPortServer.url("/cover.jpg")).build()
                ).execute()
            }
            assertEquals(0, otherPortServer.requestCount)
        } finally {
            approvedServer.shutdown()
            otherPortServer.shutdown()
        }
    }
}
