package com.example.pocastcloni.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test(timeout = 2_000)
    fun androidParserRejectsRecursiveDocumentDeclarationsBeforeExpansion() = runBlocking {
        val xml =
            """<!DOCTYPE rss [
                <!ENTITY a "1234567890">
                <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
                <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
                ]><rss><channel><title>&c;</title></channel></rss>
            """.trimIndent()

        val error = requireNotNull(
            runCatching {
                parseXml(
                    xml,
                    RssSmartSyncParser.Limits(maxDepth = 64, maxTokens = 1_000, maxExpandedChars = 32)
                )
            }.exceptionOrNull()
        )

        assertTrue(error.message.orEmpty().contains("document declarations"))
    }

    @Test
    fun androidParserEnforcesDefaultDepthLimit() = runBlocking {
        val xml = buildString {
            append("<rss><channel>")
            repeat(Constants.SecurityLimits.MAX_RSS_XML_DEPTH) { append("<nested>") }
            repeat(Constants.SecurityLimits.MAX_RSS_XML_DEPTH) { append("</nested>") }
            append("</channel></rss>")
        }

        val error = requireNotNull(runCatching { parseXml(xml) }.exceptionOrNull())

        assertTrue(error.message.orEmpty().contains("depth limit"))
    }

    @Test
    fun androidParserPreservesPredefinedAndNumericReferences() = runBlocking {
        val result = parseXml(
            "<rss><channel><title>Rock &amp; Roll &#65;&#x42;</title></channel></rss>"
        )

        assertEquals("Rock & Roll AB", result.channel.title)
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

    private suspend fun parseXml(
        xml: String,
        limits: RssSmartSyncParser.Limits = RssSmartSyncParser.Limits()
    ): RssSmartSyncParser.ParseResult =
        RssSmartSyncParser(limits = limits).parse(
            inputStream = ByteArrayInputStream(xml.toByteArray()),
            podcastUrl = "https://example.com/feed.xml",
            limit = Constants.SecurityLimits.MAX_FEED_ITEMS,
            isFullSync = true
        )
}
