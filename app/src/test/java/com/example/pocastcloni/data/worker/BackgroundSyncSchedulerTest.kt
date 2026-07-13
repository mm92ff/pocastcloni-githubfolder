package com.example.pocastcloni.data.worker

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.pocastcloni.util.Constants
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

class BackgroundSyncSchedulerTest {
    private val workManager = mockk<WorkManager>()
    private val operation = mockk<Operation>()
    private val scheduler = BackgroundSyncScheduler(workManager)

    init {
        every { operation.result } returns Futures.immediateFuture(Operation.SUCCESS)
        every { workManager.cancelUniqueWork(any()) } returns operation
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns operation
    }

    @Test
    fun `disabled sync cancels the single unique owner`() = runTest {
        scheduler.applySettings(enabled = false, intervalHours = 6)

        verify(exactly = 1) { workManager.cancelUniqueWork(Constants.FEED_UPDATE_WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `enabled sync normalizes interval constraints and backoff`() = runTest {
        val request = slot<PeriodicWorkRequest>()

        scheduler.applySettings(enabled = true, intervalHours = Int.MAX_VALUE)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                Constants.FEED_UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                capture(request)
            )
        }
        assertEquals(
            TimeUnit.HOURS.toMillis(Constants.SettingsDefaults.MAX_BACKGROUND_CHECK_INTERVAL_HOURS.toLong()),
            request.captured.workSpec.intervalDuration
        )
        assertEquals(NetworkType.CONNECTED, request.captured.workSpec.constraints.requiredNetworkType)
        assertTrue(request.captured.workSpec.constraints.requiresBatteryNotLow())
        assertEquals(BackoffPolicy.EXPONENTIAL, request.captured.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30L), request.captured.workSpec.backoffDelayDuration)
    }
}
