package com.example.pocastcloni.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupEpisodeState
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.BackupRoomSnapshot
import com.example.pocastcloni.data.local.BackupSettingsFieldPresence
import com.example.pocastcloni.data.local.DownloadStatus
import com.example.pocastcloni.data.local.EpisodeEntity
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.data.manager.parseBackupJson
import com.example.pocastcloni.data.worker.BackupPostImportSyncScheduler
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.util.Constants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.IOException
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class BackupRepositorySecurityTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<PodcastDao>(relaxed = true)
    private val backupHelper = mockk<PodcastBackupHelper>()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val transactionRunner = mockk<BackupImportTransactionRunner>()
    private val journalDao = mockk<BackupImportJournalDao>(relaxed = true)
    private val recovery = mockk<BackupImportRecovery>(relaxed = true)
    private val importCoordinator = BackupImportCoordinator()
    private val postImportSyncScheduler = mockk<BackupPostImportSyncScheduler>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()
    private val dispatcherProvider = mockk<DispatcherProvider>().also {
        every { it.io } returns dispatcher
    }
    private val context = mockk<Context>(relaxed = true).also {
        every { it.contentResolver } returns mockk<ContentResolver>(relaxed = true)
    }
    private val repository = BackupRepositoryImpl(
        podcastDao = dao,
        backupHelper = backupHelper,
        userPreferencesRepository = preferences,
        dispatcherProvider = dispatcherProvider,
        transactionRunner = transactionRunner,
        backupImportJournalDao = journalDao,
        objectMapper = objectMapper,
        backupImportRecovery = recovery,
        importCoordinator = importCoordinator,
        postImportSyncScheduler = postImportSyncScheduler,
        context = context
    )

    init {
        every { preferences.userSettingsFlow } returns flowOf(com.example.pocastcloni.domain.repository.UserSettings())
        coEvery { transactionRunner.run(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
    }

    @Test
    @Suppress("LongMethod")
    fun `export maps one transactional Room snapshot with feed scoped episode state`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val settings = UserSettings(autoDownloadLimit = 7)
        val feedA = "https://feed-a.example/rss"
        val feedB = "https://feed-b.example/rss"
        val sharedGuid = "shared-guid"
        coEvery { transactionRunner.captureBackupSnapshot() } returns BackupRoomSnapshot(
            podcasts = listOf(
                PodcastEntity(feedA, "Feed A", "", "", autoDownloadEnabled = true, sortOrder = 0),
                PodcastEntity(feedB, "Feed B", "", "", autoDownloadEnabled = false, sortOrder = 1)
            ),
            episodeStates = listOf(
                EpisodeEntity(
                    guid = sharedGuid,
                    podcastRssUrl = feedA,
                    title = "Episode A",
                    description = "",
                    pubDate = null,
                    link = "$feedA/episode-a",
                    enclosureUrl = "https://cdn.example/a.mp3",
                    type = "audio/ogg",
                    fileSize = 42_000L,
                    isFavorite = true,
                    favoriteTimestamp = 10,
                    favoriteAddedAt = 100,
                    isPlayed = true,
                    playbackPositionMs = 5_000,
                    downloadStatus = DownloadStatus.DOWNLOADED,
                    downloadPath = "/private/local/a.ogg"
                ),
                EpisodeEntity(
                    guid = sharedGuid,
                    podcastRssUrl = feedB,
                    title = "Episode B",
                    description = "",
                    pubDate = null,
                    link = "",
                    enclosureUrl = "",
                    playbackPositionMs = 2_000
                )
            )
        )
        coEvery { backupHelper.exportBackup(any(), any(), any(), any(), any()) } returns Unit

        repository.exportFullBackup(uri, settings)

        coVerify(exactly = 1) { transactionRunner.captureBackupSnapshot() }
        coVerify {
            backupHelper.exportBackup(
                podcasts = match { podcasts ->
                    podcasts.map { it.autoDownloadEnabled } == listOf(true, false)
                },
                episodeStates = match { states ->
                    states.map { it.podcastUrl to it.episodeGuid } ==
                        listOf(feedA to sharedGuid, feedB to sharedGuid) &&
                        states.first().favoriteOrder == 0L &&
                        states.first().link == "$feedA/episode-a" &&
                        states.first().enclosureUrl == "https://cdn.example/a.mp3" &&
                        states.first().type == "audio/ogg" &&
                        states.first().fileSize == 42_000L
                },
                settings = settings,
                uri = uri,
                contentResolver = any()
            )
        }
        coVerify(exactly = 0) { dao.getAllPodcastsForExport() }
        coVerify(exactly = 0) { dao.getFavoriteEpisodesSync() }
    }

    @Test
    fun `invalid backup is rejected before recovery journal settings or database mutation`() = runTest(dispatcher) {
        val duplicated = BackupPodcast(url = "https://example.com/feed.xml")
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            podcasts = listOf(duplicated, duplicated)
        )

        try {
            repository.importFullBackup(mockk())
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        coVerify(exactly = 0) { recovery.recoverInterruptedImportLocked() }
        coVerify(exactly = 0) { journalDao.savePendingImport(any()) }
        coVerify(exactly = 0) { preferences.restoreSettingsOrThrow(any()) }
        coVerify(exactly = 0) { transactionRunner.run(any()) }
    }

    @Test
    fun `versionless v1 object preserves existing podcast auto download`() = runTest(dispatcher) {
        val url = "https://example.com/versionless.xml"
        val existing = PodcastEntity(
            rssUrl = url,
            title = "Existing",
            description = "",
            imageUrl = "",
            autoDownloadEnabled = true
        )
        val parsed = parseBackupJson(
            """
            {
              "podcasts": [
                {
                  "url": "$url",
                  "sortOrder": 0,
                  "title": "Legacy"
                }
              ]
            }
            """.trimIndent(),
            objectMapper
        )
        assertEquals(1, parsed.version)
        coEvery { backupHelper.importBackup(any(), any()) } returns parsed
        coEvery { dao.getAllPodcastsForExport() } returns listOf(existing)
        coEvery { dao.getPodcastByUrl(url) } returns existing

        repository.importFullBackup(mockk())

        coVerify(exactly = 0) { dao.updateAutoDownloadEnabled(url, any()) }
    }

    // The two-feed fixture stays co-located so every duplicate-GUID assertion uses the same identities.
    @Suppress("LongMethod")
    @Test
    fun `v2 restore scopes duplicate guids and only writes portable state`() = runTest(dispatcher) {
        val feedA = "https://feed-a.example/rss"
        val feedB = "https://feed-b.example/rss"
        val localFeed = "https://local.example/rss"
        val sharedGuid = "shared-guid"
        val podcastA = PodcastEntity(feedA, "Feed A", "", "", sortOrder = 9)
        val podcastB = PodcastEntity(feedB, "Feed B", "", "", sortOrder = 9)
        val localPodcast = PodcastEntity(localFeed, "Local", "", "", sortOrder = 0)
        val episodeA = EpisodeEntity(
            guid = sharedGuid,
            podcastRssUrl = feedA,
            title = "Episode A",
            description = "",
            pubDate = null,
            link = "",
            enclosureUrl = "",
            downloadStatus = DownloadStatus.DOWNLOADED,
            downloadPath = "/local/a.mp3",
            episodeId = 11
        )
        val episodeB = episodeA.copy(
            podcastRssUrl = feedB,
            title = "Episode B",
            downloadPath = "/local/b.mp3",
            episodeId = 22
        )
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            version = 2,
            podcasts = listOf(
                BackupPodcast(feedB, sortOrder = 4, autoDownloadEnabled = false),
                BackupPodcast(feedA, sortOrder = 4, autoDownloadEnabled = true)
            ),
            episodeStates = listOf(
                BackupEpisodeState(
                    podcastUrl = feedA,
                    episodeGuid = sharedGuid,
                    title = "Episode A",
                    duration = 60_000,
                    isFavorite = true,
                    favoriteAddedAt = 100,
                    favoriteOrder = 1,
                    isPlayed = true,
                    datePlayed = 300,
                    playbackPositionMs = 50_000
                ),
                BackupEpisodeState(
                    podcastUrl = feedB,
                    episodeGuid = sharedGuid,
                    title = "Episode B",
                    duration = 90_000,
                    isFavorite = true,
                    favoriteAddedAt = 200,
                    favoriteOrder = 0,
                    playbackPositionMs = 40_000
                )
            )
        )
        coEvery { dao.getAllPodcastsForExport() } returns listOf(localPodcast, podcastA, podcastB)
        coEvery { dao.getPodcastByUrl(feedA) } returns podcastA
        coEvery { dao.getPodcastByUrl(feedB) } returns podcastB
        coEvery { dao.getEpisodeByFeedAndGuid(feedA, sharedGuid) } returns episodeA
        coEvery { dao.getEpisodeByFeedAndGuid(feedB, sharedGuid) } returns episodeB
        coEvery {
            dao.updatePortableEpisodeState(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 1

        val result = repository.importFullBackup(mockk())

        assertEquals(0, result.skippedFavorites)
        coVerify(exactly = 1) {
            dao.updatePortableEpisodeState(
                episodeId = 11,
                isFavorite = true,
                favoriteAddedAt = 100,
                isPlayed = true,
                datePlayed = Date(300),
                playbackPositionMs = 50_000,
                duration = 60_000,
                restoreDuration = true
            )
        }
        coVerify(exactly = 1) {
            dao.updatePortableEpisodeState(
                episodeId = 22,
                isFavorite = true,
                favoriteAddedAt = 200,
                isPlayed = false,
                datePlayed = null,
                playbackPositionMs = 40_000,
                duration = 90_000,
                restoreDuration = true
            )
        }
        coVerify {
            dao.updatePodcastSortOrders(
                match { updates ->
                    updates.map { it.rssUrl to it.sortOrder } ==
                        listOf(feedA to 0L, feedB to 1L, localFeed to 2L)
                }
            )
        }
        coVerify {
            dao.updateFavoriteOrder(
                match { updates ->
                    updates.map { it.episodeId to it.favoriteTimestamp } ==
                        listOf(22L to 1L, 11L to 0L)
                }
            )
        }
        coVerify { dao.updateAutoDownloadEnabled(feedA, true) }
        coVerify { dao.updateAutoDownloadEnabled(feedB, false) }
        coVerify(exactly = 0) { dao.updateDownloadStatus(any(), any(), any()) }
        assertTrue(episodeA.downloadPath == "/local/a.mp3" && episodeB.downloadPath == "/local/b.mp3")
    }

    @Test
    fun `v3 restore only updates portable status on an existing episode`() = runTest(dispatcher) {
        val feedUrl = "https://feed.example/rss"
        val existing =
            EpisodeEntity(
                guid = "episode-v3",
                podcastRssUrl = feedUrl,
                title = "Local title",
                description = "Local description",
                pubDate = null,
                link = "$feedUrl/local-link",
                enclosureUrl = "https://cdn.example/local.mp3",
                type = "audio/ogg",
                fileSize = 50_000L,
                duration = 90_000L,
                episodeId = 41L
            )
        coEvery { backupHelper.importBackup(any(), any()) } returns
            BackupData(
                version = 3,
                podcasts = listOf(BackupPodcast(url = feedUrl)),
                episodeStates =
                listOf(
                    BackupEpisodeState(
                        podcastUrl = feedUrl,
                        episodeGuid = existing.guid,
                        title = "Backup title",
                        link = "$feedUrl/backup-link",
                        enclosureUrl = "https://cdn.example/backup.mp3",
                        type = "audio/mpeg",
                        fileSize = 1L,
                        duration = 1L,
                        isPlayed = true
                    )
                )
            )
        coEvery { dao.getEpisodeByFeedAndGuid(feedUrl, existing.guid) } returns existing
        coEvery {
            dao.updatePortableEpisodeState(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 1

        repository.importFullBackup(mockk())

        coVerify(exactly = 1) {
            dao.updatePortableEpisodeState(
                episodeId = 41L,
                isFavorite = false,
                favoriteAddedAt = null,
                isPlayed = true,
                datePlayed = null,
                playbackPositionMs = 0L,
                duration = 1L,
                restoreDuration = false
            )
        }
        coVerify(exactly = 0) { dao.insertEpisodesIgnore(any()) }
        assertEquals("Local title", existing.title)
        assertEquals("https://cdn.example/local.mp3", existing.enclosureUrl)
        assertEquals(90_000L, existing.duration)
    }

    @Test
    fun `v3 placeholder uses local podcast approvals and sanitizes media metadata`() = runTest(dispatcher) {
        val feedUrl = "http://192.168.1.20:8080/feed.xml"
        val state =
            BackupEpisodeState(
                podcastUrl = feedUrl,
                episodeGuid = "episode-v3",
                title = "Restored episode",
                link = "http://192.168.1.20:8080/episodes/1",
                enclosureUrl = "http://192.168.1.21:8080/audio/1.mp3",
                type = "audio/ogg",
                fileSize = 42_000L
            )
        val parent =
            PodcastEntity(
                rssUrl = feedUrl,
                title = "Local podcast",
                description = "",
                imageUrl = "",
                allowInsecureHttp = true,
                allowLocalNetwork = true
            )
        val inserted =
            EpisodeEntity(
                guid = state.episodeGuid,
                podcastRssUrl = feedUrl,
                title = state.title,
                description = "",
                pubDate = null,
                link = state.link,
                enclosureUrl = "",
                episodeId = 51L
            )
        val captured = io.mockk.slot<List<EpisodeEntity>>()
        coEvery { dao.getEpisodeByFeedAndGuid(feedUrl, state.episodeGuid) } returnsMany
            listOf(null, inserted)
        coEvery { dao.getPodcastByUrl(feedUrl) } returns parent
        coEvery { dao.insertEpisodesIgnore(capture(captured)) } returns listOf(51L)
        coEvery {
            dao.updatePortableEpisodeState(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 1

        restoreAvailableEpisodeStates(
            podcastDao = dao,
            backupData = BackupData(version = 3, episodeStates = listOf(state)),
            restoreDuration = false
        )

        val placeholder = captured.captured.single()
        assertEquals(state.link, placeholder.link)
        assertEquals("", placeholder.enclosureUrl)
        assertEquals(Constants.Backup.DEFAULT_EPISODE_MEDIA_TYPE, placeholder.type)
        assertEquals(Constants.Backup.DEFAULT_EPISODE_FILE_SIZE, placeholder.fileSize)
    }

    @Test
    fun `backup cannot self-authorize HTTP or load its HTTP image`() = runTest(dispatcher) {
        val url = "http://example.com/feed.xml"
        val storedStub = PodcastEntity(
            rssUrl = url,
            title = "Imported",
            description = "",
            imageUrl = "",
            allowInsecureHttp = false
        )
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            podcasts = listOf(
                BackupPodcast(
                    url = url,
                    title = "Imported",
                    imageUrl = "http://example.com/cover.jpg",
                    allowInsecureHttp = true
                )
            )
        )
        coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, null, storedStub, storedStub)

        repository.importFullBackup(mockk<Uri>())

        coVerify {
            dao.insertPodcasts(
                match { podcasts ->
                    podcasts.single().let { !it.allowInsecureHttp && it.imageUrl.isEmpty() }
                }
            )
        }
        coVerify(exactly = 1) { postImportSyncScheduler.schedule() }
    }

    @Test
    fun `backup cannot self-authorize local network access`() = runTest(dispatcher) {
        val url = "https://192.168.1.20/feed.xml"
        val storedStub = PodcastEntity(
            rssUrl = url,
            title = "Imported",
            description = "",
            imageUrl = "",
            allowLocalNetwork = false
        )
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            podcasts = listOf(
                BackupPodcast(
                    url = url,
                    title = "Imported",
                    imageUrl = "https://192.168.1.20/cover.jpg",
                    allowLocalNetwork = true
                )
            )
        )
        coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, null, storedStub, storedStub)

        repository.importFullBackup(mockk<Uri>())

        coVerify {
            dao.insertPodcasts(
                match { podcasts ->
                    podcasts.single().let { !it.allowLocalNetwork && it.imageUrl.isEmpty() }
                }
            )
        }
        coVerify(exactly = 1) { postImportSyncScheduler.schedule() }
    }

    @Test
    fun `legacy backup validators are ignored when creating an imported podcast`() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        val storedStub = PodcastEntity(url, "Imported", "", "")
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            podcasts = listOf(
                BackupPodcast(
                    url = url,
                    title = "Imported",
                    lastModifiedHeader = "legacy-last-modified",
                    eTagHeader = "legacy-etag"
                )
            )
        )
        coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, null, storedStub, storedStub)

        repository.importFullBackup(mockk<Uri>())

        coVerify {
            dao.insertPodcasts(
                match { podcasts ->
                    podcasts.single().lastModifiedHeader == null && podcasts.single().eTagHeader == null
                }
            )
        }
    }

    @Test
    fun `cancellation during local transaction rolls settings back and propagates`() = runTest(dispatcher) {
        val previous = UserSettings(theme = AppTheme.DARK)
        val imported = UserSettings(theme = AppTheme.LIGHT)
        every { preferences.userSettingsFlow } returns flowOf(previous)
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(settings = imported)
        coEvery { transactionRunner.run(any()) } throws CancellationException("cancelled")

        try {
            repository.importFullBackup(mockk())
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }

        coVerify { preferences.restoreSettingsOrThrow(previous) }
        coVerify { journalDao.clearPendingImport() }
        coVerify(exactly = 0) { postImportSyncScheduler.schedule() }
    }

    @Test
    fun `post-commit scheduling cancellation propagates without rolling committed settings back`() =
        runTest(dispatcher) {
            val url = "https://example.com/feed.xml"
            val stored = PodcastEntity(url, "Imported", "", "")
            coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
                podcasts = listOf(BackupPodcast(url = url, title = "Imported"))
            )
            coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, null, stored)
            coEvery { postImportSyncScheduler.schedule() } throws CancellationException("cancelled")

            try {
                repository.importFullBackup(mockk())
                fail("Expected cancellation")
            } catch (_: CancellationException) {
                // Expected.
            }

            coVerify(exactly = 0) { preferences.restoreSettingsOrThrow(any()) }
        }

    @Test
    fun `post-commit scheduling failure does not fail committed import`() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        val stored = PodcastEntity(url, "Imported", "", "")
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            podcasts = listOf(BackupPodcast(url = url, title = "Imported"))
        )
        coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, null, stored)
        coEvery { postImportSyncScheduler.schedule() } throws IOException("work manager unavailable")

        val result = repository.importFullBackup(mockk())

        assertEquals(1, result.success)
        assertEquals(1, result.total)
        coVerify(exactly = 0) { preferences.restoreSettingsOrThrow(any()) }
    }

    @Test
    fun `startup recovery and active import share one coordinator`() = runTest(dispatcher) {
        val sharedCoordinator = BackupImportCoordinator()
        val recoveryEntered = CompletableDeferred<Unit>()
        val releaseRecovery = CompletableDeferred<Unit>()
        var recoveryReads = 0
        coEvery { journalDao.getPendingImport() } coAnswers {
            recoveryReads += 1
            if (recoveryReads == 1) {
                recoveryEntered.complete(Unit)
                releaseRecovery.await()
            }
            null
        }
        val sharedRecovery = BackupImportRecovery(
            journalDao,
            preferences,
            objectMapper,
            sharedCoordinator
        )
        val sharedRepository = BackupRepositoryImpl(
            podcastDao = dao,
            backupHelper = backupHelper,
            userPreferencesRepository = preferences,
            dispatcherProvider = dispatcherProvider,
            transactionRunner = transactionRunner,
            backupImportJournalDao = journalDao,
            objectMapper = objectMapper,
            backupImportRecovery = sharedRecovery,
            importCoordinator = sharedCoordinator,
            postImportSyncScheduler = postImportSyncScheduler,
            context = context
        )
        coEvery { backupHelper.importBackup(any(), any()) } returns
            BackupData(settings = UserSettings())

        val recovery = async { sharedRecovery.recoverInterruptedImport() }
        recoveryEntered.await()
        val import = async { sharedRepository.importFullBackup(mockk()) }
        runCurrent()

        coVerify(exactly = 0) { backupHelper.importBackup(any(), any()) }
        releaseRecovery.complete(Unit)
        recovery.await()
        import.await()
        coVerify(exactly = 1) { backupHelper.importBackup(any(), any()) }
    }

    @Test
    fun `parallel imports are serialized`() = runTest(dispatcher) {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        coEvery { backupHelper.importBackup(any(), any()) } coAnswers {
            val current = active.incrementAndGet()
            maximum.updateAndGet { previous -> maxOf(previous, current) }
            delay(10)
            active.decrementAndGet()
            BackupData(settings = UserSettings())
        }

        listOf(
            async { repository.importFullBackup(mockk()) },
            async { repository.importFullBackup(mockk()) }
        ).awaitAll()

        assertEquals(1, maximum.get())
    }

    @Test
    fun `partial backup settings merge into current settings before restore`() = runTest(dispatcher) {
        val previous =
            UserSettings(
                theme = AppTheme.DARK,
                gridSize = 7,
                backgroundCheckInterval = 24,
                smartStreamItemLimit = 17,
                indicator =
                IndicatorSettings(
                    colorArgb = 0xFF112233,
                    size = 32,
                    borderWidth = 4,
                    xOffset = -5,
                    yOffset = 6
                )
            )
        val imported =
            UserSettings(
                theme = AppTheme.LIGHT,
                indicator = IndicatorSettings(size = 22)
            )
        val expected =
            previous.copy(
                theme = AppTheme.LIGHT,
                indicator = previous.indicator.copy(size = 22)
            )
        every { preferences.userSettingsFlow } returns flowOf(previous)
        coEvery { backupHelper.importBackup(any(), any()) } returns
            BackupData(
                settings = imported,
                settingsFieldPresence =
                BackupSettingsFieldPresence(
                    fields = setOf("theme", "indicator"),
                    indicatorFields = setOf("size")
                )
            )

        repository.importFullBackup(mockk())

        coVerify { preferences.restoreSettingsOrThrow(expected) }
        coVerify {
            journalDao.savePendingImport(
                match { journal ->
                    objectMapper.readValue(
                        journal.previousSettingsJson,
                        UserSettings::class.java
                    ) == previous
                }
            )
        }
    }

    @Test
    fun `transaction failure restores previous settings and clears journal`() = runTest(dispatcher) {
        val previous = UserSettings(theme = AppTheme.DARK)
        val imported = UserSettings(theme = AppTheme.LIGHT)
        every { preferences.userSettingsFlow } returns flowOf(previous)
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(settings = imported)
        coEvery { transactionRunner.run(any()) } throws IOException("database failed")

        try {
            repository.importFullBackup(mockk())
            fail("Expected import failure")
        } catch (_: IOException) {
            // Expected.
        }

        coVerify { journalDao.savePendingImport(any()) }
        coVerify { preferences.restoreSettingsOrThrow(imported) }
        coVerify { preferences.restoreSettingsOrThrow(previous) }
        coVerify { journalDao.clearPendingImport() }
        coVerify(exactly = 0) { postImportSyncScheduler.schedule() }
    }
}
