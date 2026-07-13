package com.example.pocastcloni.data.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerRetryPolicyTest {
    @Test
    fun `retry policy caps feed and cleanup work at three attempts`() {
        assertTrue(WorkerRetryPolicy.canRetry(0))
        assertTrue(WorkerRetryPolicy.canRetry(1))
        assertFalse(WorkerRetryPolicy.canRetry(2))
        assertFalse(WorkerRetryPolicy.canRetry(3))
    }
}
