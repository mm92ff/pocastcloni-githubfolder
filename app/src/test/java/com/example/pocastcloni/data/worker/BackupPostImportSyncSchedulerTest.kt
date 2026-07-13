package com.example.pocastcloni.data.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import com.example.pocastcloni.domain.usecase.podcast.FeedRefreshSource
import com.google.common.util.concurrent.Futures
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackupPostImportSyncSchedulerTest {
    private val workManager = mockk<WorkManager>()
    private val scheduler = BackupPostImportSyncScheduler(workManager)

    @Test
    fun `enqueues and awaits unique constrained backup refresh`() = runTest {
        val request = slot<OneTimeWorkRequest>()
        val operation = mockk<Operation>()
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every {
            workManager.enqueueUniqueWork(
                BackupPostImportSyncScheduler.POST_IMPORT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                capture(request)
            )
        } returns operation

        scheduler.schedule()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                BackupPostImportSyncScheduler.POST_IMPORT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request.captured
            )
        }
        assertEquals(
            FeedRefreshSource.BACKUP_RESTORE.name,
            request.captured.workSpec.input.getString(FeedUpdateWorker.KEY_REFRESH_SOURCE)
        )
        assertEquals(
            NetworkType.CONNECTED,
            request.captured.workSpec.constraints.requiredNetworkType
        )
        assertTrue(request.captured.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, request.captured.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30L), request.captured.workSpec.backoffDelayDuration)
        assertTrue(request.captured.tags.contains(BackupPostImportSyncScheduler.POST_IMPORT_WORK_TAG))
    }
}
