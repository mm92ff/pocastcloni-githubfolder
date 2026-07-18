package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.fasterxml.jackson.core.JsonProcessingException
import com.example.pocastcloni.domain.backup.BackupFailureReason
import com.example.pocastcloni.domain.usecase.app.BackupAction
import com.example.pocastcloni.domain.usecase.app.BackupResult
import com.example.pocastcloni.domain.usecase.app.ManageBackupUseCase
import com.example.pocastcloni.util.SizeLimitExceededException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException

@HiltWorker
class BackupWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val manageBackupUseCase: ManageBackupUseCase
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val actionType = inputData.getString(KEY_ACTION_TYPE)
        val path = inputData.getString(KEY_URI_PATH)

        if (path.isNullOrBlank()) {
            Timber.w("Backup operation rejected because the path is missing")
            return failureResult(BackupFailureReason.INVALID_REQUEST)
        }

        return try {
            val action =
                when (actionType) {
                    ACTION_EXPORT -> BackupAction.Export(path)
                    ACTION_IMPORT -> BackupAction.Import(path)
                    else -> throw IllegalArgumentException("Unknown action type: $actionType")
                }

            val result = manageBackupUseCase(action)
            createOutputData(result)
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            Timber.e(e, "Backup operation failed")
            failureResult(classifyBackupFailure(actionType, e))
        }
    }

    private fun failureResult(reason: BackupFailureReason): Result =
        Result.failure(
            Data.Builder()
                .putString(KEY_ERROR_CODE, reason.name)
                .build()
        )

    private fun createOutputData(result: BackupResult): Result {
        return when (result) {
            is BackupResult.ExportSuccess -> Result.success()
            is BackupResult.ImportSuccess -> {
                Result.success(
                    Data.Builder()
                        .putInt(KEY_IMPORT_SUCCESS_COUNT, result.result.success)
                        .putInt(KEY_IMPORT_TOTAL_COUNT, result.result.total)
                        .putInt(KEY_IMPORT_SKIPPED_FAVORITES, result.result.skippedFavorites)
                        .build()
                )
            }
        }
    }

    companion object {
        const val KEY_ACTION_TYPE = "action_type"
        const val KEY_URI_PATH = "uri_path"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_IMPORT_SUCCESS_COUNT = "import_success_count"
        const val KEY_IMPORT_TOTAL_COUNT = "import_total_count"
        const val KEY_IMPORT_SKIPPED_FAVORITES = "import_skipped_favorites"

        const val ACTION_EXPORT = "EXPORT"
        const val ACTION_IMPORT = "IMPORT"
    }
}

internal fun classifyBackupFailure(
    actionType: String?,
    error: Throwable
): BackupFailureReason =
    when {
        actionType != BackupWorker.ACTION_IMPORT && actionType != BackupWorker.ACTION_EXPORT ->
            BackupFailureReason.INVALID_REQUEST
        actionType == BackupWorker.ACTION_IMPORT && error is SizeLimitExceededException ->
            BackupFailureReason.INVALID_BACKUP
        actionType == BackupWorker.ACTION_IMPORT && error is JsonProcessingException ->
            BackupFailureReason.INVALID_BACKUP
        actionType == BackupWorker.ACTION_IMPORT && error is IllegalArgumentException ->
            BackupFailureReason.INVALID_BACKUP
        error is IOException || error is SecurityException -> BackupFailureReason.FILE_ACCESS
        else -> BackupFailureReason.UNKNOWN
    }
