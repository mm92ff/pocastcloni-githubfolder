package com.example.pocastcloni.data.remote

import com.example.pocastcloni.testutil.DocDeclFeatureKXmlParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class RssSmartSyncParserTest {
    @Test
    fun `known GUID before a candidate is skipped without terminating the scan`() = runTest {
        val result = parse(
            items = listOf(item("known"), item("candidate")),
            latestKnownGuid = "known"
        )

        assertEquals(listOf("candidate"), result.newItems.map { it.guid })
    }

    @Test
    fun `known GUID after candidates is skipped after returning the candidates`() = runTest {
        val result = parse(
            items = listOf(item("candidate-1"), item("candidate-2"), item("known")),
            latestKnownGuid = "known"
        )

        assertEquals(listOf("candidate-1", "candidate-2"), result.newItems.map { it.guid })
    }

    @Test
    fun `mixed ordering and unusable publication dates do not terminate parsing`() = runTest {
        val result = parse(
            items = listOf(
                item("known", "Tue, 14 Jul 2026 10:00:00 GMT"),
                item("missing-date"),
                item("invalid-date", "not-a-date"),
                item("known"),
                item("valid-date", "Mon, 13 Jul 2026 10:00:00 GMT")
            ),
            latestKnownGuid = "known"
        )

        assertEquals(
            listOf("missing-date", "invalid-date", "valid-date"),
            result.newItems.map { it.guid }
        )
    }

    @Test
    fun `item limit counts encountered known items rather than returned candidates`() = runTest {
        val result = parse(
            items = listOf(item("known"), item("candidate-1"), item("candidate-2"), item("candidate-3")),
            latestKnownGuid = "known",
            limit = 3
        )

        assertEquals(listOf("candidate-1", "candidate-2"), result.newItems.map { it.guid })
    }

    private suspend fun parse(
        items: List<String>,
        latestKnownGuid: String,
        limit: Int = 10
    ): RssSmartSyncParser.ParseResult {
        val xml = "<rss><channel><title>Podcast</title>${items.joinToString("")}</channel></rss>"
        return RssSmartSyncParser(parserFactory = ::DocDeclFeatureKXmlParser).parse(
            inputStream = ByteArrayInputStream(xml.toByteArray()),
            podcastUrl = "https://example.com/feed.xml",
            limit = limit,
            latestKnownGuid = latestKnownGuid
        )
    }

    private fun item(
        guid: String,
        pubDate: String? = null
    ): String = buildString {
        append("<item><title>")
        append(guid)
        append("</title><guid>")
        append(guid)
        append("</guid>")
        if (pubDate != null) {
            append("<pubDate>")
            append(pubDate)
            append("</pubDate>")
        }
        append("</item>")
    }
}
