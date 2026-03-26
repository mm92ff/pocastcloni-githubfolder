/**
 * Implements a memory-efficient, streaming RSS parser using XmlPullParser.
 * Its primary purpose is the "Smart Sync" operation, where parsing can be
 * stopped as soon as the last known episode is found.
 */
package com.example.pocastcloni.data.remote

import android.util.Xml
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.yield
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

class RssSmartSyncParser {
    data class ParseResult(
        val channel: RssChannel,
        val newItems: List<RssItem>
    )

    private data class ItemParseResult(val item: RssItem?, val isKnown: Boolean)

    @Suppress("UNUSED_PARAMETER")
    suspend fun parse(
        inputStream: InputStream,
        podcastUrl: String,
        limit: Int,
        isFullSync: Boolean = false,
        latestKnownGuid: String? = null
    ): ParseResult {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType

        var title: String? = null
        var description: String? = null
        var image: RssImage? = null
        var itunesImage: RssImage? = null
        val newItems = mutableListOf<RssItem>()

        val effectiveLatestKnownGuid = if (isFullSync) null else latestKnownGuid

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == Constants.Parsing.ITEM) {
                        // Hard cap: always respect limit (when limit > 0), regardless of whether we have a known GUID.
                        if (limit > 0 && newItems.size >= limit) {
                            return buildResult(title, description, image, itunesImage, newItems)
                        }

                        val itemResult = parseItem(parser, effectiveLatestKnownGuid)

                        if (itemResult.isKnown) {
                            return buildResult(title, description, image, itunesImage, newItems)
                        } else if (itemResult.item != null) {
                            newItems.add(itemResult.item)

                            // Cap again after adding, to guarantee newItems.size <= limit
                            if (limit > 0 && newItems.size >= limit) {
                                return buildResult(title, description, image, itunesImage, newItems)
                            }
                        }
                    } else {
                        when (name) {
                            Constants.Parsing.TITLE -> title = readText(parser)
                            Constants.Parsing.DESCRIPTION -> description = readText(parser)
                            Constants.Parsing.IMAGE -> image = RssImage(urlFromTag = parseImageUrl(parser))
                            Constants.Parsing.ITUNES_IMAGE, "itunes:image" -> {
                                val href = parser.getAttributeValue(null, Constants.Parsing.HREF)
                                itunesImage = RssImage(urlFromAttribute = href)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (name == Constants.Parsing.CHANNEL) {
                        break
                    }
                }
            }
            eventType = parser.next()
            yield()
        }
        return buildResult(title, description, image, itunesImage, newItems)
    }

    private fun parseImageUrl(parser: XmlPullParser): String? {
        var imageUrl: String? = null
        var inImageTag = true
        while (inImageTag) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == Constants.Parsing.URL) {
                        imageUrl = readText(parser)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == Constants.Parsing.IMAGE) {
                        inImageTag = false
                    }
                }
            }
        }
        return imageUrl
    }

    private suspend fun parseItem(
        parser: XmlPullParser,
        latestKnownGuid: String?
    ): ItemParseResult {
        var title: String? = null
        var description: String? = null
        var link: String? = null
        var guid: String? = null
        var pubDate: String? = null
        var itunesDuration: String? = null
        var enclosure: RssEnclosure? = null

        var isKnown = false
        var inItem = true

        while (inItem) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        Constants.Parsing.TITLE -> title = readText(parser)
                        Constants.Parsing.DESCRIPTION -> description = readText(parser)
                        Constants.Parsing.LINK -> link = readText(parser)
                        Constants.Parsing.GUID -> {
                            guid = readText(parser)
                            if (latestKnownGuid != null && guid == latestKnownGuid) {
                                isKnown = true
                            }
                        }

                        Constants.Parsing.PUB_DATE -> pubDate = readText(parser)
                        Constants.Parsing.ITUNES_DURATION, "itunes:duration" -> itunesDuration = readText(parser)

                        Constants.Parsing.ENCLOSURE -> {
                            val url = parser.getAttributeValue(null, Constants.Parsing.URL)
                            val length = parser.getAttributeValue(null, Constants.Parsing.LENGTH)?.toLongOrNull() ?: Constants.Parsing.DEFAULT_ENCLOSURE_LENGTH
                            val type = parser.getAttributeValue(null, Constants.Parsing.TYPE)
                            enclosure = RssEnclosure(url, type, length)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == Constants.Parsing.ITEM) {
                        inItem = false
                    }
                }
            }

            if (isKnown) {
                while (parser.eventType != XmlPullParser.END_TAG || parser.name != Constants.Parsing.ITEM) {
                    parser.next()
                }
                return ItemParseResult(null, true)
            }
        }

        val item =
            RssItem(
                title = title,
                description = description,
                link = link,
                guid = guid,
                pubDate = pubDate,
                enclosure = enclosure,
                itunesDuration = itunesDuration
            )
        return ItemParseResult(item, false)
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
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
