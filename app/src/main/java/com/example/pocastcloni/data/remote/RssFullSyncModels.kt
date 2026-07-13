/** Data models populated by the streaming RSS parser. */
package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.Constants.Parsing

data class RssChannel(
    var title: String? = null,
    var description: String? = null,
    var image: RssImage? = null,
    var itunesImage: RssImage? = null,
    var itunesImageRaw: RssImage? = null,
    var items: List<RssItem>? = null
) {
    val finalImageUrl: String?
        get() = itunesImage?.url ?: itunesImageRaw?.url ?: image?.url
}

data class RssImage(
    var urlFromTag: String? = null,
    var urlFromAttribute: String? = null
) {
    val url: String?
        get() = urlFromAttribute ?: urlFromTag
}

data class RssItem(
    var title: String? = null,
    var description: String? = null,
    var link: String? = null,
    var guid: String? = null,
    var pubDate: String? = null,
    var enclosure: RssEnclosure? = null,
    var itunesDuration: String? = null,
    var itunesImage: RssImage? = null,
    var itunesImageRaw: RssImage? = null
) {
    val bestImage: RssImage?
        get() = itunesImage ?: itunesImageRaw
}

data class RssEnclosure(
    var url: String? = null,
    var type: String? = null,
    var length: Long? = Parsing.DEFAULT_ENCLOSURE_LENGTH
)
