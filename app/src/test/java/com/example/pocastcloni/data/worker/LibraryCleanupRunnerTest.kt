package com.example.pocastcloni.data.worker

import com.example.pocastcloni.domain.repository.PodcastRepository
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCleanupRunnerTest {
    private val repository = mockk<PodcastRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>()
    private val runner = LibraryCleanupRunner(repository, preferences)

    @Test
    fun `disabled auto cleanup skips repository work`() = runTest {
        every { preferences.userSettingsFlow } returns flowOf(UserSettings(autoCleanupEnabled = false))

        assertFalse(runner())

        coVerify(exactly = 0) { repository.pruneLibrary(any()) }
    }

    @Test
    fun `enabled auto cleanup uses configured keep limit`() = runTest {
        every { preferences.userSettingsFlow } returns
            flowOf(UserSettings(autoCleanupEnabled = true, cleanupKeepLimit = 75))

        assertTrue(runner())

        coVerify(exactly = 1) { repository.pruneLibrary(75) }
    }
}
