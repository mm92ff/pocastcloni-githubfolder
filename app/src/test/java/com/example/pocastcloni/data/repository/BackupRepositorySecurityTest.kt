package com.example.pocastcloni.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.junit.Test
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositorySecurityTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<PodcastDao>(relaxed = true)
    private val backupHelper = mockk<PodcastBackupHelper>()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncFeed = mockk<SyncFeedUseCase>(relaxed = true)
    private val transactionRunner = mockk<BackupImportTransactionRunner>()
    private val journalDao = mockk<BackupImportJournalDao>(relaxed = true)
    private val recovery = mockk<BackupImportRecovery>(relaxed = true)
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
        syncFeedUseCase = Provider { syncFeed },
        dispatcherProvider = dispatcherProvider,
        transactionRunner = transactionRunner,
        backupImportJournalDao = journalDao,
        objectMapper = jacksonObjectMapper(),
        backupImportRecovery = recovery,
        context = context
    )

    init {
        every { preferences.userSettingsFlow } returns flowOf(com.example.pocastcloni.domain.repository.UserSettings())
        coEvery { transactionRunner.run(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
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

        repository.importFullBackup(
            uri = mockk<Uri>(),
            downloadLimit = 3,
            mode = FeedUpdateMode.SMART_STREAM
        )

        coVerify {
            dao.insertPodcasts(match { podcasts ->
                podcasts.single().let { !it.allowInsecureHttp && it.imageUrl.isEmpty() }
            })
        }
        coVerify(exactly = 0) { syncFeed.invoke(any(), any(), any(), any(), any(), any()) }
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

        repository.importFullBackup(
            uri = mockk<Uri>(),
            downloadLimit = 3,
            mode = FeedUpdateMode.SMART_STREAM
        )

        coVerify {
            dao.insertPodcasts(match { podcasts ->
                podcasts.single().let { !it.allowLocalNetwork && it.imageUrl.isEmpty() }
            })
        }
        coVerify(exactly = 0) {
            syncFeed.invoke(any(), any(), any(), any(), any(), any(), any())
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
            repository.importFullBackup(mockk(), 3, FeedUpdateMode.ALWAYS_FULL)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }

        coVerify { preferences.restoreSettingsOrThrow(previous) }
        coVerify { journalDao.clearPendingImport() }
        coVerify(exactly = 0) { syncFeed.invoke(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `post-commit sync cancellation propagates without rolling committed settings back`() = runTest(dispatcher) {
        val url = "https://example.com/feed.xml"
        val stored = PodcastEntity(url, "Imported", "", "")
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(
            podcasts = listOf(BackupPodcast(url = url, title = "Imported"))
        )
        coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, null, stored)
        coEvery { syncFeed.invoke(any(), any(), any(), any(), any(), any()) } throws
            CancellationException("cancelled")

        try {
            repository.importFullBackup(mockk(), 3, FeedUpdateMode.ALWAYS_FULL)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected.
        }

        coVerify(exactly = 0) { preferences.restoreSettingsOrThrow(any()) }
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
            async { repository.importFullBackup(mockk(), 3, FeedUpdateMode.ALWAYS_FULL) },
            async { repository.importFullBackup(mockk(), 3, FeedUpdateMode.ALWAYS_FULL) }
        ).awaitAll()

        assertEquals(1, maximum.get())
    }

    @Test
    fun `transaction failure restores previous settings and clears journal`() = runTest(dispatcher) {
        val previous = UserSettings(theme = AppTheme.DARK)
        val imported = UserSettings(theme = AppTheme.LIGHT)
        every { preferences.userSettingsFlow } returns flowOf(previous)
        coEvery { backupHelper.importBackup(any(), any()) } returns BackupData(settings = imported)
        coEvery { transactionRunner.run(any()) } throws IOException("database failed")

        try {
            repository.importFullBackup(mockk(), 3, FeedUpdateMode.ALWAYS_FULL)
            fail("Expected import failure")
        } catch (_: IOException) {
            // Expected.
        }

        coVerify { journalDao.savePendingImport(any()) }
        coVerify { preferences.restoreSettingsOrThrow(imported) }
        coVerify { preferences.restoreSettingsOrThrow(previous) }
        coVerify { journalDao.clearPendingImport() }
        coVerify(exactly = 0) { syncFeed.invoke(any(), any(), any(), any(), any(), any()) }
    }
}
