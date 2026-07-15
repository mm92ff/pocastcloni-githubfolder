package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.fasterxml.jackson.databind.ObjectMapper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores settings recorded by an interrupted import before clearing its journal entry.
 * Failures and cancellation leave the journal intact for the next recovery attempt. The locked
 * entry point must only be called while holding [BackupImportCoordinator].
 */
@Singleton
class BackupImportRecovery @Inject constructor(
    private val journalDao: BackupImportJournalDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val objectMapper: ObjectMapper,
    private val coordinator: BackupImportCoordinator
) {
    suspend fun recoverInterruptedImport() =
        coordinator.runExclusive {
            recoverInterruptedImportLocked()
        }

    /** Recovery variant for callers already serialized by [BackupImportCoordinator]. */
    internal suspend fun recoverInterruptedImportLocked() {
        val pending = journalDao.getPendingImport() ?: return
        val previousSettings = objectMapper.readValue(
            pending.previousSettingsJson,
            UserSettings::class.java
        )
        userPreferencesRepository.restoreSettingsOrThrow(previousSettings)
        journalDao.clearPendingImport()
        Timber.w("Recovered settings after interrupted backup import")
    }
}
