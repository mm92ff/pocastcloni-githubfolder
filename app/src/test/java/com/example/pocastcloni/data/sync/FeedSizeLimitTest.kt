package com.example.pocastcloni.data.sync

import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.IOException

class FeedSizeLimitTest {
    @Test
    fun `rejects declared feed larger than maximum`() {
        rejectOversizedFeed(Constants.SecurityLimits.MAX_FEED_BYTES)
        assertThrows(IOException::class.java) {
            rejectOversizedFeed(Constants.SecurityLimits.MAX_FEED_BYTES + 1)
        }
    }

    @Test
    fun `unknown feed length is checked by limited stream`() {
        rejectOversizedFeed(-1)
    }

    @Test
    fun `smart parser limit is always effective and bounded`() {
        assertEquals(Constants.SecurityLimits.MAX_FEED_ITEMS, effectiveFeedParserLimit(0))
        assertEquals(Constants.SecurityLimits.MAX_FEED_ITEMS, effectiveFeedParserLimit(-1))
        assertEquals(Constants.SecurityLimits.MAX_FEED_ITEMS, effectiveFeedParserLimit(Int.MAX_VALUE))
        assertEquals(
            Constants.SecurityLimits.MAX_FEED_ITEMS,
            effectiveFeedParserLimit(Constants.SecurityLimits.MAX_FEED_ITEMS + 1)
        )
        assertEquals(3, effectiveFeedParserLimit(3))
    }
}
