package com.example.pocastcloni.ui.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.backup.BackupJob
import com.example.pocastcloni.domain.backup.BackupJobOperation
import com.example.pocastcloni.domain.backup.BackupJobResult
import com.example.pocastcloni.domain.backup.BackupJobScheduler
import com.example.pocastcloni.domain.backup.BackupJobState
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `events schedule neutral import and export operations`() = runTest(dispatcher) {
        val scheduler = FakeBackupJobScheduler()
        val viewModel = SettingsBackupViewModel(scheduler, SavedStateHandle())

        viewModel.onEvent(SettingsUiEvent.ImportFullBackup("content://backup/import.json"))
        viewModel.onEvent(SettingsUiEvent.ExportFullBackup("content://backup/export.json"))

        assertEquals(
            listOf(
                ScheduledBackup(BackupJobOperation.IMPORT, "content://backup/import.json"),
                ScheduledBackup(BackupJobOperation.EXPORT, "content://backup/export.json")
            ),
            scheduler.scheduledBackups
        )
    }

    @Test
    fun `import success maps neutral result to existing UI message`() = runTest(dispatcher) {
        val scheduler = FakeBackupJobScheduler()
        val viewModel = SettingsBackupViewModel(scheduler, SavedStateHandle())
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(SettingsBackupViewModel.BackupUiState(), awaitItem())

            viewModel.onEvent(SettingsUiEvent.ImportFullBackup("content://backup/import.json"))
            val jobId = requireNotNull(scheduler.lastScheduledJobId)

            scheduler.jobState.value =
                listOf(
                    BackupJob(
                        id = jobId,
                        operation = BackupJobOperation.IMPORT,
                        state = BackupJobState.Succeeded(BackupJobResult(4, 6, 2))
                    )
                )

            val state = awaitItem()
            assertEquals(
                ImportUiState.Success(
                    UiText.StringResource(R.string.import_success_message, 4, 6, 2)
                ),
                state.importState
            )
            assertEquals(ExportUiState.Idle, state.exportState)
        }
    }

    @Test
    fun `reset dismisses finished result without hiding a later job`() = runTest(dispatcher) {
        val scheduler = FakeBackupJobScheduler()
        val viewModel = SettingsBackupViewModel(scheduler, SavedStateHandle())
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onEvent(SettingsUiEvent.ExportFullBackup("content://backup/export-1.json"))
            val firstJobId = requireNotNull(scheduler.lastScheduledJobId)
            scheduler.jobState.value =
                listOf(
                    BackupJob(
                        id = firstJobId,
                        operation = BackupJobOperation.EXPORT,
                        state = BackupJobState.Failed("disk full")
                    )
                )
            assertEquals(
                ExportUiState.Error(
                    UiText.StringResource(R.string.export_error_message, "disk full")
                ),
                awaitItem().exportState
            )

            viewModel.onEvent(SettingsUiEvent.ResetExportState)
            assertEquals(SettingsBackupViewModel.BackupUiState(), awaitItem())

            viewModel.onEvent(SettingsUiEvent.ExportFullBackup("content://backup/export-2.json"))
            val secondJobId = requireNotNull(scheduler.lastScheduledJobId)
            scheduler.jobState.value =
                scheduler.jobState.value +
                BackupJob(
                    id = secondJobId,
                    operation = BackupJobOperation.EXPORT,
                    state = BackupJobState.Running
                )
            assertEquals(ExportUiState.Loading, awaitItem().exportState)
        }
    }

    @Test
    fun `finished job from before view model creation stays idle`() = runTest(dispatcher) {
        val scheduler = FakeBackupJobScheduler()
        scheduler.jobState.value =
            listOf(
                BackupJob(
                    id = "old-export",
                    operation = BackupJobOperation.EXPORT,
                    state = BackupJobState.Succeeded()
                )
            )
        val viewModel = SettingsBackupViewModel(scheduler, SavedStateHandle())
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(SettingsBackupViewModel.BackupUiState(), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `saved state restores only the explicitly tracked job`() = runTest(dispatcher) {
        val scheduler = FakeBackupJobScheduler()
        val savedStateHandle = SavedStateHandle()
        val originalViewModel = SettingsBackupViewModel(scheduler, savedStateHandle)
        originalViewModel.onEvent(SettingsUiEvent.ExportFullBackup("content://backup/export.json"))
        val jobId = requireNotNull(scheduler.lastScheduledJobId)
        scheduler.jobState.value =
            listOf(
                BackupJob(
                    id = jobId,
                    operation = BackupJobOperation.EXPORT,
                    state = BackupJobState.Succeeded()
                )
            )

        val restoredViewModel = SettingsBackupViewModel(scheduler, savedStateHandle)
        restoredViewModel.uiState.test {
            assertEquals(SettingsBackupViewModel.BackupUiState(), awaitItem())
            assertEquals(
                ExportUiState.Success(UiText.StringResource(R.string.export_success_message)),
                awaitItem().exportState
            )
        }
    }

    private class FakeBackupJobScheduler : BackupJobScheduler {
        val jobState = MutableStateFlow<List<BackupJob>>(emptyList())
        val scheduledBackups = mutableListOf<ScheduledBackup>()
        var lastScheduledJobId: String? = null
            private set

        private var nextJobNumber = 0

        override val jobs: Flow<List<BackupJob>> = jobState

        override fun enqueue(
            operation: BackupJobOperation,
            path: String
        ): String {
            val jobId = "backup-${++nextJobNumber}"
            lastScheduledJobId = jobId
            scheduledBackups += ScheduledBackup(operation, path)
            return jobId
        }
    }

    private data class ScheduledBackup(
        val operation: BackupJobOperation,
        val path: String
    )
}
