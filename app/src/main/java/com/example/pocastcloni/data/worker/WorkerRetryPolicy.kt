package com.example.pocastcloni.data.worker

internal object WorkerRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun canRetry(runAttemptCount: Int): Boolean = runAttemptCount + 1 < MAX_ATTEMPTS
}
