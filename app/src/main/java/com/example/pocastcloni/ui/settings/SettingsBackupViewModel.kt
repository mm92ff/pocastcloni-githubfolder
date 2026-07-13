package com.example.pocastcloni.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.R
import com.example.pocastcloni.data.worker.BackupWorker
import com.example.pocastcloni.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsBackupViewModel
@Inject
constructor(
    private val workManager: WorkManager
) : ViewModel() {
    private val dismissedWorkIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val backupWorkInfos =
        workManager
            .getWorkInfosByTagFlow(TAG_BACKUP_JOB)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    @Immutable
    data class BackupUiState(
        val importState: ImportUiState = ImportUiState.Idle,
        val exportState: ExportUiState = ExportUiState.Idle
    )

    val uiState: StateFlow<BackupUiState> =
        combine(backupWorkInfos, dismissedWorkIds) { workInfos, dismissedIds ->
            val activeWork = workInfos
                .filterNot { it.id in dismissedIds }
                .maxByOrNull { it.generation }
            mapWorkInfoToState(activeWork)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = BackupUiState()
            )

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ExportFullBackup ->
                enqueueBackupWork(
                    BackupWorker.ACTION_EXPORT,
                    event.path
                )
            is SettingsUiEvent.ImportFullBackup ->
                enqueueBackupWork(
                    BackupWorker.ACTION_IMPORT,
                    event.path
                )
            SettingsUiEvent.ResetImportState,
            SettingsUiEvent.ResetExportState
            -> {
                dismissedWorkIds.update { dismissedIds ->
                    dismissedIds + backupWorkInfos.value
                        .filter { it.state.isFinished }
                        .map { it.id }
                }
            }
            else -> Unit
        }
    }

    private fun enqueueBackupWork(
        action: String,
        path: String
    ) {
        val inputData =
            Data.Builder()
                .putString(BackupWorker.KEY_ACTION_TYPE, action)
                .putString(BackupWorker.KEY_URI_PATH, path)
                .build()

        val request =
            OneTimeWorkRequest.Builder(BackupWorker::class.java)
                .setInputData(inputData)
                .setConstraints(Constraints.NONE)
                .addTag(TAG_BACKUP_JOB)
                .addTag(if (action == BackupWorker.ACTION_IMPORT) TAG_IMPORT else TAG_EXPORT)
                .build()

        workManager.enqueueUniqueWork(
            UNIQUE_BACKUP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun mapWorkInfoToState(workInfo: WorkInfo?): BackupUiState {
        if (workInfo == null) {
            return BackupUiState() // Both Idle
        }

        val isImport = workInfo.tags.contains(TAG_IMPORT)

        // Determine the state of the active operation
        val activeState =
            when (workInfo.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                    if (isImport) ImportUiState.Loading else ExportUiState.Loading
                }
                WorkInfo.State.SUCCEEDED -> {
                    if (isImport) {
                        val success = workInfo.outputData.getInt(BackupWorker.KEY_IMPORT_SUCCESS_COUNT, 0)
                        val total = workInfo.outputData.getInt(BackupWorker.KEY_IMPORT_TOTAL_COUNT, 0)
                        val skippedFavorites = workInfo.outputData.getInt(
                            BackupWorker.KEY_IMPORT_SKIPPED_FAVORITES,
                            0
                        )
                        ImportUiState.Success(
                            UiText.StringResource(
                                R.string.import_success_message,
                                success,
                                total,
                                skippedFavorites
                            )
                        )
                    } else {
                        ExportUiState.Success(UiText.StringResource(R.string.export_success_message))
                    }
                }
                WorkInfo.State.FAILED -> {
                    val errorMsg = workInfo.outputData.getString(BackupWorker.KEY_ERROR_MESSAGE) ?: ""
                    // Pass empty string if errorMsg is empty to match original code structure,
                    // assuming the resource expects a string arg.
                    val safeArg = errorMsg.ifBlank { "" }

                    if (isImport) {
                        ImportUiState.Error(UiText.StringResource(R.string.import_error_message, safeArg))
                    } else {
                        ExportUiState.Error(UiText.StringResource(R.string.export_error_message, safeArg))
                    }
                }
                WorkInfo.State.CANCELLED -> {
                    if (isImport) ImportUiState.Idle else ExportUiState.Idle
                }
                WorkInfo.State.BLOCKED -> {
                    if (isImport) ImportUiState.Loading else ExportUiState.Loading
                }
            }

        return if (isImport) {
            BackupUiState(
                importState = activeState as ImportUiState,
                exportState = ExportUiState.Idle
            )
        } else {
            BackupUiState(
                importState = ImportUiState.Idle,
                exportState = activeState as ExportUiState
            )
        }
    }

    companion object {
        private const val TAG_BACKUP_JOB = "backup_job"
        private const val TAG_IMPORT = "backup_import"
        private const val TAG_EXPORT = "backup_export"
        private const val UNIQUE_BACKUP_WORK_NAME = "backup_work"
    }
}
