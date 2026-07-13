package com.example.pocastcloni.energy

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pocastcloni.data.worker.BackgroundSyncScheduler
import com.example.pocastcloni.util.Constants
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackgroundSyncSchedulerIntegrationTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: BackgroundSyncScheduler

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            workManager = WorkManager.getInstance(context)
            scheduler = BackgroundSyncScheduler(workManager)
            scheduler.applySettings(enabled = false, intervalHours = INTERVAL_HOURS)
            awaitActiveWorkCount(expectedCount = 0)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            if (::scheduler.isInitialized) {
                scheduler.applySettings(enabled = false, intervalHours = INTERVAL_HOURS)
                awaitActiveWorkCount(expectedCount = 0)
            }
        }
    }

    @Test
    fun repeatedSettingsApplicationLeavesExactlyOneUniquePeriodicSync() {
        runBlocking {
            scheduler.applySettings(enabled = true, intervalHours = INTERVAL_HOURS)
            scheduler.applySettings(enabled = true, intervalHours = INTERVAL_HOURS)

            val activeWork = awaitActiveWorkCount(expectedCount = 1)
            assertEquals(1, activeWork.size)
            assertNotNull(activeWork.single().periodicityInfo)
        }
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
            "Timed out waiting for $expectedCount active sync work item(s); observed states=" +
                workInfos.joinToString { it.state.name },
            expectedCount,
            activeWork.size
        )
        return activeWork
    }

    private fun queryUniqueWork(): List<WorkInfo> =
        workManager
            .getWorkInfosForUniqueWork(Constants.FEED_UPDATE_WORK_NAME)
            .get(WORK_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    companion object {
        private const val INTERVAL_HOURS = 6
        private const val WORK_STATE_TIMEOUT_MS = 10_000L
        private const val WORK_QUERY_TIMEOUT_SECONDS = 5L
        private const val POLL_INTERVAL_MS = 100L
    }
}
