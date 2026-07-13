package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.MAX_NETWORK_REDIRECTS
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SafeRedirectInterceptorTest {
    @Test
    fun `follows a bounded same-scheme redirect`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val response = client().newCall(Request.Builder().url(server.url("/start")).build()).execute()
            response.use { assertEquals("ok", it.body?.string()) }
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `rejects redirect loops after the configured limit`() {
        val server = MockWebServer()
        repeat(MAX_NETWORK_REDIRECTS + 1) {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/again"))
        }
        server.start()
        try {
            assertThrows(IOException::class.java) {
                client().newCall(Request.Builder().url(server.url("/start")).build()).execute()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `removes authorization on cross-origin redirect`() {
        val origin = MockWebServer()
        val target = MockWebServer()
        target.enqueue(MockResponse().setBody("ok"))
        origin.start()
        target.start()
        origin.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", target.url("/final"))
        )
        try {
            val request = Request.Builder()
                .url(origin.url("/start"))
                .header("Authorization", "Bearer secret")
                .build()
            client().newCall(request).execute().use { assertEquals(200, it.code) }
            assertEquals(null, target.takeRequest().getHeader("Authorization"))
        } finally {
            origin.shutdown()
            target.shutdown()
        }
    }

    @Test
    fun `cross-origin redirect strips credentials cookies connection and conditional validators`() {
        val origin = MockWebServer()
        val target = MockWebServer()
        target.enqueue(MockResponse().setBody("ok"))
        origin.start()
        target.start()
        origin.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", target.url("/final"))
        )
        try {
            val requestBuilder = Request.Builder().url(origin.url("/start"))
            CROSS_ORIGIN_HEADERS.forEach { header -> requestBuilder.header(header, "sensitive") }

            client().newCall(requestBuilder.build()).execute().use { assertEquals(200, it.code) }

            val redirectedRequest = target.takeRequest()
            CROSS_ORIGIN_HEADERS.forEach { header ->
                val redirectedValue = redirectedRequest.getHeader(header)
                assertNotEquals("Expected inherited $header value to be stripped", "sensitive", redirectedValue)
                if (header != "Connection") {
                    assertEquals("Expected $header to be stripped", null, redirectedValue)
                }
            }
        } finally {
            origin.shutdown()
            target.shutdown()
        }
    }

    @Test
    fun `same-origin redirect preserves credentials cookies and validators`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        try {
            val requestBuilder = Request.Builder().url(server.url("/start"))
            CROSS_ORIGIN_HEADERS.forEach { header -> requestBuilder.header(header, "same-origin") }

            client().newCall(requestBuilder.build()).execute().use { assertEquals(200, it.code) }

            server.takeRequest()
            val redirectedRequest = server.takeRequest()
            CROSS_ORIGIN_HEADERS.forEach { header ->
                assertEquals("Expected $header to be preserved", "same-origin", redirectedRequest.getHeader(header))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `scheme change is an origin change even with identical host and port`() {
        assertEquals(
            false,
            hasSameOrigin(
                "http://example.com:8443/path".toHttpUrl(),
                "https://example.com:8443/path".toHttpUrl()
            )
        )
    }

    @Test
    fun `rejects credentialed redirect target before following it`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "https://user:secret@example.com/final")
        )
        server.start()
        try {
            val productionPolicyClient = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(SafeRedirectInterceptor())
                .build()
            assertThrows(IOException::class.java) {
                productionPolicyClient.newCall(
                    Request.Builder().url(server.url("/start")).build()
                ).execute()
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `rejects an actual HTTPS to HTTP downgrade before the second request`() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "http://example.com/final")
        )
        server.start()
        try {
            val secureClient = OkHttpClient.Builder()
                .sslSocketFactory(
                    clientCertificates.sslSocketFactory(),
                    clientCertificates.trustManager
                )
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(SafeRedirectInterceptor(isAllowedUrl = { true }))
                .build()
            val error = assertThrows(IOException::class.java) {
                secureClient.newCall(Request.Builder().url(server.url("/start")).build()).execute()
            }
            assertEquals("Blocked unsafe redirect", error.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `local policy rejects redirects to another host`() {
        val origin = MockWebServer()
        origin.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "http://127.0.0.2/final")
        )
        origin.start()
        try {
            val localClient = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(
                    SafeRedirectInterceptor(
                        isAllowedUrl = { true },
                        allowOriginChange = false
                    )
                )
                .build()

            assertThrows(IOException::class.java) {
                localClient.newCall(Request.Builder().url(origin.url("/start")).build()).execute()
            }
            assertEquals(1, origin.requestCount)
        } finally {
            origin.shutdown()
        }
    }

    @Test
    fun `local policy rejects redirects to another port on the same host`() {
        val origin = MockWebServer()
        val target = MockWebServer()
        origin.start()
        target.start()
        origin.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", target.url("/final"))
        )
        try {
            val localClient = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(
                    SafeRedirectInterceptor(
                        isAllowedUrl = { true },
                        allowOriginChange = false
                    )
                )
                .build()

            assertThrows(IOException::class.java) {
                localClient.newCall(Request.Builder().url(origin.url("/start")).build()).execute()
            }
            assertEquals(1, origin.requestCount)
            assertEquals(0, target.requestCount)
        } finally {
            origin.shutdown()
            target.shutdown()
        }
    }

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(SafeRedirectInterceptor(isAllowedUrl = { true }))
        .build()

    private companion object {
        val CROSS_ORIGIN_HEADERS =
            listOf(
                "Authorization",
                "Proxy-Authorization",
                "Connection",
                "Proxy-Connection",
                "Cookie",
                "Cookie2",
                "If-Match",
                "If-Modified-Since",
                "If-None-Match",
                "If-Range",
                "If-Unmodified-Since"
            )
    }
}
