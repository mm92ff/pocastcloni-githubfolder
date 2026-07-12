package com.example.pocastcloni.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class RssSmartSyncParserLimitAndroidTest {
    @Test
    fun parserNeverReturnsMoreThanTheConfiguredFeedItemLimit() = runBlocking {
        val exactLimit = parseFeed(Constants.SecurityLimits.MAX_FEED_ITEMS)
        val oneOverLimit = parseFeed(Constants.SecurityLimits.MAX_FEED_ITEMS + 1)

        assertEquals(Constants.SecurityLimits.MAX_FEED_ITEMS, exactLimit.newItems.size)
        assertEquals(Constants.SecurityLimits.MAX_FEED_ITEMS, oneOverLimit.newItems.size)
    }

    private suspend fun parseFeed(itemCount: Int): RssSmartSyncParser.ParseResult {
        val xml = buildString {
            append("<rss><channel><title>Test</title>")
            repeat(itemCount) { index ->
                append("<item><title>Episode ")
                append(index)
                append("</title><guid>")
                append(index)
                append("</guid><enclosure url=\"https://example.com/")
                append(index)
                append(".mp3\" type=\"audio/mpeg\" length=\"1\"/></item>")
            }
            append("</channel></rss>")
        }
        return RssSmartSyncParser().parse(
            inputStream = ByteArrayInputStream(xml.toByteArray()),
            podcastUrl = "https://example.com/feed.xml",
            limit = Constants.SecurityLimits.MAX_FEED_ITEMS,
            isFullSync = true
        )
    }
}
