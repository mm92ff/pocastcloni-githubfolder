package com.example.pocastcloni.testutil

import com.example.pocastcloni.data.local.EpisodeEntity

internal object AuditRegressionFixtures {
    const val DUPLICATE_GUID = "shared-guid-across-feeds"
    const val FEED_A_URL = "https://feed-a.example/rss"
    const val FEED_B_URL = "https://feed-b.example/rss"

    fun episode(
        feedUrl: String,
        title: String
    ): EpisodeEntity =
        EpisodeEntity(
            guid = DUPLICATE_GUID,
            podcastRssUrl = feedUrl,
            title = title,
            description = "$title description",
            pubDate = null,
            link = "$feedUrl/episode",
            enclosureUrl = "$feedUrl/audio.mp3"
        )

    fun resourceText(name: String): String =
        requireNotNull(AuditRegressionFixtures::class.java.getResource(name)) {
            "Missing audit fixture: $name"
        }.readText()
}
