package com.example.pocastcloni.data.worker

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LibraryCleanupSchedulerTest {
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val scheduler = LibraryCleanupScheduler(workManager)

    @Test
    fun `disabled cleanup cancels unique work and schedules nothing`() {
        scheduler.applySettings(enabled = false, intervalHours = 24)

        verify(exactly = 1) { workManager.cancelUniqueWork(LibraryCleanupWorker.WORK_NAME) }
        verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
    }

    @Test
    fun `enabled cleanup schedules one constrained unique worker`() {
        val request = slot<PeriodicWorkRequest>()

        scheduler.applySettings(enabled = true, intervalHours = 48)

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                LibraryCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                capture(request)
            )
        }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
        assertEquals(TimeUnit.HOURS.toMillis(48), request.captured.workSpec.intervalDuration)
        val schedule = libraryCleanupSchedule(48)
        assertTrue(schedule.requiresDeviceIdle)
        assertTrue(schedule.requiresBatteryNotLow)
    }
}
