package com.example.pocastcloni.data.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DownloadRetryPolicyTest {
    @Test
    fun `retries transient http failures before max attempts`() {
        assertTrue(shouldRetryDownloadFailure(DownloadHttpException(408), runAttemptCount = 0, maxRetryAttempts = 3))
        assertTrue(shouldRetryDownloadFailure(DownloadHttpException(429), runAttemptCount = 1, maxRetryAttempts = 3))
        assertTrue(shouldRetryDownloadFailure(DownloadHttpException(500), runAttemptCount = 2, maxRetryAttempts = 3))
    }

    @Test
    fun `does not retry permanent http client failures`() {
        assertFalse(shouldRetryDownloadFailure(DownloadHttpException(400), runAttemptCount = 0, maxRetryAttempts = 3))
        assertFalse(shouldRetryDownloadFailure(DownloadHttpException(404), runAttemptCount = 0, maxRetryAttempts = 3))
    }

    @Test
    fun `retries io failures only until max attempts`() {
        assertTrue(shouldRetryDownloadFailure(IOException("timeout"), runAttemptCount = 2, maxRetryAttempts = 3))
        assertFalse(shouldRetryDownloadFailure(IOException("timeout"), runAttemptCount = 3, maxRetryAttempts = 3))
    }

    @Test
    fun `does not retry non io failures`() {
        assertFalse(shouldRetryDownloadFailure(IllegalStateException("bad state"), runAttemptCount = 0, maxRetryAttempts = 3))
    }
}
