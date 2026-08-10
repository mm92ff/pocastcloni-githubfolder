package com.example.pocastcloni.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastCoverStateDaoAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var coverDao: PodcastCoverStateDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        podcastDao = database.podcastDao()
        coverDao = database.podcastCoverStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun unchangedCandidateDoesNotRewritePublishedState() = runBlocking {
        podcastDao.insertPodcast(podcast())
        assertTrue(coverDao.observeFeedCandidate(RSS_URL, COVER_URL, NOW))
        assertEquals(
            1,
            coverDao.promoteThumbnail(
                rssUrl = RSS_URL,
                sourceUrl = COVER_URL,
                fileName = FILE_NAME,
                checkedAt = NOW,
                contentSha256 = CONTENT_HASH,
                eTag = "etag",
                lastModified = "last-modified"
            )
        )
        val published = coverDao.getState(RSS_URL)

        assertFalse(coverDao.observeFeedCandidate(RSS_URL, "  $COVER_URL  ", NOW + 1))
        assertEquals(published, coverDao.getState(RSS_URL))
    }

    @Test
    fun changedCandidatesCollapseToLatestAndDeletionCascades() = runBlocking {
        podcastDao.insertPodcast(podcast())
        coverDao.observeFeedCandidate(RSS_URL, COVER_URL, NOW)
        coverDao.promoteThumbnail(
            RSS_URL,
            COVER_URL,
            FILE_NAME,
            NOW,
            CONTENT_HASH,
            null,
            null
        )

        assertTrue(coverDao.observeFeedCandidate(RSS_URL, SECOND_COVER_URL, NOW + 1))
        assertTrue(coverDao.observeFeedCandidate(RSS_URL, THIRD_COVER_URL, NOW + 2))
        assertEquals(THIRD_COVER_URL, coverDao.getState(RSS_URL)?.pendingSourceUrl)

        podcastDao.deletePodcastByUrl(RSS_URL)
        assertNull(coverDao.getState(RSS_URL))
    }

    @Test
    fun staleWorkerCannotRecordFailureForNewerCandidate() = runBlocking {
        podcastDao.insertPodcast(podcast())
        coverDao.observeFeedCandidate(RSS_URL, COVER_URL, NOW)
        coverDao.observeFeedCandidate(RSS_URL, SECOND_COVER_URL, NOW + 1)

        assertEquals(
            0,
            coverDao.recordFailure(
                rssUrl = RSS_URL,
                sourceUrl = COVER_URL,
                failureCount = 1,
                nextRetryAt = NOW + 10_000
            )
        )
        val state = requireNotNull(coverDao.getState(RSS_URL))
        assertEquals(SECOND_COVER_URL, state.pendingSourceUrl)
        assertEquals(0, state.failureCount)
        assertNull(state.nextRetryAt)
    }

    private fun podcast() =
        PodcastEntity(
            rssUrl = RSS_URL,
            title = "Podcast",
            description = "Description",
            imageUrl = COVER_URL
        )

    private companion object {
        const val RSS_URL = "https://example.com/feed.xml"
        const val COVER_URL = "https://example.com/cover.jpg"
        const val SECOND_COVER_URL = "https://example.com/cover-2.jpg"
        const val THIRD_COVER_URL = "https://example.com/cover-3.jpg"
        const val NOW = 1_700_000_000_000L
        const val CONTENT_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val FILE_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-$CONTENT_HASH.webp"
    }
}
