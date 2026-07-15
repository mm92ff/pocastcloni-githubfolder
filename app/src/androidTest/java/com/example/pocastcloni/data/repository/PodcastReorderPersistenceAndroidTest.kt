package com.example.pocastcloni.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.di.DefaultDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastReorderPersistenceAndroidTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private val dao by lazy { AppDatabase.getDatabase(context).podcastDao() }
    private val commands by lazy {
        PodcastCommandAdapter(
            podcastDao = dao,
            dispatcherProvider = DefaultDispatcherProvider(),
            context = context
        )
    }

    @Before
    fun setUp() = runBlocking {
        dao.deleteAllPodcasts()
        PODCAST_URLS.forEachIndexed { index, url ->
            dao.insertPodcast(
                PodcastEntity(
                    rssUrl = url,
                    title = "Podcast $index",
                    description = "Reorder integration fixture",
                    imageUrl = "",
                    sortOrder = index.toLong()
                )
            )
        }
    }

    @After
    fun tearDown() = runBlocking {
        dao.deleteAllPodcasts()
    }

    @Test
    fun latestRequestedOrderIsThePersistedDatabaseOrder() = runBlocking {
        commands.reorderPodcasts(listOf(PODCAST_URLS[1], PODCAST_URLS[2], PODCAST_URLS[0]))
        val latestOrder = listOf(PODCAST_URLS[2], PODCAST_URLS[0], PODCAST_URLS[1])
        commands.reorderPodcasts(latestOrder)

        assertEquals(latestOrder, dao.getAllPodcastsForExport().map(PodcastEntity::rssUrl))
    }

    private companion object {
        val PODCAST_URLS =
            listOf(
                "https://example.test/a.xml",
                "https://example.test/b.xml",
                "https://example.test/c.xml"
            )
    }
}
