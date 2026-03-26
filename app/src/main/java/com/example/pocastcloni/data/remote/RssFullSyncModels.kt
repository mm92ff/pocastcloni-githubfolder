/**
 * Defines the data models for parsing a complete RSS feed at once.
 * Used by Retrofit's SimpleXmlConverter for the "Full Sync" operation.
 * Now hardened to handle missing namespaces or raw tag names.
 */
package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.Constants.Parsing
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = Parsing.RSS)
data class RssFeed(
    @field:JacksonXmlProperty(localName = Parsing.CHANNEL) var channel: RssChannel? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RssChannel(
    @field:JacksonXmlProperty(localName = Parsing.TITLE) var title: String? = null,
    @field:JacksonXmlCData
    @field:JacksonXmlProperty(localName = Parsing.DESCRIPTION)
    var description: String? = null,
    // 1. Standard RSS Image (<image><url>...</url></image>)
    @field:JacksonXmlProperty(localName = Parsing.IMAGE) var image: RssImage? = null,
    // 2. iTunes image with namespace (<itunes:image href="..." />) - the clean approach
    @field:JacksonXmlProperty(localName = Parsing.ITUNES_IMAGE, namespace = "itunes") var itunesImage: RssImage? = null,
    // 3. RESILIENCE FALLBACK: iTunes image as a "raw" tag name, in case namespaces are ignored
    // Catches cases where the parser reads "itunes:image" as a simple name.
    @field:JacksonXmlProperty(localName = "itunes:image") var itunesImageRaw: RssImage? = null,
    @field:JacksonXmlElementWrapper(useWrapping = false)
    @field:JacksonXmlProperty(localName = Parsing.ITEM)
    var items: List<RssItem>? = null
) {
    // Takes the best available image (priority: iTunes > raw fallback > standard RSS)
    val finalImageUrl: String?
        get() = itunesImage?.url ?: itunesImageRaw?.url ?: image?.url
}

// Unified class for all image types
@JsonIgnoreProperties(ignoreUnknown = true)
data class RssImage(
    @field:JacksonXmlProperty(localName = Parsing.URL) var urlFromTag: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.HREF, isAttribute = true) var urlFromAttribute: String? = null
) {
    val url: String?
        get() = urlFromAttribute ?: urlFromTag
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RssItem(
    @field:JacksonXmlProperty(localName = Parsing.TITLE) var title: String? = null,
    @field:JacksonXmlCData
    @field:JacksonXmlProperty(localName = Parsing.DESCRIPTION)
    var description: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.LINK) var link: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.GUID) var guid: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.PUB_DATE) var pubDate: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.ENCLOSURE) var enclosure: RssEnclosure? = null,
    @field:JacksonXmlProperty(localName = Parsing.ITUNES_DURATION, namespace = "itunes") var itunesDuration: String? = null,
    // Here too: image with namespace...
    @field:JacksonXmlProperty(localName = Parsing.ITUNES_IMAGE, namespace = "itunes") var itunesImage: RssImage? = null,
    // ...and as fallback without namespace logic
    @field:JacksonXmlProperty(localName = "itunes:image") var itunesImageRaw: RssImage? = null
) {
    val bestImage: RssImage?
        get() = itunesImage ?: itunesImageRaw
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RssEnclosure(
    @field:JacksonXmlProperty(localName = Parsing.URL, isAttribute = true) var url: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.TYPE, isAttribute = true) var type: String? = null,
    @field:JacksonXmlProperty(localName = Parsing.LENGTH, isAttribute = true) var length: Long? = Parsing.DEFAULT_ENCLOSURE_LENGTH
)
