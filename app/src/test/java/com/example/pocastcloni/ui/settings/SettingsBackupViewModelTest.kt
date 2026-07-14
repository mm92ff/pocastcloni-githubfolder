package com.example.pocastcloni.ui.settings

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
        val viewModel = SettingsBackupViewModel(scheduler)

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
        val viewModel = SettingsBackupViewModel(scheduler)
        advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(SettingsBackupViewModel.BackupUiState(), awaitItem())

            scheduler.jobState.value =
                listOf(
                    BackupJob(
                        id = "import-1",
                        generation = 1,
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
        val viewModel = SettingsBackupViewModel(scheduler)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()
            scheduler.jobState.value =
                listOf(
                    BackupJob(
                        id = "export-1",
                        generation = 1,
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

            scheduler.jobState.value =
                scheduler.jobState.value +
                BackupJob(
                    id = "export-2",
                    generation = 2,
                    operation = BackupJobOperation.EXPORT,
                    state = BackupJobState.Running
                )
            assertEquals(ExportUiState.Loading, awaitItem().exportState)
        }
    }

    private class FakeBackupJobScheduler : BackupJobScheduler {
        val jobState = MutableStateFlow<List<BackupJob>>(emptyList())
        val scheduledBackups = mutableListOf<ScheduledBackup>()

        override val jobs: Flow<List<BackupJob>> = jobState

        override fun enqueue(
            operation: BackupJobOperation,
            path: String
        ) {
            scheduledBackups += ScheduledBackup(operation, path)
        }
    }

    private data class ScheduledBackup(
        val operation: BackupJobOperation,
        val path: String
    )
}
