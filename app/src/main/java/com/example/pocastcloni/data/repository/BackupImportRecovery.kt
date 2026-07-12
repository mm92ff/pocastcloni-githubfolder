package com.example.pocastcloni.data.repository

import com.example.pocastcloni.data.local.BackupImportJournalDao
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import com.example.pocastcloni.domain.repository.UserSettings
import com.fasterxml.jackson.databind.ObjectMapper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImportRecovery @Inject constructor(
    private val journalDao: BackupImportJournalDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val objectMapper: ObjectMapper
) {
    suspend fun recoverInterruptedImport() {
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
