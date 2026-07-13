package com.example.pocastcloni.energy

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.data.local.AppDatabase
import com.example.pocastcloni.data.local.AppDatabaseMigrations
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackPositionPersistenceIntegrationTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val databaseName = "energy-playback-position-${UUID.randomUUID()}.db"
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(databaseName)
    }

    @Test
    fun playbackPositionSurvivesFileDatabaseReopen() = runBlocking {
        val testId = UUID.randomUUID().toString()
        val podcastRssUrl = "https://test.invalid/$testId/feed.xml"
        val episodeGuid = "episode-$testId"

        val initialDatabase = openDatabase().also { database = it }
        initialDatabase.podcastDao().insertPodcast(
            PodcastEntity(
                rssUrl = podcastRssUrl,
                title = "Persistence test podcast",
                description = "Local test data",
                imageUrl = "https://test.invalid/$testId/cover.jpg"
            )
        )
        initialDatabase.podcastDao().insertEpisode(
            EpisodeEntity(
                guid = episodeGuid,
                podcastRssUrl = podcastRssUrl,
                title = "Persistence test episode",
                description = "Local test data",
                pubDate = null,
                link = "https://test.invalid/$testId/episode",
                enclosureUrl = "https://test.invalid/$testId/audio.mp3"
            )
        )
        initialDatabase.podcastDao().updateEpisodeProgressOnly(
            guid = episodeGuid,
            pos = EXPECTED_PLAYBACK_POSITION_MS
        )
        assertEquals(
            EXPECTED_PLAYBACK_POSITION_MS,
            initialDatabase.podcastDao().getEpisodeByGuid(episodeGuid)?.playbackPositionMs
        )

        initialDatabase.close()
        database = null

        val reopenedDatabase = openDatabase().also { database = it }
        assertEquals(
            EXPECTED_PLAYBACK_POSITION_MS,
            reopenedDatabase.podcastDao().getEpisodeByGuid(episodeGuid)?.playbackPositionMs
        )
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*AppDatabaseMigrations.ALL_MIGRATIONS)
            .build()

    companion object {
        private const val EXPECTED_PLAYBACK_POSITION_MS = 98_765L
    }
}
