package com.example.pocastcloni.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUpdateModeTest {
    @Test
    fun alwaysFull_requiresForceFullRefresh() {
        assertTrue(FeedUpdateMode.ALWAYS_FULL.requiresForceFullRefresh())
    }

    @Test
    fun smartStream_doesNotRequireForceFullRefresh() {
        assertFalse(FeedUpdateMode.SMART_STREAM.requiresForceFullRefresh())
    }
}
