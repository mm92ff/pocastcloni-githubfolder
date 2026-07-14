package com.example.pocastcloni.data.sync

import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertThrows
import org.junit.Test
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
}
