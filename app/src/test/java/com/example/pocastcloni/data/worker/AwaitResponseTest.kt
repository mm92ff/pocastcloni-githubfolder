package com.example.pocastcloni.data.worker

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class AwaitResponseTest {
    @Test
    fun `response arriving after cancellation is closed exactly once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val call = CallbackCall()
        val response = countingResponse()
        val job = launch(dispatcher) { call.awaitResponse() }
        testScheduler.runCurrent()

        job.cancel()
        testScheduler.runCurrent()
        call.respond(response.response)

        assertTrue(call.cancelled)
        assertEquals(1, response.closeCount.get())
    }

    @Test
    fun `prompt cancellation during response handoff closes response exactly once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val call = CallbackCall()
        val response = countingResponse()
        var received = false
        val job = launch(dispatcher) {
            call.awaitResponse()
            received = true
        }
        testScheduler.runCurrent()

        call.respond(response.response)
        assertEquals(0, response.closeCount.get())
        job.cancel()
        testScheduler.runCurrent()

        assertTrue(call.cancelled)
        assertFalse(received)
        assertEquals(1, response.closeCount.get())
    }

    @Test
    fun `normal response handoff leaves closing to caller`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val call = CallbackCall()
        val response = countingResponse()
        var delivered: Response? = null
        val job = launch(dispatcher) { delivered = call.awaitResponse() }
        testScheduler.runCurrent()

        call.respond(response.response)
        assertEquals(0, response.closeCount.get())
        testScheduler.runCurrent()
        job.join()

        assertFalse(call.cancelled)
        assertEquals(response.response, delivered)
        assertEquals(0, response.closeCount.get())
        requireNotNull(delivered).close()
        assertEquals(1, response.closeCount.get())
    }

    private fun countingResponse(): CountingResponse {
        val closeCount = AtomicInteger()
        val source =
            object : ForwardingSource(Buffer().writeUtf8("audio")) {
                override fun close() {
                    closeCount.incrementAndGet()
                    super.close()
                }
            }.buffer()
        val body =
            object : ResponseBody() {
                override fun contentType(): MediaType? = null

                override fun contentLength(): Long = 5L

                override fun source(): BufferedSource = source
            }
        val response =
            Response.Builder()
                .request(REQUEST)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        return CountingResponse(response, closeCount)
    }

    private data class CountingResponse(
        val response: Response,
        val closeCount: AtomicInteger
    )

    private class CallbackCall : Call {
        private var callback: Callback? = null
        var cancelled = false
            private set

        override fun request(): Request = REQUEST

        override fun execute(): Response = throw UnsupportedOperationException()

        override fun enqueue(responseCallback: Callback) {
            check(callback == null)
            callback = responseCallback
        }

        override fun cancel() {
            cancelled = true
        }

        override fun isExecuted(): Boolean = callback != null

        override fun isCanceled(): Boolean = cancelled

        override fun timeout(): Timeout = Timeout.NONE

        public override fun clone(): Call = CallbackCall()

        fun respond(response: Response) {
            requireNotNull(callback).onResponse(this, response)
        }
    }

    private companion object {
        val REQUEST: Request = Request.Builder().url("https://example.com/audio.mp3").build()
    }
}
