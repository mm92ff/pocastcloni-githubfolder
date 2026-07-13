package com.example.pocastcloni.data.remote

import com.example.pocastcloni.testutil.DocDeclFeatureKXmlParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RssSmartSyncParserSecurityTest {
    @Test(timeout = 1_000)
    fun `recursive internal entity fixture is rejected before expansion`() = runTest {
        val xml =
            """<!DOCTYPE rss [
                <!ENTITY a "1234567890">
                <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
                <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
                ]><rss><channel><title>&c;</title></channel></rss>
            """.trimIndent()

        val error = parseFailure(xml, limits(maxExpandedChars = 32))

        assertTrue(error.message.orEmpty().contains("document declarations"))
    }

    @Test
    fun `external document declaration is rejected`() = runTest {
        val xml =
            """<!DOCTYPE rss SYSTEM "https://private.example/feed.dtd">
                <rss><channel><title>Podcast</title></channel></rss>
            """.trimIndent()

        val error = parseFailure(xml)

        assertTrue(error.message.orEmpty().contains("document declarations"))
    }

    @Test
    fun `custom entity reference without a declaration is rejected`() = runTest {
        parseFailure("<rss><channel><title>&private;</title></channel></rss>")
    }

    @Test
    fun `depth token and expanded text limits are independently enforced`() = runTest {
        val depthError = parseFailure(
            "<rss><channel><wrapper><nested/></wrapper></channel></rss>",
            limits(maxDepth = 3)
        )
        val tokenError = parseFailure(
            "<rss><channel><title>Podcast</title></channel></rss>",
            limits(maxTokens = 4)
        )
        val textError = parseFailure(
            "<rss><channel><title>123456789</title></channel></rss>",
            limits(maxExpandedChars = 8)
        )

        assertTrue(depthError.message.orEmpty().contains("depth limit"))
        assertTrue(tokenError.message.orEmpty().contains("token limit"))
        assertTrue(textError.message.orEmpty().contains("expanded text limit"))
    }

    @Test
    fun `predefined and numeric entities retain their text`() = runTest {
        val result = parse(
            "<rss><channel><title>Rock &amp; Roll &#65;&#x42; &lt;live&gt;</title></channel></rss>"
        )

        assertEquals("Rock & Roll AB <live>", result.channel.title)
    }

    @Test
    fun `existing item and CDATA behavior remains intact`() = runTest {
        val result = parse(
            """<rss><channel>
                <title>Podcast</title>
                <description><![CDATA[Feed <description>]]></description>
                <item>
                    <title>Episode &amp; One</title>
                    <guid>episode-1</guid>
                    <enclosure url="https://example.com/episode.mp3" type="audio/mpeg" length="42"/>
                </item>
            </channel></rss>""".trimIndent()
        )

        assertEquals("Feed <description>", result.channel.description)
        assertEquals("Episode & One", result.newItems.single().title)
        assertEquals(42L, result.newItems.single().enclosure?.length)
    }

    private suspend fun parseFailure(
        xml: String,
        limits: RssSmartSyncParser.Limits = limits()
    ): Throwable = requireNotNull(runCatching { parse(xml, limits) }.exceptionOrNull())

    private suspend fun parse(
        xml: String,
        limits: RssSmartSyncParser.Limits = limits()
    ): RssSmartSyncParser.ParseResult =
        RssSmartSyncParser(
            limits = limits,
            parserFactory = ::DocDeclFeatureKXmlParser
        ).parse(
            inputStream = ByteArrayInputStream(xml.toByteArray()),
            podcastUrl = "https://example.com/feed.xml",
            limit = 10,
            isFullSync = true
        )

    private fun limits(
        maxDepth: Int = 64,
        maxTokens: Int = 1_000,
        maxExpandedChars: Long = 10_000
    ) = RssSmartSyncParser.Limits(maxDepth, maxTokens, maxExpandedChars)

}
