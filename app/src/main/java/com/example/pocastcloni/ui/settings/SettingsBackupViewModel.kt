package com.example.pocastcloni.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.backup.BackupJob
import com.example.pocastcloni.domain.backup.BackupJobOperation
import com.example.pocastcloni.domain.backup.BackupJobResult
import com.example.pocastcloni.domain.backup.BackupJobScheduler
import com.example.pocastcloni.domain.backup.BackupJobState
import com.example.pocastcloni.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsBackupViewModel
@Inject
constructor(
    private val backupJobScheduler: BackupJobScheduler,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val trackedJobId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_TRACKED_JOB_ID, null)
    private val backupJobs =
        backupJobScheduler.jobs
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
        combine(backupJobs, trackedJobId) { jobs, jobId ->
            mapJobToState(jobs.firstOrNull { it.id == jobId })
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = BackupUiState()
            )

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ExportFullBackup ->
                trackJob(BackupJobOperation.EXPORT, event.path)
            is SettingsUiEvent.ImportFullBackup ->
                trackJob(BackupJobOperation.IMPORT, event.path)
            SettingsUiEvent.ResetImportState,
            SettingsUiEvent.ResetExportState
            -> {
                savedStateHandle[KEY_TRACKED_JOB_ID] = null
            }
            else -> Unit
        }
    }

    private fun trackJob(
        operation: BackupJobOperation,
        path: String
    ) {
        savedStateHandle[KEY_TRACKED_JOB_ID] = backupJobScheduler.enqueue(operation, path)
    }

    private fun mapJobToState(job: BackupJob?): BackupUiState {
        if (job == null) return BackupUiState()
        return if (job.operation == BackupJobOperation.IMPORT) {
            BackupUiState(
                importState = job.state.toImportUiState(),
                exportState = ExportUiState.Idle
            )
        } else {
            BackupUiState(
                importState = ImportUiState.Idle,
                exportState = job.state.toExportUiState()
            )
        }
    }

    private fun BackupJobState.toImportUiState(): ImportUiState =
        when (this) {
            BackupJobState.Enqueued,
            BackupJobState.Running,
            BackupJobState.Blocked
            -> ImportUiState.Loading
            is BackupJobState.Succeeded -> result.toImportSuccessState()
            is BackupJobState.Failed ->
                ImportUiState.Error(UiText.StringResource(R.string.import_error_message, message))
            BackupJobState.Cancelled -> ImportUiState.Idle
        }

    private fun BackupJobState.toExportUiState(): ExportUiState =
        when (this) {
            BackupJobState.Enqueued,
            BackupJobState.Running,
            BackupJobState.Blocked
            -> ExportUiState.Loading
            is BackupJobState.Succeeded ->
                ExportUiState.Success(UiText.StringResource(R.string.export_success_message))
            is BackupJobState.Failed ->
                ExportUiState.Error(UiText.StringResource(R.string.export_error_message, message))
            BackupJobState.Cancelled -> ExportUiState.Idle
        }

    private fun BackupJobResult?.toImportSuccessState(): ImportUiState.Success {
        val result = this ?: BackupJobResult(0, 0, 0)
        return ImportUiState.Success(
            UiText.StringResource(
                R.string.import_success_message,
                result.importedCount,
                result.totalCount,
                result.skippedFavorites
            )
        )
    }

    private companion object {
        const val KEY_TRACKED_JOB_ID = "tracked_backup_job_id"
    }
}
