package com.example.pocastcloni.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.pocastcloni.data.local.BackupData
import com.example.pocastcloni.data.local.BackupPodcast
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.local.PodcastEntity
import com.example.pocastcloni.data.manager.PodcastBackupHelper
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.usecase.podcast.SyncFeedUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositorySecurityTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<PodcastDao>(relaxed = true)
    private val backupHelper = mockk<PodcastBackupHelper>()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val syncFeed = mockk<SyncFeedUseCase>(relaxed = true)
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
        context = context
    )

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
        coEvery { dao.getPodcastByUrl(url) } returnsMany listOf(null, storedStub, storedStub)

        repository.importFullBackup(
            uri = mockk<Uri>(),
            downloadLimit = 3,
            mode = FeedUpdateMode.SMART_STREAM
        )

        coVerify {
            dao.insertPodcast(
                match { !it.allowInsecureHttp && it.imageUrl.isEmpty() }
            )
        }
        coVerify(exactly = 0) { syncFeed.invoke(any(), any(), any(), any(), any(), any()) }
    }
}
