package com.example.pocastcloni.data.remote

import android.util.Xml
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.yield
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Streams a bounded prefix of RSS items in document order, including unordered feeds.
 * A matching known GUID is skipped without ending the scan; publication dates are payload only.
 */
class RssSmartSyncParser(
    private val limits: Limits = Limits(),
    private val parserFactory: () -> XmlPullParser = { Xml.newPullParser() }
) {
    data class Limits(
        val maxDepth: Int = Constants.SecurityLimits.MAX_RSS_XML_DEPTH,
        val maxTokens: Int = Constants.SecurityLimits.MAX_RSS_XML_TOKENS,
        val maxExpandedChars: Long = Constants.SecurityLimits.MAX_RSS_EXPANDED_CHARS
    ) {
        init {
            require(maxDepth > 0)
            require(maxTokens > 0)
            require(maxExpandedChars > 0)
        }
    }

    data class ParseResult(
        val channel: RssChannel,
        val newItems: List<RssItem>
    )

    @Suppress("UNUSED_PARAMETER")
    suspend fun parse(
        inputStream: InputStream,
        podcastUrl: String,
        limit: Int,
        isFullSync: Boolean = false,
        latestKnownGuid: String? = null
    ): ParseResult {
        val parser = parserFactory()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(inputStream, null)
        val events = LimitedXmlEventReader(parser, limits)

        var title: String? = null
        var description: String? = null
        var image: RssImage? = null
        var itunesImage: RssImage? = null
        val newItems = mutableListOf<RssItem>()
        var encounteredItemCount = 0

        val effectiveLatestKnownGuid = if (isFullSync) null else latestKnownGuid

        while (events.eventType != XmlPullParser.END_DOCUMENT) {
            val name = events.name

            when (events.eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == Constants.Parsing.ITEM) {
                        if (limit > 0 && encounteredItemCount >= limit) {
                            return buildResult(title, description, image, itunesImage, newItems)
                        }
                        encounteredItemCount++

                        parseItem(events, effectiveLatestKnownGuid)?.let(newItems::add)
                        if (limit > 0 && encounteredItemCount >= limit) {
                            return buildResult(title, description, image, itunesImage, newItems)
                        }
                    } else {
                        when (name) {
                            Constants.Parsing.TITLE -> title = readText(events)
                            Constants.Parsing.DESCRIPTION -> description = readText(events)
                            Constants.Parsing.IMAGE -> image = RssImage(urlFromTag = parseImageUrl(events))
                            Constants.Parsing.ITUNES_IMAGE -> {
                                val href = events.getAttributeValue(null, Constants.Parsing.HREF)
                                itunesImage = RssImage(urlFromAttribute = href)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (name == Constants.Parsing.CHANNEL) break
                }
            }
            events.nextToken()
            yield()
        }
        return buildResult(title, description, image, itunesImage, newItems)
    }

    private fun parseImageUrl(events: LimitedXmlEventReader): String? {
        var imageUrl: String? = null
        while (true) {
            when (events.nextToken()) {
                XmlPullParser.START_TAG -> {
                    if (events.name == Constants.Parsing.URL) {
                        imageUrl = readText(events)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (events.name == Constants.Parsing.IMAGE) return imageUrl
                }

                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException("Unexpected end of RSS image")
            }
        }
    }

    private fun parseItem(
        events: LimitedXmlEventReader,
        latestKnownGuid: String?
    ): RssItem? {
        val fields = RssItemFields()
        while (true) {
            when (events.nextToken()) {
                XmlPullParser.START_TAG ->
                    if (readItemField(events, fields, latestKnownGuid)) return null
                XmlPullParser.END_TAG ->
                    if (events.name == Constants.Parsing.ITEM) return fields.toRssItem()
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException("Unexpected end of RSS item")
            }
        }
    }

    private fun readItemField(
        events: LimitedXmlEventReader,
        fields: RssItemFields,
        latestKnownGuid: String?
    ): Boolean {
        when (events.name) {
            Constants.Parsing.TITLE -> fields.title = readText(events)
            Constants.Parsing.DESCRIPTION -> fields.description = readText(events)
            Constants.Parsing.LINK -> fields.link = readText(events)
            Constants.Parsing.GUID -> {
                fields.guid = readText(events)
                if (latestKnownGuid != null && fields.guid == latestKnownGuid) {
                    skipCurrentItem(events)
                    return true
                }
            }
            Constants.Parsing.PUB_DATE -> fields.pubDate = readText(events)
            Constants.Parsing.ITUNES_DURATION -> fields.itunesDuration = readText(events)
            Constants.Parsing.ENCLOSURE -> fields.enclosure = readEnclosure(events)
        }
        return false
    }

    private fun readEnclosure(events: LimitedXmlEventReader): RssEnclosure {
        val url = events.getAttributeValue(null, Constants.Parsing.URL)
        val length =
            events.getAttributeValue(null, Constants.Parsing.LENGTH)?.toLongOrNull()
                ?: Constants.Parsing.DEFAULT_ENCLOSURE_LENGTH
        val type = events.getAttributeValue(null, Constants.Parsing.TYPE)
        return RssEnclosure(url, type, length)
    }

    private fun skipCurrentItem(events: LimitedXmlEventReader) {
        while (
            events.eventType != XmlPullParser.END_TAG ||
            events.name != Constants.Parsing.ITEM
        ) {
            events.nextToken()
        }
    }

    private fun readText(events: LimitedXmlEventReader): String {
        val result = StringBuilder()
        while (true) {
            when (events.nextToken()) {
                XmlPullParser.TEXT,
                XmlPullParser.CDSECT,
                XmlPullParser.ENTITY_REF,
                XmlPullParser.IGNORABLE_WHITESPACE -> {
                    val text = events.eventText()
                    require(result.length.toLong() + text.length <= Constants.SecurityLimits.MAX_DESCRIPTION_CHARS) {
                        "RSS text field is too long"
                    }
                    result.append(text)
                }

                XmlPullParser.END_TAG -> return result.toString()
                XmlPullParser.START_TAG ->
                    throw IllegalArgumentException(
                        "Nested XML is not allowed in RSS text fields"
                    )
                XmlPullParser.END_DOCUMENT -> throw IllegalArgumentException("Unexpected end of RSS text field")
            }
        }
    }

    private fun buildResult(
        title: String?,
        description: String?,
        image: RssImage?,
        itunesImage: RssImage?,
        newItems: List<RssItem>
    ): ParseResult {
        val channel =
            RssChannel(
                title = title,
                description = description,
                image = image,
                itunesImage = itunesImage
            )
        return ParseResult(channel, newItems)
    }
}

private data class RssItemFields(
    var title: String? = null,
    var description: String? = null,
    var link: String? = null,
    var guid: String? = null,
    var pubDate: String? = null,
    var itunesDuration: String? = null,
    var enclosure: RssEnclosure? = null
) {
    fun toRssItem() =
        RssItem(
            title = title,
            description = description,
            link = link,
            guid = guid,
            pubDate = pubDate,
            enclosure = enclosure,
            itunesDuration = itunesDuration
        )
}

private class LimitedXmlEventReader(
    private val parser: XmlPullParser,
    private val limits: RssSmartSyncParser.Limits
) {
    private var depth = 0
    private var tokenCount = 0
    private var expandedChars = 0L

    val eventType: Int
        get() = parser.eventType

    val name: String?
        get() = parser.name

    fun getAttributeValue(
        namespace: String?,
        name: String
    ): String? = parser.getAttributeValue(namespace, name)

    fun nextToken(): Int {
        val next = parser.nextToken()
        tokenCount++
        require(tokenCount <= limits.maxTokens) { "RSS XML token limit exceeded" }

        when (next) {
            XmlPullParser.DOCDECL -> throw IllegalArgumentException("RSS XML document declarations are not allowed")
            XmlPullParser.START_TAG -> {
                depth++
                require(depth <= limits.maxDepth) { "RSS XML depth limit exceeded" }
            }
            XmlPullParser.END_TAG -> {
                depth--
                require(depth >= 0) { "RSS XML contains an invalid closing tag" }
            }
            XmlPullParser.TEXT,
            XmlPullParser.CDSECT,
            XmlPullParser.IGNORABLE_WHITESPACE -> countExpandedChars(parser.text.orEmpty())
            XmlPullParser.ENTITY_REF -> countExpandedChars(entityReplacement())
        }
        return next
    }

    fun eventText(): String =
        if (eventType == XmlPullParser.ENTITY_REF) entityReplacement() else parser.text.orEmpty()

    private fun countExpandedChars(text: String) {
        expandedChars += text.length
        require(expandedChars <= limits.maxExpandedChars) { "RSS XML expanded text limit exceeded" }
    }

    private fun entityReplacement(): String {
        val entityName = parser.name.orEmpty()
        PREDEFINED_ENTITIES[entityName]?.let { return it }
        if (entityName.startsWith("#")) return decodeNumericEntity(entityName)
        throw IllegalArgumentException("Custom RSS XML entities are not allowed")
    }

    private fun decodeNumericEntity(entityName: String): String {
        val codePoint =
            if (entityName.startsWith("#x", ignoreCase = true)) {
                entityName.substring(2).toIntOrNull(HEXADECIMAL_RADIX)
            } else {
                entityName.substring(1).toIntOrNull()
            }
        require(codePoint != null && isValidXmlCodePoint(codePoint)) { "Invalid numeric RSS XML entity" }
        return Character.toChars(codePoint).concatToString()
    }

    private fun isValidXmlCodePoint(value: Int): Boolean =
        value == XML_TAB_CODE_POINT ||
            value == XML_LINE_FEED_CODE_POINT ||
            value == XML_CARRIAGE_RETURN_CODE_POINT ||
            value in XML_BASIC_TEXT_START..XML_BASIC_TEXT_END ||
            value in XML_PRIVATE_USE_START..XML_PRIVATE_USE_END ||
            value in XML_SUPPLEMENTARY_START..XML_SUPPLEMENTARY_END

    private companion object {
        const val HEXADECIMAL_RADIX = 16
        const val XML_TAB_CODE_POINT = 0x9
        const val XML_LINE_FEED_CODE_POINT = 0xA
        const val XML_CARRIAGE_RETURN_CODE_POINT = 0xD
        const val XML_BASIC_TEXT_START = 0x20
        const val XML_BASIC_TEXT_END = 0xD7FF
        const val XML_PRIVATE_USE_START = 0xE000
        const val XML_PRIVATE_USE_END = 0xFFFD
        const val XML_SUPPLEMENTARY_START = 0x10000
        const val XML_SUPPLEMENTARY_END = 0x10FFFF

        val PREDEFINED_ENTITIES =
            mapOf(
                "amp" to "&",
                "lt" to "<",
                "gt" to ">",
                "apos" to "'",
                "quot" to "\""
            )
    }
}
