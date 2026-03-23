package com.example.pocastcloni.domain.usecase.app

import android.net.Uri
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.BackupRepository // WICHTIG: Neues Repository importieren
import com.example.pocastcloni.domain.repository.ImportResult
import com.example.pocastcloni.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface BackupAction {
    data class Export(val path: String) : BackupAction

    data class Import(val path: String) : BackupAction
}

sealed interface BackupResult {
    data object ExportSuccess : BackupResult

    data class ImportSuccess(val result: ImportResult) : BackupResult
}

// Der Klassenname bleibt ManageBackupUseCase, wie in deiner Originaldatei definiert
class ManageBackupUseCase
@Inject
constructor(
    private val backupRepository: BackupRepository, // FIX: BackupRepository statt PodcastRepository nutzen
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(action: BackupAction): BackupResult {
        return withContext(dispatcherProvider.io) {
            when (action) {
                is BackupAction.Export -> {
                    val userSettings = userPreferencesRepository.userSettingsFlow.first()
                    // Aufruf geht an das BackupRepository
                    backupRepository.exportFullBackup(Uri.parse(action.path), userSettings)
                    BackupResult.ExportSuccess
                }
                is BackupAction.Import -> {
                    val settings = userPreferencesRepository.userSettingsFlow.first()
                    // Aufruf geht an das BackupRepository
                    val result =
                        backupRepository.importFullBackup(
                            Uri.parse(action.path),
                            settings.autoDownloadLimit,
                            settings.feedUpdateMode
                        )
                    BackupResult.ImportSuccess(result)
                }
            }
        }
    }
}
