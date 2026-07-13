package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.Constants
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorResponseBodyLimitInterceptorTest {
    @Test
    fun `non-success body is replaced with a bounded body and original is closed`() {
        val original = TrackingResponseBody(ByteArray(128 * 1024) { 7 })
        val client = syntheticClient(code = 500, body = original)

        client.newCall(Request.Builder().url("https://example.com/feed.xml").build()).execute().use { response ->
            assertEquals(Constants.SecurityLimits.MAX_RSS_ERROR_BODY_BYTES, response.body?.bytes()?.size?.toLong())
            assertEquals(Constants.SecurityLimits.MAX_RSS_ERROR_BODY_BYTES.toString(), response.header("Content-Length"))
        }
        assertTrue(original.closed)
    }

    @Test
    fun `successful streaming body is returned unchanged`() {
        val original = TrackingResponseBody(ByteArray(128 * 1024) { 3 })
        val client = syntheticClient(code = 200, body = original)

        val response = client.newCall(Request.Builder().url("https://example.com/feed.xml").build()).execute()

        assertSame(original, response.body)
        assertFalse(original.closed)
        assertEquals(128 * 1024L, response.body?.contentLength())
        response.close()
        assertTrue(original.closed)
    }

    private fun syntheticClient(
        code: Int,
        body: ResponseBody
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ErrorResponseBodyLimitInterceptor())
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Synthetic")
                    .body(body)
                    .build()
            }
            .build()

    private class TrackingResponseBody(bytes: ByteArray) : ResponseBody() {
        var closed = false
            private set

        private val trackingSource =
            object : ForwardingSource(Buffer().write(bytes)) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }.buffer()

        private val length = bytes.size.toLong()

        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = length

        override fun source(): BufferedSource = trackingSource
    }
}
