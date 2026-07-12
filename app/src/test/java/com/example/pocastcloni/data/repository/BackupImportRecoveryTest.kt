package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.data.local.BackupImportJournalEntity
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class BackupImportRecoveryTest {
    private val journalDao = mockk<BackupImportJournalDao>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val objectMapper = jacksonObjectMapper()
    private val recovery = BackupImportRecovery(journalDao, preferences, objectMapper)

    @Test
    fun `restores previous settings and clears completed recovery journal`() = runTest {
        val previous = UserSettings(theme = AppTheme.DARK, gridSize = 88)
        coEvery { journalDao.getPendingImport() } returns BackupImportJournalEntity(
            previousSettingsJson = objectMapper.writeValueAsString(previous)
        )

        recovery.recoverInterruptedImport()

        coVerify { preferences.restoreSettingsOrThrow(previous) }
        coVerify { journalDao.clearPendingImport() }
    }

    @Test
    fun `keeps journal when settings rollback fails`() = runTest {
        val previous = UserSettings()
        coEvery { journalDao.getPendingImport() } returns BackupImportJournalEntity(
            previousSettingsJson = objectMapper.writeValueAsString(previous)
        )
        coEvery { preferences.restoreSettingsOrThrow(any()) } throws IOException("datastore failed")

        runCatching { recovery.recoverInterruptedImport() }

        coVerify(exactly = 0) { journalDao.clearPendingImport() }
    }
}
