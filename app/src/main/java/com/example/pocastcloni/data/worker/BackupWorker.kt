package com.example.pocastcloni.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.pocastcloni.domain.usecase.app.BackupAction
import com.example.pocastcloni.domain.usecase.app.BackupResult
import com.example.pocastcloni.domain.usecase.app.ManageBackupUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val manageBackupUseCase: ManageBackupUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val actionType = inputData.getString(KEY_ACTION_TYPE)
        val path = inputData.getString(KEY_URI_PATH)

        if (path.isNullOrBlank()) {
            return Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "Invalid path provided")
                    .build()
            )
        }

        return try {
            val action = when (actionType) {
                ACTION_EXPORT -> BackupAction.Export(path)
                ACTION_IMPORT -> BackupAction.Import(path)
                else -> throw IllegalArgumentException("Unknown action type: $actionType")
            }

            val result = manageBackupUseCase(action)
            createOutputData(result)
        } catch (e: Exception) {
            Timber.e(e, "Backup operation failed")
            Result.failure(
                Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, e.localizedMessage ?: "Unknown error")
                    .build()
            )
        }
    }

    private fun createOutputData(result: BackupResult): Result {
        return when (result) {
            is BackupResult.ExportSuccess -> Result.success()
            is BackupResult.ImportSuccess -> {
                Result.success(
                    Data.Builder()
                        .putInt(KEY_IMPORT_SUCCESS_COUNT, result.result.success)
                        .putInt(KEY_IMPORT_TOTAL_COUNT, result.result.total)
                        .build()
                )
            }
        }
    }

    companion object {
        const val KEY_ACTION_TYPE = "action_type"
        const val KEY_URI_PATH = "uri_path"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_IMPORT_SUCCESS_COUNT = "import_success_count"
        const val KEY_IMPORT_TOTAL_COUNT = "import_total_count"

        const val ACTION_EXPORT = "EXPORT"
        const val ACTION_IMPORT = "IMPORT"
    }
}
