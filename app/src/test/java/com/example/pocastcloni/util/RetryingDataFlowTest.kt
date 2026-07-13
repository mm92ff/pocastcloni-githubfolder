package com.example.pocastcloni.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class RetryingDataFlowTest {
    @Test
    fun `transient failure resubscribes and later values remain observable`() = runTest {
        var subscriptions = 0
        val values =
            RetryingDataFlow.bounded(
                upstream = flow {
                    subscriptions += 1
                    if (subscriptions == 1) throw IOException("temporary")
                    emit(1)
                    emit(2)
                },
                initialDelayMs = 1L,
                maxDelayMs = 1L
            ).toList()

        assertEquals(listOf(1, 2), values)
        assertEquals(2, subscriptions)
    }

    @Test
    fun `retry count is bounded`() = runTest {
        var subscriptions = 0
        try {
            RetryingDataFlow.bounded(
                upstream = flow<Int> {
                    subscriptions += 1
                    throw IOException("still broken")
                },
                maxRetries = 2L,
                initialDelayMs = 1L,
                maxDelayMs = 1L
            ).toList()
            fail("Expected the final upstream failure")
        } catch (_: IOException) {
            assertEquals(3, subscriptions)
        }
    }

    @Test
    fun `cancellation is never retried`() = runTest {
        var subscriptions = 0
        try {
            RetryingDataFlow.bounded(
                upstream = flow<Int> {
                    subscriptions += 1
                    throw CancellationException("cancelled")
                },
                initialDelayMs = 1L,
                maxDelayMs = 1L
            ).toList()
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, subscriptions)
        }
    }
}
