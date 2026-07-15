package com.example.pocastcloni.data.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUpdateForegroundPolicyTest {
    @Test
    fun fullLibraryRefreshUsesForegroundExecution() {
        assertTrue(feedUpdateRequiresForeground(targetFeedUrl = null))
    }

    @Test
    fun singleFeedRetryPreservesForegroundServiceBudget() {
        assertFalse(feedUpdateRequiresForeground(targetFeedUrl = "https://example.com/feed.xml"))
    }
}
