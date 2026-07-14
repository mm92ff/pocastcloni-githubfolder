package com.example.pocastcloni.playback.infrastructure

import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ForwardingListenableFuture
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `synchronous construction failure is cleaned and retried after backoff`() = runTest {
        val controller = mockk<MediaController>(relaxed = true)
        val failure = IllegalStateException("synchronous build failure")
        var buildCount = 0
        val subject =
            connection {
                buildCount++
                if (buildCount == 1) throw failure
                Futures.immediateFuture(controller)
            }
        val result = async { subject.connect() }
        runCurrent()

        assertEquals(1, buildCount)
        assertFalse(subject.hasConnectingAttempt)
        assertFalse(result.isCompleted)

        advanceTimeBy(RETRY_DELAY_MS)
        runCurrent()

        assertSame(controller, result.await())
        assertEquals(2, buildCount)
        assertFalse(subject.hasConnectingAttempt)
    }

    @Test
    fun `asynchronous future failure uses the same cleanup and retry delay`() = runTest {
        val controller = mockk<MediaController>(relaxed = true)
        val failure = IllegalArgumentException("asynchronous build failure")
        val failedFuture = SettableFuture.create<MediaController>().apply { setException(failure) }
        var buildCount = 0
        val subject =
            connection {
                buildCount++
                if (buildCount == 1) failedFuture else Futures.immediateFuture(controller)
            }
        val result = async { subject.connect() }
        runCurrent()

        assertEquals(1, buildCount)
        assertFalse(subject.hasConnectingAttempt)
        assertFalse(result.isCompleted)

        advanceTimeBy(RETRY_DELAY_MS)
        runCurrent()

        assertSame(controller, result.await())
        assertEquals(2, buildCount)
    }

    @Test
    fun `release during retry backoff stops old loop and fresh connect reuses connection`() = runTest {
        val controller = mockk<MediaController>(relaxed = true)
        val failedFuture =
            SettableFuture.create<MediaController>().apply {
                setException(IllegalStateException("retryable"))
            }
        var buildCount = 0
        val subject =
            connection(maxAttempts = 3) {
                buildCount++
                if (buildCount == 1) failedFuture else Futures.immediateFuture(controller)
            }
        val oldConnect = async { subject.connect() }
        runCurrent()
        assertEquals(1, buildCount)

        subject.release()
        advanceTimeBy(RETRY_DELAY_MS)
        runCurrent()

        assertNull(oldConnect.await())
        assertEquals(1, buildCount)
        assertFalse(subject.hasConnectingAttempt)
        assertSame(controller, subject.connect())
        assertEquals(2, buildCount)
    }

    @Test
    fun `release during in flight build releases late result and permits fresh reuse`() = runTest {
        val staleController = mockk<MediaController>(relaxed = true)
        val freshController = mockk<MediaController>(relaxed = true)
        val pendingFuture = NonCancellingFuture<MediaController>()
        var buildCount = 0
        val subject =
            connection(maxAttempts = 2) {
                buildCount++
                if (buildCount == 1) pendingFuture else Futures.immediateFuture(freshController)
            }
        val oldConnect = async { subject.connect() }
        runCurrent()
        assertTrue(subject.hasConnectingAttempt)

        subject.release()
        pendingFuture.set(staleController)
        runCurrent()

        assertNull(oldConnect.await())
        assertFalse(subject.hasConnectingAttempt)
        verify(exactly = 1) { staleController.release() }
        assertSame(freshController, subject.connect())
        assertEquals(2, buildCount)
    }

    private fun connection(
        maxAttempts: Int = 2,
        build: () -> com.google.common.util.concurrent.ListenableFuture<MediaController>
    ) =
        MediaControllerConnection(
            controllerFutureFactory = MediaControllerFutureFactory { build() },
            retryDelayMs = RETRY_DELAY_MS,
            maxAttempts = maxAttempts
        )

    private class NonCancellingFuture<T> private constructor(
        private val settable: SettableFuture<T>
    ) : ForwardingListenableFuture.SimpleForwardingListenableFuture<T>(settable) {
        constructor() : this(SettableFuture.create())

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        fun set(value: T) {
            settable.set(value)
        }
    }

    private companion object {
        private const val RETRY_DELAY_MS = 500L
    }
}
