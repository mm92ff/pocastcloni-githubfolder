package com.example.pocastcloni.ui.home.common

import com.example.pocastcloni.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.pocastcloni.domain.model.Podcast
import java.util.Date

class PodcastCoverRequestFactoryTest {
    @Test
    fun `outer whitespace maps to the same cache identity`() {
        val clean = requireNotNull(PodcastCoverRequestFactory.identity(COVER_URL, PodcastCoverSize.GRID))
        val padded = requireNotNull(PodcastCoverRequestFactory.identity("  $COVER_URL\n", PodcastCoverSize.GRID))

        assertEquals(clean, padded)
    }

    @Test
    fun `blank url creates no request identity`() {
        assertNull(PodcastCoverRequestFactory.identity(" \n\t ", PodcastCoverSize.GRID))
    }

    @Test
    fun `list and grid share disk identity but keep separate memory sizes`() {
        val list = requireNotNull(PodcastCoverRequestFactory.identity(COVER_URL, PodcastCoverSize.LIST))
        val grid = requireNotNull(PodcastCoverRequestFactory.identity(COVER_URL, PodcastCoverSize.GRID))

        assertEquals(Constants.Image.IMAGE_SIZE_LIST, list.pixelSize)
        assertEquals(Constants.Image.IMAGE_SIZE_GRID, grid.pixelSize)
        assertEquals(list.diskCacheKey, grid.diskCacheKey)
        assertNotEquals(list.memoryCacheKey, grid.memoryCacheKey)
        assertEquals(list.diagnosticId, grid.diagnosticId)
    }

    @Test
    fun `signed query remains part of the normalized disk identity`() {
        val signedUrl = "$COVER_URL?token=AbC%2B123&width=400"
        val identity = requireNotNull(PodcastCoverRequestFactory.identity(" $signedUrl ", PodcastCoverSize.GRID))

        assertEquals(signedUrl, identity.normalizedUrl)
        assertEquals(signedUrl, identity.diskCacheKey)
    }

    @Test
    fun `changed query creates a new disk identity`() {
        val first = requireNotNull(PodcastCoverRequestFactory.identity("$COVER_URL?v=1", PodcastCoverSize.GRID))
        val second = requireNotNull(PodcastCoverRequestFactory.identity("$COVER_URL?v=2", PodcastCoverSize.GRID))

        assertNotEquals(first.diskCacheKey, second.diskCacheKey)
        assertNotEquals(first.diagnosticId, second.diagnosticId)
    }

    @Test
    fun `diagnostic identity is stable and does not contain url material`() {
        val identity = requireNotNull(PodcastCoverRequestFactory.identity(COVER_URL, PodcastCoverSize.GRID))

        assertEquals(12, identity.diagnosticId.length)
        assertTrue(identity.diagnosticId.all { character -> character.isDigit() || character in 'a'..'f' })
        assertFalse(identity.diagnosticId.contains("example"))
        assertFalse(identity.diagnosticId.contains("cover"))
    }

    @Test
    fun `podcast metadata refresh does not change persistent cover identity`() {
        val before = Podcast(
            rssUrl = "https://example.test/feed.xml",
            title = "Before",
            description = "Description",
            imageUrl = COVER_URL,
            lastRefreshed = Date(1L),
            coverRevision = 4L
        )
        val after = before.copy(title = "After", lastRefreshed = Date(2L))

        assertEquals(
            PodcastCoverRequestFactory.persistentIdentity(
                before.rssUrl,
                before.coverRevision,
                PodcastCoverSize.GRID
            ),
            PodcastCoverRequestFactory.persistentIdentity(
                after.rssUrl,
                after.coverRevision,
                PodcastCoverSize.GRID
            )
        )
    }

    private companion object {
        const val COVER_URL = "https://images.example.test/cover.png"
    }
}
