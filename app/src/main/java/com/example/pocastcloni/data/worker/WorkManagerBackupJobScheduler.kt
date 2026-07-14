package com.example.pocastcloni.data.worker

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.domain.backup.BackupJob
import com.example.pocastcloni.domain.backup.BackupJobOperation
import com.example.pocastcloni.domain.backup.BackupJobProgress
import com.example.pocastcloni.domain.backup.BackupJobResult
import com.example.pocastcloni.domain.backup.BackupJobScheduler
import com.example.pocastcloni.domain.backup.BackupJobState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerBackupJobScheduler
@Inject
constructor(
    private val workManager: WorkManager
) : BackupJobScheduler {
    override val jobs: Flow<List<BackupJob>>
        get() =
            workManager.getWorkInfosByTagFlow(TAG_BACKUP_JOB).map { workInfos ->
                workInfos.mapNotNull(::toBackupJob)
            }

    override fun enqueue(
        operation: BackupJobOperation,
        path: String
    ): String {
        val inputData =
            Data.Builder()
                .putString(BackupWorker.KEY_ACTION_TYPE, operation.toWorkerAction())
                .putString(BackupWorker.KEY_URI_PATH, path)
                .build()
        val request =
            OneTimeWorkRequest.Builder(BackupWorker::class.java)
                .setInputData(inputData)
                .setConstraints(Constraints.NONE)
                .addTag(TAG_BACKUP_JOB)
                .addTag(operation.toWorkTag())
                .build()

        workManager.enqueueUniqueWork(
            UNIQUE_BACKUP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return request.id.toString()
    }

    internal fun toBackupJob(workInfo: WorkInfo): BackupJob? {
        val operation = workInfo.tags.toBackupOperation() ?: return null
        return BackupJob(
            id = workInfo.id.toString(),
            operation = operation,
            state = workInfo.toBackupJobState(operation),
            progress = workInfo.progress.toBackupJobProgress()
        )
    }

    private fun WorkInfo.toBackupJobState(operation: BackupJobOperation): BackupJobState =
        when (state) {
            WorkInfo.State.ENQUEUED -> BackupJobState.Enqueued
            WorkInfo.State.RUNNING -> BackupJobState.Running
            WorkInfo.State.BLOCKED -> BackupJobState.Blocked
            WorkInfo.State.SUCCEEDED ->
                BackupJobState.Succeeded(
                    result = outputData.toBackupJobResult(operation)
                )
            WorkInfo.State.FAILED ->
                BackupJobState.Failed(
                    message = outputData.getString(BackupWorker.KEY_ERROR_MESSAGE).orEmpty()
                )
            WorkInfo.State.CANCELLED -> BackupJobState.Cancelled
        }

    private fun Data.toBackupJobResult(operation: BackupJobOperation): BackupJobResult? {
        if (operation != BackupJobOperation.IMPORT) return null
        return BackupJobResult(
            importedCount = getInt(BackupWorker.KEY_IMPORT_SUCCESS_COUNT, 0),
            totalCount = getInt(BackupWorker.KEY_IMPORT_TOTAL_COUNT, 0),
            skippedFavorites = getInt(BackupWorker.KEY_IMPORT_SKIPPED_FAVORITES, 0)
        )
    }

    private fun Data.toBackupJobProgress(): BackupJobProgress? {
        val values = keyValueMap
        val hasCompleted = BackupWorker.KEY_IMPORT_SUCCESS_COUNT in values
        val hasTotal = BackupWorker.KEY_IMPORT_TOTAL_COUNT in values
        if (!hasCompleted && !hasTotal) return null
        return BackupJobProgress(
            completedCount = getInt(BackupWorker.KEY_IMPORT_SUCCESS_COUNT, 0),
            totalCount = getInt(BackupWorker.KEY_IMPORT_TOTAL_COUNT, 0)
        )
    }

    private fun BackupJobOperation.toWorkerAction(): String =
        when (this) {
            BackupJobOperation.IMPORT -> BackupWorker.ACTION_IMPORT
            BackupJobOperation.EXPORT -> BackupWorker.ACTION_EXPORT
        }

    private fun BackupJobOperation.toWorkTag(): String =
        when (this) {
            BackupJobOperation.IMPORT -> TAG_IMPORT
            BackupJobOperation.EXPORT -> TAG_EXPORT
        }

    private fun Set<String>.toBackupOperation(): BackupJobOperation? =
        when {
            TAG_IMPORT in this -> BackupJobOperation.IMPORT
            TAG_EXPORT in this -> BackupJobOperation.EXPORT
            else -> null
        }

    internal companion object {
        const val TAG_BACKUP_JOB = "backup_job"
        const val TAG_IMPORT = "backup_import"
        const val TAG_EXPORT = "backup_export"
        const val UNIQUE_BACKUP_WORK_NAME = "backup_work"
    }
}
