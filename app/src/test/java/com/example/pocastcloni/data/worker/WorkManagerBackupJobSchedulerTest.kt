package com.example.pocastcloni.data.worker

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.domain.backup.BackupJobOperation
import com.example.pocastcloni.domain.backup.BackupFailureReason
import com.example.pocastcloni.domain.backup.BackupJobProgress
import com.example.pocastcloni.domain.backup.BackupJobResult
import com.example.pocastcloni.domain.backup.BackupJobState
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WorkManagerBackupJobSchedulerTest {
    private val workManager = mockk<WorkManager>()
    private val scheduler = WorkManagerBackupJobScheduler(workManager)

    @Test
    fun `import enqueue preserves unique replace policy input and tags`() {
        val request = slot<OneTimeWorkRequest>()
        every {
            workManager.enqueueUniqueWork(
                WorkManagerBackupJobScheduler.UNIQUE_BACKUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        } returns mockk<Operation>(relaxed = true)

        val jobId = scheduler.enqueue(BackupJobOperation.IMPORT, "content://backup/import.json")

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                WorkManagerBackupJobScheduler.UNIQUE_BACKUP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request.captured
            )
        }
        assertEquals(
            BackupWorker.ACTION_IMPORT,
            request.captured.workSpec.input.getString(BackupWorker.KEY_ACTION_TYPE)
        )
        assertEquals(
            "content://backup/import.json",
            request.captured.workSpec.input.getString(BackupWorker.KEY_URI_PATH)
        )
        assertTrue(request.captured.tags.contains(WorkManagerBackupJobScheduler.TAG_BACKUP_JOB))
        assertTrue(request.captured.tags.contains(WorkManagerBackupJobScheduler.TAG_IMPORT))
        assertEquals(request.captured.id.toString(), jobId)
    }

    @Test
    fun `successful import maps result and progress without WorkManager types`() {
        val jobId = UUID.randomUUID()
        val output =
            Data.Builder()
                .putInt(BackupWorker.KEY_IMPORT_SUCCESS_COUNT, 4)
                .putInt(BackupWorker.KEY_IMPORT_TOTAL_COUNT, 6)
                .putInt(BackupWorker.KEY_IMPORT_SKIPPED_FAVORITES, 2)
                .build()
        val progress =
            Data.Builder()
                .putInt(BackupWorker.KEY_IMPORT_SUCCESS_COUNT, 3)
                .putInt(BackupWorker.KEY_IMPORT_TOTAL_COUNT, 6)
                .build()
        val workInfo = workInfo(
            id = jobId,
            state = WorkInfo.State.SUCCEEDED,
            tags = setOf(WorkManagerBackupJobScheduler.TAG_IMPORT),
            output = output,
            progress = progress
        )

        val job = requireNotNull(scheduler.toBackupJob(workInfo))

        assertEquals(jobId.toString(), job.id)
        assertEquals(BackupJobOperation.IMPORT, job.operation)
        assertEquals(
            BackupJobState.Succeeded(BackupJobResult(4, 6, 2)),
            job.state
        )
        assertEquals(BackupJobProgress(3, 6), job.progress)
    }

    @Test
    fun `failed export maps failure and omits import result`() {
        val workInfo = workInfo(
            state = WorkInfo.State.FAILED,
            tags = setOf(WorkManagerBackupJobScheduler.TAG_EXPORT),
            output =
            Data.Builder()
                .putString(BackupWorker.KEY_ERROR_CODE, BackupFailureReason.FILE_ACCESS.name)
                .build()
        )

        val job = requireNotNull(scheduler.toBackupJob(workInfo))

        assertEquals(BackupJobOperation.EXPORT, job.operation)
        assertEquals(BackupJobState.Failed(BackupFailureReason.FILE_ACCESS), job.state)
        assertNull(job.progress)
    }

    @Test
    fun `missing or unknown failure code maps to unknown and ignores localized legacy message`() {
        listOf(
            Data.Builder().putString("error_message", "Datei konnte nicht gelesen werden").build(),
            Data.Builder().putString(BackupWorker.KEY_ERROR_CODE, "UNRECOGNIZED").build()
        ).forEach { output ->
            val workInfo = workInfo(
                state = WorkInfo.State.FAILED,
                tags = setOf(WorkManagerBackupJobScheduler.TAG_IMPORT),
                output = output
            )

            val job = requireNotNull(scheduler.toBackupJob(workInfo))

            assertEquals(BackupJobState.Failed(BackupFailureReason.UNKNOWN), job.state)
        }
    }

    @Test
    fun `unrelated work is not exposed as a backup job`() {
        val workInfo = workInfo(
            state = WorkInfo.State.RUNNING,
            tags = setOf("unrelated")
        )

        assertNull(scheduler.toBackupJob(workInfo))
    }

    private fun workInfo(
        id: UUID = UUID.randomUUID(),
        state: WorkInfo.State,
        tags: Set<String>,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY
    ): WorkInfo =
        mockk<WorkInfo>().also { workInfo ->
            every { workInfo.id } returns id
            every { workInfo.state } returns state
            every { workInfo.tags } returns tags
            every { workInfo.outputData } returns output
            every { workInfo.progress } returns progress
        }
}
