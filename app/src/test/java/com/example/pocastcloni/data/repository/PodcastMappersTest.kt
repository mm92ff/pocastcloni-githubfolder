package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.remote.RssEnclosure
import com.example.pocastcloni.data.remote.RssItem
import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PodcastMappersTest {

    // ---- PodcastEntity.toDomain() ----

    @Test
    fun `toDomain maps isLatestEpisodePlayed correctly when true`() {
        val entity = podcastEntity(isLatestEpisodePlayed = true)
        val domain = entity.toDomain()
        assertTrue(domain.isLatestEpisodePlayed == true)
    }

    @Test
    fun `toDomain maps isLatestEpisodePlayed correctly when false`() {
        val entity = podcastEntity(isLatestEpisodePlayed = false)
        val domain = entity.toDomain()
        assertFalse(domain.isLatestEpisodePlayed == true)
    }

    @Test
    fun `toDomain maps isLatestEpisodePlayed correctly when null`() {
        val entity = podcastEntity(isLatestEpisodePlayed = null)
        val domain = entity.toDomain()
        assertNull(domain.isLatestEpisodePlayed)
    }

    @Test
    fun `toDomain preserves hasNewEpisodes`() {
        val entity = podcastEntity(hasNewEpisodes = true)
        assertTrue(entity.toDomain().hasNewEpisodes)
    }

    // ---- RssItem.toEpisodeEntity() ----

    @Test
    fun `toEpisodeEntity sets default values for new episode`() {
        val item = rssItem(guid = "guid-1", title = "Test Title", audioUrl = "https://ep1.mp3")
        val entity = item.toEpisodeEntity("https://feed.url")

        assertEquals("guid-1", entity.guid)
        assertEquals("https://feed.url", entity.podcastRssUrl)
        assertEquals("Test Title", entity.title)
        assertFalse(entity.isPlayed)
        assertEquals(0L, entity.playbackPositionMs)
        assertEquals(DownloadStatus.NOT_DOWNLOADED, entity.downloadStatus)
        assertFalse(entity.isFavorite)
    }

    @Test
    fun `toEpisodeEntity sanitizes future date beyond 7 days`() {
        val farFuture = Date(System.currentTimeMillis() + Constants.Validation.MAX_FUTURE_DATE_THRESHOLD_MS + 100_000L)
        val item = rssItem(pubDate = null) // Pass null to force Date(now) as fallback via sanitize
        val entity = item.toEpisodeEntity("https://feed.url")

        // Date should be close to now (within 5 seconds)
        val diff = System.currentTimeMillis() - entity.pubDate!!.time
        assertTrue("Date should be close to now, diff=$diff", diff < 5_000L)
    }

    @Test
    fun `toEpisodeEntity falls back to link as guid when guid is null`() {
        val item = rssItem(guid = null, link = "https://episode.link")
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals("https://episode.link", entity.guid)
    }

    @Test
    fun `toEpisodeEntity preserves legacy title fallback before enclosure`() {
        val item = rssItem(guid = null, link = null, title = "Episode Title")
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals("Episode Title", entity.guid)
    }

    @Test
    fun `missing guid fallback is deterministic and feed scoped`() {
        val item = rssItem(
            guid = null,
            link = null,
            audioUrl = null,
            title = null,
            pubDate = "Mon, 01 Jan 2024 10:00:00 +0000",
            itunesDuration = "60"
        )

        val first = item.toEpisodeEntity("https://example.com/a.xml")
        val repeated = item.toEpisodeEntity("https://example.com/a.xml")
        val otherFeed = item.toEpisodeEntity("https://example.com/b.xml")

        assertEquals(first.guid, repeated.guid)
        assertTrue(first.guid.startsWith("fallback:"))
        assertNotEquals(first.guid, otherFeed.guid)
    }

    @Test
    fun `toEpisodeEntity parses HH_MM_SS duration to milliseconds`() {
        val item = rssItem(itunesDuration = "01:30:00")
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals(5_400_000L, entity.duration) // 90 minutes = 5400 seconds = 5_400_000 ms
    }

    @Test
    fun `toEpisodeEntity parses MM_SS duration to milliseconds`() {
        val item = rssItem(itunesDuration = "45:30")
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals(2_730_000L, entity.duration) // 45*60+30 = 2730 seconds
    }

    @Test
    fun `toEpisodeEntity parses raw seconds duration to milliseconds`() {
        val item = rssItem(itunesDuration = "3600")
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals(3_600_000L, entity.duration)
    }

    @Test
    fun `toEpisodeEntity returns zero duration for null duration string`() {
        val item = rssItem(itunesDuration = null)
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals(0L, entity.duration)
    }

    @Test
    fun `toEpisodeEntity returns empty enclosure url when enclosure is null`() {
        val item = rssItem(audioUrl = null)
        val entity = item.toEpisodeEntity("https://feed.url")
        assertEquals("", entity.enclosureUrl)
    }

    // ---- Helpers ----

    private fun podcastEntity(
        hasNewEpisodes: Boolean = false,
        isLatestEpisodePlayed: Boolean? = null
    ) = PodcastEntity(
        rssUrl = "https://example.com/feed.rss",
        title = "Test Podcast",
        description = "Desc",
        imageUrl = "https://img.jpg",
        hasNewEpisodes = hasNewEpisodes,
        isLatestEpisodePlayed = isLatestEpisodePlayed
    )

    private fun rssItem(
        guid: String? = "default-guid",
        title: String? = "Default Title",
        link: String? = "https://default.link",
        audioUrl: String? = "https://default.mp3",
        pubDate: String? = null,
        itunesDuration: String? = null
    ) = RssItem(
        guid = guid,
        title = title,
        link = link,
        description = null,
        pubDate = pubDate,
        enclosure = audioUrl?.let { RssEnclosure(url = it, type = "audio/mpeg", length = 0L) },
        itunesDuration = itunesDuration
    )
}
