package com.example.pocastcloni.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

object RetryingDataFlow {
    const val DEFAULT_MAX_RETRIES = 2L
    const val DEFAULT_INITIAL_DELAY_MS = 100L
    const val DEFAULT_MAX_DELAY_MS = 1_000L

    fun <T> bounded(
        upstream: Flow<T>,
        maxRetries: Long = DEFAULT_MAX_RETRIES,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean = { true }
    ): Flow<T> {
        require(maxRetries >= 0L)
        require(initialDelayMs >= 0L)
        require(maxDelayMs >= initialDelayMs)

        return upstream.retryWhen { cause, attempt ->
            if (
                cause is CancellationException ||
                attempt >= maxRetries ||
                !shouldRetry(cause)
            ) {
                return@retryWhen false
            }

            val multiplier = 1L shl attempt.coerceAtMost(MAX_SHIFT).toInt()
            delay((initialDelayMs * multiplier).coerceAtMost(maxDelayMs))
            true
        }
    }

    private const val MAX_SHIFT = 30L
}
