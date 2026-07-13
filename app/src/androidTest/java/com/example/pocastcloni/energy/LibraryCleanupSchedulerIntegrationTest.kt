package com.example.pocastcloni.energy

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.data.worker.LibraryCleanupScheduler
import com.example.pocastcloni.data.worker.LibraryCleanupWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LibraryCleanupSchedulerIntegrationTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: LibraryCleanupScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        workManager = WorkManager.getInstance(context)
        scheduler = LibraryCleanupScheduler(workManager)
        scheduler.applySettings(enabled = false, intervalHours = CLEANUP_INTERVAL_HOURS)
        awaitActiveWorkCount(expectedCount = 0)
    }

    @After
    fun tearDown() {
        if (::scheduler.isInitialized) {
            scheduler.applySettings(enabled = false, intervalHours = CLEANUP_INTERVAL_HOURS)
            awaitActiveWorkCount(expectedCount = 0)
        }
    }

    @Test
    fun schedulingTwiceLeavesOneActivePeriodicWorkAndDisablingRemovesIt() {
        scheduler.applySettings(enabled = true, intervalHours = CLEANUP_INTERVAL_HOURS)
        scheduler.applySettings(enabled = true, intervalHours = CLEANUP_INTERVAL_HOURS)

        val activeWork = awaitActiveWorkCount(expectedCount = 1)
        assertEquals(1, activeWork.size)
        assertNotNull(activeWork.single().periodicityInfo)

        scheduler.applySettings(enabled = false, intervalHours = CLEANUP_INTERVAL_HOURS)

        assertEquals(0, awaitActiveWorkCount(expectedCount = 0).size)
    }

    private fun awaitActiveWorkCount(expectedCount: Int): List<WorkInfo> {
        val deadline = SystemClock.elapsedRealtime() + WORK_STATE_TIMEOUT_MS
        var workInfos = queryUniqueWork()
        var activeWork = workInfos.filterNot { it.state.isFinished }

        while (activeWork.size != expectedCount && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            workInfos = queryUniqueWork()
            activeWork = workInfos.filterNot { it.state.isFinished }
        }

        assertEquals(
            "Timed out waiting for $expectedCount active work item(s); observed states=" +
                workInfos.joinToString { it.state.name },
            expectedCount,
            activeWork.size
        )
        return activeWork
    }

    private fun queryUniqueWork(): List<WorkInfo> =
        workManager
            .getWorkInfosForUniqueWork(LibraryCleanupWorker.WORK_NAME)
            .get(WORK_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    companion object {
        private const val CLEANUP_INTERVAL_HOURS = 24
        private const val WORK_STATE_TIMEOUT_MS = 10_000L
        private const val WORK_QUERY_TIMEOUT_SECONDS = 5L
        private const val POLL_INTERVAL_MS = 100L
    }
}
